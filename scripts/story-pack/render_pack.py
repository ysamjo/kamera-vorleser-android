#!/usr/bin/env python3
"""Rendert bereinigte Story-Pack-Skripte (script.jsonl) zu Opus-Audios + index.jsonl."""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))
import dialogue_postprocess as dp  # noqa: E402

ROLE_NAMES: dict[int, str] = {slot: label for slot, label, _ in dp.POKOPIA_ROLES}

FISH_ENDPOINT = "https://api.fish.audio/v1/tts"
FISH_SAMPLE_RATE = 24000

# Exakte 15 Fish-Stimmen aus CloudVoiceProfiles.FISH_DEFAULTS (Slots 0..14)
FISH_VOICES: tuple[str, ...] = (
    "c079932b47564fcfad0a9f6874a49df7",  # 0: Erzähler
    "77e3908a672a4b43b30786e52655d1bc",  # 1: Pikachu
    "88b18e0d81474a0ca08e2ea6f9df5ff4",  # 2: Evoli
    "3130ae7c3d844e2998978289c7c11c98",  # 3: Professor Tangoloss
    "9b1041732d1b4c2686d695c8bba3b149",  # 4: Scherox
    "3937b44b604249f18b94913daef94043",  # 5: Schlappchu
    "7b7c88bf825a4ab3bca27d55c26ddf16",  # 6: Glumanda
    "b5b77e8422b948efbf13be1e567a406c",  # 7: Bisasam
    "164f91694aef42509dc9ba1b7f8e2c7e",  # 8: Relaxo / Mooslaxo
    "c1e7cc926aa5469aa966c353094d4b0b",  # 9: DJ Rotom
    "3235abc9a84b407d92f73539a5651720",  # 10: Plinfa
    "a3f28343dfac42358e1f4fdf7562a773",  # 11: Schiggy
    "e5fb46af69e0481fa0d2d0f209b07ae2",  # 12: Felori
    "44f1ec96a51e429f8b87776cbc55a997",  # 13: Onix
    "86a132379c9e497785d53a4d1e05a2c1",  # 14: Driftlon
)

# Exakte 15 Regieanweisungen aus PokopiaVoiceCatalog.ROLES (Slots 0..14)
ROLE_DIRECTIONS: tuple[str, ...] = (
    "ruhig und klar erzählen, warm und deutlich sprechen",
    "hell, neugierig und energiegeladen, deutlich sprechen",
    "herzlich, verspielt und freundlich, deutlich sprechen",
    "bedacht, erfahren und geduldig, ruhig und deutlich sprechen",
    "kraftvoll und tatkräftig, rau aber freundlich und deutlich sprechen",
    "sanft und etwas erschöpft, herzlich und deutlich sprechen",
    "warm, mutig und lebhaft, deutlich sprechen",
    "sanft, naturverbunden und zuversichtlich, deutlich sprechen",
    "gemütlich, tief und schläfrig, langsam aber deutlich sprechen",
    "präzise, rhythmisch und lebhaft, klar und deutlich sprechen",
    "klein, stolz und aufgeweckt, hell und deutlich sprechen",
    "frech, fröhlich und flink, natürlich und deutlich sprechen",
    "weich, elegant und gelassen, ruhig und deutlich sprechen",
    "tief, standfest und würdevoll, ruhig und deutlich sprechen",
    "geheimnisvoll, verschmitzt und leise, trotzdem deutlich sprechen",
)

ALIAS_DISPLAY_OVERRIDES = {
    "erzähler": "Erzähler",
    "narrator": "Erzähler",
    "system": "Erzähler",
    "professor tangoloss": "Professor Tangoloss",
    "professor tangrowth": "Professor Tangoloss",
    "professor": "Professor Tangoloss",
    "tangoloss": "Professor Tangoloss",
    "tangrowth": "Professor Tangoloss",
    "dj rotom": "DJ Rotom",
    "ho oh": "Ho-Oh",
    "chef dente": "Chef Dente",
    "meister farbeagle": "Meister Farbeagle",
}

UI_NOISE_NORMS = {
    "aufwecken",
    "horter",
    "sprechblasen",
    "info ausblenden hand wechseln",
}

NON_SPEAKER_NOISE_WORDS = {"des", "der", "die", "das", "und", "mit", "für", "steuerung"}

PRONUNCIATION_DICT = {
    "pokémon": "Pokemohn",
    "pokemon": "Pokemohn",
    "pokéball": "Pokeball",
    "pokeball": "Pokeball",
    "pokédex": "Pokedex",
    "pokedex": "Pokedex",
    "pokopia": "Pokohpia",
    "bisasam": "Biesasam",
    "bisaknosp": "Biesaknosp",
    "bisaflor": "Biesaflor",
    "glumanda": "Glumannda",
    "glutexo": "Glutäxo",
    "glurak": "Glurahk",
    "schiggy": "Schiggie",
    "schillok": "Schillokk",
    "turtok": "Turtokk",
    "pikachu": "Piekatschu",
    "raichu": "Raichu",
    "pichu": "Piechu",
    "evoli": "Ewoli",
    "relaxo": "Rälaxo",
    "mooslaxo": "Mohslaxo",
    "dragoran": "Dragohran",
    "gengar": "Gängar",
    "tangoloss": "Tangoloss",
    "rotom": "Rohtom",
}


def _canonical_alias_display(alias: str) -> str:
    if alias in ALIAS_DISPLAY_OVERRIDES:
        return ALIAS_DISPLAY_OVERRIDES[alias]
    return " ".join(part.capitalize() for part in alias.split())


def clean_speaker(raw_speaker: Optional[str]) -> str:
    """Bereinigt verrauschte Namensschilder gegen die bekannten Pokopia-Rollen."""
    raw = (raw_speaker or "").strip()
    if not raw:
        return dp.NARRATOR

    direct_role = dp.map_pokopia_role(raw)
    norm = dp.normalize(raw)

    # Exakte Wortgrenzen-Suche über alle bekannten Aliase (längste zuerst)
    all_aliases: list[tuple[int, str]] = []
    for slot, _, aliases in dp.POKOPIA_ROLES:
        for alias in aliases:
            all_aliases.append((slot, alias))
    all_aliases.sort(key=lambda item: -len(item[1]))

    for _, alias in all_aliases:
        if re.search(rf"(?:^|\s){re.escape(alias)}(?:\s|$)", norm):
            return _canonical_alias_display(alias)

    if direct_role is not None:
        return dp.canonical_name(raw)

    if not dp.is_character_label(raw):
        return dp.NARRATOR

    tokens = norm.split()
    letters = sum(1 for ch in norm if ch.isalpha())
    if letters < 3 or any(tok in NON_SPEAKER_NOISE_WORDS for tok in tokens):
        return dp.NARRATOR
    # Reine Mini-Silben wie "en Ex" oder "nm" abweisen
    if len(tokens) >= 2 and all(len(tok) <= 2 for tok in tokens):
        return dp.NARRATOR
    return dp.canonical_name(raw)


def clean_dialogue_text(text: str) -> str:
    """Entfernt typische Tesseract-Symbolartefakte und UI-Cursorreste."""
    s = (text or "").strip()
    if not s:
        return ""
    # Häufige Tesseract-Verwechslungen bei é / W / Icon-Glyphen
    s = re.sub(r"\bPok&dex\b", "Pokédex", s)
    s = re.sub(r"\bpok&dex\b", "pokédex", s)
    s = re.sub(r"\bPok&mon\b", "Pokémon", s)
    s = re.sub(r"\bpok&mon\b", "pokémon", s)
    s = re.sub(r"\\+unsch\b", "Wunsch", s)
    s = re.sub(r"\\+(?=[A-Za-zÄÖÜäöüß])", "", s)

    # Führende UI-/Icon-Reste
    s = re.sub(r"^(?:[@®)\\_]+\s*|f\s+(?=[A-ZÄÖÜ]))", "", s)
    # Eingestreute UI-Icon-Glyphen zwischen Leerzeichen (z. B. "ein & Lagerfeuer", "den ® Baumstammstuhl")
    s = re.sub(r"(?<=\s)[&®$©](?=\s)", "", s)
    s = re.sub(r"(?:^|\s)[&®$©]\s+(?=[A-Za-zÄÖÜäöüß])", " ", s)
    # Isoliertes Backslash/Pipe-Rauschen
    s = re.sub(r"\s+[\\|]+\s+", " ", s)
    # Nachgestellte UI-Pfeil-/Cursor-Reste am Satzende ("Kameral v", "können! )", "gemacht! ’")
    s = re.sub(r"\s+(?:v|[)|=\\’'])+\s*$", "", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def is_noise_entry(cleaned_text: str, confidence: float) -> bool:
    norm = dp.normalize(cleaned_text)
    if not norm or not dp.has_dialogue_text(norm):
        return True
    if norm in UI_NOISE_NORMS or dp.is_action_label(cleaned_text) or dp.is_non_story_notification(norm):
        return True
    if confidence < 0.65 and len(norm) < 12:
        return True
    letters = sum(1 for ch in norm if ch.isalpha())
    words = norm.split()
    has_punct = bool(re.search(r"[!?]", cleaned_text))
    if letters < 4 and not has_punct:
        return True
    if len(words) < 2 and letters < 6 and not has_punct:
        return True
    return False


def apply_pronunciation(text: str) -> str:
    result = text
    for key, replacement in PRONUNCIATION_DICT.items():
        result = re.sub(
            rf"(?iu)(?<![^\W_]){re.escape(key)}(?![^\W_])",
            replacement,
            result,
        )
    return result


@dataclass
class PackEntry:
    id: str
    speaker: str
    speaker_norm: str
    role_slot: int
    text: str
    text_norm: str
    first_line: str
    first_line_norm: str
    confidence: float
    surface: str

    def index_dict(self) -> dict:
        return {
            "id": self.id,
            "speaker": self.speaker,
            "text": self.text,
            "first_line": self.first_line,
            "audio": f"audio/{self.id}.ogg",
        }


def _bigrams(text: str) -> frozenset[str]:
    if len(text) < 2:
        return frozenset()
    return frozenset(text[i:i + 2] for i in range(len(text) - 1))


def _is_compatible_speaker(existing: PackEntry, speaker: str, sp_norm: str) -> bool:
    return (
        existing.speaker_norm == sp_norm
        or existing.speaker == dp.NARRATOR
        or speaker == dp.NARRATOR
    )


def prepare_entries(script_paths: list[Path], limit: Optional[int] = None) -> tuple[list[PackEntry], dict]:
    raw_count = 0
    filtered_noise = 0
    merged_dups = 0
    entries: list[PackEntry] = []
    entry_bigrams: list[frozenset[str]] = []
    exact_by_text: dict[str, list[int]] = {}

    for script_path in script_paths:
        with script_path.open("r", encoding="utf-8") as fh:
            for raw_line in fh:
                stripped = raw_line.strip()
                if not stripped:
                    continue
                raw_count += 1
                rec = json.loads(stripped)
                conf = float(rec.get("confidence", 1.0))
                cleaned_text = clean_dialogue_text(rec.get("text", ""))
                if is_noise_entry(cleaned_text, conf):
                    filtered_noise += 1
                    continue

                cleaned_first = clean_dialogue_text(rec.get("first_line", "")) or cleaned_text
                speaker = clean_speaker(rec.get("speaker"))
                sp_norm = dp.normalize(speaker)
                text_norm = dp.normalize(cleaned_text)
                first_norm = dp.normalize(cleaned_first)
                role = dp.map_pokopia_role(speaker)
                slot = role[0] if role else 0
                surface = str(rec.get("surface", "BLUE"))
                text_bg = _bigrams(text_norm)
                text_len = len(text_norm)

                dup_idx: Optional[int] = None
                for idx in exact_by_text.get(text_norm, ()):
                    if _is_compatible_speaker(entries[idx], speaker, sp_norm):
                        dup_idx = idx
                        break

                if dup_idx is None:
                    for idx, existing in enumerate(entries):
                        if not _is_compatible_speaker(existing, speaker, sp_norm):
                            continue
                        ex_norm = existing.text_norm
                        ex_len = len(ex_norm)
                        max_len = ex_len if ex_len >= text_len else text_len
                        if max_len < 8:
                            continue
                        max_dist = max(1, int(max_len * dp.DUPLICATE_MAX_LEVENSHTEIN_RATIO))
                        if abs(ex_len - text_len) > max_dist:
                            continue
                        ex_bg = entry_bigrams[idx]
                        if len(ex_bg - text_bg) > 2 * max_dist or len(text_bg - ex_bg) > 2 * max_dist:
                            continue
                        if dp.similar_enough(ex_norm, text_norm):
                            dup_idx = idx
                            break

                if dup_idx is not None:
                    duplicate = entries[dup_idx]
                    merged_dups += 1
                    # Bevorzuge spezifischen Sprecher statt Erzähler
                    if duplicate.speaker == dp.NARRATOR and speaker != dp.NARRATOR:
                        duplicate.speaker = speaker
                        duplicate.speaker_norm = sp_norm
                        duplicate.role_slot = slot
                    if text_len > len(duplicate.text_norm) or (
                        text_len == len(duplicate.text_norm) and conf > duplicate.confidence
                    ):
                        old_norm = duplicate.text_norm
                        duplicate.text = cleaned_text
                        duplicate.text_norm = text_norm
                        if len(first_norm) >= len(duplicate.first_line_norm):
                            duplicate.first_line = cleaned_first
                            duplicate.first_line_norm = first_norm
                        duplicate.confidence = max(duplicate.confidence, conf)
                        if old_norm != text_norm:
                            old_bucket = exact_by_text.get(old_norm)
                            if old_bucket and dup_idx in old_bucket:
                                old_bucket.remove(dup_idx)
                                if not old_bucket:
                                    del exact_by_text[old_norm]
                            exact_by_text.setdefault(text_norm, []).append(dup_idx)
                            entry_bigrams[dup_idx] = text_bg
                    continue

                new_idx = len(entries)
                entries.append(PackEntry(
                    id=f"dlg_{new_idx + 1:04d}",
                    speaker=speaker,
                    speaker_norm=sp_norm,
                    role_slot=slot,
                    text=cleaned_text,
                    text_norm=text_norm,
                    first_line=cleaned_first,
                    first_line_norm=first_norm,
                    confidence=conf,
                    surface=surface,
                ))
                entry_bigrams.append(text_bg)
                exact_by_text.setdefault(text_norm, []).append(new_idx)

    if limit is not None and limit > 0:
        entries = entries[:limit]
    for idx, entry in enumerate(entries, start=1):
        entry.id = f"dlg_{idx:04d}"

    slot_counter = Counter(e.role_slot for e in entries)
    role_counts = {
        ROLE_NAMES.get(slot, f"Slot {slot}"): slot_counter[slot]
        for slot in sorted(slot_counter)
    }

    stats = {
        "raw_count": raw_count,
        "filtered_noise": filtered_noise,
        "merged_duplicates": merged_dups,
        "final_count": len(entries),
        "total_chars": sum(len(e.text) for e in entries),
        "role_counts": role_counts,
    }
    return entries, stats


def build_fish_payload(entry: PackEntry, speed: float = 1.0, apply_dict: bool = False) -> dict:
    slot = entry.role_slot if 0 <= entry.role_slot < len(FISH_VOICES) else 0
    spoken = apply_pronunciation(entry.text) if apply_dict else entry.text
    direction = ROLE_DIRECTIONS[slot]
    text_with_direction = f"[{direction}] {spoken}" if direction else spoken
    return {
        "text": text_with_direction,
        "reference_id": FISH_VOICES[slot],
        "language": "de-DE",
        "format": "pcm",
        "sample_rate": FISH_SAMPLE_RATE,
        "prosody": {"speed": round(max(0.75, min(1.75, speed)), 2)},
    }


def _default_http_post(url: str, headers: dict[str, str], body: bytes) -> tuple[int, bytes]:
    req = urllib.request.Request(url, data=body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as err:
        return err.code, err.read()


def synthesize_fish_pcm(
    entry: PackEntry,
    api_key: str,
    speed: float,
    apply_dict: bool,
    state: dict,
    state_lock: threading.Lock,
    allow_free_fallback: bool = True,
    http_post: Callable[[str, dict[str, str], bytes], tuple[int, bytes]] = _default_http_post,
    max_retries: int = 4,
    sleep_fn: Callable[[float], None] = time.sleep,
) -> bytes:
    payload = json.dumps(build_fish_payload(entry, speed=speed, apply_dict=apply_dict), ensure_ascii=False).encode("utf-8")
    retryable_statuses = {429, 500, 502, 503, 504}
    last_exc: Optional[BaseException] = None
    last_status: Optional[int] = None

    for attempt in range(max(0, max_retries) + 1):
        with state_lock:
            model = state.get("model", "drama-3")

        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json; charset=utf-8",
            "model": model,
        }
        try:
            status, body = http_post(FISH_ENDPOINT, headers, payload)
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            last_exc = exc
            if attempt < max_retries:
                sleep_fn(min(16.0, float(2 ** attempt)))
                continue
            raise RuntimeError(f"Fish Audio Netzwerkfehler für {entry.id}: {exc}") from exc

        if status == 402 and allow_free_fallback and model != "s2.1-pro-free":
            with state_lock:
                state["model"] = "s2.1-pro-free"
                model = "s2.1-pro-free"
            headers["model"] = model
            try:
                status, body = http_post(FISH_ENDPOINT, headers, payload)
            except (urllib.error.URLError, TimeoutError, OSError) as exc:
                last_exc = exc
                if attempt < max_retries:
                    sleep_fn(min(16.0, float(2 ** attempt)))
                    continue
                raise RuntimeError(f"Fish Audio Netzwerkfehler für {entry.id}: {exc}") from exc

        if status in retryable_statuses:
            last_status = status
            if attempt < max_retries:
                sleep_fn(min(16.0, float(2 ** attempt)))
                continue
            raise RuntimeError(f"Fish Audio HTTP {status} für {entry.id}")

        if status < 200 or status >= 300:
            raise RuntimeError(f"Fish Audio HTTP {status} für {entry.id}")
        if not body or (len(body) & 1) != 0:
            raise RuntimeError(f"Ungültige PCM-Antwort für {entry.id} ({len(body)} Bytes)")
        return body

    if last_exc is not None:
        raise RuntimeError(f"Fish Audio Netzwerkfehler für {entry.id}: {last_exc}") from last_exc
    raise RuntimeError(f"Fish Audio HTTP {last_status} für {entry.id}")


def encode_pcm_to_opus(pcm_bytes: bytes, target_ogg: Path, bitrate: str) -> None:
    target_ogg.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=".opus-", suffix=".ogg", dir=str(target_ogg.parent))
    os.close(fd)
    tmp_path = Path(tmp_name)
    try:
        proc = subprocess.run(
            [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-f", "s16le", "-ar", str(FISH_SAMPLE_RATE), "-ac", "1", "-i", "pipe:0",
                "-c:a", "libopus", "-b:a", bitrate, "-vbr", "on", "-application", "voip",
                str(tmp_path),
            ],
            input=pcm_bytes,
            capture_output=True,
        )
        if proc.returncode != 0:
            err = proc.stderr.decode("utf-8", errors="replace")
            raise RuntimeError(f"ffmpeg libopus Fehler für {target_ogg.name}: {err}")
        os.replace(tmp_path, target_ogg)
    except Exception:
        tmp_path.unlink(missing_ok=True)
        raise


def synthesize_macos_say_opus(
    entry: PackEntry,
    target_ogg: Path,
    say_voice: str,
    speed: float,
    bitrate: str,
    apply_dict: bool,
) -> None:
    target_ogg.parent.mkdir(parents=True, exist_ok=True)
    spoken = apply_pronunciation(entry.text) if apply_dict else entry.text
    rate_wpm = str(max(90, min(350, round(175 * speed))))

    fd_aiff, tmp_aiff_name = tempfile.mkstemp(prefix=".say-", suffix=".aiff", dir=str(target_ogg.parent))
    os.close(fd_aiff)
    fd_ogg, tmp_ogg_name = tempfile.mkstemp(prefix=".opus-", suffix=".ogg", dir=str(target_ogg.parent))
    os.close(fd_ogg)
    tmp_aiff = Path(tmp_aiff_name)
    tmp_ogg = Path(tmp_ogg_name)
    try:
        subprocess.run(
            ["say", "-v", say_voice, "-r", rate_wpm, "-o", str(tmp_aiff), "--", spoken],
            check=True,
            capture_output=True,
        )
        subprocess.run(
            [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-i", str(tmp_aiff),
                "-c:a", "libopus", "-b:a", bitrate, "-vbr", "on", "-application", "voip",
                str(tmp_ogg),
            ],
            check=True,
            capture_output=True,
        )
        os.replace(tmp_ogg, target_ogg)
    except Exception:
        tmp_ogg.unlink(missing_ok=True)
        raise
    finally:
        tmp_aiff.unlink(missing_ok=True)


def write_index_jsonl(entries: list[PackEntry], index_path: Path) -> None:
    index_path.parent.mkdir(parents=True, exist_ok=True)
    with index_path.open("w", encoding="utf-8") as fh:
        for entry in entries:
            fh.write(json.dumps(entry.index_dict(), ensure_ascii=False) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Rendert Pokopia-Dialogskripte zu einem lokalen Opus-Story-Pack.")
    parser.add_argument("--scripts", nargs="+", required=True, type=Path, help="Eine oder mehrere script.jsonl-Dateien")
    parser.add_argument("--out-dir", required=True, type=Path, help="Zielordner für index.jsonl und audio/")
    parser.add_argument("--provider", choices=("fish", "macos-say"), default="fish")
    parser.add_argument("--dry-run", action="store_true", help="Nur Bereinigung, Statistik und Schätzung ohne Audio-Erzeugung")
    parser.add_argument("--api-key", default=os.environ.get("FISH_API_KEY", ""), help="Fish Audio API-Key (Standard: $FISH_API_KEY)")
    parser.add_argument("--no-free-fallback", action="store_true", help="Bei HTTP 402 nicht auf s2.1-pro-free wechseln")
    parser.add_argument("--speed", type=float, default=1.0, help="Sprechtempo (0.75–1.75)")
    parser.add_argument("--bitrate", default="28k", help="Opus-Bitrate (Standard: 28k)")
    parser.add_argument("--limit", type=int, default=None, help="Optional maximal N Dialoge rendern")
    parser.add_argument("--workers", type=int, default=None, help="Parallele Render-Worker")
    parser.add_argument("--say-voice", default="Anna", help="macOS-Say-Stimme für --provider macos-say")
    parser.add_argument("--apply-pronunciation", action="store_true", help="Aussprache-Wörterbuch vor Synthese anwenden")
    args = parser.parse_args()

    entries, stats = prepare_entries(args.scripts, limit=args.limit)
    est_sec = stats["total_chars"] / 15.0
    est_mb = est_sec * 3.5 / 1024.0
    print(
        f"Bereinigung: {stats['raw_count']} roh -> {stats['filtered_noise']} Rauschen entfernt, "
        f"{stats['merged_duplicates']} Duplikate zusammengeführt -> {stats['final_count']} Dialoge "
        f"({stats['total_chars']:,} Zeichen, ~{est_sec:.0f} s Audio / ~{est_mb:.2f} MB Opus)"
    )
    if stats["role_counts"]:
        role_summary = ", ".join(f"{name}: {cnt}" for name, cnt in stats["role_counts"].items())
        print(f"Rollenverteilung ({len(stats['role_counts'])} Rollen): {role_summary}")

    if args.dry_run:
        return 0

    if args.provider == "fish" and not args.api_key:
        print("Fehler: Für --provider fish wird --api-key oder FISH_API_KEY benötigt.", file=sys.stderr)
        return 2

    out_dir = args.out_dir
    audio_dir = out_dir / "audio"
    audio_dir.mkdir(parents=True, exist_ok=True)

    workers = args.workers if args.workers and args.workers > 0 else (4 if args.provider == "macos-say" else 2)
    fish_state = {"model": "drama-3"}
    fish_lock = threading.Lock()
    skipped_existing = 0
    rendered_now = 0
    todo: list[PackEntry] = []

    for entry in entries:
        target = audio_dir / f"{entry.id}.ogg"
        if target.is_file() and target.stat().st_size > 200:
            skipped_existing += 1
        else:
            todo.append(entry)

    def render_one(entry: PackEntry) -> None:
        target = audio_dir / f"{entry.id}.ogg"
        if args.provider == "macos-say":
            synthesize_macos_say_opus(
                entry=entry,
                target_ogg=target,
                say_voice=args.say_voice,
                speed=args.speed,
                bitrate=args.bitrate,
                apply_dict=args.apply_pronunciation,
            )
        else:
            pcm = synthesize_fish_pcm(
                entry=entry,
                api_key=args.api_key,
                speed=args.speed,
                apply_dict=args.apply_pronunciation,
                state=fish_state,
                state_lock=fish_lock,
                allow_free_fallback=not args.no_free_fallback,
            )
            encode_pcm_to_opus(pcm, target, args.bitrate)

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        futures = {pool.submit(render_one, e): e for e in todo}
        for future in concurrent.futures.as_completed(futures):
            entry = futures[future]
            future.result()
            rendered_now += 1
            if rendered_now % 25 == 0 or rendered_now == len(todo):
                print(f"Fortschritt: {rendered_now}/{len(todo)} neu gerendert ({entry.id})", flush=True)

    write_index_jsonl(entries, out_dir / "index.jsonl")
    total_bytes = sum((audio_dir / f"{e.id}.ogg").stat().st_size for e in entries if (audio_dir / f"{e.id}.ogg").is_file())
    manifest = {
        "provider": args.provider,
        "model": fish_state["model"] if args.provider == "fish" else f"macos-say:{args.say_voice}",
        "bitrate": args.bitrate,
        "entry_count": len(entries),
        "total_chars": stats["total_chars"],
        "rendered_count": rendered_now,
        "skipped_existing": skipped_existing,
        "total_audio_bytes": total_bytes,
        "role_counts": stats["role_counts"],
    }
    (out_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"Story-Pack fertig in {out_dir}: {len(entries)} Dialoge "
        f"({rendered_now} neu, {skipped_existing} wiederverwendet, {total_bytes / (1024 * 1024):.2f} MB Audio)"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
