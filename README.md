# Kung

Fabric client mod scaffold for Hypixel SkyBlock quality-of-life features.

Owner: [github.com/Beng420](https://github.com/Beng420)

## Ziel

Dieses Projekt wird aktuell fuer Minecraft `26.1.2` entwickelt. Der alte `1.21.11`-Stand bleibt nur noch als eingefrorene Referenz im Repository; `26.2` ist geparkt und soll nur angefasst werden, wenn es explizit verlangt wird.

Der Aufbau ist absichtlich versionsfreundlich:

- `versions/mc26_1_2` ist das aktive Modul fuer Minecraft `26.1.2`.
- `versions/mc26_2` ist nur Referenz/parked.
- `versions/mc1_21_11` wird nicht mehr als Entwicklungsziel eingebunden.

## Entwickeln

Neue Maintainer und neue AI-Chats sollten zuerst diese Datei lesen:

- [`docs/AI_HANDOFF.md`](docs/AI_HANDOFF.md)

Empfohlen:

1. Projekt in IntelliJ IDEA als Gradle-Projekt oeffnen.
2. Fuer `26.1.2` bauen:

```powershell
./gradlew.bat :versions:mc26_1_2:build
```

Hinweis: Die 26er Module brauchen Java 25.

## Projektregeln fuer Hypixel

Dieses Template ist fuer clientseitige QoL- und Anzeige-Features gedacht. Vermeide alles, was Spielaktionen automatisiert, Eingaben simuliert, Serverpakete manipuliert oder Makros/Botting ermoeglicht.

Gute erste Features:

- SkyBlock-Kontext erkennen
- Overlay fuer Statuswerte
- Konfigurierbare Warnungen
- Chat-Parsing fuer rein informative Hinweise
- Debug-Panel fuer eigene Events

Nicht in dieses Projekt einbauen:

- Auto-Klicker
- Auto-Farming
- automatische Auktionen/Bazaar-Aktionen
- Movement- oder Combat-Automation
- versteckte Serverinteraktionen
