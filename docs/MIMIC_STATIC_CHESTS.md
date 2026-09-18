# Feste Trapped Chests

**Buttons, Dueces und Redstone Key sind direkt in der JAR enthalten.** Der Nutzer
hat je eine feste Trapped Chest in diesen Räumen bestätigt. Alle Aufnahmen enthalten
48 unabhängige Strukturpunkte und die beobachteten Core-/Stable-Hashes.
Dueces ist der zuvor unbekannte 1x1-Raum. Die Aufnahmen wurden unverändert aus dem
Spiel übernommen; für diese Ausschlüsse wird keine Profildatei benötigt.

## Benutzung

Seit 0.3.3 zeigt Mimic ESP den 2D-Waypoint und die Mimic-Kartenmarkierung.
Die rote 3D-Box ist entfernt; Erkennung, Truhenausschlüsse und Kill-Erfassung
verwenden weiterhin dieselben Kandidaten und Filter.

Für Buttons, Dueces und Redstone Key genügt die aktuelle JAR. `/kung mimic` zeigt die Zahl
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
| Redstone Key | 1×1 | 18 / 69 / 29 | 48 |

Buttons/Dueces: vom Nutzer am 12.09.2026 bestätigte Spielaufnahmen; die zweite Aufnahme
wurde um 05:24:55 gespeichert. Die übernommenen Originaldaten haben SHA-256
`9da7776971d372ac982a7056df1bfb2be8cea5f4f3f02a684c14d56cbf520cbe`.

Redstone Key: unveränderter `/kung mimic copy`-Export vom 18.09.2026,
Core `1786984420`, Stable `1448328045`. Der Trace bestätigt um 15:35:50.288
die Weltposition `-118,69,-11` bei Raumursprung `-136,-40`. Die Aufnahme ergänzt
die bestehenden zwei Vorlagen; zusätzliche Truhen im Raum bleiben Kandidaten.
Die Ressourcentests prüfen alle vier Drehungen, Verschiebungen, beide Hash-Arten
und zusätzliche Truhen. Wiedererkennung mit der gebauten JAR bleibt live zu prüfen.

## Matching und Grenzen

- Der inkrementelle Truhenscan liest den vollständigen Block-Entity-Positionsindex
  geladener Chunks, einschließlich noch nicht initialisierter Einträge. Jede
  Position muss tatsächlich eine Trapped Chest enthalten. Die Suche bleibt auf
  nahe Chunks und vier fortlaufende Chunk-Prüfungen pro Tick begrenzt, mit einem
  kooperativen 2-ms-Budget und höchstens einem vollständigen Chunk-Fallback pro
  Tick. Es werden keine Chunks nachgeladen.
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
  Für nicht abgedeckte Varianten bleibt die Übergangsregel bestehen; drei Vorlagen
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

### Trace vom 13.09.2026, 14:44:17

`kung-trace-20260913-144417.log` aus `Dungeons 26.1.2/logs/kung` zeigt eine
späte **erste Erkennung** in Catwalk, keine nachgewiesene falsche Zuordnung.
Der Nutzer hält Catwalk auch für den tatsächlichen Mimic-Raum.

| Zeitpunkt | Gesicherte Beobachtung |
| --- | --- |
| 14:43:48.321 | Der spätere Catwalk-Chunk ist bei der Raumprüfung geladen. Das beweist noch keine Truhe an der späteren Position. |
| 14:43:48.670–14:43:49.172 | Ein anderer Kandidat bei `-193,81,-109` wird nach Erkennung des Trap-Raums korrekt entfernt. |
| 14:43:53.251 | Akzeptiertes Startsignal `Starting in 1 second.` |
| 14:44:14.621 | Erste Truhenevidenz bei `-120,63,-116`, Catwalk, Zelle `2,2`; die Karte wird unmittelbar markiert. |

Die Catwalk-Erkennung liegt 21,370 Sekunden nach dem Startsignal und 26,300
Sekunden nach der frühen Chunk-Beobachtung. Bis dahin meldet Kung keine gültigen
Kandidaten; es gibt keine konkurrierenden Räume, die die Karte blockieren.
Der HUD-Waypoint bleibt außerhalb desselben Raums absichtlich unsichtbar
(`sameRoom=0`), während die Karte den erkannten Raum markieren kann.

Der alte Trace protokolliert weder den Eingang des Truhenblocks noch den
Erkennungsweg. Ein serverseitig später gesetzter Block und eine verspätete
Scanner-Entdeckung lassen sich deshalb nicht sicher trennen. Insbesondere war
das alte Feld `blockEntities` nur die Zahl gefilterter Kandidaten, kein Zähler
geprüfter Block Entities.

Der schnelle Scan berücksichtigt jetzt auch noch nicht initialisierte Block
Entities; vorher mussten solche Truhen gegebenenfalls auf den vollständigen
Block-Fallback warten. Das beseitigt diese mögliche Verzögerung, beweist aber
nicht ihre Ursache im Catwalk-Run. `mimic-discovery` erfasst neue rohe
Trapped-Chest-Funde vor dem Raumfilter, mit `source` (`block-entity`,
`pending-block-entity` oder `block-state-fallback`), aktuellem Dungeon-Tick,
erster Chunk-Beobachtung und letzter Index-/Vollprüfung derselben Chunk-Instanz.
Die Tick-Werte sind Scanner-Beobachtungen, keine Paket-Eingangszeiten.
`indexedPositionsThisTick` zählt jetzt die tatsächlich geprüften Indexeinträge.
Bei erneut später Anzeige unmittelbar `/kung log save` verwenden; die tatsächliche
Verbesserung und der verbleibende Server-/Scan-Anteil sind noch live zu prüfen.

Validierung: bestehende Tests für Mimic-Persistenz, feste Truhen und
Extra-Score-Meldungen erfolgreich; vollständiger Java-25-Build mit **315 Tests**,
keinen Fehlern oder übersprungenen Tests. Die Index-API wurde zusätzlich an
Minecraft 26.1.2 geprüft. Die Tests simulieren keinen verspäteten Chunk-/Truheneingang;
die schnellere Erkennung steht damit noch unter Live-Prüfung.

### Trace vom 13.09.2026, 01:56:28

`kung-trace-20260913-015628.log` belegt zwei Kandidaten: zuerst
`-135,69,-150` in **Redstone_Key** (Zelle `2,1`), danach `-48,78,-14` in
**Mines** (Zelle `4,5` im erkannten 2×2-Raum). Ab 01:55:28.890 wird die
Kartenmarkierung deshalb als `ambiguous-candidate-rooms` entfernt. Der
2D-Waypoint in Mines bleibt aktiv. Dass der andere Kandidat später nicht
mehr geladen ist, darf dessen gespeicherte Raumevidenz nicht löschen.

Damals fehlte für Redstone Key eine genaue Static-Chest-Aufnahme. Aus den
Weltkoordinaten allein entsteht keine rotationssichere Vorlage. Die oben genannte
Aufnahme vom 18.09.2026 ergänzt jetzt diesen Ausschluss. Ein pauschaler Ausschluss
des gesamten Raums würde auch einen zusätzlichen echten Mimic dort verbergen.

Um 01:56:12.693 trifft `Party > [MVP+] starziiiii: Mimic Killed!` ein;
danach zeigt die Score-Berechnung `mimic=true`. Eine Party-Meldung erzeugt keine
Rückmeldung an die Party. Die später ausgelesene Profilkonfiguration hat
`extraScoreMessagesEnabled=false`; das ist kein gespeicherter Einstellungsstand
vom Kill-Zeitpunkt. Ein eigener Todes-Paketnachweis vor der Party-Meldung ist
im alten Trace nicht dokumentiert. Ein eigener Erkennungsfehler ist damit nicht
bewiesen. Die Entfernung des 3D-Renderers änderte weder Kandidatenfilter noch
Kill-Erkennung.

Die ergänzte Diagnostik schreibt bei Änderungen alle Kandidaten mit Position,
Raum und Ladezustand nach `mimic-candidates`. `mimic-kill` erfasst Baby-Zombie-
Todespakete mit Klassifikation sowie Kill-Quelle, erste/weitere Evidenz und die
Nachrichtenschalter zum Ereigniszeitpunkt. Nach dem nächsten eigenen Kill direkt
`/kung log save` ausführen; kein Debug-Chat-Schalter ist nötig. Ein Kill wird
weiterhin nur aufgrund der bisherigen Evidenz erkannt, nicht aufgrund bloßen
Verschwindens. Für gewünschte Meldungen vorher **Extra Score Messages > Mimic**
einschalten. Die neue Regression prüft unterdrückte eigene Meldungen sowie eine
Party-Meldung vor nachfolgender eigener Evidenz, ohne doppelt zu senden.

### Frühere Static-Chest-Validierung (0.2.15)

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
