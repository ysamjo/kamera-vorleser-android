#!/usr/bin/env python3
"""Nachverarbeitung von raw_ocr.jsonl -> script.jsonl + report.md (nur Python-Stdlib)."""

from __future__ import annotations

import argparse
import json
import math
import re
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Optional

# Exakte Kopie der Rollen aus PokopiaVoiceCatalog.java (Slots 0..14)
POKOPIA_ROLES: list[tuple[int, str, tuple[str, ...]]] = [
    (0, "Erzähler / System", ("erzähler", "narrator", "system")),
    (1, "Pikachu / Pichu", ("pikachu", "pichu")),
    (2, "Evoli & Niedliche", (
        "evoli", "eevee", "togepi", "emolga", "dedenne", "pummeluff", "jigglypuff",
        "wuffels", "rockruff", "hoppspross", "hoppip", "knospi", "budew", "mobai",
        "bonsly", "riolu",
    )),
    (3, "Professor Tangoloss", (
        "professor tangoloss", "professor tangrowth", "professor", "tangoloss", "tangrowth",
    )),
    (4, "Scherox & Handwerker", (
        "scherox", "scizor", "sichlor", "scyther", "praktibalk", "timburr", "rotomurf",
        "drilbur", "machollo", "machop", "maschock", "machoke", "machomei", "machamp",
        "panzaeron", "skarmory", "tinkmaster",
    )),
    (5, "Schlappchu", ("schlappchu", "peakychu")),
    (6, "Glumanda-Linie", ("glumanda", "charmander", "glutexo", "charmeleon", "glurak", "charizard")),
    (7, "Bisasam-Linie", ("bisasam", "bulbasaur", "bisaknosp", "ivysaur")),
    (8, "Relaxo & Gemütliche", (
        "mooslaxo", "mosslax", "relaxo", "snorlax", "dragoran", "dragonite", "lapras",
        "bisaflor", "venusaur", "turtok", "blastoise", "chelterrar", "torterra", "altaria",
        "wailord", "chef dente",
    )),
    (9, "DJ Rotom & Technik", (
        "dj rotom", "rotom", "porygon", "magnetilo", "magnemite", "magneton", "metagross",
        "genesect", "golbit", "golett", "golgantes", "golurk",
    )),
    (10, "Plinfa & Kleine", (
        "plinfa", "piplup", "meikro", "rookidee", "staralili", "starly", "taubsi", "pidgey",
        "habitak", "spearow", "waumpel", "wurmple", "raupy", "caterpie", "hornliu",
        "weedle", "wommel", "cutiefly",
    )),
    (11, "Schiggy & Flinke", (
        "schiggy", "squirtle", "schillok", "wartortle", "mauzi", "meowth", "karpador",
        "magikarp", "unratütox", "trubbish", "schlaraffel", "greedent", "kleptifux", "nickit",
    )),
    (12, "Felori & Elegante", (
        "felori", "sprigatito", "suicune", "mew", "celebi", "guardevoir", "gardevoir",
        "galagladi", "gallade", "vulnona", "ninetales", "milotic", "arktos", "articuno",
    )),
    (13, "Onix & Legendäre", (
        "onix", "stahlos", "steelix", "meistagrif", "conkeldurr", "stalobor", "excadrill",
        "geowaz", "golem", "rihorn", "rhyhorn", "rizeros", "rhydon", "despotar",
        "tyranitar", "volcanion", "groudon", "mewtu", "mewtwo", "entei", "raikou",
        "zapdos", "lavados", "moltres", "ho oh", "lugia", "rayquaza", "dialga", "palkia",
    )),
    (14, "Driftlon & Geister", (
        "driftlon", "drifloon", "nebulak", "gastly", "alpollo", "haunter", "gengar",
        "traunfugil", "misdreavus", "zwirrlicht", "duskull", "lichtel", "litwick",
        "mimigma", "mimikyu", "kryppuk", "spiritomb", "farbeagle", "smeargle",
        "meister farbeagle", "smearguru",
    )),
]

NON_CHARACTER_LABELS = {
    "lagerbestand", "inventar", "beschreibung", "informationen", "aufgaben", "aufgabe",
    "ziel", "ziele", "material", "materialien", "herstellen", "rezept", "rezepte",
    "karte", "hilfe", "einstellungen", "status", "gegenstände", "sammlung", "menü",
}

ACTION_LABEL_RE = re.compile(
    r"^(?:[abxy] ?)?(?:weiter|zurück|bestätigen|abbrechen|schließen|überspringen|"
    r"auswählen|nächstes|untersuchen|sprechen|reden|öffnen|nehmen|verwenden|interagieren)$"
)
ACTION_SINGLE_RE = re.compile(r"^(?:ja|nein|menü|optionen|automatisch|protokoll)$")
NON_STORY_PATTERNS = (
    re.compile(r"du hast .* hergestellt"),
    re.compile(r"neue kreation"),
    re.compile(r"(?:gegenstand|möbelstück|rezept) (?:erhalten|gefunden)"),
    re.compile(r"wird gespeichert"),
)
DIALOGUE_WORDS_RE = re.compile(
    r"\b(?:ich|du|wir|ihr|das|ist|sind|hallo|guten|danke|bitte|komm)\b"
)
INLINE_PROFESSOR_RE = re.compile(r"^\s*professor\s+tangoloss(?:\s|[:：])", re.IGNORECASE | re.UNICODE)

# Schwellen aus TextRules.java & MainActivity.java
SIMILARITY_MIN_LENGTH_RATIO = 0.85
SIMILARITY_MAX_LEVENSHTEIN_RATIO = 0.12
DUPLICATE_MIN_LENGTH_RATIO = 0.80
DUPLICATE_MAX_LEVENSHTEIN_RATIO = 0.10

NARRATOR = "Erzähler"
MIN_SPEAKER_NAME_LENGTH = 2
MAX_SPEAKER_NAME_LENGTH = 28
MAX_SPEAKER_NAME_WORDS = 3
SPEAKER_TOP_RATIO = 0.70
SPEAKER_BOTTOM_RATIO = 0.81
SPEAKER_MAX_CENTER_X = 0.62
SPEAKER_MAX_WIDTH = 0.36
STORY_GAP_MAX_HEIGHT_RATIO = 0.14
STORY_GAP_MAX_HORIZONTAL_RATIO = 0.25


def normalize(value: Optional[str]) -> str:
    """Normalisiert analog zu TextRules.normalize (NFC, Kleinbuchstaben, nur Unicode L/N)."""
    if not value:
        return ""
    lower = unicodedata.normalize("NFC", value).lower()
    out: list[str] = []
    prev_space = True
    for ch in lower:
        cat = unicodedata.category(ch)
        if cat and cat[0] in ("L", "N"):
            out.append(ch)
            prev_space = False
        elif not prev_space:
            out.append(" ")
            prev_space = True
    return "".join(out).strip()


def is_action_label(text: str) -> bool:
    value = normalize(text)
    return bool(ACTION_LABEL_RE.match(value) or ACTION_SINGLE_RE.match(value))


def is_non_story_notification(norm_value: str) -> bool:
    return any(p.search(norm_value) for p in NON_STORY_PATTERNS)


def has_dialogue_text(norm_value: str) -> bool:
    letters = 0
    for ch in norm_value:
        cat = unicodedata.category(ch)
        if cat and cat[0] == "L":
            letters += 1
            if letters == 2:
                return True
    return False


def levenshtein(left: str, right: str) -> int:
    if left == right:
        return 0
    if len(left) < len(right):
        left, right = right, left
    costs = list(range(len(right) + 1))
    for i, lc in enumerate(left, start=1):
        prev_diag = costs[0]
        costs[0] = i
        for j, rc in enumerate(right, start=1):
            old = costs[j]
            costs[j] = min(costs[j] + 1, costs[j - 1] + 1, prev_diag + (0 if lc == rc else 1))
            prev_diag = old
    return costs[-1]


def similar_story_text(left: str, right: str) -> bool:
    max_len = max(len(left), len(right))
    min_len = min(len(left), len(right))
    if max_len == 0 or min_len < max_len * SIMILARITY_MIN_LENGTH_RATIO:
        return False
    return levenshtein(left, right) <= max(1, int(max_len * SIMILARITY_MAX_LEVENSHTEIN_RATIO))


def similar_enough(left: str, right: str) -> bool:
    max_len = max(len(left), len(right))
    if max_len < 8 or min(len(left), len(right)) < max_len * DUPLICATE_MIN_LENGTH_RATIO:
        return False
    return levenshtein(left, right) <= max(1, int(max_len * DUPLICATE_MAX_LEVENSHTEIN_RATIO))


def has_dialogue_words(text: str) -> bool:
    if re.search(r"[.!?…]", text):
        return True
    norm = normalize(text)
    return bool(DIALOGUE_WORDS_RE.search(norm)) or len(norm) > 28


def is_character_label(name: Optional[str]) -> bool:
    if not name or is_action_label(name) or has_dialogue_words(name):
        return False
    key = normalize(name)
    return bool(key) and key not in NON_CHARACTER_LABELS


def canonical_name(name: str) -> str:
    normalized = normalize(name)
    if normalized in ("tangoloss", "professor tangoloss"):
        return "Professor Tangoloss"
    cleaned = unicodedata.normalize("NFC", name).strip()
    return re.sub(r"[：:]+$", "", cleaned).strip()


def inline_name(text: str) -> Optional[str]:
    nfc = unicodedata.normalize("NFC", text)
    return "Professor Tangoloss" if INLINE_PROFESSOR_RE.match(nfc) else None


def map_pokopia_role(speaker: Optional[str]) -> Optional[tuple[int, str]]:
    key = normalize(speaker)
    if not key:
        return POKOPIA_ROLES[0][0], POKOPIA_ROLES[0][1]
    for slot, label, aliases in POKOPIA_ROLES:
        for alias in aliases:
            if key == alias or key.startswith(alias + " ") or key.endswith(" " + alias):
                return slot, label
    return None


@dataclass(frozen=True)
class OcrLine:
    text: str
    left: int
    top: int
    right: int
    bottom: int
    confidence: float = 1.0
    surface: str = "NONE"

    @property
    def normalized(self) -> str:
        return normalize(self.text)

    @property
    def center_x(self) -> float:
        return (self.left + self.right) / 2.0

    @property
    def center_y(self) -> float:
        return (self.top + self.bottom) / 2.0

    @property
    def width(self) -> int:
        return self.right - self.left

    @property
    def height(self) -> int:
        return self.bottom - self.top


def _group_rows(lines: Iterable[OcrLine]) -> list[list[OcrLine]]:
    valid = [
        line for line in lines
        if line is not None and line.text and line.text.strip() and line.right > line.left and line.bottom > line.top
    ]
    valid.sort(key=lambda l: (l.top, l.left, l.bottom, l.right, l.text))
    rows: list[list[OcrLine]] = []
    for line in valid:
        target: Optional[list[OcrLine]] = None
        for row in rows:
            anchor = row[0]
            avg_height = max(1, (line.height + anchor.height) // 2)
            if abs(line.center_y - anchor.center_y) < avg_height * 0.5:
                target = row
                break
        if target is None:
            target = []
            rows.append(target)
        target.append(line)
    for row in rows:
        row.sort(key=lambda l: (l.left, l.top, l.text))
    return rows


def assemble_story_text(lines: Iterable[OcrLine]) -> str:
    rows = _group_rows(lines)
    result = ""
    for row in rows:
        row_text = " ".join(l.text.strip() for l in row)
        if result:
            if result.endswith("-") and (len(result) < 2 or result[-2] != " "):
                result = result[:-1]
            else:
                result += " "
        result += row_text
    return result


def extract_first_line(lines: Iterable[OcrLine]) -> str:
    rows = _group_rows(lines)
    if not rows:
        return ""
    return " ".join(l.text.strip() for l in rows[0])


def without_speaker(text: str, speaker: Optional[str]) -> str:
    visible = unicodedata.normalize("NFC", text or "").strip()
    if not speaker or not speaker.strip() or normalize(speaker) == normalize(NARRATOR):
        return visible
    label = unicodedata.normalize("NFC", speaker).strip()
    if len(visible) < len(label):
        return visible
    if visible[:len(label)].lower() != label.lower():
        return visible
    if len(visible) == len(label):
        return ""
    sep = visible[len(label)]
    if not sep.isspace() and sep not in ":：–—-":
        return visible
    return re.sub(r"^[\s:：–—-]+", "", visible[len(label):]).strip()


def speaker_above(all_lines: list[OcrLine], story: OcrLine, width: int, height: int) -> Optional[str]:
    best: Optional[OcrLine] = None
    for line in all_lines:
        name = line.normalized
        gap = story.top - line.bottom
        words = name.split(" ") if name else []
        if (
            len(name) < 2
            or len(name) > MAX_SPEAKER_NAME_LENGTH
            or len(words) > MAX_SPEAKER_NAME_WORDS
            or line.width > width * 0.50
            or is_action_label(line.text)
            or has_dialogue_words(line.text)
            or gap < 0
            or gap > height * STORY_GAP_MAX_HEIGHT_RATIO
            or abs(line.left - story.left) > width * STORY_GAP_MAX_HORIZONTAL_RATIO
        ):
            continue
        if best is None or line.bottom > best.bottom or (line.bottom == best.bottom and line.left > best.left):
            best = line
    if best is None:
        return None

    label = [best]
    for line in all_lines:
        if line is best or line.normalized != "professor":
            continue
        same_row = (
            abs(line.center_y - best.center_y) < best.height * 0.5
            and line.right <= best.left
            and (best.left - line.right) < width * 0.08
        )
        preceding_row = (
            line.bottom <= best.top
            and (best.top - line.bottom) < best.height
            and abs(line.left - best.left) < width * 0.10
        )
        if same_row or preceding_row:
            label.append(line)

    merged = re.sub(r"[：:]+$", "", assemble_story_text(label)).strip()
    return merged if is_character_label(merged) else None


def _is_short_speaker_name(line: OcrLine, frame_width: int) -> bool:
    norm = line.normalized
    if len(norm) < MIN_SPEAKER_NAME_LENGTH or len(norm) > MAX_SPEAKER_NAME_LENGTH or not is_character_label(line.text):
        return False
    return len(norm.split()) <= MAX_SPEAKER_NAME_WORDS and line.width < frame_width * SPEAKER_MAX_WIDTH


def _is_speaker_label(line: OcrLine, frame_width: int, frame_height: int) -> bool:
    norm = line.normalized
    if len(norm) < MIN_SPEAKER_NAME_LENGTH or len(line.text) > MAX_SPEAKER_NAME_LENGTH:
        return False
    words = len(norm.split())
    cy = line.center_y / float(frame_height)
    cx = line.center_x / float(frame_width)
    w = line.width / float(frame_width)
    return (
        words <= MAX_SPEAKER_NAME_WORDS
        and SPEAKER_TOP_RATIO <= cy < SPEAKER_BOTTOM_RATIO
        and cx < SPEAKER_MAX_CENTER_X
        and w < SPEAKER_MAX_WIDTH
        and is_character_label(line.text)
    )


def detect_speaker(
    all_lines: list[OcrLine],
    story_lines: list[OcrLine],
    frame_width: int,
    frame_height: int,
) -> Optional[str]:
    if not story_lines:
        return None
    story_norms = {l.normalized for l in story_lines}
    top_story = min(story_lines, key=lambda l: (l.top, l.left))

    above = speaker_above(all_lines, top_story, frame_width, frame_height)
    if above and normalize(above) not in story_norms:
        return canonical_name(above)

    reference: Optional[OcrLine] = None
    for line in story_lines:
        if (
            reference is None
            or len(line.text) > len(reference.text)
            or line.width > reference.width * 1.25
        ):
            reference = line

    if reference is not None:
        best: Optional[str] = None
        smallest_gap = math.inf
        for line in all_lines:
            if not _is_short_speaker_name(line, frame_width):
                continue
            gap = reference.top - line.bottom
            hdist = abs(line.left - reference.left)
            if (
                gap < -reference.height
                or gap > frame_height * STORY_GAP_MAX_HEIGHT_RATIO
                or hdist > frame_width * STORY_GAP_MAX_HORIZONTAL_RATIO
            ):
                continue
            if line.normalized == reference.normalized or line.normalized in story_norms:
                continue
            if gap < smallest_gap:
                smallest_gap = gap
                best = line.text
        if best:
            return canonical_name(best)

    best_fallback: Optional[str] = None
    best_top = -10**9
    for line in all_lines:
        if not _is_speaker_label(line, frame_width, frame_height):
            continue
        if len(story_lines) == 1 and line == story_lines[0]:
            continue
        if line.normalized in story_norms:
            continue
        if line.top > best_top:
            best_top = line.top
            best_fallback = line.text
    return canonical_name(best_fallback) if best_fallback else None


def overlap_words(previous_norm: str, current_norm: str) -> int:
    if not previous_norm or not current_norm:
        return 0
    left = previous_norm.split(" ")
    right = current_norm.split(" ")
    for size in range(min(len(left), len(right)), 0, -1):
        if left[-size:] == right[:size]:
            return size
    return 0


def contains_words(full_norm: str, fragment_norm: str) -> bool:
    return bool(fragment_norm) and f" {fragment_norm} " in f" {full_norm} "


def same_dialogue(previous_norm: str, current_norm: str) -> bool:
    if not previous_norm or not current_norm:
        return False
    return (
        previous_norm == current_norm
        or similar_story_text(previous_norm, current_norm)
        or current_norm.startswith(previous_norm + " ")
        or previous_norm.startswith(current_norm + " ")
        or contains_words(previous_norm, current_norm)
        or overlap_words(previous_norm, current_norm) >= 3
    )


def _is_prefix_growth(prev_norm: str, curr_norm: str) -> bool:
    """Erkennt Typewriter-Wachstum auch bei 1-2 OCR-Zeichen am wachsenden Ende."""
    if not prev_norm or not curr_norm:
        return False
    if curr_norm.startswith(prev_norm) or prev_norm.startswith(curr_norm):
        return True
    if len(prev_norm) >= 8 and len(curr_norm) > len(prev_norm):
        # Letzte 2 Zeichen am Tipp-Rand ignorieren
        stem = prev_norm[:-2].rstrip()
        if len(stem) >= 6 and curr_norm.startswith(stem):
            return True
    if len(curr_norm) >= 8 and len(prev_norm) > len(curr_norm):
        stem = curr_norm[:-2].rstrip()
        if len(stem) >= 6 and prev_norm.startswith(stem):
            return True
    return False


@dataclass
class FrameDialogue:
    frame_index: int
    timestamp_sec: float
    speaker: str
    speaker_confident: bool
    text: str
    text_norm: str
    first_line: str
    first_line_norm: str
    surface: str
    confidence: float


@dataclass
class DialogueSegment:
    start_sec: float
    end_sec: float
    text: str
    text_norm: str
    first_line: str
    first_line_norm: str
    surface: str
    confidence: float
    frame_count: int = 1
    speaker_votes: Counter = field(default_factory=Counter)
    speaker: str = NARRATOR

    def add_speaker(self, speaker: str, confident: bool) -> None:
        if confident:
            self.speaker_votes[speaker] += 2
        elif speaker != NARRATOR:
            self.speaker_votes[speaker] += 1
        elif not self.speaker_votes:
            self.speaker_votes[NARRATOR] += 1
        self.speaker = self._best_speaker()

    def _best_speaker(self) -> str:
        non_narrator = [(cnt, name) for name, cnt in self.speaker_votes.items() if name != NARRATOR]
        if non_narrator:
            non_narrator.sort(key=lambda item: (-item[0], item[1]))
            return non_narrator[0][1]
        return NARRATOR


def parse_raw_frame(record: dict) -> tuple[int, float, Optional[FrameDialogue]]:
    frame_index = int(record["frame_index"])
    ts = float(record["timestamp_sec"])
    width = int(record["width"])
    height = int(record["height"])

    all_lines = [
        OcrLine(
            text=re.sub(r"\s+", " ", item["text"]).strip(),
            left=int(item["left"]),
            top=int(item["top"]),
            right=int(item["right"]),
            bottom=int(item["bottom"]),
            confidence=float(item.get("confidence", 1.0)),
            surface=str(item.get("surface", "NONE")),
        )
        for item in record.get("lines", [])
        if item.get("text", "").strip()
    ]

    story_lines: list[OcrLine] = []
    gray_lines: list[OcrLine] = []
    for line in all_lines:
        if line.surface == "NONE":
            continue
        norm = line.normalized
        if (
            (len(norm) < 3 and not has_dialogue_text(norm))
            or is_action_label(line.text)
            or is_non_story_notification(norm)
        ):
            continue
        story_lines.append(line)
        if line.surface == "GRAY":
            gray_lines.append(line)

    detected_speaker = detect_speaker(all_lines, story_lines, width, height)
    if not is_character_label(detected_speaker):
        gray_ids = {id(g) for g in gray_lines}
        story_lines = [l for l in story_lines if id(l) not in gray_ids]

    if detected_speaker:
        label_key = normalize(detected_speaker)
        story_lines = [l for l in story_lines if l.normalized != label_key]

    if not story_lines:
        return frame_index, ts, None

    assembled = assemble_story_text(story_lines)
    first_raw = extract_first_line(story_lines)

    speaker = detected_speaker
    speaker_confident = bool(detected_speaker)
    if not speaker:
        inline = inline_name(assembled)
        if inline:
            speaker = inline
            speaker_confident = True
        else:
            speaker = NARRATOR

    visible_story = without_speaker(assembled, speaker)
    first_line = without_speaker(first_raw, speaker)
    norm_story = normalize(visible_story)
    if (
        not visible_story.strip()
        or is_non_story_notification(norm_story)
        or not has_dialogue_text(norm_story)
    ):
        return frame_index, ts, None

    surfaces = Counter(l.surface for l in story_lines)
    dominant_surface = surfaces.most_common(1)[0][0]
    avg_conf = sum(l.confidence for l in story_lines) / len(story_lines)

    return frame_index, ts, FrameDialogue(
        frame_index=frame_index,
        timestamp_sec=ts,
        speaker=speaker,
        speaker_confident=speaker_confident,
        text=visible_story,
        text_norm=norm_story,
        first_line=first_line,
        first_line_norm=normalize(first_line),
        surface=dominant_surface,
        confidence=avg_conf,
    )


def merge_frames(frames: list[tuple[int, float, Optional[FrameDialogue]]]) -> list[DialogueSegment]:
    """Führt Typewriter-Wachstum und Frame-Wiederholungen zeitlich zusammen."""
    ordered = sorted(frames, key=lambda x: (x[1], x[0]))
    segments: list[DialogueSegment] = []
    cur: Optional[DialogueSegment] = None
    saw_empty_boundary = False

    for _, ts, dlg in ordered:
        if dlg is None:
            if cur is not None:
                saw_empty_boundary = True
                if ts - cur.end_sec > 1.5:
                    segments.append(cur)
                    cur = None
                    saw_empty_boundary = False
            continue

        had_empty = saw_empty_boundary
        saw_empty_boundary = False
        if cur is None:
            cur = DialogueSegment(
                start_sec=dlg.timestamp_sec,
                end_sec=dlg.timestamp_sec,
                text=dlg.text,
                text_norm=dlg.text_norm,
                first_line=dlg.first_line,
                first_line_norm=dlg.first_line_norm,
                surface=dlg.surface,
                confidence=dlg.confidence,
            )
            cur.add_speaker(dlg.speaker, dlg.speaker_confident)
            continue

        gap = dlg.timestamp_sec - cur.end_sec
        same_speaker_compat = (
            cur.speaker == dlg.speaker
            or cur.speaker == NARRATOR
            or dlg.speaker == NARRATOR
            or normalize(cur.speaker) == normalize(dlg.speaker)
        )
        prefix_growth = _is_prefix_growth(cur.text_norm, dlg.text_norm)
        similar = similar_story_text(cur.text_norm, dlg.text_norm)
        contained = contains_words(cur.text_norm, dlg.text_norm) or contains_words(dlg.text_norm, cur.text_norm)

        if had_empty:
            should_merge = gap <= 1.5 and same_speaker_compat and (similar or contained)
        else:
            should_merge = gap <= 20.0 and same_speaker_compat and (prefix_growth or similar or contained)

        if should_merge:
            cur.end_sec = dlg.timestamp_sec
            cur.frame_count += 1
            cur.add_speaker(dlg.speaker, dlg.speaker_confident)
            # Vollständigeres/längeres Textende übernehmen
            if len(dlg.text_norm) > len(cur.text_norm) or (
                len(dlg.text_norm) == len(cur.text_norm) and dlg.confidence > cur.confidence
            ):
                cur.text = dlg.text
                cur.text_norm = dlg.text_norm
                if len(dlg.first_line_norm) >= len(cur.first_line_norm):
                    cur.first_line = dlg.first_line
                    cur.first_line_norm = dlg.first_line_norm
                cur.surface = dlg.surface
            cur.confidence = max(cur.confidence, dlg.confidence)
        else:
            segments.append(cur)
            cur = DialogueSegment(
                start_sec=dlg.timestamp_sec,
                end_sec=dlg.timestamp_sec,
                text=dlg.text,
                text_norm=dlg.text_norm,
                first_line=dlg.first_line,
                first_line_norm=dlg.first_line_norm,
                surface=dlg.surface,
                confidence=dlg.confidence,
            )
            cur.add_speaker(dlg.speaker, dlg.speaker_confident)

    if cur is not None:
        segments.append(cur)
    return segments


@dataclass
class UniqueDialogue:
    id: str
    speaker: str
    speaker_norm: str
    role_slot: Optional[int]
    role_label: str
    text: str
    text_norm: str
    first_line: str
    first_line_norm: str
    surface: str
    confidence: float
    first_seen_sec: float
    last_seen_sec: float
    occurrences: list[dict] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "speaker": self.speaker,
            "speaker_norm": self.speaker_norm,
            "role_slot": self.role_slot,
            "role_label": self.role_label,
            "text": self.text,
            "text_norm": self.text_norm,
            "first_line": self.first_line,
            "first_line_norm": self.first_line_norm,
            "surface": self.surface,
            "confidence": round(self.confidence, 4),
            "first_seen_sec": round(self.first_seen_sec, 2),
            "last_seen_sec": round(self.last_seen_sec, 2),
            "occurrence_count": len(self.occurrences),
            "occurrences": self.occurrences,
        }


def deduplicate_segments(segments: list[DialogueSegment]) -> list[UniqueDialogue]:
    """Dedupliziert identische/fast identische Dialoge desselben Sprechers."""
    unique: list[UniqueDialogue] = []
    by_exact: dict[tuple[str, str], UniqueDialogue] = {}

    for seg in segments:
        sp_norm = normalize(seg.speaker)
        exact_key = (sp_norm, seg.text_norm)
        match = by_exact.get(exact_key)
        if match is None:
            for item in unique:
                if item.speaker_norm == sp_norm and similar_enough(item.text_norm, seg.text_norm):
                    match = item
                    break

        occ = {
            "start_sec": round(seg.start_sec, 2),
            "end_sec": round(seg.end_sec, 2),
        }
        if match is not None:
            match.occurrences.append(occ)
            match.last_seen_sec = max(match.last_seen_sec, seg.end_sec)
            if len(seg.text_norm) > len(match.text_norm) or (
                len(seg.text_norm) == len(match.text_norm) and seg.confidence > match.confidence
            ):
                match.text = seg.text
                match.text_norm = seg.text_norm
                if len(seg.first_line_norm) >= len(match.first_line_norm):
                    match.first_line = seg.first_line
                    match.first_line_norm = seg.first_line_norm
                match.confidence = max(match.confidence, seg.confidence)
            continue

        role = map_pokopia_role(seg.speaker)
        entry = UniqueDialogue(
            id=f"dlg_{len(unique) + 1:04d}",
            speaker=seg.speaker,
            speaker_norm=sp_norm,
            role_slot=role[0] if role else None,
            role_label=role[1] if role else "unmapped",
            text=seg.text,
            text_norm=seg.text_norm,
            first_line=seg.first_line,
            first_line_norm=seg.first_line_norm,
            surface=seg.surface,
            confidence=seg.confidence,
            first_seen_sec=seg.start_sec,
            last_seen_sec=seg.end_sec,
            occurrences=[occ],
        )
        unique.append(entry)
        by_exact[exact_key] = entry

    return unique


def analyze_prefixes(unique: list[UniqueDialogue]) -> dict:
    qualifying = [
        d for d in unique
        if len(d.first_line_norm) >= 25 or len(d.first_line_norm.split()) >= 4
    ]
    global_counts = Counter(d.first_line_norm for d in qualifying)
    speaker_counts = Counter((d.speaker_norm, d.first_line_norm) for d in qualifying)
    unique_global = sum(1 for d in qualifying if global_counts[d.first_line_norm] == 1)
    unique_per_speaker = sum(1 for d in qualifying if speaker_counts[(d.speaker_norm, d.first_line_norm)] == 1)
    return {
        "total_unique": len(unique),
        "qualifying_count": len(qualifying),
        "unique_global_count": unique_global,
        "unique_per_speaker_count": unique_per_speaker,
        "collisions": [
            {"first_line_norm": k, "count": v}
            for k, v in global_counts.most_common(10)
            if v > 1
        ],
    }


def _fmt_time(sec: float) -> str:
    total = max(0, int(round(sec)))
    h = total // 3600
    m = (total % 3600) // 60
    s = total % 60
    return f"{h:02d}:{m:02d}:{s:02d}" if h > 0 else f"{m:02d}:{s:02d}"


def build_report(
    segments: list[DialogueSegment],
    unique: list[UniqueDialogue],
    stats: Optional[dict] = None,
    title: str = "Pokopia Story-Pack Extraktion",
) -> str:
    total_chars = sum(len(d.text) for d in unique)
    est_audio_sec = total_chars / 15.0
    est_opus_kb = est_audio_sec * 3.5  # 28 kbps = 3.5 KB/s
    prefix_stats = analyze_prefixes(unique)

    lines: list[str] = [f"# {title}", ""]

    if stats:
        lines.extend([
            "## Frame- & Laufzeitstatistik",
            f"- Gesamtframes: **{stats.get('total_frames', '?')}**",
            f"- Übersprungen (unverändert): **{stats.get('skipped_unchanged', '?')}**",
        ])
        if "skipped_changing" in stats:
            lines.append(f"- Übersprungen (Tipp-Animation / Wechsel): **{stats.get('skipped_changing')}**")
        lines.extend([
            f"- Übersprungen (keine Dialogbox-Farbe): **{stats.get('skipped_no_bubble', '?')}**",
            f"- Ge-OCRt (Tesseract deu): **{stats.get('ocr_frames', '?')}**",
            f"- OCR-Laufzeit: **{stats.get('elapsed_sec', '?')} s**",
        ])
        if stats.get("ocr_errors", 0):
            lines.append(f"- **Warnung · OCR-Fehler:** **{stats.get('ocr_errors')}**")
        lines.append("")

    lines.extend([
        "## Dialog- & Speicher-Schätzung",
        f"- Zeitlich zusammengeführte Dialog-Segmente: **{len(segments)}**",
        f"- Eindeutige Dialoge (`script.jsonl`): **{len(unique)}**",
        f"- Gesamte Zeichenanzahl (eindeutig): **{total_chars:,}**",
        f"- Geschätzte Vorlesedauer (~15 Zeichen/s): **{_fmt_time(est_audio_sec)}** ({est_audio_sec:.0f} s)",
        f"- Geschätzte Opus-Größe (~28 kbps): **{est_opus_kb / 1024:.2f} MB** ({est_opus_kb:.0f} KB)",
        "",
        "## Early-Prefix-Analyse (Zeile 1)",
        f"- Dialoge mit qualifizierender Zeile 1 (≥ 25 Zeichen oder ≥ 4 Wörter): **{prefix_stats['qualifying_count']} / {prefix_stats['total_unique']}** "
        f"({(100.0 * prefix_stats['qualifying_count'] / max(1, prefix_stats['total_unique'])):.1f} %)",
        f"- Davon global eindeutig (ohne Sprecher): **{prefix_stats['unique_global_count']} / {prefix_stats['total_unique']}** "
        f"({(100.0 * prefix_stats['unique_global_count'] / max(1, prefix_stats['total_unique'])):.1f} %)",
        f"- Davon eindeutig pro (Sprecher, Zeile 1): **{prefix_stats['unique_per_speaker_count']} / {prefix_stats['total_unique']}** "
        f"({(100.0 * prefix_stats['unique_per_speaker_count'] / max(1, prefix_stats['total_unique'])):.1f} %)",
        "",
    ])

    if prefix_stats["collisions"]:
        lines.append("Häufigste mehrdeutige Zeilenanfänge:")
        for c in prefix_stats["collisions"][:5]:
            lines.append(f"- `{c['first_line_norm']}` ({c['count']}×)")
        lines.append("")

    # Sprecher-Übersicht
    sp_unique: Counter = Counter()
    sp_occ: Counter = Counter()
    for d in unique:
        sp_unique[d.speaker] += 1
        sp_occ[d.speaker] += len(d.occurrences)

    lines.extend([
        "## Sprecher-Übersicht",
        "| Sprecher | Eindeutige Dialoge | Vorkommen | Pokopia-Rolle |",
        "| --- | ---: | ---: | --- |",
    ])
    for sp, cnt in sp_unique.most_common():
        role = map_pokopia_role(sp)
        role_str = f"Slot {role[0]} ({role[1]})" if role else "unmapped"
        lines.append(f"| {sp} | {cnt} | {sp_occ[sp]} | {role_str} |")
    lines.append("")

    # Referenz-Stichprobe für Domtendo Folge 1 (docs/DIALOGBOX_REFERENZEN.md)
    refs = [
        ("01:23 (Positiv · Blauer Kasten)", 83.0),
        ("01:42 (Positiv · Blauer Kasten + Antworten)", 102.0),
        ("02:00 (Negativ · Frisurenmenü)", 120.0),
        ("04:00 (Positiv · Tangoloss hell)", 240.0),
        ("12:00 (Negativ · Pokédex)", 720.0),
        ("20:50 (Negativ · Einstellungen)", 1250.0),
    ]
    lines.extend([
        "## Referenz-Stichprobe (`DIALOGBOX_REFERENZEN.md` ±6 s)",
        "| Erwartung | Zeitfenster | Gefundene Segmente |",
        "| --- | --- | --- |",
    ])
    for label, center in refs:
        hits = [
            s for s in segments
            if s.end_sec >= center - 6.0 and s.start_sec <= center + 6.0
        ]
        if not hits:
            desc = "*(keine)*"
        else:
            desc = " / ".join(
                f"[{_fmt_time(s.start_sec)}–{_fmt_time(s.end_sec)}] **{s.speaker}** ({s.surface}): „{s.text[:70]}“"
                for s in hits[:3]
            )
        lines.append(f"| {label} | {_fmt_time(max(0, center - 6))}–{_fmt_time(center + 6)} | {desc} |")
    lines.append("")

    # Stichproben
    lines.extend(["## Stichproben extrahierter Dialoge (erste 20)", ""])
    for d in unique[:20]:
        lines.append(
            f"- `{d.id}` [{_fmt_time(d.first_seen_sec)} · {d.surface} · {d.speaker}]: „{d.text}“ *(Zeile 1: „{d.first_line}“)*"
        )
    lines.append("")

    if len(unique) > 20:
        rest = unique[20:]
        step = max(1, len(rest) // 10)
        sampled = [rest[i] for i in range(0, len(rest), step)][:10]
        lines.extend(["## Stichproben Mitte/Ende (10 gleichmäßig verteilt)", ""])
        for d in sampled:
            lines.append(
                f"- `{d.id}` [{_fmt_time(d.first_seen_sec)} · {d.surface} · {d.speaker}]: „{d.text}“"
            )
        lines.append("")

    return "\n".join(lines)


def process_jsonl(
    raw_jsonl: Path,
    script_out: Path,
    report_out: Path,
    stats: Optional[dict] = None,
    title: str = "Pokopia Story-Pack Extraktion",
) -> tuple[list[DialogueSegment], list[UniqueDialogue]]:
    frames: list[tuple[int, float, Optional[FrameDialogue]]] = []
    with raw_jsonl.open("r", encoding="utf-8") as fh:
        for line in fh:
            stripped = line.strip()
            if not stripped:
                continue
            frames.append(parse_raw_frame(json.loads(stripped)))

    segments = merge_frames(frames)
    unique = deduplicate_segments(segments)

    script_out.parent.mkdir(parents=True, exist_ok=True)
    with script_out.open("w", encoding="utf-8") as out_fh:
        for item in unique:
            out_fh.write(json.dumps(item.to_dict(), ensure_ascii=False) + "\n")

    report = build_report(segments, unique, stats=stats, title=title)
    report_out.write_text(report, encoding="utf-8")
    return segments, unique


def main() -> int:
    parser = argparse.ArgumentParser(description="Nachverarbeitung von raw_ocr.jsonl zu script.jsonl + report.md")
    parser.add_argument("--raw-jsonl", required=True, type=Path)
    parser.add_argument("--script-out", required=True, type=Path)
    parser.add_argument("--report-out", required=True, type=Path)
    parser.add_argument("--stats-json", type=Path, default=None)
    parser.add_argument("--title", default="Pokopia Story-Pack Extraktion")
    args = parser.parse_args()

    stats = None
    if args.stats_json and args.stats_json.is_file():
        stats = json.loads(args.stats_json.read_text(encoding="utf-8"))

    segments, unique = process_jsonl(
        raw_jsonl=args.raw_jsonl,
        script_out=args.script_out,
        report_out=args.report_out,
        stats=stats,
        title=args.title,
    )
    print(f"Fertig: {len(segments)} Segmente -> {len(unique)} eindeutige Dialoge ({args.script_out})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
