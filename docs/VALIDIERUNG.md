# Umsetzung und Validierung

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
