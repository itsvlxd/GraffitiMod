# Graffiti Mod

A **NeoForge** mod for Minecraft **1.21.1** that lets players paint pixels on any block surface using a spray can with full color customization.

## Features

- **Spray Can item** — right-click to open the color editor, left-click to paint
- **Full color picker** — HSB palette, hue/alpha sliders, HEX input, brush size control
- **4 tools** — Pencil, Eraser, Fill, Color Picker (switch with Ctrl + A/D)
- **HUD tool selector** — smooth fade-in/out overlay when holding Ctrl + spray can
- **Adjustable brush size** — paint multiple pixels at once
- **Depth-aware rendering** — pixels follow stairs, slabs, and complex block shapes
- **Greedy meshing** — optimized rendering with frustum culling
- **Multiplayer support** — graffiti syncs across all players, persists on disk
- **Translations** — English and Romanian

## Dependencies

- [NeoForge](https://neoforged.net/) >= 21.1.1
- Minecraft 1.21.1

## Installation

1. Install **NeoForge** for Minecraft 1.21.1
2. Download the mod JAR and place it in your `mods/` folder

## Building from source

### First-time setup

#### 1. Install Java 21

This mod **requires Java 21**. Check your current version:

```bash
java -version
```

If you don't have Java 21, download it from one of these sources:

- **Oracle JDK** — [https://www.oracle.com/java/technologies/downloads/#java21](https://www.oracle.com/java/technologies/downloads/#java21)

After installing, verify:

```bash
java -version   # should show version 21
```

#### 2. Clone the repository

```bash
git clone <repo-url>
cd GraffitiMod
```

#### 3. Generate the Gradle wrapper

If the `gradlew` script is missing, generate it:

```bash
gradle wrapper --gradle-version 8.12
```

This creates `gradlew`, `gradlew.bat`, and a `gradle/` directory.

#### 4. Build the mod

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew build
```

> **Note:** Replace the `JAVA_HOME` path with the path to your Java 21 installation.  
> On macOS with Homebrew: `JAVA_HOME=/opt/homebrew/opt/openjdk@21`  
> On Windows (PowerShell): `$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.x"` then run `.\gradlew build`

The compiled JAR will be at `build/libs/graffiti-1.0.0.jar`.

### Setting `JAVA_HOME` permanently (optional)

To avoid specifying `JAVA_HOME` every time, add it to your shell profile:

```bash
# Add to ~/.bashrc, ~/.zshrc, or equivalent
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
export PATH=$JAVA_HOME/bin:$PATH
```

Then reload with `source ~/.bashrc` and run:

```bash
./gradlew build
```

## Deploying on a server

### Steps

1. **Delete** the old Fabric JAR (`graffiti-0.04.jar` or any older version) from `mods/`
2. **Copy** the newly built JAR from `build/libs/graffiti-1.0.0.jar` to `mods/`
3. Restart the server

### Server config

After the first run, a config file is created at `config/graffiti.json`:

| Option | Description |
|--------|-------------|
| `enableGraffitiRendering` | Toggle on/off |
| `enableSmartCulling` | Hides off-screen graffiti for better FPS |
| `renderDistance` | How far graffiti is visible (4–128 blocks) |

## Controls

| Action | Input |
|--------|-------|
| Open color editor | Right-click with spray can |
| Paint pixel | Left-click on a block face |
| Switch tool | Hold Ctrl + A (left) / D (right) |
| Tools | Pencil, Eraser, Fill, Color Picker |

## Troubleshooting

### Build fails with "invalid source release"

You are not using Java 21. Set `JAVA_HOME` to your Java 21 installation (see [Building from source](#building-from-source)).

### Server crash: "ClassNotFoundException: net.minecraft.client.gui.screens.Screen"

An **old Fabric version** of the mod is still in your `mods/` folder. Delete it and replace with the new NeoForge JAR (see [Deploying on a server](#deploying-on-a-server)).

### Gradle wrapper missing

Run `gradle wrapper --gradle-version 8.12` in the project directory to generate it.

### Build succeeds but the mod doesn't appear in-game

- Make sure you are using NeoForge 21.1.1+ for Minecraft 1.21.1
- Check that the JAR is placed in the correct `mods/` folder (client: `.minecraft/mods/`, server: `<server>/mods/`)
- Check the latest log for any errors: `logs/latest.log`

## License

**GNU General Public License v3.0** — see [LICENSE](LICENSE).
