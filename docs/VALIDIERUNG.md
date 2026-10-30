# Umsetzung und Validierung

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
