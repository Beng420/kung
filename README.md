# Kung

Fabric-Clientmod für passive Hypixel-SkyBlock-Hilfen: Dungeon- und Bosskarten,
Blood Rush, Splits, Spielerstatistiken, Mimic-Hinweise, Superpairs und anpassbare Sounds.

Owner: [Beng420](https://github.com/Beng420)

## Entwicklung

Aktives Ziel ist **Minecraft 26.1.2 mit Java 25** unter `versions/mc26_1_2`.
`mc26_2` ist geparkt, `mc1_21_11` bleibt historische Referenz.

- [Projektregeln für Codex und Mitwirkende](AGENTS.md)
- [Aktueller Stand und offene Live-Prüfungen](docs/AI_HANDOFF.md)
- [Feature → Code → Tests](docs/CODE_MAP.md)
- [Build, Diagnose und Dokumentationspflege](docs/DEVELOPMENT.md)
- [Neue und frühere Codex-Aufgaben bearbeiten](docs/WORKING_WITH_CODEX.md)

Build aus dem Projektverzeichnis:

```powershell
./gradlew.bat :versions:mc26_1_2:build --console=plain
```

Die Mod bleibt clientseitig und passiv. Feature-Hauptschalter sind bei einer frischen
Installation aus; HUD-Position und Skalierung werden über `/kung hud` bearbeitet.
