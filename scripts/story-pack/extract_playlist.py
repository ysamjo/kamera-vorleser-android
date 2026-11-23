#!/usr/bin/env python3
"""Resumable Playlist-Batch-Runner für extract_script.py (nur Python-Stdlib)."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))
import extract_script  # noqa: E402

DEFAULT_OUT_ROOT = extract_script.REPO_ROOT / "Archiv" / "story-pack"


@dataclass(frozen=True)
class PlaylistItem:
    index: int
    video_id: str
    duration: Optional[int]
    title: str


def parse_flat_playlist_lines(stdout: str) -> list[PlaylistItem]:
    """Parst `yt-dlp --flat-playlist --print "%(playlist_index)s\\t%(id)s\\t%(duration)s\\t%(title)s"`."""
    items: list[PlaylistItem] = []
    for raw_line in (stdout or "").splitlines():
        line = raw_line.rstrip("\r\n")
        if not line.strip():
            continue
        parts = line.split("\t", 3)
        if len(parts) < 4:
            continue
        idx_raw, video_id, dur_raw, title = parts
        idx_raw = idx_raw.strip()
        video_id = video_id.strip()
        dur_raw = dur_raw.strip()
        title = title.strip()
        if not idx_raw.isdigit() or not video_id or video_id == "NA":
            continue
        duration: Optional[int]
        if not dur_raw or dur_raw.upper() == "NA":
            duration = None
        else:
            try:
                duration = round(float(dur_raw))
            except ValueError:
                duration = None
        items.append(PlaylistItem(
            index=int(idx_raw),
            video_id=video_id,
            duration=duration,
            title=title,
        ))
    return items


def select_items(
    items: list[PlaylistItem],
    start: int,
    end: Optional[int],
) -> list[PlaylistItem]:
    """Filtert Playlist-Einträge auf den inklusiven 1-basierten Bereich [start, end]."""
    selected: list[PlaylistItem] = []
    for item in items:
        if item.index < start:
            continue
        if end is not None and item.index > end:
            continue
        selected.append(item)
    return selected


def episode_dir(out_root: Path, prefix: str, index: int) -> Path:
    """Erzeugt den Ausgabepfad `<out_root>/<prefix>-NNN`."""
    return out_root / f"{prefix}-{index:03d}"


def should_skip(ep_dir: Path, force: bool) -> bool:
    """Überspringt bereits extrahierte Folgen mit nicht-leerer script.jsonl, sofern nicht --force."""
    if force:
        return False
    script_file = ep_dir / "script.jsonl"
    return script_file.is_file() and script_file.stat().st_size > 0


def fetch_playlist_items(playlist_url: str) -> list[PlaylistItem]:
    proc = subprocess.run(
        [
            "yt-dlp",
            "--flat-playlist",
            "--print",
            "%(playlist_index)s\t%(id)s\t%(duration)s\t%(title)s",
            playlist_url,
        ],
        check=True,
        text=True,
        capture_output=True,
    )
    return parse_flat_playlist_lines(proc.stdout)


def read_episode_metrics(ep_dir: Path) -> tuple[Optional[int], Optional[int]]:
    dialogue_count: Optional[int] = None
    script_file = ep_dir / "script.jsonl"
    if script_file.is_file():
        with script_file.open("r", encoding="utf-8") as fh:
            dialogue_count = sum(1 for line in fh if line.strip())

    ocr_errors: Optional[int] = None
    stats_file = ep_dir / "ocr_stats.json"
    if stats_file.is_file():
        try:
            stats = json.loads(stats_file.read_text(encoding="utf-8"))
            if isinstance(stats, dict) and "ocr_errors" in stats:
                ocr_errors = int(stats["ocr_errors"])
        except (json.JSONDecodeError, TypeError, ValueError):
            pass
    return dialogue_count, ocr_errors


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Extrahiert mehrere Pokopia-Folgen einer YouTube-Playlist sequenziell und resumable."
    )
    parser.add_argument("--playlist", required=True, help="YouTube-Playlist-URL")
    parser.add_argument("--out-root", type=Path, default=DEFAULT_OUT_ROOT, help="Basisordner (Standard: Archiv/story-pack)")
    parser.add_argument("--prefix", default="domtendo", help="Ordner-Präfix je Folge (Standard: domtendo)")
    parser.add_argument("--start", type=int, default=1, help="Erster Playlist-Index inklusiv (Standard: 1)")
    parser.add_argument("--end", type=int, default=None, help="Letzter Playlist-Index inklusiv (optional)")
    parser.add_argument("--fps", type=float, default=2.0, help="Abtastrate in Frames/s (Standard: 2.0)")
    parser.add_argument("--crop-y-ratio", type=float, default=0.60, help="Oberer Crop-Startanteil (Standard: 0.60)")
    parser.add_argument("--delete-video", action="store_true", help="Heruntergeladenes Video nach erfolgreicher Extraktion löschen")
    parser.add_argument("--force", action="store_true", help="Bereits vorhandene script.jsonl neu extrahieren")
    parser.add_argument("--retries", type=int, default=2, help="Wiederholungen je Folge bei transienten Fehlern (Standard: 2)")
    parser.add_argument("--sleep-between", type=float, default=2.0, help="Pause zwischen Folgen in Sekunden (Standard: 2.0)")
    parser.add_argument("--min-free-gb", type=float, default=1.5, help="Mindestens freier Speicherplatz in GiB vor jeder Folge (Standard: 1.5)")
    parser.add_argument("--dry-run", action="store_true", help="Nur ausgewählte Folgen mit Skip-/Run-Status anzeigen")
    args = parser.parse_args()

    if args.start < 1:
        print("Fehler: --start muss >= 1 sein.", file=sys.stderr)
        return 2
    if args.end is not None and args.end < args.start:
        print("Fehler: --end darf nicht kleiner als --start sein.", file=sys.stderr)
        return 2

    out_root = args.out_root
    out_root.mkdir(parents=True, exist_ok=True)

    all_items = fetch_playlist_items(args.playlist)
    selected = select_items(all_items, start=args.start, end=args.end)
    if not selected:
        print(f"Keine Playlist-Einträge im Bereich {args.start}..{args.end or 'Ende'} gefunden.", file=sys.stderr)
        return 1

    print(
        f"Playlist-Auswahl: {len(selected)} Folgen (#{selected[0].index}–#{selected[-1].index}, "
        f"Präfix '{args.prefix}')",
        flush=True,
    )

    if args.dry_run:
        for item in selected:
            ep_dir = episode_dir(out_root, args.prefix, item.index)
            action = "SKIP" if should_skip(ep_dir, args.force) else "RUN"
            dur_str = f"{item.duration}s" if item.duration is not None else "?"
            print(f"[{action}] #{item.index:03d} | {item.video_id} | {dur_str} | {item.title} -> {ep_dir}")
        return 0

    extract_script.ensure_vision_binary(extract_script.DEFAULT_BIN)

    results: list[dict] = []
    failed_count = 0
    ok_count = 0
    skipped_count = 0
    processed_non_skipped = 0

    for item in selected:
        ep_dir = episode_dir(out_root, args.prefix, item.index)
        if should_skip(ep_dir, args.force):
            dialogue_count, ocr_errors = read_episode_metrics(ep_dir)
            skipped_count += 1
            print(f"[SKIP] #{item.index:03d} ({item.video_id}): bereits vorhanden in {ep_dir}", flush=True)
            results.append({
                "index": item.index,
                "video_id": item.video_id,
                "title": item.title,
                "dir": str(ep_dir),
                "status": "skipped",
                "attempts": 0,
                "dialogue_count": dialogue_count,
                "ocr_errors": ocr_errors,
            })
            continue

        free_gb = shutil.disk_usage(out_root).free / (1024 ** 3)
        if free_gb < args.min_free_gb:
            msg = f"Zu wenig freier Speicher ({free_gb:.2f} GiB < {args.min_free_gb:.2f} GiB) vor Folge #{item.index:03d}"
            print(f"Abbruch: {msg}", file=sys.stderr, flush=True)
            failed_count += 1
            results.append({
                "index": item.index,
                "video_id": item.video_id,
                "title": item.title,
                "dir": str(ep_dir),
                "status": "failed",
                "attempts": 0,
                "dialogue_count": None,
                "ocr_errors": None,
                "error": msg,
            })
            break

        if processed_non_skipped > 0 and args.sleep_between > 0:
            time.sleep(args.sleep_between)
        processed_non_skipped += 1

        cmd = [
            sys.executable,
            str(SCRIPT_DIR / "extract_script.py"),
            "--url", f"https://www.youtube.com/watch?v={item.video_id}",
            "--out-dir", str(ep_dir),
            "--fps", str(args.fps),
            "--crop-y-ratio", str(args.crop_y_ratio),
        ]
        if args.delete_video:
            cmd.append("--delete-video")

        max_attempts = max(1, 1 + args.retries)
        last_error = ""
        succeeded = False
        attempts_done = 0

        for attempt in range(1, max_attempts + 1):
            attempts_done = attempt
            print(
                f"[RUN ] #{item.index:03d} ({item.video_id}) Versuch {attempt}/{max_attempts} "
                f"[frei: {free_gb:.2f} GiB]: {item.title}",
                flush=True,
            )
            proc = subprocess.run(cmd, check=False)
            if proc.returncode == 0 and (ep_dir / "script.jsonl").is_file() and (ep_dir / "script.jsonl").stat().st_size > 0:
                succeeded = True
                break
            last_error = f"extract_script.py Exit-Code {proc.returncode}"
            print(f"[WARN] #{item.index:03d} fehlgeschlagen ({last_error})", file=sys.stderr, flush=True)
            if attempt < max_attempts:
                wait_s = 5.0 * attempt
                time.sleep(wait_s)

        if succeeded:
            ok_count += 1
            dialogue_count, ocr_errors = read_episode_metrics(ep_dir)
            print(
                f"[ OK ] #{item.index:03d}: {dialogue_count} Dialoge, ocr_errors={ocr_errors}",
                flush=True,
            )
            results.append({
                "index": item.index,
                "video_id": item.video_id,
                "title": item.title,
                "dir": str(ep_dir),
                "status": "ok",
                "attempts": attempts_done,
                "dialogue_count": dialogue_count,
                "ocr_errors": ocr_errors,
            })
        else:
            failed_count += 1
            frames_leftover = ep_dir / "frames"
            if frames_leftover.exists():
                shutil.rmtree(frames_leftover, ignore_errors=True)
            print(f"[FAIL] #{item.index:03d} endgültig fehlgeschlagen: {last_error}", file=sys.stderr, flush=True)
            results.append({
                "index": item.index,
                "video_id": item.video_id,
                "title": item.title,
                "dir": str(ep_dir),
                "status": "failed",
                "attempts": attempts_done,
                "dialogue_count": None,
                "ocr_errors": None,
                "error": last_error,
            })

    summary = {
        "playlist": args.playlist,
        "prefix": args.prefix,
        "start": args.start,
        "end": args.end,
        "updated_at": datetime.now(timezone.utc).isoformat(),
        "total_selected": len(selected),
        "ok": ok_count,
        "skipped": skipped_count,
        "failed": failed_count,
        "episodes": results,
    }
    summary_path = out_root / f"{args.prefix}-playlist-summary.json"
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        f"Playlist-Zusammenfassung ({summary_path}): "
        f"{ok_count} ok, {skipped_count} übersprungen, {failed_count} fehlgeschlagen",
        flush=True,
    )
    return 0 if failed_count == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
