# Verbesserungsplan – Pokopia Story-Vorleser (Android)

**Stand:** 08.10.2026 · **Status:** in Umsetzung · **Basis:** `main` (Initial-Review, Commit `c4c4df4`)
**Geltungsbereich:** Stabilität, OCR-/Dialogqualität, TTS, Bedienbarkeit, Architektur und Qualitätssicherung.

> Aktuelle Umsetzung und Validierungsgrenzen stehen in [VALIDIERUNG.md](VALIDIERUNG.md). Der ursprüngliche Plan folgt. Alle Befunde stammen aus einem statischen Code-Review; Laufzeitfehler und Performanceannahmen müssen auf Geräten bzw. Emulatoren verifiziert werden.

## Aktueller Umsetzungsstand

KAV-01–04 technisch umgesetzt; Geräte-Matrix und reale Fehler-/Recovery-Szenarien
noch offen. KAV-06 implementiert und Queue-Logik getestet. KAV-10/11 sowie KAV-13
teilweise umgesetzt. KAV-12/14: reine Komponenten, 15 Unit-Tests und drei
Instrumentation-Tests. KAV-15: CI erfolgreich (Code-Stand `7a94ada`), Details siehe Protokoll.
Häkchen kennzeichnen den jeweiligen Umsetzungsschritt, nicht die vollständige
Geräteakzeptanz der Aufgabe. Alle Grenzen stehen in [VALIDIERUNG.md](VALIDIERUNG.md).

## Zielbild

Der Vorleser soll deutsche Pokopia-Dialoge im Kamerabild **zügig, korrekt, in der richtigen Reihenfolge und nur einmal pro logischem Dialog** erkennen und mit der passenden Stimme ausgeben. Die App soll vollständig offline verwendbar bleiben und von Android-API 23 bis zur aktuellen Ziel-API zuverlässig funktionieren.

**Nicht-Ziele:** vollständiger Rewrite nach Kotlin/Compose, Cloud-OCR, externe Spracherkennungsdienste, neue Berechtigungen ohne Funktionsbedarf oder umfassende UI-Neugestaltung.

### Definition of Done (für jede Aufgabe)

- [ ] Verhalten und Fehlerfall dokumentiert; bei Logikänderungen Regressionstest ergänzt.
- [ ] `./gradlew :app:assembleDebug` erfolgreich; sobald eingerichtet auch `./gradlew :app:lintDebug :app:testDebugUnitTest`.
- [ ] Kamera- und TTS-Ressourcen werden nach Pause, Wechsel in den Hintergrund und Activity-Neustart sauber behandelt.
- [ ] Keine Verschlechterung gegenüber den Referenzdialogen (siehe Teststrategie).
- [ ] Android-Versionen und reale Geräte, auf denen getestet wurde, im PR dokumentiert.

## Prioritäten / Umsetzung

- **P0:** potenzieller Crash oder offenkundiger Funktionsfehler.
- **P1:** Zuverlässigkeit, Performance und fehlerhafte Sprachausgabe.
- **P2:** Wartbarkeit, Konfigurierbarkeit und UX.

Die Aufwände sind **grobe Größenordnungen** für die Umsetzung, keine Zusage. Aufgaben möglichst in kleinen, unabhängigen Commits oder PRs bearbeiten.

### Phase 1 – Stabilität und nachweisbare Fehler (zuerst)

#### KAV-01 · Android-API-Kompatibilität prüfen und korrigieren – P0 · S

**Fundstellen:** `app/build.gradle.kts` (`minSdk = 23`), `MainActivity.java` (u. a. `String.isBlank()`, `VibrationEffect` und Collection-APIs).

- [x] Für alle verwendeten Java-/Android-APIs den Mindest-API-Level gegen API 23 prüfen.
- [x] `String.isBlank()` und weitere neuere Java-APIs entweder mit geprüftem Core-Library-Desugaring absichern oder kompatibel ersetzen.
- [x] Für Android-APIs oberhalb API 23 versionsabhängige Implementierungen bzw. Fallbacks einbauen.
- [ ] Start, Kamera-Workflow, Pause und erneutes Vorlesen auf API 23, 26, 30 und einem aktuellen API-Level testen.

**Akzeptanz:** Keine `NoSuchMethodError`-, `VerifyError`- oder API-bedingten Abstürze im Testlauf. **Hinweis:** Die tatsächliche Verfügbarkeit von `isBlank()` auf den Zielgeräten vor dem Fix verifizieren, nicht allein aus der Java-Version ableiten.

#### KAV-02 · Vibration korrekt absichern – P0 · XS

**Fundstellen:** `AndroidManifest.xml`, `MainActivity.java:610–613`.

- [x] Fehlende normale Berechtigung `android.permission.VIBRATE` ergänzen, falls Feedback beibehalten wird.
- [x] `VibrationEffect` nur auf unterstützten Android-Versionen verwenden; älteren API-Level abfangen.
- [ ] Optional Vibrationsfeedback separat schaltbar machen.

**Akzeptanz:** Kein Crash wegen fehlender Berechtigung oder nicht verfügbarer Vibrations-API; Vorlesen funktioniert auch ohne Vibrationshardware.

#### KAV-03 · OCR-Lesereihenfolge korrigieren – P1 · S

**Fundstelle:** `MainActivity.java:1137–1198`, `assembleStoryText()`.

**Bestätigter Logikfehler:** Zeilen werden in `validLines` sortiert und zu `rows` gruppiert, aber für den fertigen Text wird erneut über die unsortierte Eingabeliste `lines` iteriert.

- [x] Zusammenbau auf Basis der sortierten `rows` implementieren.
- [x] Links-rechts-Reihenfolge innerhalb einer Zeile und oben-unten-Reihenfolge zwischen Zeilen erhalten.
- [x] Zeilenumbruch, Silbentrennung, leere OCR-Zeilen und leicht versetzte Bounding-Boxes testen.

**Akzeptanz:** Permutierte Eingabelisten derselben geometrischen OCR-Zeilen liefern identischen, korrekt geordneten Text.

#### KAV-04 · Fehlerbehandlung im OCR-Lebenszyklus – P1 · S

**Fundstellen:** `MainActivity.java:393–428`, `handleRecognizedText()`.

- [x] Fehlgeschlagene ML-Kit-Tasks explizit behandeln (`addOnFailureListener` und sinnvoller Status).
- [x] `ImageProxy.close()` und Freigabe von `ocrBusy` in jedem Erfolgs-, Abbruch- und Fehlerpfad sicherstellen.
- [x] Kein Zugriff auf geschlossene `ImageProxy`-Daten; keine alten OCR-Ergebnisse nach Pause/Zerstörung der Activity verarbeiten.
- [ ] Recovery testen: Kamera kurz verdeckt, schnelle Pause/Weiter-Wechsel, App im Hintergrund, Kamera erneut gestartet.

**Akzeptanz:** Die Analyse blockiert nach einem OCR-Fehler nicht dauerhaft; kein doppeltes Schließen und keine nachlaufenden UI-Updates nach Zerstörung.

### Phase 2 – Dialogerkennung und Vorlesen (größter Nutzwert)

#### KAV-05 · OCR-Nachverarbeitung von UI-Thread trennen – P1 · M

**Fundstellen:** `MainActivity.java:416–580`, `805–881`, `1226–1242`.

- [ ] Verifizieren, auf welchem Executor ML Kit den Success-Callback ausführt; CPU-intensive Weiterverarbeitung explizit auf einen Worker legen.
- [ ] Analysezustand seriell halten (z. B. ein dedizierter Executor), um Race Conditions zu vermeiden.
- [ ] Nur View-Zugriffe und notwendige UI-Zustandsänderungen auf dem Main Thread ausführen.
- [ ] Zeitmessung für Frame-Eingang, OCR-Ende, Dialogentscheidung und TTS-Start ergänzen.
- [ ] `ImageProxy` erst schließen, nachdem bildabhängige Farberkennung beendet ist.

**Akzeptanz:** Keine spürbaren UI-Hänger bei längeren OCR-Texten; Zustandsupdates bleiben in korrekter Reihenfolge; Kamera-Pipeline verarbeitet keine veralteten Frames nach Neustart.

#### KAV-06 · TTS-Warteschlange und Abbruchlogik robust machen – P1 · M

**Fundstellen:** `MainActivity.java:259–280`, `337–350`, `529–628`.

**Risiko:** Nach `textToSpeech.stop()` wird `queuedUtterances` sofort auf 0 gesetzt; spät eintreffende `onDone`-/`onError`-Callbacks könnten den Zustand neuer Utterances verfälschen.

- [x] Laufende Ausgaben anhand eindeutiger Utterance-IDs statt allein eines Zählers verfolgen.
- [x] Abgebrochene Generationen oder IDs von aktuellen Ausgaben unterscheiden; `onStop` berücksichtigen.
- [x] Queue-Policy für schnelle Dialogwechsel definieren: aktuelle Zeile fertig sprechen, veraltete Zeile ersetzen oder bewusst verwerfen.
- [x] `QUEUE_ADD`/`QUEUE_FLUSH` und `isSpeaking()` nicht ohne gemeinsamen Zustandsautomaten mischen.
- [ ] Testfälle: Pause mitten im Satz, `NOCHMAL`, neue Dialogbox während TTS, mehrere schnelle Sprecherwechsel und TTS-Fehler.

**Akzeptanz:** Kein endloser „Lese vor“-Status, kein dauerhaft blockiertes Queue-Limit, kein unerwartetes Abspielen bereits abgebrochener Zeilen.

#### KAV-07 · Dialog-Tracking als Zustandsmaschine – P1 · L

**Fundstellen:** `MainActivity.java:430–580`, `762–803`, `1121–1135`, `1336–1349`.

- [ ] `DialogueTracker` mit klaren Zuständen einführen, etwa `NO_DIALOG`, `TYPING`, `STABLE`, `QUEUED`, `SPOKEN`.
- [ ] Zwei stabile OCR-Frames um eine Mindest-Ruhezeit ergänzen, damit kurze Pausen eines Typewriter-Effekts keinen unvollständigen Satz auslösen.
- [ ] Identische Zeilen bei unterschiedlichen Sprechern oder in neuen Dialogszenen als eigenständige Dialoge behandeln.
- [ ] Wachstum eines Satzes und Erweiterung nach abgeschlossenem Satz explizit unterscheiden.
- [ ] Textähnlichkeit und Deduplizierung als reine, separat testbare Funktionen implementieren.

**Akzeptanz:** Dialoge werden in Reihenfolge einmal pro logischem Auftreten gelesen; Tippanimationen werden nicht vorzeitig gesprochen; wiederholte Texte in späteren Szenen bleiben möglich.

#### KAV-08 · Sprechererkennung stabilisieren – P1 · M

**Fundstellen:** `MainActivity.java:630–737`, `958–1057`.

- [ ] Erkannten Sprecher über mehrere Frames plausibilisieren, statt nur den letzten OCR-Kandidaten zu übernehmen.
- [ ] Den 2,5-Sekunden-Haltewert (`SPEAKER_HOLD_MS`) mit dem Dialogzustand koppeln und Fehlzuordnungen bei schnellen Wechseln vermeiden.
- [ ] Namensschild-Erkennung bei geänderter Skalierung und Kameraausrichtung testen.
- [ ] Sicheren Fallback auf Erzählerstimme definieren, wenn Sprecher nicht zuverlässig bestimmbar ist.

**Akzeptanz:** Keine Übernahme der vorherigen Figurenstimme bei einem eindeutig neuen Dialog mit unbekanntem Sprecher.

#### KAV-09 · Kamera-Koordinaten und Story-Zone kalibrieren – P1 · M–L

**Fundstellen:** `MainActivity.java:75–82`, `732–737`, `805–877`, `activity_main.xml`.

- [ ] Transformationsbeziehung zwischen CameraX-Analysekoordinaten und `PreviewView` (inkl. `fillCenter`, Rotation, Crop) prüfen.
- [ ] Sichtbares Story-Overlay mit tatsächlicher Analyse-ROI abgleichen.
- [ ] Story-Zone verschiebbar und skalierbar machen; Standardwerte per „Zurücksetzen“ wiederherstellbar.
- [ ] Position geräte- und rotationssicher persistieren.
- [ ] Erkennung blauer Dialogflächen über verschiedene Licht- und Zoomverhältnisse prüfen.

**Akzeptanz:** Bei mehreren Displayformaten deckt die sichtbare Markierung tatsächlich den erfassten Analysebereich ab; eine Nutzerkalibrierung bleibt nach Neustart erhalten.

### Phase 3 – Sprachqualität, UX und Wartbarkeit

#### KAV-10 · TTS-Stimmen deterministischer und offline-fähig – P2 · M

**Fundstellen:** `MainActivity.java:883–1084`.

- [x] Netzwerkabhängige Stimmen ausschließen, wenn echte Offline-Nutzung das Ziel ist.
- [x] Feste Zuordnung der Figurenprofile unabhängig von der zufälligen Reihenfolge installierter Stimmen herstellen; sinnvolle Fallbacks definieren.
- [x] Unbekannte Sprecher konsistent einer Stimme zuweisen.
- [ ] Extremwerte für Pitch/Rate auf Verständlichkeit prüfen (insbesondere `PITCH_MULTIPLIER = 3.2f`).

**Akzeptanz:** Dieselbe Figur erhält bei gleicher Geräte-/Stimmenkonfiguration dasselbe Profil; offline entsteht kein stiller Wechsel auf eine Netzwerkstimme.

#### KAV-11 · Aussprache und Satzfluss verbessern – P2 · S–M

**Fundstellen:** `MainActivity.java:1250–1334`.

- [x] Wörterbuch aus `MainActivity` in eine eigene Daten-/Ressourcenklasse auslagern.
- [x] Doppelte Einträge (`evoli`, `glurak`) entfernen und Schlüssel auf Kollisionen prüfen.
- [x] Reguläre Ausdrücke sicher erstellen (`Pattern.quote`, passende Unicode-Wortgrenzen) und Eigennamen testen.
- [x] Wirkungsgleiche `replaceAll`-Aufrufe in `addNaturalPauses()` entfernen.
- [ ] Optional Sätze/TTS-Utterances segmentieren und natürliche Pausen mit mehreren Geräten bzw. Engines vergleichen.

**Akzeptanz:** Textanzeige bleibt unverändert, Ausgabe nutzt das Wörterbuch; keine Ersetzungen innerhalb fremder Wörter; Satzpausen sind nachvollziehbar getestet.

#### KAV-12 · Schlanke Komponenten statt God Activity – P2 · M–L

**Ist-Zustand:** `MainActivity.java` umfasst rund 1.491 Zeilen.

Geplante Aufteilung, **inkrementell** und ohne UI-Rewrite:

```text
MainActivity.java          UI, Berechtigungen, Lifecycle und Wiring
CameraPipeline.java        CameraX, Frame-Throttling, Zoom
DialogueDetector.java      OCR-Auswertung, ROI, Sprecherkandidaten
DialogueTracker.java       Stabilität, Typing, Wiederholungen, Dialogzustand
SpeechController.java      TTS, Utterance-Queue, Abbruch, Ausgabe
SpeakerProfileResolver.java Stimmen-/Figurenprofile
PronunciationDictionary.java Aussprache
SettingsRepository.java    Persistente App-Einstellungen
```

- [x] Zuerst reine Funktionen (`normalize`, `levenshtein`, Textzusammenbau) herauslösen.
- [ ] Dann `DialogueTracker` und TTS isolieren.
- [ ] Zuletzt Kamera-/Lifecycle-Code extrahieren; keine gleichzeitige Änderung des UI-Frameworks.
- [ ] State-Ownership und Thread-Zuständigkeit pro Komponente dokumentieren.

**Akzeptanz:** `MainActivity` koordiniert primär UI und Lebenszyklus; zentrale Dialoglogik lässt sich ohne Android-Framework unit-testen.

#### KAV-13 · Einstellungen, Bedienung und Barrierefreiheit – P2 · S–M

**Fundstellen:** `activity_main.xml`, `MainActivity.java:231–314`.

- [x] Initialwert des Tempo-Sliders und tatsächliche `speechRate` synchronisieren (XML: 48 %, Code: 1,15; Formel führt bei 48 % zu 1,23).
- [ ] Tempo, Lautstärke, Sounds und Story-Zone über Neustarts speichern.
- [ ] Interaktive Elemente mit ausreichend großen Touch-Flächen, Beschriftungen und Accessibility-Labels versehen.
- [ ] Texte in `strings.xml` auslagern; HUD bei kleinen Displays und großer Systemschrift überprüfen.
- [ ] OCR-Status optional um „Warum nicht vorgelesen?“ erweitern.

**Akzeptanz:** Angezeigte Regler entsprechen effektiven Werten; Einstellung nach Neustart korrekt; wichtige Funktionen sind über TalkBack eindeutig beschriftet.

### Phase 4 – Tests, Diagnostik und Release-Hygiene

#### KAV-14 · Referenzdatensatz und automatisierte Tests – P1 als Voraussetzung für größere Umbauten · M

- [ ] Mindestens 30–50 repräsentative Dialogfälle als anonymisierte, textuelle OCR-Fixtures mit Bounding-Boxes anlegen; echte Screenshots nur bei Bedarf und geeigneter Freigabe.
- [ ] Unit-Tests für Sortierung, Duplikate, Sprecherwechsel, Textwachstum, Benachrichtigungsfilter und Aussprache ergänzen.
- [ ] Tests für schnelle `pause → resume → repeat`-Abfolgen erstellen.
- [x] Android-Instrumentation-Tests für Kamera-/Lifecycle-/TTS-Integration auf Referenzgeräten vorbereiten.

**Akzeptanz:** Regressionen in den genannten Fällen werden automatisch erkannt, bevor Erkennungsalgorithmen geändert werden.

#### KAV-15 · Reproduzierbarer Build und CI – P2 · S–M

**Fundstellen:** Gradle-Dateien, Repo-Wurzel.

- [x] GitHub Actions für Debug-Build, Unit-Tests und Android Lint einrichten.
- [x] JDK-/Gradle-/Android-Gradle-Plugin-Kompatibilität passend zur vorhandenen Toolchain (u. a. Gradle 9.5, JDK 21 für Gradle) überprüfen.
- [x] `.DS_Store` und `*.drive-timeout-backup` aus dem Versionsstand entfernen und `.gitignore` ergänzen; keine benötigten Wrapperdateien löschen.
- [x] `AGENTS.md` mit Projektkontext, Build-/Testbefehlen und Coding-Konventionen vervollständigen.
- [ ] Release-Minifizierung und APK-Größe separat prüfen; nicht im ersten Stabilitäts-Patch aktivieren.

**Akzeptanz:** Jeder PR kann automatisch gebaut und getestet werden; Repository enthält keine versehentlichen lokalen Backup-Dateien.

#### KAV-16 · Lokale Diagnostik und Performance-Messung – P2 · M

- [ ] Opt-in-Diagnoseansicht für OCR-Dauer, erkannte Zeilen, ROI, Filter-/Skip-Grund, stabilen Dialogzustand und TTS-Warteschlange einbauen.
- [ ] Messpunkte: Frame → OCR-Ergebnis → stabiler Text → `onStart` der TTS-Ausgabe.
- [ ] Keine Bild- oder Dialoginhalte ungefragt hochladen oder dauerhaft speichern.
- [ ] Auf mindestens einem langsamen und einem schnellen Android-Gerät messen.

**Akzeptanz:** Falsch-positive/fehlende Vorlesungen lassen sich anhand lokaler Diagnosedaten reproduzieren, ohne die Offline-Eigenschaft zu verlieren.

## Abhängigkeiten und empfohlene Reihenfolge

1. **Sofort:** KAV-01, KAV-02, KAV-03, KAV-04 (kleine Bugfixes).
2. **Testbasis:** KAV-14 mit ersten Fixtures für die bestehenden Algorithmen; ergänzend KAV-16 mit minimalem Timing-Logging.
3. **Kernfunktion:** KAV-05 → KAV-06 → KAV-07 → KAV-08. Threading und TTS zuerst absichern, damit die Zustandsmaschine nicht auf instabilem Verhalten aufsetzt.
4. **Visuelle Präzision:** KAV-09, parallel mit Referenzgerät-Tests.
5. **Qualität/Produktpflege:** KAV-10, KAV-11, KAV-13.
6. **Refactoring/CI:** KAV-12 schrittweise entlang vorhandener Tests; KAV-15 möglichst früh für automatisierte Rückmeldung vorziehen.

**PR-Vorschlag:** Kleine P0-Fixes einzeln, OCR-Zeilensortierung separat, dann Tests/Diagnose, anschließend TTS-Queue und Dialog-Tracker in getrennten PRs. Keine fachlichen Bugfixes mit umfangreichen Layout- oder Architekturänderungen vermischen.

## Messbare Qualitätsziele

Zunächst eine **Baseline** auf mindestens zwei realen Android-Geräten mit denselben Referenzdialogen erheben. Anschließend diese Zielwerte anstreben (nicht als bereits erreichte Werte verstehen):

| Messgröße | Ziel / Prüfbedingung |
| --- | --- |
| Absturzfreiheit | 0 reproduzierbare Crashes in der Testmatrix und den festgelegten Dialogszenarien |
| Dialog-Lesereihenfolge | 100 % der geometrisch eindeutigen Sortier-Fixtures korrekt |
| Doppelte Ausgabe | Keine unbeabsichtigte Wiederholung derselben stabilen Zeile in einem Dialog |
| Erkennung von Menüs/Benachrichtigungen | 0 Vorlesungen in den dedizierten Negativ-Fixtures |
| Sprecherzuordnung | Keine nachweisbar veraltete Sprecherstimme bei eindeutigem Sprecherwechsel |
| Reaktionszeit | Median und p95 „letzte stabile Textänderung → TTS onStart“ dokumentieren; Optimierungsziel p95 ≤ 1,2 s auf dem Referenzgerät, sofern realistisch |
| Offline | Kamera-OCR und TTS mit vorher installierter Offline-Stimme im Flugmodus nutzbar |
| Kompatibilität | Smoke-Test auf API 23, 26, 30 und aktuellem Zielgerät |

**Wichtig:** Latenzziel und Testumfang nach der Baseline ggf. begründet anpassen. Eine reine Unit-Test-Abdeckung ist kein Ersatz für reproduzierbare Kamera-/TTS-Tests.

## Offene technische Entscheidungen

- Sollte bei schnell wechselnden Dialogen die vorherige Sprache unterbrochen werden oder vollständig zu Ende laufen? Im ersten Schritt die aktuelle Bedienungssemantik dokumentieren; anschließend per Test entscheiden.
- Sollen Stimmen auf allen Android-Geräten möglichst ähnlich klingen oder bewusst die lokal bestverfügbare Offline-Engine nutzen? Priorität zunächst auf Verständlichkeit und Offline-Funktion.
- Wie weit darf die Story-Zone kalibriert werden, ohne falsche Dialoge aus HUD und Menüs einzuschließen? Mit Negativ-Fixtures evaluieren.

## Referenzdateien

- [`MainActivity.java`](../app/src/main/java/de/stegmann/kameravorleser/MainActivity.java)
- [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml)
- [`activity_main.xml`](../app/src/main/res/layout/activity_main.xml)
- [`app/build.gradle.kts`](../app/build.gradle.kts)
- [`README.md`](../README.md)

**Pflege:** Nach jedem abgeschlossenen PR die zugehörigen Checkboxen und Ergebnisse aktualisieren; neue Befunde mit KAV-ID ergänzen.
