# Umsetzung und Validierung

## 04.11.2026 · Sprechblasen, Reaktionszeit und Wiederholungen (1.6 / Code 7)

- Die untere Bildschirmzone allein gibt keinen Text mehr frei. OCR-Zeilen müssen
  in der unteren mittleren Zone liegen und seitlich sowie darüber/darunter von
  einer überwiegend blauen oder gleichmäßig grauen Fläche umgeben sein. Zusätzlich
  muss sich die Fläche von einer Stichprobenreihe oberhalb der Dialogzone abheben.
  Graue Zeilen benötigen ein aktuelles plausibles Namensschild; typische Menü-
  Überschriften und Dialogwörter werden als Namensschild abgewiesen. Schriftkanten
  werden als Ausreißer toleriert. Farbe/Geometrie sind Heuristiken, keine trainierte
  Erkennung der beiden Originalkonturen. Reale Spielaufnahmen fehlen weiterhin;
  falsch negative Fälle bei anderer Beleuchtung, Kameraposition oder fehlendem
  Namensschild sowie gleich gestaltete Beschreibungsfelder bleiben möglich.
- OCR-Abstand 180 → 120 ms. Zwei identische Befunde bleiben Pflicht. Satzenden
  benötigen 120 ms Ruhe, Text ohne Satzende 420 ms. Jede Wortänderung setzt die
  Ruhezeit zurück; die Zeitbasis ist monoton. Das verkürzt den nominellen Abstand
  um 60 ms, ist aber kein gemessener Gewinn bis zum hörbaren Sprachstart. Ein
  Typewriter kann auch nach Satzzeichen weitergehen; Cloud-Synthese/Netzwerk bleiben
  zusätzliche Latenz. Tempo-/Figurenprofile werden nicht verändert.
- Persistierte, abgeschlossene Fenster werden mehrfach am sichtbaren Anfang
  abgezogen. Überlappungen ab drei Wörtern tolerieren eine eng begrenzte OCR-
  Ziffer/Buchstabenverwechslung mit zwei exakten Ankern. Neue Inhaltswörter und
  Verneinungen bleiben erhalten. Historie bleibt sprecherbezogen (maximal 500
  Fenster), nur onDone speichert; Stop/Fehler und „Neu lesen“ behalten ihre Semantik.
- Neues Vektor-Icon mit Sprechblase, Linse und Schallbögen; adaptive Masken ab API 26
  und monochrome Variante ab API 33. Lokales ZIP vor Umbau unter Archiv/ gesichert.
- Separates Code-Review durchgeführt; Befunde zu grauen Schriftkanten, Menülabels
  und Inhaltswortänderungen mit gezielten Regressionstests bearbeitet.

Vollprüfung des Code-Stands `0293a03` (Dokumentationsstand `e107dec`) auf dem Mac
mit JDK 21 erfolgreich: Debug-/Release-APK, Debug-/Release-Bundle, Debug-/Release-
Lint, je **91 Unit-Testmethoden**, 0 Fehler/Skips, und Instrumentation-APK.
Lint jeweils **0 Fehler, 59 Warnungen** (darunter zwei zusätzliche Icon-Hinweise
zur Vektor-Rasterung vor API 24 und zum monochromen Tag in der API-26-Variante;
die API-33-Variante enthält das Tag). Keine Baseline oder Unterdrückung ergänzt.
40 fokussierte Tests zusätzlich unter Linux/JDK 21 und im separaten Review
bestanden. Lokaler Linux-Gradle-Lauf konnte mangels Android-SDK nicht bauen;
der erfolgreiche vollständige Mac-Lauf ist der Build-Nachweis.

Datenerhaltender Rollout ausschließlich auf das Pixel 10 Pro
(`192.168.178.117:5555`, bestätigt als `blazer`): App und Test-APK mit `install -r`.
Version **1.6 / Code 7** danach per Paketmanager bestätigt. Der direkte Runner
für `BundledOcrSmokeTest` meldet **1 Test erfolgreich, 0 Skips**. SHA-256 der
vollständigen Preferences-Datei und des verschlüsselten `no_backup/cloud-tts.bin`
sind vor/nach dem Lauf identisch. Keine Deinstallation oder Live-Anbieteranfrage.
Der OCR-Test verwendet synthetischen Text und prüft keine echten Kamerablasen.
Kameraabnahme mit den beiden echten Spielblasen, akustische Abnahme und
Ende-zu-Ende-Latenzmessung stehen noch aus. Automatisierte Tests senden keine
Anbieter-Requests.

## 02.11.2026 · 15 feste Figurenrollen (1.5 / Code 6)

- Fünf vorhandene Fish-Stimmen um zehn deutsche Fish-Official-Stimmen ergänzt;
  mit Ego Lite im angemeldeten Web-Katalog recherchiert. Alle IDs, Rollen und
  Beispiele stehen in [STIMMEN.md](STIMMEN.md). Keine neuen Originalstimmen-Klone
  erstellt und keine neue Behauptung zur Häufigkeit der Figuren im Spiel.
- Schlappchu/Pikachu getrennt; Bisasam, Glumanda und Schiggy erhalten eigene
  Rollen. Weitere Gruppen für gemütliche Riesen, Technik, kleine Figuren,
  elegante, massive und geisterhafte Figuren. Deutsche/englische Aliase und
  zusammengesetzte Umlaute getestet. Lokale Pitch-/Rate-Profile unverändert.
- Bekannte Rollen werden im vollständigen Standardpool anhand der ID zugeordnet,
  unabhängig von Listenreihenfolge, vorherigem Belegen durch unbekannte Figuren
  und alten gespeicherten automatischen Zuordnungen. Verwandte Figuren teilen
  bewusst eine Grundstimme; Custom-Pools behalten die bisherige Verteilung.
- Migration `pokopiaVoicesV2` erweitert nur fehlende oder alte Standardlisten.
  Eigene/leere/ungültige Listen bleiben bestehen, auch ohne V1-Marker. Nur beim
  Standard-Upgrade werden automatische Fish-Zuordnungen neu vergeben. Google-
  Einstellungen, Zugangsdaten, lokaler Modus und Dialoghistorie bleiben erhalten.
- Separates Read-only-Code-Review ohne konkrete Befunde. Regressionen für feste
  Rollen trotz belegtem Pool, Neustart/Umsortieren, Custom-Pools, Aliasgrenzen und
  Migration ergänzt; keine Anbieter-Requests aus automatisierten Tests.

### Prüfungen dieses Stands

Vollprüfung unter JDK 21: Debug-/Release-APKs, Debug-/Release-Bundles,
Debug-/Release-Lint, je **72 Unit-Testmethoden** ohne Fehler/Skip und
Instrumentation-APK erfolgreich. Beide Lint-Varianten: **0 Fehler, 57 Warnungen**;
keine Baseline eingeführt. `git diff --check` erfolgreich.

Die zehn neuen Stimmen lieferten im Fish-Web-Editor mit **Fish Audio S2.1 Pro**
kurze deutsche Rollenbeispiele (Bisasam, Schlappchu, Glumanda, Mooslaxo, Rotom,
Plinfa, Schiggy, Felori, Onix, Gengar); Player-/Download-Ergebnis im Browser sichtbar.
Das ist ein Synthesenachweis, keine akustische Abnahme und kein Nachweis für
`s2.1-pro-free` über die API. Keine Klone angelegt, kein Creator-Upgrade gebucht.

Drei neue Migrationstests auf Pixel 10 Pro / Android 17 erfolgreich, 0 Skips.
**Vorfall beim ersten Gerätelauf:** Der AGP-Aufruf `connectedDebugAndroidTest`
hat die App deinstalliert/neu installiert. Die Android-Wiederherstellung brachte
eine ältere Zweierliste zurück; `no_backup/cloud-tts.bin` und damit der Zugang
waren danach nicht vorhanden. Vorher erfasste Dialoghistorie (SHA-256/21.141 Zeichen),
Tempo, Engine und Effekt-/Cloud-Einstellung sind unverändert erhalten.
Die vor dem Rollout gelesene Fünferliste wurde bei gestoppter App wiederhergestellt.
Kein Erhalt des alten Zugangsspeichers behaupten.

Korrektur: `scripts/test-device.sh` nutzt ausschließlich `adb install -r` für
App/Test-APK und den direkten Instrumentation-Runner. AGENTS/README warnen vor
`connectedDebugAndroidTest` auf persönlichen Geräten. Separates Review fand einen
Bash-3.2-Randfall mit leeren Arrays; durch nichtleere Runner-Argumente korrigiert.
Abschluss: eigener Fish-Schlüssel „Pokopia Pixel 10 Pro“ im angemeldeten Konto
angelegt, per temporärem lokalen Hilfslauf ausschließlich über `CloudCredentials`
AES-GCM/Android-Keystore gespeichert und zurückgelesen. Der Hilfslauf sendete keine
Anbieter-Requests und ist nicht Bestandteil von Quellstand oder Test-APK. Ein
früherer Google-Backup-Zugang konnte nicht rekonstruiert werden; auf dem Gerät ist
nun Fish eingerichtet, Google muss bei Bedarf erneut eingetragen werden.

Alle zehn zusätzlichen Voice-IDs anschließend separat mit dem neuen Zugang gegen
`POST /v1/tts`, `model: s2.1-pro-free` geprüft: jeweils HTTP 200, `audio/mpeg`,
30.092–35.107 Bytes für einen kurzen deutschen Satz. Diese bewusst ausgeführte
Live-Prüfung ist vom schlüssellosen Unit-/CI-Testlauf getrennt. Keine Preis- oder
künftige Verfügbarkeitsgarantie aus dem erfolgreichen Gratis-Modell-Aufruf ableiten.

Neuen datenerhaltenden Ablauf unter macOS-Bash 3.2 auf demselben Pixel ausgeführt:
**3 Tests erfolgreich, 0 Skips**. SHA-256 des verschlüsselten Zugangsspeichers vor/nach
diesem Lauf identisch. Version 1.5 / Code 6 installiert. Die vollständige 15er-Liste
und V2-Marker wurden abschließend bei gestoppter App gespeichert und von Disk
zurückgelesen. Dialoghistorie bytegleich, Tempo 1,3 und bestehende Engine erhalten.
Die Migration selbst wurde im isolierten Test und temporären Hilfslauf geprüft.

Nach Nutzer-Entsperrung am 02.11.2026: sichtbarer Kaltstart auf Pixel 10 Pro
bestätigt (`LaunchState: COLD`, MainActivity im Vordergrund, Sperrbildschirm aus).
Hauptansicht zeigt „Suche Text …“, Sprachausgabe-Menü „Figureneffekte: EIN“.
Die echte App-Hörprobe „Umlaute und Sprecherwechsel“ wurde über die Oberfläche
mit Pikachu und anschließend Bisasam ausgelöst. Sichtbare Statusfolge:
„Erzeuge Sprache …“ → „Lese vor · Fish Audio“ → „Probehören beendet“.
Der zweite Teil wird erst nach erfolgreichem Wiedergabeabschluss des ersten
aktiviert. Beide Rollen sind gespeichert: Pikachu mit `77e3908a672a4b43b30786e52655d1bc`,
Bisasam mit `b5b77e8422b948efbf13be1e567a406c`. Weiterhin 15 Stimmen; SHA-256 von
Dialoghistorie und verschlüsseltem Zugangsspeicher vor/nach der Hörprobe identisch.
Keine Neuinstallation, Deinstallation oder Änderung von Nutzereinstellungen.

Dies bestätigt Synthese, Player-Abschluss und den technischen Sprecherwechsel
auf dem entsperrten Pixel. Subjektiver Klang und Unterscheidbarkeit wurden nicht
vom Nutzer bestätigt; der UI-Status nennt nur Fish Audio, nicht das benutzte
Fish-Modell. Echte Kamera-Namensschilder, Vorlesen im laufenden Spiel und die
API-23/26/30-Matrix bleiben gesonderte Gerätetests. Ein Web-Editor-Erfolg bestätigt
nicht den API-Free-Tarif oder dessen zukünftige Verfügbarkeit.

## 02.11.2026 · 5 Pokopia-Figurenstimmen & Slot-Zuordnung (1.4 / Code 5)

Umsetzung im Branch `feature/pokopia-5-character-voices`:

- Analyse der tatsächlichen Pokopia-Spieldialoge aus der Historie ergab fünf zentrale Figurenrollen:
  1. **Erzähler (Narrator / System / Items):** `c079932b47564fcfad0a9f6874a49df7` ("German Father" – ruhige, tiefe, sonore Erzählerstimme).
  2. **Pikachu / Schlappchu (Protagonist / Begleiterin):** `77e3908a672a4b43b30786e52655d1bc` ("Paluten 0.1" – jung, dynamisch, expressiv).
  3. **Evoli / Weibliche Gefährten:** `88b18e0d81474a0ca08e2ea6f9df5ff4` ("Christa deutsch" – klar, freundlich, natürlich).
  4. **Professor Tangoloss (Weiser Ältester / Mentor):** `3130ae7c3d844e2998978289c7c11c98` ("GERMAN WISDOM. M" – tief, bedacht, würdevoll).
  5. **Abenteurer / Kämpfer (Scherox, Machollo, Bisasam):** `9b1041732d1b4c2686d695c8bba3b149` ("EN_A1_P0_P0A" – kraftvoll, mutig, engagiert).
- Alle 5 Modelle wurden live mit dem Fish-Audio-Modell `s2.1-pro-free` verifiziert; alle liefern fehlerfrei saubere 44.1-kHz-MP3-Audiodaten.
- Standard-Konfiguration:
  - `CloudVoiceProfiles.FISH_DEFAULTS` enthält diese 5 Voice-IDs ab Werk kommagetrennt.
  - Einmalige automatische Migration (`pokopiaVoicesV1`) initialisiert bestehende und neue Installationen, setzt veraltete 2-Stimmen-Zuordnungen zurück und belässt spätere manuelle Anpassungen im STIMME-Dialog unangetastet.
- Slot-basierte Rollenverteilung in `VoiceAssignments`:
  - `choose(speaker, available, preferredSlot)` weist einer Figur bevorzugt die passende Rolle (Slot 0..4) zu, sofern diese noch frei ist, und weicht bei Belegung kollisionsfrei auf die seltenste Stimme im Pool aus.
  - Figurenbeziehungen in `voiceProfileFor`:
    - Slot 0: Erzähler / System
    - Slot 1: Pikachu, Schlappchu, Pichu
    - Slot 2: Evoli, Felori, Plinfa, Schiggy
    - Slot 3: Professor Tangoloss, Tangrowth
    - Slot 4: Abenteurer, Scherox, Machollo, Bisasam, Glumanda, Tinkmaster
- Prüfung:
  - Unit-Test `pokopiaCharacterVoicesMapToPreferredSlotsWithoutCollisions` testet stabile Slot-Zuordnungen, Wiedererkennung und kollisionsfreies Ausweichen bei Zusatzfiguren.
  - Alle Tests und Lint-Prüfungen (`./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`) erfolgreich.

## 01.11.2026 · Fish Audio primär, Google Chirp als Backup (1.4 / Code 5)

Umsetzung im Branch `feat/fish-audio-chirp-backup`, PR:
https://github.com/ysamjo/kamera-vorleser-android/pull/3

- Nutzerauftrag ersetzt für Cloud-TTS das frühere reine Offline-Ziel. Kamera und
  ML-Kit bleiben lokal; HTTPS-Anfragen enthalten Dialogtext, keine Bilder.
  INTERNET-Berechtigung hinzugefügt. Lokale Engine weiterhin explizit wählbar.
- Fish-Anfrage: `POST https://api.fish.audio/v1/tts`, Bearer-Schlüssel,
  `model: drama-3`, deutsche Normalisierung, konfigurierbare Voice-IDs, MP3.
  Bei HTTP 402 (unzureichende API-Credits / Gratis-Paket) automatischer Ausweichversuch
  auf das kostenlose Modell `s2.1-pro-free`, bevor Google Chirp als Backup greift.
  Kurze natürliche Figurenanweisungen nur bei aktivierten Figureneffekten.
- Backup: `POST https://texttospeech.googleapis.com/v1/text:synthesize`,
  deutscher Chirp-3-HD-Stimmenpool, MP3 aus Base64, keine Fish-Regieanweisungen
  im Vorlesetext. Google-Cloud-Key als Header oder expliziter Bearer-Access-Token.
  Token-Refresh ist nicht implementiert. Projekt/API/Abrechnung muss der Nutzer
  selbst einrichten; kein Gemini-Key und kein eingebauter Anbieter-Schlüssel.
- Pro Ausgabe höchstens ein Fish- und ein Google-Versuch. Bei fehlendem Fish-Zugang
  ist Google direkt nutzbar. Ohne Google-Konfiguration kein Backup-Request.
  Nach beidseitigem Fehler zehn Sekunden Ruhezeit, keine sofortige OCR-Retry-Schleife.
  Ein Decoder-/Wiedergabefehler nach erfolgreicher Synthese löst keinen zweiten
  Syntheseversuch aus. Keine Erkennung des Provider-Freikontingents/Kostensperre.
- Netzwerk/Datei-IO auf einem Worker; Queue, UI, MediaPlayer und Historie auf dem
  Main Thread. Stop entwertet IDs, trennt HTTP, entwertet zurückkommende Ergebnisse
  und gibt MediaPlayer/MP3-Dateien frei. Ein Anbieter kann eine bereits angenommene
  abgebrochene Anfrage trotzdem berechnen. Nur Wiedergabeabschluss speichert Dialoge.
- Eigene Zugänge AES-GCM/Android Keystore, Datei im `noBackupFilesDir`.
  Schlüssel nicht in Preferences, Logs, Repository oder Backups. Redirects aus,
  feste HTTPS-Endpunkte, keine Anbieter-Fehlerpayloads angezeigt. Passworteingaben
  ohne View-State/Autofill und mit FLAG_SECURE für den Zugangsdialog.
- Temporäre Audiodateien im privaten Cache, nach Ende/Stop gelöscht; auf Neustart
  hinterlassene Dateien werden entfernt. Kein dauerhafter Audio-Cache.
- Stabile gespeicherte Figurenzuordnung auf konfigurierbare Pools. Google-Standard:
  de-DE-Chirp3-HD-Charon/Kore/Puck/Aoede. Ein Fish-Modell allein ist eine Grundstimme;
  mehrere echte Figurenstimmen brauchen mehrere eigene Voice-IDs. Natürliche
  Regieanweisungen garantieren keine bestimmte Tonhöhe.
- NFC-Umlaute/JSON-Escaping erhalten; 5000 UTF-8-Bytes je Dialog, keine Kürzung.
  Die Eingabegrenze berücksichtigt im Google-Pfad den reinen Dialogtext.

### Prüfungen und Grenzen

Acht neue Unit-Testmethoden decken Primary/Backup-Reihenfolge, fehlende Anbieter,
Fehler und Abbruch während Requests, späten Erfolg nach Stop, deutsche Zeichen,
JSON-Escaping, UTF-8-Grenzen und Stimmenpools ab. Sie verwenden Fake-Anbieter,
keine Schlüssel, echten Dialoge oder kostenpflichtigen Requests.

Der erste CI-Lauf 41289583830 scheiterte an schon im Ausgangsstand vorhandenen
Merge-Fehlern: zwei `DialogueHistory.unreadText`-Methoden und veraltete
SpeakerVoice-Overload-Aufrufe. Für die aktive Completion-basierte Historie bleibt
nur die Variante erhalten, die vollständig gehörte Texte abschneidet. Wrapper und
zugehörige Profiltests nutzen die bereits vorhandene zurückhaltende Figurenlogik.
Keine zusätzliche Satzzeichen-/Emotion-Multiplikation im lokalen Modus.

CI-Lauf 41289818178 auf `da0b0c3` erfolgreich: Debug-/Release-APKs, beide
App-Bundles, beide Lint-Varianten, alle 62 Unit-Testmethoden je Variante und
Instrumentation-APK. Acht davon sind die neuen Cloud-Regressionen.
Der abschließende Stand liest AtomicFile direkt, damit dessen Backup-Recovery
auch bei zwischenzeitlich fehlender Hauptdatei greifen kann. Aktueller Status
und Prüfberichte dieses Abschlussstands stehen in PR #3. Die CI führt
Debug/Release-APK, beide Bundles, beide Lint-/Unit-Test-Varianten und den
Instrumentation-APK-Bau aus. Instrumentation wird dort ohne Gerät nicht ausgeführt.
In dieser Arbeitsumgebung fehlen ein funktionsfähiges JDK/Android-SDK und ein
angeschlossenes Telefon; kein lokaler Gradle-Build und keine Installation.
`git diff --check` erfolgreich.

**Noch nicht nachgewiesen:** echte Fish-/Google-Authentifizierung mit eigenen
Zugängen, Provider-Quoten/Fehler, hörbarer deutscher Klang und Umlautaussprache,
Latenz, verschlüsselte Zugangswiederherstellung am Gerät, Pause/Sprecherwechsel
während realem HTTP/MediaPlayer sowie kleine Displays/TalkBack für den Dialog.
Die vorhandenen lokalen TTS- und Kamera-Gerätelücken bleiben offen.

Quellen:
https://docs.fish.audio/api-reference/endpoint/openapi-v1/text-to-speech
https://docs.cloud.google.com/text-to-speech/docs/chirp3-hd
https://docs.cloud.google.com/text-to-speech/docs/reference/rest/v1/text/synthesize


## 30.10.2026 · Umstellung auf sherpa-onnx / Piper Thorsten high

Aktueller Stand nach der ausdrücklichen Wahl dieser Engine:

- Einmalige Migration der Engine-Präferenz auf
  `com.k2fsa.sherpa.onnx.tts.engine`, auch für bestehende Installationen.
  Figureneffekte werden bei der Umstellung deaktiviert; ein späterer manueller
  Enginewechsel bleibt erhalten. Vorhandenes Nutzertempo bleibt unverändert.
- Fehlende gewählte Pakete werden vor dem TTS-Konstruktor erkannt, damit der
  Konstruktor in diesem Fall nicht automatisch die Systemengine verwendet.
  Keine Sprache und ein Installationshinweis statt falscher Bereitschaft.
  Das ist kein Nachweis gegen jeden internen Android-Fallback bei einem zwar
  installierten, aber defekten Dienst. Bei installiertem Paket bleibt der
  Android-TTS-Konstruktor mit expliziter Engine-Auswahl zuständig.
- STIMME enthält „Thorsten einrichten / aktivieren“ und die offizielle
  Downloadübersicht im externen Browser. Keine Bild-/Textübertragung durch die
  App. Die Engine-Auswahl wird unabhängig von einem laufenden TTS-Objekt über
  Androids Dienstauflösung gefüllt und funktioniert auch ohne installierte Engine.
- Rückkehr nach Installation versucht eine zuvor fehlende Engine erneut.
  Fehler-/Bereitschaftsanzeigen werden nicht mehr durch normale OCR-Ergebnisse
  ohne betriebsbereite TTS überschrieben.

### Tatsächlich erzeugte und geprüfte APKs

- App: Version 1.3 / Code 4, `app/build/outputs/apk/debug/app-debug.apk`.
  **Gradle 9.5.0/JDK 21: assembleDebug und testDebugUnitTest erfolgreich.**
  Alle 46 JUnit-Testmethoden bestanden. Der frühere Speicherplatzengpass war
  behoben; der bestehende Cache wurde als APFS-Kopie in einen beschreibbaren
  temporären Gradle-Cache übernommen.
- Engine: über die offizielle Downloadübersicht ermittelte ARM64-APK,
  sherpa-onnx 1.13.8 (Code 20260910), Paket
  `com.k2fsa.sherpa.onnx.tts.engine`. Signatur mit `apksigner verify` erfolgreich,
  Zertifikat SHA-256 `030a90bdd6e4c5eef1e161acfa6455a912c45cb2423b64df449ca88fdee097bb`.
  Signaturprüfung belegt Integrität, keine unabhängige Bewertung des Codes.
  AAPT-Metadaten und Modellassets geprüft: `de_DE-thorsten-high.onnx`,
  Modellkonfiguration Sprache de_DE, eSpeak-Stimme de, **ein Sprecher**.
  Dateiprüfsumme SHA-256:
  `9b2ffc5d40fdfcf2b78f146976b44a09726ba2d971a1cec32bcf77892d6e1bbb`.
  Lokale Datei: `app/build/outputs/tts-engine/thorsten-high-arm64.apk`.
- Das zusammengeführte App-Manifest und die Engine-APK haben keine
  INTERNET-Berechtigung. Die App benötigt weiterhin die Kamera-Berechtigung.
- Lint zunächst offline an fehlenden Android-Test-Abhängigkeiten gescheitert.
  Erneuter Aufruf für lintDebug/assembleDebugAndroidTest mit Netzwerk scheitert
  beim Google-Maven-Zugriff (`dl.google.com: nodename nor servname provided, or not known`).
  Deshalb **kein erfolgreicher aktueller Lint-/Instrumentation-APK-Build**.
  Neuer Instrumentation-Fall für einmalige Migration, fehlende Engine und
  weiterhin nutzbare Einstellungen ergänzt, aber nicht gebaut/ausgeführt.
- `git diff --check` erfolgreich. `adb devices -l` und `adb mdns services`
  zeigen keine Geräte. **Keine Installation und kein Gerätetest durchgeführt.**
  Subjektiver Klang, Umlaute, Latenz und Flugmodus bleiben am Gerät zu bestätigen.

Der Engine-Paketname ist für verschiedene sherpa-Modell-APKs gleich. Die App
kann allein anhand dieses Namens nicht nachweisen, dass eine fremde bestehende
Installation ebenfalls Thorsten enthält. Die bereitgestellte und geprüfte APK
enthält das gewünschte Modell. Nur ein Sprecher bedeutet auch, dass natürliche
unterschiedliche Figurenstimmen mit dieser einzelnen APK nicht verfügbar sind.

Quelle der heruntergeladenen Engine:
https://huggingface.co/csukuangfj2/sherpa-onnx-apk/resolve/main/tts-engine-new/1.13.8/sherpa-onnx-1.13.8-arm64-v8a-deu-tts-engine-vits-piper-de_DE-thorsten-high.apk


## 30.10.2026 · Originalklang statt automatischer Stimmverzerrung (Version 1.3)

Auf Rückmeldung zur weiterhin schlechten Stimme wurde der aktive Ausgabepfad
geprüft. Die Version davor variierte Tonhöhe/Tempo pro Figur und Satzzeichen
und konnte bei Sprecherwechseln auf schlechter bewertete Grundstimmen wechseln.
Das sind mögliche Klangursachen; ohne angeschlossenes Gerät ist die tatsächlich
installierte Version oder Engine nicht feststellbar.

- Standard: Originaltonhöhe 1,0 und ausschließlich das vom Nutzer gewählte Tempo.
  Neue Einstellungen starten bei 1,0×; gespeicherte Tempi werden erhalten.
  Das Stimmenmenü bietet einen expliziten Rücksetzer auf 1,0×.
- Bestehende Figurenprofile sind über eine lokal gespeicherte Option einschaltbar;
  diese ist auch für vorhandene Installationen standardmäßig aus. Automatische
  Satzzeichen-Pitch-/Temposprünge entfallen in beiden Modi. Anzeige und Hörprobe
  verwenden denselben Modus wie echte Dialoge.
- Die Automatik beginnt mit der bestbewerteten Offline-Stimme. Beim Wechsel
  werden nur Stimmen mindestens derselben gemeldeten Qualität zugelassen.
  Eine manuelle Grundstimme definiert entsprechend die Mindestqualität.
  Bei nur einer geeigneten Stimme bleibt die Grundstimme gleich. Weiterhin
  ausschließlich installierte deutsche Offline-Stimmen, bevorzugt de-DE.
- Versionsnummer auf 1.3 / Code 4 erhöht, um den nächsten Build identifizieren
  zu können. Noch keine Aussage über eine Installation dieser Version.

Prüfung: 46 lokale JUnit-Testmethoden mit JDK 21/JUnit 4.13.2 direkt ausgeführt,
alle bestanden. Neue Regressionen prüfen unverzerrte Ausgabe über alle
Figurenprofile und Reglerwerte sowie das Vermeiden schlechterer Grundstimmen.
Der reguläre Wrapper konnte den vorhandenen Cache nicht beschreiben. Direkter
Gradle-9.5.0-Start mit beschreibbarem Cache gelang, scheiterte aber vor der
Projektkonfiguration an `No space left on device` beim Erzeugen der Gradle-API-JAR.
Die Systemplatte hatte nur etwa 170 MB frei. Keine fremden Dateien gelöscht.
Build/Lint und APK-Erstellung deshalb nicht abgeschlossen.

ADB ist erreichbar; `adb devices -l` zeigte eine leere Geräteliste. Keine APK
installiert, kein Hörtest oder Flugmodus-/Kamera-Gerätetest durchgeführt.
Natürlichkeit und Umlautaussprache der tatsächlich verwendeten Engine bleiben
unbestätigt. Unterschiedliche Stimmen nach Engine-Qualitätswert sind kein Beleg
für hörbar bessere Stimmen.

Die folgenden Abschnitte sind historische Prüfprotokolle früherer Versionen.

Stand: 08.10.2026. Basis: `3ad0ea9`.

## Stabilität (KAV-01 bis KAV-04)

- Core-Library-Desugaring 2.1.5 für Java-APIs; API 23 bleibt Mindestversion.
- VIBRATE-Berechtigung und API-26-Prüfung mit älterem Fallback, Hardware-Prüfung bleibt erhalten.
- Geometrischer Zusammenbau nutzt sortierte Reihen; Silbentrennung nur zwischen Reihen.
- Expliziter ML-Kit-Abschluss behandelt Erfolg, Fehler und Abbruch; auch synchrone
  Fehler geben das Bild frei. Generationen verwerfen Ergebnisse nach Pause/Weiter,
  Wiederholen, Hintergrundwechsel und Zerstörung. Recognizer wird erst nach einem
  laufenden Frame geschlossen. Nachverarbeitung bleibt vorerst auf dem Main Thread.
- Tests: 40 deterministische Permutationen einer synthetischen Geometrie,
  Silbentrennung, leere/ungültige Zeilen, einmalige Freigabe einschließlich Fehlerfall.
  Diese Permutationen sind **kein** Datensatz aus 30–50 repräsentativen Spieldialogen.

## Noch erforderliche Validierung

API 23/26/30: echte Kamera-/TTS-Smoke-Tests fehlen. Reale Pokopia-Referenzdialoge,
Flugmodus mit installierter deutscher Offline-Stimme, Vibration ohne Hardware,
ML-Kit-Fehlerinjektion und Rotation sind bisher nicht am Gerät validiert.
Synthetische Unit-Tests belegen keine reale Erkennungsqualität oder Latenz.

## Bewusst offene Planaufgaben

KAV-05 (Worker-Nachverarbeitung), KAV-07/08 (Dialog-/Sprecherzustände), KAV-09
(ROI-Transformation/Kalibrierung) benötigen repräsentative Kamera-Fixtures und
Gerätevergleiche vor Änderungen an Erkennungsschwellen oder Zustandssemantik.
KAV-12 wird nur schrittweise umgesetzt. KAV-14 repräsentativer Referenzdatensatz, KAV-16 Diagnoseoberfläche/Leistungsmessungen bleiben offen.

Erster Stabilitätsstand: Debug-Build, Lint (0 Fehler, 50 bestehende Warnungen) und
5 JUnit-Tests erfolgreich unter JDK 21. Keine Lint-Baseline oder Fehlersuppression.

## TTS-Warteschlange (KAV-06)

Eindeutige IDs ersetzen den Zähler. Sämtliche Queue-Zustände und Callbacks laufen
auf dem Main Thread. Stop entwertet IDs vor dem Engine-Aufruf; verspätete und
doppelte Callbacks werden ignoriert. `onStop` wird behandelt. Synchron abgelehnte
Ausgaben werden nicht als gelesen gespeichert; asynchrone Fehler geben den
zugehörigen Dialog für einen neuen Versuch frei.
Policy bleibt erhalten: maximal zwei Ausgaben, neue Dialoge per QUEUE_ADD;
Wachstum eines gerade gesprochenen Dialogs ersetzt diesen durch den Gesamttext.
Pause/Neu lesen/Hintergrund stoppen die Queue; bereits angenommene Dialoge bleiben
wie bisher gemerkt, bis Neu lesen oder die bestehende Gedächtnisfrist greift.
Drei Queue-Regressionstests prüfen verspätete/doppelte Callbacks und 50 schnelle
Abbruch-/Neustartfolgen. Echte Engine-Fehler und akustische Sprecherwechsel offen.

TTS-Stand: Debug-Build, Lint und alle 8 JUnit-Tests erfolgreich.

## CI und Repository-Hygiene (KAV-15)

GitHub Actions: Push auf main, Pull Requests und manueller Start; JDK 21,
SDK 36, Wrapper 9.5.0, Debug-Build/Lint/JUnit; Berichte und APK als Artefakte.
Lokale .DS_Store-/Drive-Backup-Dateien aus Git entfernt und ignoriert.
Release-Minifizierung bleibt unverändert. Lokales Sicherungs-ZIP vor der
Extraktion: `Archiv/KameraVorleser_v1.2_vor_stabilisierung.zip` (Basis 3ad0ea9).

## Sprachqualität und Einstellungen (KAV-10/11/13, teilweise KAV-12)

Netzwerkstimmen und als nicht installiert markierte Stimmen werden ausgeschlossen.
Vorhandene Qualität-/Namenssortierung bleibt deterministisch; fehlende Offline-
Stimme führt zu einem Installationshinweis. Pitch-/Rate-Profile bleiben unverändert.
Das Wörterbuch ist extrahiert, doppelte Schlüssel entfernt, Regex-Schlüssel und
Ersetzungen sicher maskiert; Unicode-Wortgrenzen schützen fremde Wörter.
Wirkungslose Pausenersetzungen entfernt. Drei Tests prüfen Namen, Wortgrenzen und
Satzzeichen. Tempo/Lautstärke werden gespeichert und initial mit Reglern abgeglichen.
Akustische Qualität und Flugmodus-Test offen; vollständige Accessibility-/
String-Ressourcen-Überarbeitung und Story-Zonen-Persistenz nicht umgesetzt.

Sprachqualitätsstand: Debug-Build, Lint und alle 11 JUnit-Tests erfolgreich.

Erster CI-Lauf scheiterte vor Gradle: setup-android verlangte das entfernte SDK-
Paket `tools`. Pakete nun explizit auf `platform-tools` begrenzt; SDK 36 und
Build-Tools werden danach gezielt installiert.

Zweiter CI-Befund: `gradle-daemon-jvm.properties` erzwang JetBrains-JDK und tote
Download-URLs. Die Datei verlangt jetzt herstellerneutral JDK 21; lokal und in
CI wird das vorhandene JDK verwendet. Lokaler Build/Lint/11 Tests bestanden.

## Testbasis und Gerät (KAV-12/14, Teilumsetzung)

`TextRules` extrahiert Normalisierung, Levenshtein, Ähnlichkeit, Aktions- und
Benachrichtigungsfilter ohne Änderung ihrer Schwellen. 34 synthetische Textfälle
(16 Dialoge, 12 Aktionshinweise, 6 Meldungen) und Grenzfalltests ergänzt.
Insgesamt 15 JUnit-Testmethoden erfolgreich. Debug-APK, Test-APK und Lint bestanden.
Diese Fälle sind keine repräsentativen Pokopia-OCR-Fixtures mit Bounding-Boxes.

Pixel 10 Pro, per WLAN-ADB, gemeldete API 37: App und Instrumentation installiert.
`BundledOcrSmokeTest` bestanden, synthetischer deutscher Text mit gebündeltem
ML Kit erkannt. Die INTERNET-Berechtigung wurde explizit aus dem zusammengeführten
Manifest entfernt; damit kann die App keine eigenen Internetverbindungen öffnen.
Das beweist weder einen Flugmodus-Hörtest noch die Offline-Fähigkeit jeder TTS-Engine.

Zwei erste Lifecycle-/Einstellungstests scheiterten an der Gerätesperre
(`RESUMED` erwartet, `STOPPED` beobachtet). Die Tests prüfen diesen Umgebungszustand
jetzt vorab und melden ihn als übersprungen. Erfolgreicher entsperrter Lauf noch offen.
Kein bestandenes Lifecycle-Testergebnis aus dem OCR-Test oder Build ableiten.
CI kompiliert auch die Instrumentation; sie führt diese ohne Gerät nicht aus.

Abschlussprüfung: Auch synchrone Exceptions der TTS-Engine geben ihre Ausgabe-ID
frei, damit kein Queue-Platz dauerhaft blockiert bleibt. Lint derzeit 0 Fehler,
53 Warnungen (u. a. vorhandene Layout-/Übersetzungs-/Versionshinweise).

Der zusätzliche Queue-Test simuliert Ablehnung und synchrone Engine-Exception
neben einer bestehenden Ausgabe. Alle 15 Unit-Tests, Lint und beide APK-Builds bestanden.

## Abschließendes Ergebnis

Code-Stand: `7a94ada`, alle Implementierungscommits auf `main` gepusht.
[GitHub-Actions-Lauf 37814138924](https://github.com/ysamjo/kamera-vorleser-android/actions/runs/37814138924)
**erfolgreich**: Linux/JDK 21, Debug-APK, Android Lint, 15 JUnit-Tests und
Instrumentation-APK-Build. Berichte und Debug-APK sind dort als Artefakte abrufbar.
CI führt die Instrumentation ohne Android-Gerät nicht aus.

Die abschließende App- und Test-APK sind auf dem Pixel 10 Pro installiert.
OCR-Geräte-Smoke-Test bestanden; entsperrte Lifecycle-/Reglertests bleiben offen,
da während dieser Sitzung keine Entsperrbestätigung vorlag. API-23/26/30-Matrix,
reale Pokopia-Kameradialoge, akustische Qualität und Flugmodus-Hörtest bleiben offen.

CI-Wartungshinweise ohne Buildfehler: verwendete Actions melden Node-20-/
setup-java-v4-Abkündigungen; der Runner führt sie erfolgreich unter Node 24 aus.
Ein Wechsel der Action-Hauptversionen wurde nicht mit den Stabilitätsfixes vermischt.

Technische Referenzen: [Java-API-Desugaring](https://developer.android.com/studio/write/java8-support),
[VibrationEffect ab API 26](https://developer.android.com/reference/android/os/VibrationEffect).

## Dialogstimmen, Umlaute und Dialoggedächtnis (Oktober 2026)

- Dialoge werden in einer auf 500 Einträge begrenzten Historie aus Sprecher und
  normalisiertem Text gespeichert; JSON liegt ausschließlich in lokalen App-Einstellungen.
  Die bisherige 60-Sekunden-Verfallszeit entfällt.
- Derselbe Text mit einem anderen Sprecher bleibt sprechbar. Bei manueller Wiederholung
  wird nur die sichtbare Zeile freigegeben. Unterbrochene/fehlgeschlagene TTS-Ausgaben
  werden aus der Historie entfernt und können erneut gesprochen werden.
- Erkannte Namenspräfixe wie „Pikachu: Hallo“ werden aus dem Vorlesetext entfernt;
  die Sprecheranzeige bleibt erhalten.
- Sprecherwechsel leeren veraltete TTS-Ausgaben, bevor die neue lokale Offline-Stimme
  mit eigenem Pitch/Tempo angewendet wird. Extreme Pitch-/Rate-Werte wurden begrenzt.
  Deutsche de-DE-Stimmen erhalten bei gleichwertiger Offline-Verfügbarkeit Vorrang.
- Deutsche Zeichen werden für die TTS in NFC normalisiert. Das behebt Unicode-
  Kombinationen aus der OCR, garantiert aber keine gute Aussprache einer fehlerhaften
  oder qualitativ schwachen Android-TTS-Engine.
- Neue JUnit-Regressionstests decken Sprechertrennung, Deduplizierung, Neustart-
  Wiederherstellung, manuelles Neu-Lesen, Namensentfernung, Umlaute und TTS-Abbruch ab.
- **Nicht am Gerät verifiziert:** reales Pokopia-OCR-Namensschild, akustische Qualität
  einzelner deutscher Offline-Stimmen, Timing bei raschem Dialogwechsel und
  Wiederherstellung der Einstellungen auf einem Testgerät. Diese Prüfungen bleiben offen.


## PR #1: erneute Codeprüfung am 24.10.2026

Geprüfter Ausgangsstand: `c5fceb6189691952fb829e29806ec807fb07d8bf`.
Der über GitHub abgerufene PR wurde auf der lokalen Basis rekonstruiert;
der komplette Git-Tree stimmt mit `139d45060f018466891be83594e6791f798df795`
überein (einschließlich Symlinks). Alle 11 geänderten Dateien sowie die
angebundenen Kamera-, TTS-, Text- und Lifecycle-Pfade wurden gelesen.
Der angegebene Actions-Lauf `40069308661` war erfolgreich; er prüft den alten
PR-Stand und belegt keine Validierung der nachstehenden Korrekturen.

### Befunde und Korrekturen

| Priorität | Befund | Korrektur |
| --- | --- | --- |
| P1 | Bereits eingereihte Sprache wurde dauerhaft als gelesen gespeichert; ein Prozessabbruch ließ ungehörte Dialoge verschwinden. | Laufende Dialoge bleiben flüchtig; erst `onDone` speichert abgeschlossene Dialoge. Fehler und Stop entfernen nur laufende Einträge. |
| P1 | „Neu lesen“ löschte einen exakten Schlüssel, während die Duplikatsuche ähnliche OCR-Texte weiter blockierte. | Einmalige, ebenfalls OCR-tolerante Freigabe; abgeschlossene Historie bleibt auch bei Abbruch einer Wiederholung erhalten. |
| P1 | Erweiterung konnte vor der Historienprüfung erneut gesprochen werden. | Historie zuerst prüfen; echte zusätzliche Wörter bleiben neue Dialogteile. |
| P1 | Bei voller Queue erreichte ein neuer Sprecher den Abbruchpfad nicht. | Stabile Sprecherwechsel stoppen die alte Queue vor der Duplikat- und Kapazitätsprüfung. |
| P1 | Gleicher Text konnte die Stabilitätszählung über Sprecherwechsel hinweg übernehmen; fehlendes Schild übernahm beim neuen Dialog die vorige Stimme. | Sprecherwechsel entwerten die Analyse; bei abweichendem Dialog ohne Schild Erzähler verwenden. |
| P1 | Sieben Namensvariationen und Pitch-Clamping führten zu gleichen Figurenprofilen. | Stabile Variation in Tonhöhe und Tempo, unabhängig von Java-Hashcode-Kollisionen; Variation bleibt an Profilgrenzen erhalten. |
| P1 | Skalierung gespeicherter, begrenzter Tempi war nicht reversibel und ignorierte Teile des Reglers. | Tempo direkt aus aktuellem Regler und Figurenprofil berechnen; alte Raten-Einstellungen werden nicht mehr verwendet. |
| P1 | Nach 900 Zeichen abgeschnittener Text wurde als vollständig gelesen gespeichert. | Keine stille Abschneidung; oberhalb des Engine-Limits Hinweis und keine Speicherung. |
| P2 | NFC erst bei TTS ließ zerlegte Umlaute in Sprecherkennung und Historie beschädigen. | NFC auch beim Vergleichen und Entfernen von Namenspräfixen. ä, ö, ü, ß bleiben erhalten. |
| P2 | `restore` verwarf lange, zuvor gespeicherte Dialoge über 1200 Zeichen. | Schlüssel bis 8192 Zeichen zulassen; Langtext-Restore getestet. |
| P2 | Kurze zusätzliche Wörter einschließlich Verneinungen konnten als OCR-Rauschen verschwinden. | Unscharfer Vergleich nur bei gleicher Wortanzahl; bewusste Präfix-Erkennung bleibt erhalten. |
| P2 | `setPitch`/`setSpeechRate`-Fehler wurden ignoriert; Init und synchrone Abschluss-Callbacks konnten Zustand zu früh ändern. | Rückgabewerte prüfen; Initialisierung und Abschluss immer in den Main-Thread-Handler stellen. |
| P2 | Auswahl mehrerer Voice-Slots konnte trotz vorhandener de-DE-Stimme auf andere deutsche Regionen wechseln. | Bei vorhandenen de-DE-Offline-Stimmen nur diese verwenden. |

### Lokale Prüfungen

- 36 JUnit-Testmethoden erfolgreich mit JDK 21/JUnit 4.13.2, direkt per `javac`
  und `JUnitCore` ausgeführt. Sämtliche vorhandenen Unit-Testklassen enthalten;
  keine Netzwerkzugriffe. Neue Fälle prüfen laufende/abgeschlossene Historie,
  Wiederholung nach OCR-Abweichung und Abbruch, Neustart-Snapshots, lange Texte,
  Umlaute, fehlende Namensschilder und Verneinungen.
- Stimmenprüfung: pro Kombination von sechs Basisprofilen und drei Reglerwerten
  1100 synthetische Namen ohne identische Pitch-/Rate-Paare; zusätzlich bekannte
  Namen sowie die Java-Hashcode-Kollision `Aa`/`BB`. Das ist kein Hörtest und
  keine vollständige Liste aller Pokémon.
- Alle Android-Java-Quellen mit SDK-36-`android.jar`, lokal vorhandenen
  Abhängigkeits-JARs und bestehendem `R.jar` kompiliert. Dies ersetzt weder
  Gradle noch Ressourcenverarbeitung, Desugaring, APK-Bau oder Lint.
- `git diff --check` erfolgreich.
- Vollständiger Gradle-Aufruf versucht: Debug-/Release-APK, beide App-Bundles,
  beide Lint-/JUnit-Varianten und Instrumentation-APK. Zunächst verhindert der
  schreibgeschützte Gradle-Cache den Start. Mit beschreibbarem Cache fehlt
  Gradle 9.5; Download scheitert mit `UnknownHostException: services.gradle.org`.
  Vorhandenes Gradle 9.6 scheitert schon vor der Projektkonfiguration an
  `SocketException: Operation not permitted` im File-Lock-Dienst.
- CI-Aufruf auf sämtliche oben genannten Varianten erweitert. Ergebnis des
  neuen Laufs separat festhalten; der alte erfolgreiche Lauf reicht nicht aus.

### Tatsächlicher Installations- und Gerätestatus

`adb devices -l` wurde versucht. Der ADB-Daemon kann in dieser Sitzung den
lokalen Listener nicht öffnen: `could not install *smartsocket* listener:
Operation not permitted`. Es gibt daher **keine bestätigte Geräteliste**;
daraus folgt nicht, dass das Pixel physisch getrennt ist.

**Keine APK installiert. Keine App-Daten gelöscht. Kein anderes Gerät benutzt.**
Der ursprüngliche Ordner `/Users/family/Developer/kamera-vorleser-android` ist
in dieser Sitzung nur lesbar und bleibt auf `main`/`f3c2bfa`; Änderungen entstehen
in `/tmp/pokopia-review` und werden auf dem bestehenden PR-Branch veröffentlicht.

| Gerätetest auf Pixel 10 | Ergebnis dieser Sitzung |
| --- | --- |
| Modell/Seriennummer per ADB bestätigen | Blockiert |
| APK mit `adb -s <bestätigte-ID> install -r` aktualisieren | Nicht durchgeführt |
| Reale Kamera und Pokopia-OCR | Nicht durchgeführt |
| Schnelle Sprecherwechsel und akustisch unterscheidbare Stimmen | Nicht durchgeführt |
| Deutsche Aussprache inklusive ä/ö/ü/ß und Flugmodus | Nicht durchgeführt |
| Pause/Weiter, Neu lesen, Neustart und gespeichertes Dialoggedächtnis | Nicht durchgeführt |
| `connectedDebugAndroidTest` | Blockiert; kein Instrumentation-Ergebnis |

### Verbleibende Grenzen

- Gleiche Figurenprofile mit kleinen numerischen Abweichungen garantieren keine
  hörbar einzigartige Stimme: Die installierte Engine kann Werte quantisieren.
  Die Engine stellt nur endlich viele lokale Grundstimmen bereit.
- Geometrische Namensschild-/ROI-Erkennung und Typewriter-Ruhezeiten benötigen
  weiterhin reale Kamera-Fixtures. Fehlende oder falsch erkannte Namen können
  keinem zuverlässigen Pokémon-Profil zugeordnet werden. Zwei völlig gleiche
  Dialoge ohne unterscheidbares Schild lassen sich nicht sicher zuordnen.
- Unscharfe OCR-Erkennung kann weiterhin ähnlich geschriebene, aber inhaltlich
  verschiedene Sätze derselben Wortanzahl gleichsetzen. Bei Bedarf „Neu lesen“.
- Gedächtnis bleibt auf die letzten 500 abgeschlossenen Dialoge begrenzt.
  Ältere Einträge können erneut gesprochen werden. Ein Absturz unmittelbar
  zwischen hörbarem Ende und persistiertem TTS-Abschluss kann eine Wiederholung
  verursachen; es gibt keine atomare Transaktion zwischen Lautsprecher und Disk.
- Ältere PR-Versionen bereits fälschlich gespeicherte Queue-Einträge lassen sich
  rückwirkend nicht von tatsächlich gehörten Einträgen unterscheiden.
- API-23/26/30-Kompatibilität und subjektive Natürlichkeit sind ohne Gerätetests
  nicht nachgewiesen. Keine Freigabe als vollständig am Pixel getestet ableiten.

Zusätzlicher Randfall der Abschlussprüfung: Die alte Erweiterungslogik zählte
nur durch Leerzeichen getrennte Wörter. Bei `Hallo!Wie geht es?` nach `Hallo!`
verschwand dadurch „Wie“; bei Erweiterung des letzten Worts konnte die Ausgabe
ganz ausfallen. `DialogueText.extensionSuffix` zählt nun Unicode-Wörter und
liest bei fehlender Wortgrenze den vollständigen Text. Eigener Regressionstest.


CI-Nachprüfung: Lauf [40077860630](https://github.com/ysamjo/kamera-vorleser-android/actions/runs/40077860630)
auf `e13e2b1` bestand mit Debug-/Release-APK, Debug-/Release-App-Bundles,
Debug-Lint, Debug-Unit-Tests und Instrumentation-APK. Das detaillierte Log zeigt,
dass die AGP-Sammelbefehle `lint` und `test` nur Debug geprüft haben.
Die CI fordert deshalb nun beide Varianten ausdrücklich an;
`android.onlyEnableUnitTestForTheTestedBuildType=false` aktiviert auch die
Release-Unit-Tests. Ergebnisse des abschließenden Laufs stehen in PR #1.


## Nutzerbefunde: Namensschild, Stimmen, Fokus und Scrollen (24.10.2026)

Ausgangsstand: PR #1 / `318518e`. Die zuvor installierte APK konnte in dieser
Sitzung nicht festgestellt werden; die dort beobachteten Fehler werden nicht
als Gerätetest des veröffentlichten PR-Stands gewertet.

- Erkennungston und Sprecherwechselton einschließlich ToneGenerator entfernt.
  Vibration und VIBRATE-Berechtigung ebenfalls entfernt, unabhängig von alten
  Sound-Einstellungen. Die bisherige Sound-Schaltfläche öffnet die Stimmenauswahl.
- Geometrische Namensschildsuche akzeptiert das Schild nun auch dann, wenn es
  selbst in der Story-ROI liegt. „Professor“ und „Tangoloss“ können aus getrennten
  OCR-Zeilen zusammengesetzt werden; beide Schreibweisen nutzen dieselbe Identität.
  Ein bekannter zusammengeführter Professor-Präfix wird ebenfalls entfernt.
  Satz-/Aktionszeilen werden nicht als Namensschild bevorzugt. Synthetische
  Geometrien ersetzen keine realen Pokopia-Kamera-Fixtures.
- Pro Sprecher wird ein echter installierter deutscher Offline-Voice-Name lokal
  gespeichert. Freie Stimmen werden zunächst separat vergeben, anschließend wird
  die bisher am wenigsten verwendete Stimme genutzt. Manuelle Auswahl pro Figur,
  Umlaut-Hörprobe und Link zu Android-TTS-Einstellungen ergänzt. Stimmennamen statt
  Listenindizes erhalten die Zuordnung auch bei geänderter Stimmenreihenfolge.
  Entfernte Stimmen werden durch eine verfügbare ersetzt. Numerische Figurenprofile
  bleiben moderat; satzzeichenabhängige Tonhöhen-/Temposprünge entfallen.
- NFC-Erhalt von ä/ö/ü/ß bleibt getestet. Die App verändert keine Umlaute in ae/oe/ue.
  Die tatsächliche deutsche Aussprache und Natürlichkeit der installierten Engine
  sind ohne Hörtest ausdrücklich nicht bestätigt. Es werden keine neuen Sprachmodelle
  oder Cloud-Stimmen mitgeliefert.
- Tippen auf die Vorschau setzt CameraX-Autofokus und Belichtungsmesspunkt; ein
  farbiger Rahmen meldet Erfolg/Fehler. Fokus bleibt bis zur nächsten Aktion bzw.
  Kamera-Unterbrechung erhalten; langes Drücken aktiviert wieder Autofokus.
  Mehrfingergesten lösen keinen Fokus-Tap aus. Hardwareprüfung auf Pixel bleibt offen.
- Scrollfenster werden mit bereits gehörten und seriell eingereihten Fenstern
  desselben Sprechers verglichen. Exakte Wortüberlappung ab drei Wörtern sowie
  Präfix-Erweiterungen geben nur den neuen Rest aus; vollständig gehörte Ausschnitte
  bleiben still. Sprecherzuordnung läuft bei gleichem/überlappendem Dialog ohne
  zeitgesteuerten Wechsel zur Erzählerstimme weiter. „Neu lesen“ gibt das sichtbare
  Fenster einmal vollständig frei. Nur onDone speichert; Fehler/Stop verwerfen auch
  abhängige Scroll-Suffixe, verspätete Callbacks bleiben wirkungslos.

Prüfung: 51 JUnit-Testmethoden direkt mit JDK 21/JUnit 4.13.2 erfolgreich,
alle Android-Java-Quellen mit SDK 36, vorhandenen Abhängigkeiten und bestehendem
R.jar kompiliert. AAPT2 hat die Ressourcen kompiliert; das ist kein vollständiger
Ressourcen-Link und kein APK-Build. `git diff --check` bestanden.
Vollständiger Gradle-Aufruf versucht; fehlende Distribution 9.5 und gesperrte
DNS-Auflösung (`services.gradle.org`) blockieren lokale Builds. CI-Ergebnis des
neuen Commits separat prüfen; ältere grüne Läufe belegen diese Änderungen nicht.

ADB erneut versucht: Listener weiterhin `Operation not permitted`. Kein Modell,
keine Seriennummer und keine installierte Version bestätigt. Keine Installation,
Deinstallation, Datenlöschung oder echte Kamera-/Hör-/Fokus-/Neustartprüfung.

Grenzen: Endlich viele Grundstimmen garantieren keine eigene natürliche Stimme
für jede Figur; unterschiedliche Pitch-/Rate-Werte garantieren keine hörbare
Einzigartigkeit. Ohne mindestens drei überlappende Wörter oder bei OCR-Fehlern in
diesem Übergang kann ein Scrollfenster vollständig erneut gelesen werden.
Sehr schnelles Scrollen bei voller Zwei-Ausgaben-Queue oder vor stabiler OCR
kann Text verlieren. Fehlende/falsch erkannte Namensschilder bleiben mehrdeutig.
API-23/26/30-Gerätematrix, Flugmodus und akustische Abnahme bleiben offen.

