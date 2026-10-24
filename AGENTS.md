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

`TextRules` enthält unverändert extrahierte Filter und Ähnlichkeitsschwellen.
Instrumentation bauen: `./gradlew :app:assembleDebugAndroidTest`.
Instrumentation ausführen: `ANDROID_SERIAL=<ADB-Gerät> ./gradlew :app:connectedDebugAndroidTest`.
Gerät wach/entsperrt halten; übersprungene Tests ausdrücklich berichten.
Das zusammengeführte App-Manifest darf keine INTERNET-Berechtigung enthalten.

`DialogueHistory` trennt laufende von abgeschlossenen Dialogen. Nur `finish(key,
true)` nach `onDone` speichert dauerhaft; Stop/Fehler geben nur laufende Einträge
frei. „Neu lesen“ setzt eine einmalige Freigabe statt gespeicherte Einträge zu
löschen. TTS-Abschluss und Initialisierung immer auf den Main Thread posten.
`SpeakerVoice` berechnet Namensvariation und Tempo ohne persistierten Raten-Cache.
Auch Normalisierung und Namensentfernung müssen zusammengesetzte Umlaute erhalten.
Vollprüfung: `./gradlew :app:assembleDebug :app:assembleRelease :app:bundleDebug
:app:bundleRelease :app:lint :app:test :app:assembleDebugAndroidTest` (eine Zeile).
