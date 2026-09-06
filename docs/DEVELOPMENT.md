# Entwicklung

## Struktur

- `versions/mc1_21_11/src/main/java`: eingefrorener alter Mod-Code fuer Minecraft `1.21.11`
- `versions/mc26_1_2`: aktiver Build und Ressourcen fuer Minecraft `26.1.2`
- `versions/mc26_2`: geparkte Referenz fuer Minecraft `26.2`; nur anfassen, wenn explizit verlangt
- `versions/mc1_21_11`: eingefrorene Referenz, kein aktives Entwicklungsziel

Neue Chats und Maintainer sollten zuerst `docs/AI_HANDOFF.md` lesen. Dort stehen der aktuelle Arbeitskontext, die Dungeon-Map-Regeln, Player-Rendering-Details, Logger-Kategorien, Score/Footer-Semantik und sichere Refactor-Ziele.

## Namensschema

- Mod ID: `kung`
- Java Package: `com.github.beng420.kung`
- Maven Group: `com.github.beng420`

## Erste sinnvolle Schritte

1. `docs/AI_HANDOFF.md` lesen.
2. Aenderung im Modul `versions/mc26_1_2` machen.
3. Wenn Dungeon-Map, Player-Rendering, Score/Footer oder Instance-Erkennung betroffen sind, passende Trace-Kategorie pruefen oder erweitern.
4. Mit `./gradlew.bat :versions:mc26_1_2:build` kompilieren.
5. Alles, was Hypixel beeinflusst, bewusst clientseitig und passiv halten.

## Aktuelle Wartungsregeln

- Neue Arbeit standardmaessig nur in `mc26_1_2` machen.
- `mc26_2` nur anfassen oder synchronisieren, wenn es explizit verlangt wird.
- `mc1_21_11` nur anfassen, wenn es explizit verlangt wird.
- Layouts in `KungConfigScreen`, `KungHudEditorScreen` und den HUD-Overlays nicht bei Cleanup-Arbeiten veraendern.
- Vor Datenfixes bei Dungeon-Raeumen erst `Dungeon Map > Room Debug` oder `/kung room debug` verwenden.
- Nach Code-Aenderungen mindestens den `mc26_1_2`-Build pruefen.
