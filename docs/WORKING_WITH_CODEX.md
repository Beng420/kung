# Mit Codex am Kung-Projekt arbeiten

Für neue Arbeit reicht ein konkretes Ziel mit den nötigen Beobachtungen. Die
[AGENTS.md](../AGENTS.md) enthält dauerhafte Projektregeln; der kurze
[aktuelle Stand](AI_HANDOFF.md) und die [Code-Übersicht](CODE_MAP.md) führen zu den
passenden Dateien. Du musst dafür nicht die gesamte bisherige Unterhaltung kopieren.

## Welche Aufgabe weiterverwenden?

- **Derselbe Fehler, neuer Log oder Testergebnis:** In der bisherigen Aufgabe
  antworten. Nenne, welche JAR du tatsächlich getestet hast und was noch schiefläuft.
- **Ein neues, eigenständiges Thema:** Eine neue Aufgabe im Kung-Projekt beginnen.
  Beispielsweise Custom Sounds getrennt von der Raumdatenbank bearbeiten.
- **Eine alte Aufgabe mit vielen vermischten Themen:** Eine kurze Übergabe erzeugen
  lassen und mit dem konkreten verbleibenden Problem eine neue Aufgabe beginnen.
  Es gibt keine feste Anzahl Nachrichten, ab der ein Wechsel nötig ist.

Eine Aufgabe pro zusammenhängendem Ergebnis hält den Kontext überschaubar. Das
entspricht auch den [offiziellen Codex-Empfehlungen](https://learn.chatgpt.com/guides/best-practices).
Kurze aktuelle Dokumentation kann Suchaufwand sparen; die tatsächliche
Token-Ersparnis hängt von der jeweiligen Aufgabe ab und ist hier nicht gemessen.

## Frühere Aufgaben wieder aufnehmen

1. **Aktuellen Wunsch nennen.** Was davon ist noch offen, was wurde inzwischen
   anderweitig behoben? Frühere Pläne sind keine zuverlässige Liste fehlender Features.
2. **Heutigen Projektstand prüfen lassen.** Die Aufgabe soll aktuelle Dateien,
   lokale Änderungen und die passende Feature-Doku lesen, bevor sie alte Vorschläge
   umsetzt. Alte Build-Zahlen und Chat-Zusammenfassungen können überholt sein.
3. **Neue Beobachtung hinzufügen.** Verhalten, erwartetes Ergebnis, Version,
   Floor/Raum und Logpfad. Eine konkrete Uhrzeit hilft bei langen Traces.
4. **Ein Ergebnis abschließen lassen.** Fix, passende Prüfung und verbleibenden
   Live-Test festhalten. Erkenntnisse gehören in die bestehende Feature-Doku;
   der zentrale Handoff bekommt nur den aktuellen Stand.

Wenn eine frühere Aufgabe noch Code ändert, diese Arbeit zuerst abschließen oder
anhalten, bevor eine zweite Aufgabe dieselben Dateien bearbeitet. Eine neue Aufgabe
enthält nicht automatisch sämtliche Entscheidungen aus einer alten Unterhaltung;
wichtige Besonderheiten gehören in die Übergabe oder ins Repository.

Für eine alte Aufgabe kannst du schreiben:

```text
Wir setzen diese Aufgabe am aktuellen Kung-Projektstand fort. Lies AGENTS.md,
docs/AI_HANDOFF.md und über docs/CODE_MAP.md nur die relevanten Detaildateien.
Prüfe vorhandene Änderungen und ob frühere Vorschläge inzwischen umgesetzt oder
überholt sind. Erhalte andere lokale Arbeit.

Noch offen: <konkretes Problem>
Getestete JAR / Floor / Raum: <Angaben>
Beobachtung und erwartetes Verhalten: <kurz>
Logpfad und Zeitpunkt: <Pfad, Uhrzeit>

Behebe das verbleibende Problem, prüfe es angemessen und aktualisiere die betroffene
Feature-Doku. Berichte kurz, was bestätigt ist und was ich noch live testen muss.
```

## Übergabe aus einer langen Aufgabe

Diese Nachricht genügt, wenn du das Thema in einer neuen Aufgabe fortsetzen willst:

```text
Erstelle eine kurze Übergabe für das noch offene Thema <Thema>, höchstens 300 Wörter:
Ziel, tatsächlich umgesetzter Stand, relevante Dateien, wichtige Entscheidungen,
offene Fehler, letzte Prüfungen und genaue Logpfade. Trenne bestätigte Befunde von
Vermutungen. Verlinke vorhandene Feature-Doku statt den ganzen Verlauf zu wiederholen.
Nimm dafür keine weiteren Codeänderungen vor.
```

Die Übergabe zusammen mit dem neuen Auftrag in die neue Aufgabe kopieren. Eine
frühere Unterhaltung muss dafür nicht gelöscht werden. Nicht jeden Chat in den
zentralen Handoff schreiben; das würde den Einstieg wieder aufblähen.

## Gute Fehlerberichte für Dungeon-Features

Nenne möglichst die installierte Kung-Version, F/M-Floor, Run-Phase und den
Raumnamen. Beschreibe einen beobachtbaren Unterschied: „Buttons: feste Truhe wird
nach Rotation wieder markiert“, statt nur „Mimic buggy“. Bei falschen Raumdaten
zusätzlich `/kung roomdata` verwenden und keine Namen anhand ähnlicher Optik raten.

Nach dem Fehler `/kung log save` ausführen und den ausgegebenen Pfad mitteilen.
Lokale Pfade reichen hier zur Auswertung. Screenshots helfen bei Darstellung und Position, Traces bei Zeitpunkt
und Zustandswechseln. Ein kurzer Auftrag mit diesen Angaben ist meist hilfreicher
als ein langer Rückblick auf alle bisherigen Dungeon-Probleme.
