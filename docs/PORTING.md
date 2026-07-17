# Porting auf Minecraft 26.1.2

Minecraft `26.1` und neuer sind im Fabric-Umfeld anders aufgebaut als `1.21.11` und aelter.

Wichtig fuer dieses Projekt:

- `versions/mc1_21_11` nutzt `net.fabricmc.fabric-loom-remap`.
- `versions/mc26_1_2` nutzt `net.fabricmc.fabric-loom`.
- Im `26.1.2`-Modul gibt es keine Yarn/Intermediary-Mappings-Zeile.
- Dependencies im `26.1.2`-Modul nutzen die normalen Gradle-Konfigurationen wie `implementation`.
- Java `25` ist fuer den Port eingeplant.
- Das `26.1.2`-Modul wird nur eingebunden, wenn Java 25 laeuft oder wenn du explizit mit `-Pinclude26_1_2=true` baust.

Halte Minecraft-spezifische Klassen moeglichst klein und versioniert. Logik, Parsing und Feature-Zustand gehoeren nach `src/shared/java`, solange sie keine instabilen Minecraft-Internals importieren.
