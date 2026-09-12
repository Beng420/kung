# Feste Trapped Chests — Kung 0.2.15

**Buttons und Dueces sind direkt in der JAR enthalten.** Der Nutzer hat je eine
feste Trapped Chest in beiden Räumen bestätigt. Beide Aufnahmen enthalten 48
unabhängige Strukturpunkte und die beobachteten Core-/Stable-Hash-Alternativen.
Dueces ist der zuvor unbekannte 1x1-Raum. Die Aufnahmen wurden unverändert aus dem
Spielprofil übernommen; für diese Ausschlüsse wird keine Profildatei benötigt.

## Benutzung

Seit 0.3.3 zeigt Mimic ESP den 2D-Waypoint und die Mimic-Kartenmarkierung.
Die rote 3D-Box ist entfernt; Erkennung, Truhenausschlüsse und Kill-Erfassung
verwenden weiterhin dieselben Kandidaten und Filter.

Für Buttons und Dueces genügt die aktuelle JAR. `/kung mimic` zeigt die Zahl
eingebauter Ausschlüsse und zusätzlicher Sitzungsmarkierungen sowie den Status
einer anvisierten Position. Erneutes Markieren bekannter Truhen ist nicht nötig.

Für weitere feste Truhen bleibt die Aufnahme verfügbar:

1. Eine sicher fest eingebaute Trapped Chest direkt ansehen und
   `/kung mimic ignore` ausführen. Der vollständige Raum muss erkannt sein.
2. Die Markierung gilt bis zum nächsten Minecraft-Neustart, auch über Run-Wechsel.
   `/kung mimic copy` kopiert neue Aufnahmen in die Zwischenablage, damit sie bei
   einem späteren Update eingebaut werden können. Es wird keine Datei geschrieben.
3. `/kung mimic undo` entfernt nur die letzte zusätzliche Sitzungsmarkierung.
   Die eingebauten Ausschlüsse bleiben erhalten.

`DungeonStaticChestCatalog` lädt einmalig die interne Ressource
`kung-dungeon-scans/static-trapped-chests.json`. Sie wird nicht ins Profil entpackt;
alte gleichnamige Profildateien werden nicht gelesen. Neue Markierungen werden
weder automatisch dauerhaft gespeichert noch hochgeladen. Die laufende Mod
verändert ihre JAR nicht selbst.

## Eingebaute Aufnahmen

| Raum | Zellen | Lokale Truhe X / Y / Z | Referenzblöcke |
| --- | --- | --- | --- |
| Buttons | 2×2 | 48 / 81 / 59 | 48 |
| Dueces | 1×1 | 15 / 79 / 12 | 48 |

Quelle: vom Nutzer am 12.09.2026 bestätigte Spielaufnahmen; die zweite Aufnahme
wurde um 05:24:55 gespeichert. Die übernommenen Originaldaten haben SHA-256
`9da7776971d372ac982a7056df1bfb2be8cea5f4f3f02a684c14d56cbf520cbe`.

## Matching und Grenzen

- X/Z liegen relativ zur nordwestlichen Bauecke des aufgenommenen Raums; Y ist
  absolute Bauhöhe. Eine Kachel hat 31 Blockpositionen, benachbarte Mittelpunkte
  sind 32 Blöcke auseinander. Rotation verwendet die tatsächliche Bauausdehnung
  (`maxCell * 32 + 31`), nicht die Spielerblickrichtung.
- Die vollständige Zellform und erfasste Core-/Stable-Hash-Alternativen binden
  eine Vorlage an die Variante. Beim Bestätigen kommen bekannte Hash-Alternativen
  derselben Katalogkomponente hinzu, um vorhandene Pre-/Run-Hashes zu unterstützen.
  Namen oder gespiegelte Kartenformen allein reichen nicht. Ein unvollständiger
  Match oder ein zusammengeführter Render-Raum mit abweichenden Komponenten wird
  noch nicht zum Speichern verwendet.
- Vier echte 90-Grad-Drehungen werden geprüft. Die Vorlage enthält 12–48
  unabhängige Strukturblöcke. Die Aufnahme verteilt diese im Raum und priorisiert
  Blöcke, die alternative Drehungen nachweislich unterscheiden. Truhen, Luft,
  Redstone und andere dynamische Blöcke sind keine Orientierungspunkte.
- Alle Proben einer gültigen Drehung müssen geladen und passend sein. Fehlende
  Chunks halten andere Drehungen als möglich offen. Ein Ausschluss gilt nur für
  eine Position, die unter allen möglichen Drehungen fest ist; nie für die
  Vereinigung möglicher Truhenpositionen. Eine mehrdeutige Erstaufnahme wird
  abgelehnt, ohne Daten zu speichern.
- Exakte Position inklusive Y: Eine zusätzliche Trapped Chest im selben Raum
  bleibt Kandidat. Der gemeinsame Filter greift im 2D-Waypoint und der
  persistenten Mimic-Kartenerkennung. Spätere sichere Ausschlüsse entfernen
  zuvor gespeicherte Fake-Kandidaten und können eine mehrdeutige Karte auflösen.
- Bestehende pauschale Buttons-/Slime-/Slime-Maze-Filter bleiben für Varianten
  **ohne** präzise Vorlage zunächst bestehen. Für Varianten **mit** Vorlage
  gelten nur die bestätigten Positionsausschlüsse. Trap-Räume bleiben ausgeschlossen.
  Buttons wird für die erfasste Variante damit gezielt nach Position gefiltert.
  Für nicht abgedeckte Varianten bleibt die Übergangsregel bestehen; zwei Vorlagen
  bilden keine vollständige Datenbank aller festen Truhen.
- Bestätigte Positionen werden pro Raum/Hashes/Instanz gecacht und überleben
  Chunk-Unloads. Instanzwechsel und Katalogänderung (auch Undo) verwerfen die
  Auflösung. Unklare Vorlagen werden erneut geprüft. Der normale Filter hat
  insgesamt höchstens 512 Weltblockabfragen pro Aktualisierung; aufgeschobene
  Vorlagen werden fortgesetzt. Es gibt keine Blockabfragen im Renderpfad.
  Nur der explizite Aufnahmebefehl darf eine größere einmalige Stichprobe lesen.

## Quellenprüfung

Die bisherigen Referenzen stehen in [MIMIC_STATIC_CHESTS_PLAN.md](MIMIC_STATIC_CHESTS_PLAN.md).
Dungeon Rooms' Skeleton-Dateien enthalten nur eine Auswahl von Baublöcken und
keine Trapped Chests; die Secret-Waypoints ersetzen ebenfalls keine vollständige
Liste fester Truhen. Die zusätzlich geprüften Dungeons-Guide-Raumdaten haben
eigene Nutzungsbeschränkungen; keine davon wurde importiert oder extrahiert.

[Stellas rooms.json](https://github.com/Eclipse-5214/ether/blob/30a1b4bcdd82ef4b2e1ac45bf6b981ff564b900c/rooms.json)
nennt je eine feste Trapped Chest für Buttons, Slime, Dueces, Redstone Key und
New Trap, liefert aber keine Positionskoordinaten. Dueces und Redstone Key
waren 1x1-Kandidaten für den damals unbekannten Raum; die Türbeschreibung allein
bewies keine Identität. Die jetzige Dueces-Zuordnung stammt aus der echten Aufnahme
mit passenden Raum-Hashes. Aus fremden Truhenanzahlen wurden keine Positionen
abgeleitet und keine Raum-Metadaten überschrieben.

## Prüfung

Vollständiger Build für Minecraft 26.1.2: **262 Tests erfolgreich**, keine Fehler
oder übersprungenen Tests. Die 18 Static-Chest-Tests prüfen unter anderem die
beiden echten Aufnahmen aus den Ressourcen, alle Drehungen/Verschiebungen,
Core-/Stable-Alternativen, Übereinstimmung mit den kanonischen Raumdaten,
zusätzliche echte Mimic-Kandidaten, fehlende Chunks, Mehrdeutigkeit und das
Blockabfragebudget. Sitzungs-Export/Undo werden geprüft; Undo kann eingebaute
Ausschlüsse nicht entfernen. Eine neue Kataloginstanz enthält nur die JAR-Daten.

Artefakt: `versions/mc26_1_2/build/libs/kung-26.1.2-0.2.15.jar`.
Die Ressource in der fertigen JAR wurde per SHA-256 gegen beide Originalaufnahmen
geprüft. Anschließend wurde die identische, überflüssige Profildatei entfernt.
Die Aufnahme erfolgte im Spiel; wiederholte Erkennung in anderen Drehungen ist
noch nicht durch einen Live-Trace bestätigt. Die Rotationsprüfungen verwenden
simulierte Weltblöcke aus den echten Aufnahmen.
