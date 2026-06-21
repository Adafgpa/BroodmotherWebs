package com.udd108.broodmotherwebs;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BroodmotherWebs extends JavaPlugin implements Listener, CommandExecutor {

    private final Set<String> protectedBlocks = new HashSet<>();
    private File configFile;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        configFile = new File(getDataFolder(), "protected-blocks.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create protected-blocks.yml file!");
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        loadProtectedBlocks();

        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("bmwebs").setExecutor(this);

        getLogger().info("BroodmotherWebs enabled! Loaded " + protectedBlocks.size() + " total protected map entries.");
    }

    private void loadProtectedBlocks() {
        protectedBlocks.clear();
        List<String> loaded = config.getStringList("protected-keys");
        if (loaded != null) {
            protectedBlocks.addAll(loaded);
        }
    }

    private void saveProtectedBlocks() {
        config.set("protected-keys", List.copyOf(protectedBlocks));
        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Could not save coordinate map to protected-blocks.yml!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPunchBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Material material = block.getType();
        
        // Form unified identifier format: "MATERIAL_NAME:X,Y,Z"
        String blockKey = block.getWorld().getName() + ":" + material.name() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();

        // If the database explicitly recognizes this static block at this spot -> PROTECT IT
        if (protectedBlocks.contains(blockKey)) {
            return;
        }

        // --- SPECIFIC MECHANIC BYPASS RULES ---
        // Rule 1: COBWEB Bypasses (Broodmother Cocoon Attack)
        if (material == Material.COBWEB) {
            event.setCancelled(true);
            block.setType(Material.AIR);
        }
        
        // You can easily chain more dynamic custom blocks down here in the future:
        // else if (material == Material.IRON_BARS) { ... }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only in-game players can run this scanner command.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("broodmotherwebs.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to run this command.");
            return true;
        }

        // Command format: /bmwebs scan <radius> <material>
        if (args.length >= 3 && args[0].equalsIgnoreCase("scan")) {
            int radius;
            try {
                radius = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Please specify a valid radius number.");
                return true;
            }

            String targetMaterialInput = args[2].toUpperCase();
            Material targetMaterial = Material.getMaterial(targetMaterialInput);
            if (targetMaterial == null) {
                player.sendMessage(ChatColor.RED + "Invalid Minecraft material: '" + targetMaterialInput + "'. Check your spelling!");
                return true;
            }

            player.sendMessage(ChatColor.YELLOW + "Scanning for static " + targetMaterial.name() + " within a " + radius + " block radius...");
            
            Location origin = player.getLocation();
            World world = origin.getWorld();
            int foundCount = 0;

            // Clear old entries belonging to THIS material type so you can overwrite it cleanly
            protectedBlocks.removeIf(key -> key.startsWith(targetMaterial.name() + ":"));

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block b = world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
                        if (b.getType() == targetMaterial) {
                            String key = targetMaterial.name() + ":" + b.getX() + "," + b.getY() + "," + b.getZ();
                            protectedBlocks.add(key);
                            foundCount++;
                        }
                    }
                }
            }

            saveProtectedBlocks();
            player.sendMessage(ChatColor.GREEN + "Success! Added " + foundCount + " static " + targetMaterial.name() + " blocks to the protection system.");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "BroodmotherWebs Generalized Commands:");
        player.sendMessage(ChatColor.YELLOW + "/bmwebs scan <radius> <material> " + ChatColor.WHITE + "- Maps specific blocks to safety list (e.g. /bmwebs scan 50 COBWEB).");
        return true;
    }
}
