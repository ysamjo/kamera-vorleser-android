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
- **Neu lesen:** Der gerade sichtbare Text darf erneut vorgelesen werden.
- **Sprechtempo:** Regelt die Geschwindigkeit der deutschen Systemstimme.

Die App bleibt vollständig still, solange im eingegrenzten Storybereich oder auf einer großen blauen Dialogfläche kein neuer Text erscheint. Ein Text muss in zwei aufeinanderfolgenden Kamerabildern stabil erkannt werden, bevor er gesprochen wird. Das verkürzt die Reaktionszeit deutlich. Herstellungsanzeigen wie „Neue Kreation“ und „Du hast … hergestellt“, Hinweise wie „kein Text sichtbar“, Pokémon-Namen, Auswahlmenüs sowie typische Aktionshinweise wie „A Weiter“, „A Untersuchen“, „Zurück“, „Ja“ oder „Nein“ werden nicht vorgelesen.

Neben dem erkannten Namen zeigt die App das verwendete Stimmenprofil an, etwa „TIEF“ oder „HELL“. Bekannte Figuren haben feste Profile; auch neue Figuren erhalten anhand ihres Namens dauerhaft eine eigene, deutlich gespreizte Tonlage und Geschwindigkeit.

Ein lokales Aussprache-Wörterbuch korrigiert wichtige Begriffe nur für die Sprachausgabe, darunter Pokémon, Pokopia, Pokéball, Pokédex sowie zentrale Pokémon- und Figurennamen. In der erkannten Textanzeige bleibt immer die originale Schreibweise sichtbar. Das Wörterbuch liegt direkt in `MainActivity.java` unter `createPronunciationDictionary()` und kann leicht ergänzt werden.

Der Name auf dem Schild oberhalb der Dialogbox wird nur intern als Sprecherkennung genutzt. Der Name selbst wird nicht gesprochen; namenlose Texte erhalten eine neutrale Erzählerstimme. Die Stimmen sind passend zu Pokémon-Gruppen kuratiert: kleine Pokémon klingen heller und lebhafter, große oder felsige Pokémon tiefer, Mooslaxo langsam und weich, Rotom/Porygon technisch, Geist-Pokémon dunkler und legendäre Pokémon ruhig-autoritär. Professor Tangoloss besitzt ein eigenes warmes, tiefes Profil. Unbekannte Figuren erhalten eine neutrale Stimme statt eines zufälligen Effekts. Es werden keine urheberrechtlich geschützten Original-Pokémon-Stimmen mitgeliefert.

Das OCR-Modell ist in der App enthalten und funktioniert ohne Internet. Die App hält den Bildschirm wach und arbeitet, solange sie sichtbar im Vordergrund läuft. Bereits gelesene oder nahezu identische Zeilen werden kurzzeitig übersprungen, damit stehender Text nicht dauernd wiederholt wird.

Die Oberfläche verwendet einen eigenständigen, Pokémon-inspirierten Abenteuerstil mit dunkelblauem HUD, goldener Story-Markierung, spielartiger Dialogkarte und einem eigenen Kamera-Sprechsymbol. Es werden keine offiziellen Logos oder Originalgrafiken verwendet.

## Entwicklung und Prüfung

Mit JDK 21: `./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`.
Die App unterstützt API 23; Core-Library-Desugaring stellt die verwendeten neueren
Java-APIs bereit. Vibration nutzt auf API 23–25 den älteren Aufruf.
`StoryTextAssembler` ordnet OCR-Fragmente geometrisch und ist ohne Android testbar.
OCR-Ergebnisse werden bei Pause, erneutem Lesen und Hintergrundwechsel verworfen;
ein laufendes Bild bleibt bis zum Abschluss offen und wird genau einmal freigegeben.

Umsetzungsstand und Geräte-Testlücken: [Validierung](docs/VALIDIERUNG.md).

Die TTS-Warteschlange verfolgt eindeutige Ausgabe-IDs. Verspätete Rückmeldungen
abgebrochener Ausgaben beeinflussen neue Ausgaben nicht. Fehlerhafte Ausgaben
werden erneut versuchbar; Pause und Hintergrundwechsel stoppen die Sprache.
