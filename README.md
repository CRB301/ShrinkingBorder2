# ShrinkingBorder Plugin

This is a custom Minecraft Bukkit/Spigot plugin that gradually shrinks the world border during gameplay. It's ideal for UHC or battle royale-style mini-games.

## 📦 Features
- Configurable initial/final border size and shrinking speed
- BossBar countdown timer for shrink warnings
- Broadcast messages and sounds when shrinking occurs
- Optional teleportation for players outside the border
- Admin commands to control the shrink process

## 🛠 Requirements
- Java 8 or higher
- Spigot or Paper server (1.13+)
- Maven (for compilation)

## 🚀 How to Build
1. Extract this zip file.
2. Open a terminal and navigate to the project root folder.
3. Run:

```
mvn clean package
```

4. Your compiled plugin JAR will be located in the `target/` folder.

## 🔧 Configuration
Edit `config.yml` to adjust:
- World name
- Border size and center
- Shrink interval and amount
- Messages, sounds, and teleport settings

## ✅ Commands
Use `/shrinkborder` with the following subcommands:
- `start` – Begin shrinking
- `pause` – Temporarily pause shrinking
- `resume` – Resume if paused
- `stop` – Stop and reset shrinking
- `status` – Show current status
- `set <option> <value>` – Change config values on the fly

## 🔐 Permissions
- `shrinkingborder.admin`: Required to use commands (default: OPs only)

## 📂 Notes
- Default config uses the first world on the server. You can change the world name in `config.yml`.

Happy shrinking!
