# Visuell geprüfte Dialogboxen

Am 04.11.2026 in Ego Lite auf YouTube geöffnet, die unten aufgeführten Stellen
abgespielt, pausiert und als dekodierte Videoframes visuell geprüft. Dies ist ein
Vergleich direkter Spielaufnahmen, kein Kameratest der Android-App.

## Primärquellen: deutsche Spielaufnahmen von Domtendo

| Stelle | Sichtbarer Fall |
| --- | --- |
| [Teil 1, 01:23](https://www.youtube.com/watch?v=Cxwecy8pOas&t=83s) | Dunkelblauer, stark abgerundeter unterer Kasten, weiße Schrift, kein Namensschild. |
| [Teil 1, 01:42](https://www.youtube.com/watch?v=Cxwecy8pOas&t=102s) | Derselbe blaue Kasten vor blaugrünem Hintergrund, kurze erste und lange zweite Zeile; Antwortmenü rechts darüber. |
| [Teil 1, 02:00](https://www.youtube.com/watch?v=Cxwecy8pOas&t=120s) | Frisurenmenü und Bedienhinweise: nicht vorlesen. |
| [Teil 1, 04:00](https://www.youtube.com/watch?v=Cxwecy8pOas&t=240s) | Heller Tangoloss-Dialog am Tag mit rechts darüber liegender Antwortliste. |
| [Teil 1, 12:00](https://www.youtube.com/watch?v=Cxwecy8pOas&t=720s) | Pokédex-Beschreibungen: nicht vorlesen. |
| [Teil 1, 20:50](https://www.youtube.com/watch?v=Cxwecy8pOas&t=1250s) | Einstellungen mit hellem Beschreibungstext am unteren Rand: nicht vorlesen. |
| [Teil 2, 01:01](https://www.youtube.com/watch?v=2hRty3TXSDg&t=61s) | Professor Tangoloss bei Nacht: nahezu weißer Innenraum, geschwungener hellvioletter Rand, türkisfarbenes Namensschild. |

Weitere Stichproben aus Teil 1: 00:25, 01:20, 01:28, 01:37, 03:10, 05:30,
06:00, 07:00, 08:00, 10:00, 15:00 und 16:40. Keine vollständige Sichtung der Videos
oder aller Dialogvarianten behaupten.

## Konkrete Befunde und Korrektur gegenüber Version 1.6

Der dominante Innenraum des hellen Tierdialogs hat im Nacht-Frame RGB
**243,243,243**. Version 1.6 erlaubte maximal 242; dadurch wurden echte Dialogzeilen
verworfen. Die Obergrenze entfällt; Abgrenzung, Flächenprüfung und Namensschild
bleiben erforderlich. Eine weiße Vollbildseite wird weiterhin abgewiesen.

Die blaue Introbox hat ungefähr RGB **58,68,159**. Ihr blaugrüner Hintergrund
unterscheidet sich weniger stark als die weiße Box von der Spielwelt. Die bisherige
Kontrastsumme 100 verwarf kurze blaue Zeilen; 50 genügt bei den gemessenen positiven
und negativen Beispielen auch mit auf 80 % skalierten RGB-Kanälen.

Beide Kästen liegen in diesen 1920×1080-Frames ungefähr zwischen x=340 und x=1580,
ab y=840. Text beginnt etwa bei x=432, Zeilen bei y=909 und y=984. Der Mindest-
Samplingradius einer kurzen Zeile darf nicht links in die Spielwelt greifen;
die Abtastung wird auf den Innenbereich x=21–79 % begrenzt.

## Reproduzierbare Regressionen und Grenzen

`BubbleReferenceTest` nutzt zwölf gemessene Zeilen-/Hintergrundfälle in
`app/src/test/resources/dialogue/`: sieben positive Zeilen aus beiden Boxformen,
fünf Gegenbeispiele (Pokédex, Einstellungen, Frisuren-Bedienung, Antwortliste,
Spielwelt). Gespeichert werden nur je 45 RGB-Pixel, neun obere Vergleichspixel,
Quellzeit und manuell gemessene Textgrenzen; keine vollständigen Screenshots oder
Dialogtexte. Originalframes liegen ausschließlich im lokalen ZIP unter `Archiv/`.

Die Tests prüfen reale Farbstichproben, Region, Abgrenzung und Abtastpositionen.
Sie prüfen keine ML-Kit-Bounding-Boxes der Videos, keine Kameraprojektion, Reflexe,
Belichtungsautomatik, Perspektive, Schatten oder akustische Latenz. Die Formprüfung
bleibt eine Farb-/Geometrieheuristik und kein vollständiger Konturabgleich.
