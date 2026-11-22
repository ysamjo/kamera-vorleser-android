#!/usr/bin/env python3
"""Orchestrierungs-CLI: YouTube/Video -> Cropped Frames -> Tesseract OCR -> script.jsonl + report.md."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
DEFAULT_BIN = REPO_ROOT / "Archiv" / "story-pack" / ".bin" / "frame-ocr"
SWIFT_SRC = SCRIPT_DIR / "frame_ocr.swift"

sys.path.insert(0, str(SCRIPT_DIR))
import dialogue_postprocess  # noqa: E402


def run_cmd(cmd: list[str], capture: bool = False) -> subprocess.CompletedProcess:
    print("+", " ".join(cmd), flush=True)
    return subprocess.run(
        cmd,
        check=True,
        text=True,
        capture_output=capture,
    )


def find_video_file(out_dir: Path) -> Path | None:
    mp4 = out_dir / "video.mp4"
    if mp4.is_file():
        return mp4
    for candidate in sorted(out_dir.glob("video.*")):
        if candidate.suffix not in {".part", ".ytdl", ".json"} and candidate.is_file():
            return candidate
    return None


def download_video(url: str, out_dir: Path) -> Path:
    existing = find_video_file(out_dir)
    meta_path = out_dir / "metadata.json"
    if not meta_path.is_file():
        proc = run_cmd(
            ["yt-dlp", "--dump-single-json", "--no-playlist", url],
            capture=True,
        )
        meta_path.write_text(proc.stdout, encoding="utf-8")

    if existing is not None:
        print(f"Nutze vorhandenes Video: {existing}", flush=True)
        return existing

    fmt = "bestvideo[height<=720][ext=mp4]/best[height<=720][ext=mp4]/bestvideo[height<=720]/best[height<=720]"
    run_cmd([
        "yt-dlp",
        "-f", fmt,
        "--no-playlist",
        "-o", str(out_dir / "video.%(ext)s"),
        url,
    ])
    video = find_video_file(out_dir)
    if video is None:
        raise FileNotFoundError(f"yt-dlp hat keine Videodatei in {out_dir} erzeugt.")
    return video


def probe_resolution(video: Path) -> tuple[int, int]:
    proc = run_cmd([
        "ffprobe",
        "-v", "error",
        "-select_streams", "v:0",
        "-show_entries", "stream=width,height",
        "-of", "json",
        str(video),
    ], capture=True)
    data = json.loads(proc.stdout)
    stream = data["streams"][0]
    return int(stream["width"]), int(stream["height"])


def ensure_vision_binary(bin_path: Path) -> Path:
    bin_path.parent.mkdir(parents=True, exist_ok=True)
    needs_build = (
        not bin_path.is_file()
        or SWIFT_SRC.stat().st_mtime > bin_path.stat().st_mtime
    )
    if needs_build:
        run_cmd(["swiftc", "-O", str(SWIFT_SRC), "-o", str(bin_path)])
    return bin_path


def extract_frames(
    video: Path,
    frames_dir: Path,
    width: int,
    height: int,
    crop_y_ratio: float,
    fps: float,
    max_seconds: float | None,
) -> None:
    if frames_dir.exists():
        shutil.rmtree(frames_dir)
    frames_dir.mkdir(parents=True, exist_ok=True)

    crop_top = round(height * crop_y_ratio)
    crop_h = height - crop_top
    vf = f"fps={fps},crop={width}:{crop_h}:0:{crop_top}"

    cmd = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "warning", "-i", str(video)]
    if max_seconds is not None and max_seconds > 0:
        cmd.extend(["-t", str(max_seconds)])
    cmd.extend(["-vf", vf, str(frames_dir / "frame_%06d.png")])
    run_cmd(cmd)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Extrahiert Pokopia-Dialoge aus YouTube-Walkthroughs oder lokalen Videos."
    )
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--url", help="YouTube-Video-URL")
    source.add_argument("--video", type=Path, help="Lokale Videodatei (.mp4/.mkv/.webm)")
    parser.add_argument("--out-dir", required=True, type=Path, help="Ausgabeordner (z. B. Archiv/story-pack/domtendo-001)")
    parser.add_argument("--fps", type=float, default=2.0, help="Abtastrate in Frames/s (Standard: 2.0)")
    parser.add_argument("--crop-y-ratio", type=float, default=0.60, help="Oberer Crop-Startanteil (Standard: 0.60)")
    parser.add_argument("--keep-frames", action="store_true", help="Extrahierte PNG-Frames behalten")
    parser.add_argument("--skip-download", action="store_true", help="Vorhandenes Video in --out-dir verwenden")
    parser.add_argument("--max-seconds", type=float, default=None, help="Optional nur erste N Sekunden verarbeiten")
    parser.add_argument("--ocr-bin", "--vision-bin", dest="ocr_bin", type=Path, default=DEFAULT_BIN, help="Pfad zur kompilierten frame-ocr-Binary")
    args = parser.parse_args()

    out_dir = args.out_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    if args.video is not None:
        video = args.video
        if not video.is_file():
            raise FileNotFoundError(f"Videodatei nicht gefunden: {video}")
    elif args.skip_download:
        video = find_video_file(out_dir)
        if video is None:
            raise FileNotFoundError(f"--skip-download gesetzt, aber kein Video in {out_dir} gefunden.")
    else:
        video = download_video(args.url, out_dir)

    width, height = probe_resolution(video)
    print(f"Video-Auflösung: {width}x{height}", flush=True)

    frames_dir = out_dir / "frames"
    extract_frames(
        video=video,
        frames_dir=frames_dir,
        width=width,
        height=height,
        crop_y_ratio=args.crop_y_ratio,
        fps=args.fps,
        max_seconds=args.max_seconds,
    )

    ocr_bin = ensure_vision_binary(args.ocr_bin)
    raw_jsonl = out_dir / "raw_ocr.jsonl"
    ocr_proc = run_cmd([
        str(ocr_bin),
        "--frames-dir", str(frames_dir),
        "--full-width", str(width),
        "--full-height", str(height),
        "--crop-y-ratio", str(args.crop_y_ratio),
        "--fps", str(args.fps),
        "--out", str(raw_jsonl),
    ], capture=True)

    stats = None
    stats_lines = [line for line in (ocr_proc.stderr or "").splitlines() if line.strip()]
    for line in stats_lines:
        try:
            candidate = json.loads(line)
            if isinstance(candidate, dict) and "total_frames" in candidate:
                stats = candidate
                continue
        except json.JSONDecodeError:
            pass
        print("OCR-Hinweis:", line, file=sys.stderr)
    if stats is not None:
        (out_dir / "ocr_stats.json").write_text(
            json.dumps(stats, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print("OCR-Statistik:", stats, flush=True)

    title = "Pokopia Story-Pack Extraktion"
    meta_path = out_dir / "metadata.json"
    if meta_path.is_file():
        try:
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
            if meta.get("title"):
                title = f"Pokopia Story-Pack · {meta['title']}"
        except json.JSONDecodeError:
            pass

    script_out = out_dir / "script.jsonl"
    report_out = out_dir / "report.md"
    segments, unique = dialogue_postprocess.process_jsonl(
        raw_jsonl=raw_jsonl,
        script_out=script_out,
        report_out=report_out,
        stats=stats,
        title=title,
    )
    print(f"Ergebnis: {len(segments)} Segmente -> {len(unique)} eindeutige Dialoge", flush=True)
    print(f"Skript:  {script_out}", flush=True)
    print(f"Bericht: {report_out}", flush=True)

    if not args.keep_frames and frames_dir.exists():
        shutil.rmtree(frames_dir)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
