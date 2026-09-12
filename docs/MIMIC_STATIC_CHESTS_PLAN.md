# Mimic: feste Trapped Chests erkennen

Ursprünglicher Vorschlag vom 12.09.2026. Die Positionsfilterung und explizite
Aufnahme sind seit **0.2.14** implementiert. **0.2.15** enthält die bestätigten
Buttons-/Dueces-Vorlagen in der JAR; zusätzliche Aufnahmen bleiben im Arbeitsspeicher.
Aktueller Stand und Befehle: [MIMIC_STATIC_CHESTS.md](MIMIC_STATIC_CHESTS.md).
Die folgenden Abschnitte dokumentieren die ursprüngliche Analyse und Referenzen.

## Was der aktuelle Code erklärt

`DungeonMimicChestScanner` sammelt Trapped Chests aus geladenen Chunks. Die
runbezogene `DungeonMimicChestMemory` behält Kandidaten auch nach Chunk-Unload.
Die Karte markiert nur dann einen Raum, wenn alle Kandidaten demselben logischen
Raum gehören. Kandidaten in mehreren Räumen unterdrücken die Mimic-Markierung;
die 2D-Waypoints können trotzdem sichtbar sein. Eine noch nicht erkannte feste
Trapped Chest kann somit beide gemeldeten Symptome erklären. Für den Run ohne
Log ist diese Ursache nicht nachgewiesen.

Der vorhandene Filter überspringt Trap-Räume und vollständig die bekannten
Raumnamen Buttons, Slime und Slime Maze. Das ersetzt keine präzise Erkennung
einzelner fester Truhen. Die Diagnose protokolliert jetzt zusätzlich `mapReason`
und `mapRoom`, damit keine Kandidaten von mehrdeutigen Kandidaten unterschieden
werden können.

## Empfohlene Lösung

1. **Feste Truhen pro Raumvariante speichern.** Jede Truhe erhält eine lokale
   Blockposition `(x, y, z)` relativ zu einem eindeutig definierten Raumursprung.
   Zusätzlich werden mehrere unveränderliche, asymmetrisch verteilte Baublöcke
   als Orientierungspunkte erfasst. Raumname allein genügt nicht: Eintrag an
   die erkannte Variante und ihre bestätigten Hashes binden.
2. **Position und Drehung auflösen.** Der Matcher muss den Ursprung und eine
   nachgewiesene Drehung (0/90/180/270 Grad) liefern. Mehrzellige Formen können
   Kandidaten einschränken; insbesondere bei 1x1 und symmetrischen Formen ist
   die Form nicht eindeutig. Dort die Orientierungspunkte in den geladenen
   Chunks vergleichen. Keine Ausrichtung aus Spielerblickrichtung ableiten.
3. **Nur die bestätigte Position ausschließen.** Lokale Truhenkoordinaten mit
   diesem Transform in Weltkoordinaten umrechnen und exakt vergleichen, inklusive
   Höhe. Eine zusätzliche Trapped Chest an einer anderen Position bleibt ein
   Mimic-Kandidat, auch innerhalb desselben Raums.
4. **Unsicherheit erhalten.** Wenn die Orientierung mehrdeutig oder der Raum
   noch nicht geladen ist, keinen geratenen Ausschluss anwenden. Bei mehreren
   möglichen Drehungen nur Positionen ignorieren, die unter allen noch gültigen
   Drehungen als feste Truhe bestätigt sind. Niemals die Vereinigung aller vier
   gedrehten Truhenlisten sperren: Das könnte einen echten Mimic ausblenden.
5. **Daten bewusst bestätigen.** Ein einzelner Run kann einen Mimic enthalten
   und darf deshalb keine dauerhaften Ausschlüsse erzeugen. Vorlagen anhand
   mehrerer Runs oder einer überprüften Raumaufnahme bestätigen; genaue
   Positionen, Variante und Herkunft dokumentieren. Erst mit vollständigen
   Vorlagen die pauschalen Raumfilter ersetzen.

Die Erkennung gehört in die bereits begrenzten Scan-Batches und wird pro
Instanz/Raumvariante gecacht. Kein kompletter Blockscan pro Frame. Änderungen
an Chunk-Verfügbarkeit oder Raumzuordnung lösen nur die betroffenen Prüfungen aus.

## Alternativen und Grenzen

- Ein kleines Blockmuster rund um jede Truhe kann zusätzliche Bestätigung
  liefern, sollte aber nicht allein entscheiden: ähnliche Nischen können in
  mehreren Räumen vorkommen.
- Ein zusätzlicher Kandidat gegenüber einer vollständig bestätigten Vorlage
  ist ein starkes Mimic-Indiz. Ein bloßer Vergleich der Anzahl bei teilweise
  geladenem Raum reicht nicht.
- Die gesamte Truhe oder den ganzen Raum wegen des ersten Fundes dauerhaft zu
  ignorieren wäre unzuverlässig; derselbe Raum kann später anders geladen oder
  ein zusätzlicher Kandidat sichtbar werden.

## Prüffälle für die Umsetzung

Alle vier Drehungen, mehrere Weltpositionen und negative Koordinaten; identische
X/Z bei anderer Höhe; symmetrische 1x1-Räume mit unklarer Richtung; Teil-Loads und
erneuter Chunk-Load; feste Truhe plus echter zusätzlicher Mimic im selben Raum;
zwei voneinander unabhängige Raumvarianten; Reset beim Instanzwechsel.

## Quellcode-Abgleich mit anderen Mods

Geprüft am 12.09.2026. Recherche, noch keine Änderung am Laufzeitverhalten.

Alle drei geprüften Implementierungen speichern X/Z relativ zum Raum und rechnen
über einen Ursprung und 90-Grad-Drehungen zurück in Weltkoordinaten. Die Y-Koordinate
bleibt dabei eine absolute Bauhöhe. Ihre Koordinatenkonventionen sind nicht ohne
Umrechnung austauschbar.

- **Odin**, Commit `331628623c59f7a7274e3985ae16f5411ae21123`: Bei 1x1-Räumen
  prüft `get1x1Rotation` vier Eckpositionen auf der ermittelten oberen Bauhöhe auf
  blaue Terrakotta. Ein Treffer liefert Richtung und `clayPos`. Für größere Räume
  werden Richtung und Ursprung aus der Kachelgeometrie abgeleitet; Fairy hat eine
  Sonderbehandlung. `getRelativeCoords` und `getRealCoords` drehen um diesen Ursprung.
  Das beweist keine universelle Eindeutigkeit jedes Raums: Für Kung Marker und
  mehrzellige Annahmen an den tatsächlichen Varianten prüfen.
  [Raum und Umrechnung](https://github.com/odtheking/Odin/blob/331628623c59f7a7274e3985ae16f5411ae21123/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/map/tile/DungeonRoom.kt#L82-L183),
  [Waypoint laden](https://github.com/odtheking/Odin/blob/331628623c59f7a7274e3985ae16f5411ae21123/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/dungeonwaypoints/DungeonWaypointPacks.kt#L83-L89),
  [Waypoint aufnehmen](https://github.com/odtheking/Odin/blob/331628623c59f7a7274e3985ae16f5411ae21123/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/dungeonwaypoints/DungeonWaypointEditor.kt#L47-L53).
- **Skyblocker**, Commit `249c9cd0b0214ecb848d6e064687a4b63031a59a`: Die Raumform
  reduziert mögliche Drehungen auf vier bei Quadraten, zwei bei langen Rechtecken
  und eine bei L-Formen. Blockproben aus einer 11x11x11-Umgebung des Spielers werden
  in die möglichen lokalen Koordinaten umgerechnet und gegen Raumvorlagen geprüft.
  Ein eindeutiges Paar aus Raum und Richtung wird mit zehn weiteren geeigneten
  Blöcken nachgeprüft. Bestätigte Räume brauchen diesen Abgleich nicht weiter.
  [Kandidaten und Matching](https://github.com/SkyblockerMod/Skyblocker/blob/249c9cd0b0214ecb848d6e064687a4b63031a59a/src/main/java/de/hysky/skyblocker/skyblock/dungeon/secrets/Room.java#L207-L510),
  [Koordinaten](https://github.com/SkyblockerMod/Skyblocker/blob/249c9cd0b0214ecb848d6e064687a4b63031a59a/src/main/java/de/hysky/skyblocker/skyblock/dungeon/secrets/DungeonMapUtils.java#L228-L265).
- **Dungeon Rooms Mod**, Branch `3.x`, Commit `4d9ce89042c5bbf11b6f5a9bcf689c84e521aeb0`:
  Ähnliches Kandidatenverfahren mit sichtbaren Blockproben und gespeicherten
  `.skeleton`-Daten. Anschließend transformiert der Renderer gespeicherte
  Secret-Positionen. Die alte Abschlussprüfung zählt eindeutige Raumnamen statt
  eindeutiger Raum/Richtung-Paare; diese Mehrdeutigkeit nicht übernehmen.
  [Erkennung](https://github.com/Quantizr/DungeonRoomsMod/blob/4d9ce89042c5bbf11b6f5a9bcf689c84e521aeb0/src/main/java/io/github/quantizr/dungeonrooms/dungeons/catacombs/RoomDetection.java#L179-L212),
  [Waypoint-Transformation](https://github.com/Quantizr/DungeonRoomsMod/blob/4d9ce89042c5bbf11b6f5a9bcf689c84e521aeb0/src/main/java/io/github/quantizr/dungeonrooms/dungeons/catacombs/Waypoints.java#L75-L98).

**Ableitung für Kung:** vorhandene Hash-Erkennung behalten und eine getrennte,
gecachte Raumtransformation ergänzen. Zuerst die günstige Terrakotta-Eckprüfung
validieren, bei unklarer Richtung wenige bestätigte, unterscheidbare Baublöcke
prüfen. Danach feste Truhenpositionen exakt transformieren. Der derzeitige
Kung-Katalog erzeugt gedrehte und gespiegelte Zellformen, normalisiert und
dedupliziert sie; `MatchedRoom` bewahrt keine eindeutige physische Drehung. Eine
passende Kartenform darf deshalb nicht bereits als Waypoint-Ausrichtung gelten.

Secret-Daten sind keine vollständige Liste fester Trapped Chests. Beispielsweise
kennzeichnet Dungeon Rooms Mods `secretlocations.json` bei Buttons/Slime nur
Secret-Kategorien, keine vollständige Menge statischer Trapped Chests. Diese
Positionen weiterhin gesondert bestätigen; die Raum-Metadatenautorität bleibt
wie vom Nutzer festgelegt die Wiki plus dessen bestätigte Korrekturen.
