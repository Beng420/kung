# Entwicklung

## Struktur

- `src/shared/java`: gemeinsamer Mod-Code fuer alle Versionen
- `versions/mc1_21_11`: Build und Ressourcen fuer Minecraft `1.21.11`
- `versions/mc26_1_2`: Build und Ressourcen fuer Minecraft `26.1.2`

## Namensschema

- Mod ID: `kung`
- Java Package: `com.github.beng420.kung`
- Maven Group: `com.github.beng420`

## Erste sinnvolle Schritte

1. Konfigurationssystem ergaenzen.
2. SkyBlock-Erkennung ueber Serveradresse, Scoreboard und Tablist kapseln.
3. Ein kleines Overlay bauen.
4. Feature-Toggles im `FeatureRegistry` verdrahten.
5. Alles, was Hypixel beeinflusst, bewusst clientseitig und passiv halten.
