# Graffiti Mod

A **NeoForge** mod for Minecraft **1.21.1** that lets players paint pixels on any block surface using a spray can with full color customization, clean graffiti with brushes, and watch it age over time.

## Features

### Spray Can
- **Right-click** a block face to paint — smooth, per-frame painting with no cooldown
- **12800 durability** — each unique pixel costs 1 point. Empty can stays in inventory
- **Refill** — craft the can with **Red Dye**, **Blue Dye**, or **Green Dye** to restore durability while preserving color, lock, size, and shape
- **Color lock** (survival) — color can only be set once per can. Creative mode bypasses this
- **5 brush shapes**: Square, Circle, Rounded, Cloud, Leaky
- **Brush overflow** — painting near the edge of a face continues onto adjacent blocks
- **4 tool modes**: Pencil (draw), Fill (flood-fill), Picker (pick color), Select (pick two corners for gallery)
- **Configurable spray reach** — defaults to 8 blocks, server-enforced cap at 20 (set in `graffiti.json`)

### Color Editor (press **C**)
- HSB palette with hue/alpha sliders and HEX input
- Brush size (± buttons, 1–8)
- Brush shape cycling button
- All settings saved per-can via NBT — persists when dropped or shared

### Brushes (Cleaning)

| Item | Obtaining | Cleaning speed |
|------|-----------|----------------|
| **Brush** | Craft: Iron Ingot + Sponge + Blue Wool | 16 strokes per pixel |
| **Wet Brush** | Drop Brush in water for 2s, or craft with Water Bucket | 5 strokes per pixel |

- **Circular 6×6 area** cleaning via right-click
- Smelt Wet Brush in a furnace to dry it back
- Custom textures

### Graffiti Aging
- Every **5 in-game days**, all graffiti loses a random **2–15%** of its current saturation
- Saturation never drops below **85%** of the original color
- Pixels gradually fade over time for a natural weathered look

### Clipboard
- Breaking a block with graffiti saves it to your clipboard
- Placing the **same block type** pastes the graffiti back
- Graffiti follows block types, not FIFO order

### Gallery
- Press **G** while holding the spray can to open the gallery
- **Save** a design: switch to **Select** tool mode (cycle via C menu), right-click two blocks to define a region (red wireframe appears), then press G → type a name → Save
- **Paste** a design: select it in the gallery → click Paste → preview appears at the targeted block → scroll or PgUp/PgDn to adjust Y offset → right-click to confirm placement
- Designs **auto-rotate** to match your facing direction when pasted
- **Durability cost**: 1 per pixel when saving or pasting (deducted from the can)
- Per-world persistence in `gallery.bin` — synced to players on login

### Sound System
- **Spray painting** — looping spatial `spray_can_paint.ogg` at the block position (nearby players hear it)
- **Equip** — `spray_can_equip.ogg` plays when selecting the spray can
- **Brushing** — vanilla brush sound, spatial at the block position
- **Water conversion** — "pling" sound when brush turns wet
- All sounds are 3D with distance attenuation

### Multiplayer & Persistence
- Graffiti syncs across all players in real-time
- Saved to disk (`graffiti.bin` world data) on server stop
- Client cache persists across sessions in single-player
- All spray can settings (color, size, shape, lock) stored in NBT — survive drops and dimension changes

### Depth-aware Rendering
- Paint follows stairs, slabs, and complex block shapes
- Greedy meshing merges same-color quads for performance
- Frustum culling and configurable render distance

### Translations
- English and Romanian

## Dependencies

- [NeoForge](https://neoforged.net/) >= 21.1.1
- Minecraft 1.21.1
- [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) (optional — shows recipes and usage info)

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
| `sprayReach` | Max distance to paint/clean from (1–20 blocks, default 8) |

## Controls

| Action | Input |
|--------|-------|
| Paint pixel | Right-click on a block face (spray can) |
| Continuous spray | Hold right-click (spray can) |
| Open color editor | **C** key (while holding spray can) |
| Open gallery | **G** key (while holding spray can) |
| Set selection corner | Right-click in Select mode (spray can) |
| Undo / Redo | **Z** / **Y** (while holding spray can, looking at the face) |
| Switch tool | Cycle via C menu (Pencil / Fill / Picker / Select) |
| Adjust preview Y offset | Scroll or **PgUp** / **PgDn** (gallery preview mode) |
| Confirm paste | Right-click (gallery preview mode) |
| Cancel preview | **Escape** or **G** (gallery preview mode) |
| Cleaning | Right-click with Brush or Wet Brush |
| Wet brush in water | Drop brush in water for 2 seconds |

> **JEI** shows all crafting recipes and usage info for the spray can, brush, and wet brush.

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
