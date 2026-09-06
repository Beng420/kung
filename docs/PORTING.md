# Porting auf Minecraft 26

Minecraft `26.1` und neuer sind im Fabric-Umfeld anders aufgebaut als `1.21.11` und aelter. Aktive Arbeit laeuft derzeit nur auf `26.1.2`; `26.2` ist geparkt, bis es explizit wieder angefordert wird.

Wichtig fuer dieses Projekt:

- `versions/mc1_21_11` bleibt nur als alte Referenz erhalten.
- `versions/mc26_1_2` und `versions/mc26_2` nutzen `net.fabricmc.fabric-loom`, aber nur `mc26_1_2` ist aktuelles Entwicklungsziel.
- In den 26er Modulen gibt es keine Yarn/Intermediary-Mappings-Zeile.
- Dependencies in den 26er Modulen nutzen die normalen Gradle-Konfigurationen wie `implementation`.
- Java `25` ist fuer den Port eingeplant.
- Beide 26er Module sind im Gradle-Projekt eingebunden, aber neue Fixes sollen nicht automatisch nach `mc26_2` portiert werden.

Halte Minecraft-spezifische Klassen moeglichst klein und versioniert. Logik, Parsing und Feature-Zustand koennen spaeter in ein eigenes Common-Modul wandern, wenn die beiden 26er Versionen wieder parallel gepflegt werden.
