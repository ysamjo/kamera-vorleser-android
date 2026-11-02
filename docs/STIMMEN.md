# Figurenstimmen ab Version 1.5

Stand: 02.11.2026. Der Standard enthält 15 unterschiedliche Fish-Grundstimmen.
Die fünf bisherigen IDs bleiben enthalten; zehn weitere stammen aus dem deutschen
Katalog von **Fish Official**, recherchiert mit Ego Lite. Dies sind kuratierte
Rollen, keine neu erstellten Klone der Original-Synchronstimmen.

| Rolle / Beispiele | Fish-Stimme | Voice-ID |
| --- | --- | --- |
| Erzähler / System | German Father | `c079932b47564fcfad0a9f6874a49df7` |
| Pikachu / Pichu | Paluten 0.1 | `77e3908a672a4b43b30786e52655d1bc` |
| Evoli / Togepi / Pummeluff | Christa deutsch | `88b18e0d81474a0ca08e2ea6f9df5ff4` |
| Professor Tangoloss | GERMAN WISDOM. M | `3130ae7c3d844e2998978289c7c11c98` |
| Scherox / Machollo / Tinkmaster | EN_A1_P0_P0A | `9b1041732d1b4c2686d695c8bba3b149` |
| Schlappchu | Lena | `3937b44b604249f18b94913daef94043` |
| Glumanda / Glutexo / Glurak | Leon | `7b7c88bf825a4ab3bca27d55c26ddf16` |
| Bisasam / Bisaknosp | Amelie | `b5b77e8422b948efbf13be1e567a406c` |
| Mooslaxo / Relaxo / Chef Dente / gemütliche Riesen | Markus | `164f91694aef42509dc9ba1b7f8e2c7e` |
| DJ Rotom / Porygon / technische Pokémon | Lukas | `c1e7cc926aa5469aa966c353094d4b0b` |
| Plinfa / kleine Vögel und Insekten | Emma | `3235abc9a84b407d92f73539a5651720` |
| Schiggy / Mauzi / freche Figuren | Nico | `a3f28343dfac42358e1f4fdf7562a773` |
| Felori / Mew / elegante und magische Figuren | Valentina | `e5fb46af69e0481fa0d2d0f209b07ae2` |
| Onix / Stahlos / massive und autoritäre Figuren | Jonas | `44f1ec96a51e429f8b87776cbc55a997` |
| Gengar / Geister / verschmitzte Figuren / Farbeagle | Stefan | `86a132379c9e497785d53a4d1e05a2c1` |

Die Profile sind redaktionell zugeordnet, keine Häufigkeitsmessung aller
Spieldialoge. Mit Figureneffekten sendet Fish zusätzlich kurze Regieanweisungen:
beispielsweise sanft/naturverbunden für Bisasam, gemütlich/schläfrig für Mooslaxo,
präzise/rhythmisch für Rotom. Das sind Hinweise an den Anbieter, keine garantierten
Klangparameter. Die lokalen Pitch-/Tempo-Profile bleiben unverändert.

## Verhalten und Einstellungen

Der vollständige Standardpool aktiviert die festen Rollen anhand der Voice-IDs,
auch wenn die Liste umsortiert wurde. Eine früh erkannte oder unbekannte Figur
kann einer bekannten Figur ihre Rolle nicht wegnehmen. Verwandte Figuren teilen
sich ihre Rollenstimme bewusst. Unbekannte Namen erhalten weiterhin gespeicherte,
möglichst gleichmäßig verteilte Grundstimmen. Deutsche und englische Namen sowie
NFC-Umlaute werden unterstützt; beispielsweise Schlappchu/Peakychu und
Bisasam/Bulbasaur. Nur korrekt erkannte Namen können passend zugeordnet werden.

Beim ersten Start von Version 1.5 wird ausschließlich eine fehlende oder der alten
Fünfer-Standardliste entsprechende Konfiguration erweitert. Eigene, leere und
ungültige Listen werden nicht automatisch überschrieben. Die vorher automatisch
vergebenen Fish-Zuordnungen werden bei dieser Erweiterung neu vergeben. Andere
Einstellungen, Google-Zuordnungen, lokale Stimmen und Zugangsdaten bleiben erhalten.
Eine eigene Voice-Liste verwendet die bisherige gespeicherte Stimmenverteilung.

## Quellen und Prüfgrenzen

Die neuen Katalogeinträge sind direkt auf Fish abrufbar, zum Beispiel
[Amelie](https://fish.audio/app/m/b5b77e8422b948efbf13be1e567a406c),
[Markus](https://fish.audio/app/m/164f91694aef42509dc9ba1b7f8e2c7e),
[Lena](https://fish.audio/app/m/3937b44b604249f18b94913daef94043) und
[Lukas](https://fish.audio/app/m/c1e7cc926aa5469aa966c353094d4b0b).
Die vollständigen Aliase stehen in `PokopiaVoiceCatalog.java`.

Browser-Synthesen und Android-Prüfungen dieses Stands werden separat in
[VALIDIERUNG.md](VALIDIERUNG.md) festgehalten. Ein erfolgreicher Web-Editor-Test
belegt weder die API-Verfügbarkeit noch die subjektive Natürlichkeit
oder die Zuordnung bei echten Kamera-Dialogen. Anbieter können Stimmen ändern
oder entfernen. Das vorhandene Drama-3-/HTTP-402-/Google-Verhalten bleibt erhalten.
