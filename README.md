# Kung

Fabric client mod scaffold for Hypixel SkyBlock quality-of-life features.

Owner: [github.com/Beng420](https://github.com/Beng420)

## Ziel

Dieses Projekt ist fuer Minecraft `1.21.11` vorbereitet und enthaelt bereits ein separates Modul fuer den schnellen Port auf `26.1.2`.

Der Aufbau ist absichtlich versionsfreundlich:

- `src/shared/java` enthaelt Code, der fuer beide Minecraft-Versionen gleich bleiben soll.
- `versions/mc1_21_11` ist das aktuelle Startmodul.
- `versions/mc26_1_2` ist der vorbereitete Port, weil Fabric ab Minecraft `26.1` ein anderes Loom-Setup nutzt.

## Entwickeln

Empfohlen:

1. Projekt in IntelliJ IDEA als Gradle-Projekt oeffnen.
2. Fuer `1.21.11` bauen:

```powershell
./gradlew.bat :versions:mc1_21_11:build
```

3. Spaeter mit Java 25 fuer `26.1.2` bauen:

```powershell
./gradlew.bat -Pinclude26_1_2=true :versions:mc26_1_2:build
```

Hinweis: Auf dieser Maschine ist aktuell Java 24 installiert. Fuer den `26.1.2`-Port brauchst du Java 25.

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
