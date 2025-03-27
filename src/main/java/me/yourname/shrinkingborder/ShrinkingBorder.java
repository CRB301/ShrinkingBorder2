package me.yourname.shrinkingborder;

import org.bukkit.*;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class ShrinkingBorder extends JavaPlugin implements Listener {
    private Location borderCenter;
    private double initialSize, finalSize, shrinkPerStep;
    private long intervalTicks, graceTicks;
    private boolean broadcast, teleport, isShrinking, isPaused;
    private String shrinkSound;
    private BossBar bossBar;
    private World world; // Configurable world

    private File dataFile;
    private FileConfiguration dataConfig;
    private long ticksUntilNextShrink;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupData();
        loadSettings();
        applyBorderSettings();
        getServer().getPluginManager().registerEvents(this, this);
        if (isShrinking && !isPaused) {
            startShrinking();
        }
    }

    @Override
    public void onDisable() {
        if (bossBar != null) bossBar.removeAll();
        saveDataAsync();
    }

    private void setupData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            saveResource("data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        isShrinking = dataConfig.getBoolean("shrinking", false);
        isPaused = dataConfig.getBoolean("paused", false);
        // Default to config's interval if not set
        long defaultInterval = getConfig().getLong("border.shrinkIntervalTicks", 100L);
        ticksUntilNextShrink = dataConfig.getLong("ticksUntilNextShrink", defaultInterval);
    }

    private void saveData() {
        dataConfig.set("shrinking", isShrinking);
        dataConfig.set("paused", isPaused);
        dataConfig.set("ticksUntilNextShrink", ticksUntilNextShrink);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Error saving data.yml", e);
        }
    }
    
    // Save data asynchronously to reduce main thread lag.
    private void saveDataAsync() {
        getServer().getScheduler().runTaskAsynchronously(this, this::saveData);
    }

    private void loadSettings() {
        FileConfiguration config = getConfig();
        String worldName = config.getString("worldName", Bukkit.getWorlds().get(0).getName());
        world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
            getLogger().warning("World " + worldName + " not found. Defaulting to " + world.getName());
        }
        borderCenter = new Location(world,
                config.getDouble("border.centerX"),
                64,
                config.getDouble("border.centerZ"));
        initialSize = config.getDouble("border.initialSize");
        finalSize = config.getDouble("border.finalSize");
        shrinkPerStep = config.getDouble("border.shrinkAmountPerStep");
        intervalTicks = config.getLong("border.shrinkIntervalTicks");
        graceTicks = config.getLong("border.gracePeriodTicks");
        broadcast = config.getBoolean("messages.enabled");
        teleport = config.getBoolean("teleportOutsidePlayersInside");
        shrinkSound = config.getString("sounds.shrinkSound");
    }

    private void applyBorderSettings() {
        WorldBorder border = world.getWorldBorder();
        border.setCenter(borderCenter);
        border.setSize(initialSize);
    }

    private void startShrinking() {
        if (bossBar != null) bossBar.removeAll();
        WorldBorder border = world.getWorldBorder();
        bossBar = Bukkit.createBossBar("World border shrinking in ...", BarColor.RED, BarStyle.SEGMENTED_10);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(p);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isShrinking || isPaused) {
                    cancel();
                    return;
                }
                if (ticksUntilNextShrink <= 0) {
                    double currentSize = border.getSize();
                    if (currentSize <= finalSize) {
                        border.setSize(finalSize);
                        if (bossBar != null) bossBar.setVisible(false);
                        cancel();
                        return;
                    }
                    double newSize = Math.max(finalSize, currentSize - shrinkPerStep);
                    border.setSize(newSize);

                    if (broadcast) {
                        String msg = ChatColor.RED + "⚠ The world border is shrinking! New size: " + (int) newSize + " blocks.";
                        Bukkit.broadcastMessage(msg);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (shrinkSound != null && !shrinkSound.isEmpty()) {
                                p.playSound(p.getLocation(), shrinkSound, 1.0f, 1.0f);
                            }
                        }
                    }
                    if (teleport) {
                        teleportOutsidePlayers(border);
                    }
                    ticksUntilNextShrink = intervalTicks;
                } else {
                    ticksUntilNextShrink--;
                    updateBossBar();
                }
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    private void updateBossBar() {
        if (bossBar == null) return;
        double progress = (double) ticksUntilNextShrink / intervalTicks;
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
        bossBar.setTitle(ChatColor.RED + "World border shrinking in " + formatTime(ticksUntilNextShrink / 20));
    }

    private String formatTime(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private void teleportOutsidePlayers(WorldBorder border) {
        double half = border.getSize() / 2.0;
        for (Player p : world.getPlayers()) {
            Location loc = p.getLocation();
            double x = loc.getX(), z = loc.getZ();
            if (Math.abs(x - borderCenter.getX()) > half || Math.abs(z - borderCenter.getZ()) > half) {
                double clampedX = Math.max(borderCenter.getX() - half + 1, Math.min(borderCenter.getX() + half - 1, x));
                double clampedZ = Math.max(borderCenter.getZ() - half + 1, Math.min(borderCenter.getZ() + half - 1, z));
                Location safe = findSafeLocation(loc.getWorld(), clampedX, loc.getY(), clampedZ);
                p.teleport(safe);
                p.sendMessage(ChatColor.YELLOW + "⚠ You were outside the border and teleported to safety.");
            }
        }
    }

    private Location findSafeLocation(World world, double x, double y, double z) {
        int searchRadius = 5;
        for (int i = -searchRadius; i <= searchRadius; i++) {
            int checkY = (int) y + i;
            if (checkY < 1 || checkY > world.getMaxHeight() - 2) continue;
            Location loc = new Location(world, x, checkY, z);
            if (loc.getBlock().isPassable() && loc.clone().add(0, 1, 0).getBlock().isPassable()
                    && !loc.clone().subtract(0, 1, 0).getBlock().isPassable()) {
                return loc;
            }
        }
        int surfaceY = world.getHighestBlockYAt((int) x, (int) z);
        return new Location(world, x, surfaceY + 1, z);
    }

    // Add the boss bar to any new player who joins.
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (bossBar != null) {
            bossBar.addPlayer(event.getPlayer());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Ensure only players or console with the proper permission can execute these commands.
        if (!(sender instanceof Player || sender instanceof ConsoleCommandSender)) return false;
        if (!sender.hasPermission("shrinkingborder.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) return false;

        switch (args[0].toLowerCase()) {
            case "start":
                if (!isShrinking) {
                    isShrinking = true;
                    isPaused = false;
                    ticksUntilNextShrink = graceTicks;
                    Bukkit.broadcastMessage(ChatColor.GOLD + "Shrinking will begin in " + formatTime(graceTicks / 20));
                    startShrinking();
                }
                return true;
            case "pause":
                isPaused = true;
                sender.sendMessage(ChatColor.YELLOW + "Shrinking paused.");
                return true;
            case "resume":
                if (isShrinking) {
                    isPaused = false;
                    sender.sendMessage(ChatColor.GREEN + "Shrinking resumed.");
                    startShrinking();
                }
                return true;
            case "stop":
                isShrinking = false;
                isPaused = false;
                sender.sendMessage(ChatColor.RED + "Shrinking stopped.");
                return true;
            case "status":
                sender.sendMessage(ChatColor.AQUA + "Shrinking: " + isShrinking + ", Paused: " + isPaused +
                        ", Time until next: " + formatTime(ticksUntilNextShrink / 20));
                return true;
            case "help":
                sender.sendMessage(ChatColor.GREEN + "ShrinkingBorder Commands:");
                sender.sendMessage(ChatColor.YELLOW + "/shrinkborder start" + ChatColor.GRAY + " – Start border shrinking");
                sender.sendMessage(ChatColor.YELLOW + "/shrinkborder pause" + ChatColor.GRAY + " – Pause shrinking");
                sender.sendMessage(ChatColor.YELLOW + "/shrinkborder resume" + ChatColor.GRAY + " – Resume shrinking");
                sender.sendMessage(ChatColor.YELLOW + "/shrinkborder stop" + ChatColor.GRAY + " – Stop and reset");
                sender.sendMessage(ChatColor.YELLOW + "/shrinkborder set <option> <value...>" + ChatColor.GRAY + " – Change config");
                sender.sendMessage(ChatColor.YELLOW + "/shrinkborder status" + ChatColor.GRAY + " – Show plugin status");
                return true;
            case "set":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /shrinkborder set <option> <value>");
                    return true;
                }
                handleSetCommand(sender, args);
                return true;
        }
        return false;
    }

    private void handleSetCommand(CommandSender sender, String[] args) {
        FileConfiguration config = getConfig();
        try {
            switch (args[1].toLowerCase()) {
                case "center":
                    if (args.length < 4) {
                        sender.sendMessage(ChatColor.RED + "Usage: /shrinkborder set center <x> <z>");
                        return;
                    }
                    borderCenter.setX(Double.parseDouble(args[2]));
                    borderCenter.setZ(Double.parseDouble(args[3]));
                    config.set("border.centerX", borderCenter.getX());
                    config.set("border.centerZ", borderCenter.getZ());
                    break;
                case "size":
                    if (args.length < 4) {
                        sender.sendMessage(ChatColor.RED + "Usage: /shrinkborder set size <initialSize> <finalSize>");
                        return;
                    }
                    initialSize = Double.parseDouble(args[2]);
                    finalSize = Double.parseDouble(args[3]);
                    config.set("border.initialSize", initialSize);
                    config.set("border.finalSize", finalSize);
                    break;
                case "interval":
                    intervalTicks = Long.parseLong(args[2]);
                    config.set("border.shrinkIntervalTicks", intervalTicks);
                    break;
                case "amount":
                    shrinkPerStep = Double.parseDouble(args[2]);
                    config.set("border.shrinkAmountPerStep", shrinkPerStep);
                    break;
                case "grace":
                    graceTicks = Long.parseLong(args[2]);
                    config.set("border.gracePeriodTicks", graceTicks);
                    break;
                case "broadcast":
                    broadcast = Boolean.parseBoolean(args[2]);
                    config.set("messages.enabled", broadcast);
                    break;
                case "teleport":
                    teleport = Boolean.parseBoolean(args[2]);
                    config.set("teleportOutsidePlayersInside", teleport);
                    break;
                case "sound":
                    shrinkSound = args[2];
                    config.set("sounds.shrinkSound", shrinkSound);
                    break;
                default:
                    sender.sendMessage(ChatColor.RED + "Unknown setting.");
                    return;
            }
            saveConfig();
            sender.sendMessage(ChatColor.GREEN + "Setting updated.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Invalid usage or value.");
        }
    }
}
