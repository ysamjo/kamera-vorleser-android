# Pokopia Story-Vorleser

Ein schneller Vorleser für die deutschen Storydialoge von Pokémon Pokopia. Die Rückkamera läuft dauerhaft, wertet nur die Dialogbox im unteren Bildschirmbereich aus und liest neue Storyzeilen sofort vor.

## Starten

1. Den Ordner `KameraVorleser` in Android Studio öffnen.
2. Falls gefragt: Android SDK 36 und JDK 21 (Java-Quellcode: 17) installieren lassen.
3. Ein echtes Android-Gerät anschließen und **Run** drücken.
4. Kamerazugriff erlauben und den Switch-2-Bildschirm quer ins Kamerabild nehmen.
5. Die weiße Pokopia-Dialogbox muss in der goldenen Story-Zone liegen. Große blaue Dialogflächen werden unabhängig von ihrer Position erkannt.

## Bedienung

- **Pause/Weiter:** Kamera bleibt sichtbar, OCR und Sprache pausieren.
- **Neu lesen:** Nur der gerade sichtbare Dialog darf erneut vorgelesen werden; frühere Dialoge bleiben im Gedächtnis.
- **Fokus:** Auf die gewünschte Textstelle im Kamerabild tippen. Der Rahmen zeigt den Fokuspunkt; langes Drücken schaltet zurück auf Autofokus. Pinch-Zoom bleibt verfügbar.
- **Stimme wählen:** Für die aktuell erkannte Figur eine deutsche Offline-Stimme auswählen. Die Zuordnung bleibt nach Neustarts erhalten. „Ä/Ö/Ü testen“ spielt eine deutsche Hörprobe; „Android-TTS“ öffnet die Systemauswahl und Sprachdaten.
- **Sprechtempo:** Regelt die Geschwindigkeit der deutschen Systemstimme.

Die App bleibt vollständig still, solange im eingegrenzten Storybereich oder auf einer großen blauen Dialogfläche kein neuer Text erscheint. Ein Text muss in zwei aufeinanderfolgenden Kamerabildern stabil erkannt werden, bevor er gesprochen wird. Das verkürzt die Reaktionszeit deutlich. Herstellungsanzeigen wie „Neue Kreation“ und „Du hast … hergestellt“, Hinweise wie „kein Text sichtbar“, Pokémon-Namen, Auswahlmenüs sowie typische Aktionshinweise wie „A Weiter“, „A Untersuchen“, „Zurück“, „Ja“ oder „Nein“ werden nicht vorgelesen.

Neben dem erkannten Namen zeigt die App das verwendete Stimmenprofil an, etwa „TIEF“ oder „HELL“. Bekannte Figuren haben feste Profile; auch neue Figuren erhalten anhand ihres Namens dauerhaft eine moderate Variation von Tonlage und Geschwindigkeit. Unterschiedliche installierte Grundstimmen werden zuerst getrennt vergeben; bei mehr Figuren als Grundstimmen werden diese wiederverwendet.

Ein lokales Aussprache-Wörterbuch korrigiert wichtige Begriffe nur für die Sprachausgabe, darunter Pokémon, Pokopia, Pokéball, Pokédex sowie zentrale Pokémon- und Figurennamen. In der erkannten Textanzeige bleibt immer die originale Schreibweise sichtbar. Das Wörterbuch liegt direkt in `PronunciationDictionary.java` und kann leicht ergänzt werden.

Der Name auf dem Schild oberhalb der Dialogbox wird nur intern als Sprecherkennung genutzt. Der Name selbst wird nicht gesprochen; namenlose Texte erhalten eine neutrale Erzählerstimme. Die Stimmen sind passend zu Pokémon-Gruppen kuratiert: kleine Pokémon klingen heller und lebhafter, große oder felsige Pokémon tiefer, Mooslaxo langsam und weich, Rotom/Porygon technisch, Geist-Pokémon dunkler und legendäre Pokémon ruhig-autoritär. Professor Tangoloss besitzt ein eigenes warmes, tiefes Profil. Unbekannte Figuren erhalten ein aus ihrem Namen deterministisch abgeleitetes Profil. Es werden keine urheberrechtlich geschützten Original-Pokémon-Stimmen mitgeliefert.

Das OCR-Modell ist in der App enthalten und funktioniert ohne Internet. Die App hält den Bildschirm wach und arbeitet, solange sie sichtbar im Vordergrund läuft. Bereits vorgelesene oder nahezu identische Dialoge desselben Sprechers werden auch nach einem App-Neustart nicht erneut gelesen. Die letzten 500 Dialoge bleiben lokal auf dem Gerät gespeichert. Derselbe Satz von einem anderen Pokémon wird als eigener Dialog behandelt. Bei abgebrochener oder fehlgeschlagener Sprachausgabe darf die Zeile erneut gestartet werden.

Die Oberfläche verwendet einen eigenständigen, Pokémon-inspirierten Abenteuerstil mit dunkelblauem HUD, goldener Story-Markierung, spielartiger Dialogkarte und einem eigenen Kamera-Sprechsymbol. Es werden keine offiziellen Logos oder Originalgrafiken verwendet.

## Entwicklung und Prüfung

Mit JDK 21: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`.
Die App unterstützt API 23; Core-Library-Desugaring stellt die verwendeten neueren
Java-APIs bereit. Erkennungstöne und Vibration sind vollständig entfernt; die App benötigt keine Vibrationsberechtigung.
`StoryTextAssembler` ordnet OCR-Fragmente geometrisch und ist ohne Android testbar.
OCR-Ergebnisse werden bei Pause, erneutem Lesen und Hintergrundwechsel verworfen;
ein laufendes Bild bleibt bis zum Abschluss offen und wird genau einmal freigegeben.

Umsetzungsstand und Geräte-Testlücken: [Validierung](docs/VALIDIERUNG.md).

Die TTS-Warteschlange verfolgt eindeutige Ausgabe-IDs. Verspätete Rückmeldungen
abgebrochener Ausgaben beeinflussen neue Ausgaben nicht. Fehlerhafte Ausgaben
werden erneut versuchbar; Pause und Hintergrundwechsel stoppen die Sprache.

GitHub Actions prüft jeden Push auf `main` und jeden Pull Request mit JDK 21,
Debug-/Release-APK und App-Bundles, Android Lint und JUnit für beide Varianten. Prüfberichte und Debug-APK werden als
Workflow-Artefakte bereitgestellt.

Es werden ausschließlich installierte deutsche Offline-Stimmen ausgewählt, bevorzugt de-DE und anschließend nach Qualität und Latenz sortiert. Sprecherwechsel brechen eine eventuell noch laufende vorherige Ausgabe ab, damit neue Dialoge das neue Stimmenprofil erhalten. Moderate Tonhöhen und Tempi vermeiden unnötig hölzernen Klang; die tatsächliche Sprachqualität hängt von den installierten TTS-Stimmen ab. Zusammengesetzte OCR-Umlaute werden vor der Sprachausgabe normalisiert. Fehlt eine solche Stimme, zeigt die App einen
Hinweis zur Installation in den Android-TTS-Einstellungen; ein Netzwerkfallback
findet nicht statt. Die Offline-Stimme muss vor der Nutzung installiert sein.
Tempo, Lautstärke und Figurenstimmen werden über Neustarts gespeichert.
Der Tempo-Regler zeigt beim ersten Start den tatsächlichen Wert 1,15 an.

Die App besitzt keine Internetberechtigung; das gebündelte OCR-Modell wurde auf
Pixel 10 Pro (API 37) mit synthetischem Text erfolgreich ausgeführt.
Gerätetests bauen: `./gradlew :app:assembleDebugAndroidTest`.
Auf einem ausgewählten, entsperrten Testgerät ausführen:
`ANDROID_SERIAL=<ADB-Gerät> ./gradlew :app:connectedDebugAndroidTest`.
Lifecycle-Tests werden bei gesperrtem/schlafendem Gerät ausdrücklich übersprungen.
Ein OCR-Smoke-Test ersetzt keinen realen Pokopia-Kameratest oder Hörtest.

Die Historie speichert nur per TTS-Abschluss bestätigte Dialoge. Laufende Texte
bleiben bis zum Abschluss im Arbeitsspeicher; ein Prozessabbruch verhindert so
nicht den nächsten Versuch. „Neu lesen“ gibt die sichtbare Zeile einmal frei,
ohne die gespeicherte Historie zu löschen. OCR-Umlaute werden bereits für
Sprecherkennung und Dialogvergleich in NFC normalisiert.

Jede Sprecherkennung erhält eine stabile Variation von Tonlage und Tempo;
die Berechnung hängt weder von der Reihenfolge der Dialoge noch von früheren
Reglerbewegungen ab. Wenn de-DE-Offline-Stimmen vorhanden sind, werden nur diese
verwendet; sonst bleiben andere installierte deutsche Offline-Stimmen zulässig.
Die hörbare Unterscheidbarkeit aller Figuren hängt von der Engine ab und ist
nicht durch unterschiedliche Zahlenprofile garantiert. Bei einem neuen Dialog
ohne erkanntes Namensschild fällt die App auf den Erzähler zurück.

Beim Scrollen vergleicht die Historie vollständige Wörter desselben Sprechers:
Ein überlappender bereits gelesener Anfang wird übersprungen, nur der neue Rest
wird gesprochen. Für Scroll-Überlappungen gelten mindestens drei gleiche Wörter;
Wachstum des bisherigen Textfensters wird auch bei kürzeren Präfixen erkannt.
Eine Änderung oder Unterbrechung löscht keine abgeschlossene Historie.
Fehler stoppen auch abhängige, noch wartende Scroll-Ausgaben. Es gibt keine
akustischen Erkennungssignale. Sehr schnelles Scrollen, das Text vor stabiler
Erkennung oder bei voller Queue entfernt, kann weiterhin Text verpassen.
