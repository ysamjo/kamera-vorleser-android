# Story-Pack Dialog-Extraktor (macOS, ohne pip-Abhängigkeiten)

Extrahiert deutsche *Pokémon Pokopia*-Dialoge (Sprecher, Volltext und erste Zeile) aus YouTube-Walkthroughs oder lokalen Videodateien mithilfe von `yt-dlp`, `ffmpeg`, schnellem Swift-Vorfilter und `tesseract` (`deu`).

Alle heruntergeladenen Videos, extrahierten Frames, Binärdateien und Ergebnisse gehören unter `Archiv/story-pack/` und bleiben durch `.gitignore` außerhalb von Git.

## Voraussetzungen

- macOS mit Xcode Command Line Tools (`swiftc`)
- `python3`, `yt-dlp`, `ffmpeg`, `ffprobe`, `tesseract` inkl. `deu`-Sprachdaten (z. B. via Homebrew `brew install tesseract tesseract-lang`)

## Aufruf

### Schnelltest (erste 300 Sekunden einer Folge)
```bash
python3 scripts/story-pack/extract_script.py \
  --url "https://www.youtube.com/watch?v=Cxwecy8pOas" \
  --out-dir Archiv/story-pack/domtendo-001-test \
  --max-seconds 300
```

### Komplette Folge
```bash
python3 scripts/story-pack/extract_script.py \
  --url "https://www.youtube.com/watch?v=Cxwecy8pOas" \
  --out-dir Archiv/story-pack/domtendo-001
```

### Wichtige Optionen
- `--fps 2.0`: Abtastrate (Standard: 2 Frames/s)
- `--crop-y-ratio 0.60`: Oberer Crop-Beginn als Anteil der Bildhöhe (Standard: untere 40 %)
- `--skip-download`: Vorhandenes `video.*` in `--out-dir` direkt weiterverarbeiten
- `--keep-frames`: Temporäre PNG-Frames behalten (für visuelle Fehlersuche)
- `--video <pfad>`: Lokale Videodatei statt YouTube-URL verwenden

## Ausgabeformat

- `raw_ocr.jsonl`: Rohe OCR-Zeilen je geprüftem Frame inkl. erkannter Box-Fläche (`BLUE`, `GRAY`, `OUTLINED`, `NONE`)
- `script.jsonl`: Bereinigte, zeitlich zusammengeführte und deduplizierte Dialoge (`id`, `speaker`, `role_slot`, `text`, `first_line`, `first_line_norm`, `occurrences`, …)
- `report.md`: Auswertung mit Frame-Statistik, geschätzter Audiodauer/Opus-Größe, Early-Prefix-Eindeutigkeit, Sprecherverteilung und Referenz-Stichprobe

## Unit-Tests
```bash
python3 -m unittest discover -s scripts/story-pack -v
```

## Story-Pack rendern & aufs Gerät übertragen

### 1. Bereinigung & Größe vorab prüfen (ohne API-Aufruf)
```bash
python3 scripts/story-pack/render_pack.py \
  --scripts Archiv/story-pack/domtendo-001/script.jsonl \
  --out-dir Archiv/story-pack/pack-ep001 \
  --dry-run
```

### 2. Kostenlose Vorschau mit macOS-Stimme (`Anna`) als Opus rendern
```bash
python3 scripts/story-pack/render_pack.py \
  --scripts Archiv/story-pack/domtendo-001/script.jsonl \
  --out-dir Archiv/story-pack/pack-ep001-say \
  --provider macos-say
```

### 3. Produktives Pack mit Fish Audio Drama 3 rendern (mehrere Folgen kombinierbar)
```bash
FISH_API_KEY="..." python3 scripts/story-pack/render_pack.py \
  --scripts Archiv/story-pack/domtendo-*/script.jsonl \
  --out-dir Archiv/story-pack/pack-pokopia \
  --provider fish
```
Bereits gerenderte `audio/<id>.ogg`-Dateien werden bei erneutem Aufruf automatisch übersprungen (Resume).

### 4. Sicher aufs gewählte Testgerät übertragen (`files/story-pack`, ohne Keystore anzufassen)
```bash
ANDROID_SERIAL=<ADB-Gerät> ./scripts/story-pack/push-pack.sh Archiv/story-pack/pack-pokopia
```
