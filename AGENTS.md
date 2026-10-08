# Pokopia Story-Vorleser

## Kontext und Grenzen
Native Android-App (Java 17, CameraX, gebündeltes ML Kit, Android-TTS).
Offline-OCR ist Pflicht; keine Cloud-Dienste und keine Übertragung von Bildern/Texten.
`MainActivity` besitzt UI, Dialogzustand und TTS auf dem Main Thread. Der
CameraX-Executor nimmt Frames an. ML-Kit-Abschluss läuft explizit auf dem Main
Thread; Bilddaten bleiben bis zum Abschluss gültig. `FrameRelease` gibt genau
einmal frei. `StoryTextAssembler` enthält reine Geometrie-/Textlogik. `SpeechQueue` verfolgt
Utterance-IDs ausschließlich auf dem Main Thread; vor TTS-Stop immer leeren.
Schwellen, Sprecherprofile und Bediensemantik nur mit gezieltem Regressionstest ändern.

## Befehle
- JDK 21 für Gradle 9.5.0 / AGP 9.3.2; SDK 36, minSdk 23.
- Prüfung: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Gerät prüfen: `adb devices -l`; nur auf dem ausdrücklich gewählten Testgerät installieren.
- Vor Änderungen aktuellen Plan in `docs/VERBESSERUNGSPLAN.md` lesen.
- Validierungsgrenzen in `docs/VALIDIERUNG.md` pflegen; Unit-Tests ersetzen keine Kamera-/TTS-Gerätetests.

CI: `.github/workflows/android.yml` führt dieselben Prüfungen unter Linux/JDK 21
aus. Keine Lint-Baseline verwenden, um neue Fehler zu verstecken. Lokale Archive
liegen unter `Archiv/` und bleiben außerhalb von Git.

`PronunciationDictionary` ist reine Textlogik mit Unicode-Wortgrenzen. Nur lokale,
installierte deutsche TTS-Stimmen verwenden; kein Netzwerkfallback.
