package com.udd108.broodmotherwebs;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BroodmotherWebs extends JavaPlugin implements Listener, CommandExecutor {

    // Structure: Map<DungeonBaseName, Map<MaterialName, Set<"X,Y,Z">>>
    private final Map<String, Map<String, Set<String>>> protectedBlocks = new HashMap<>();
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

        int totalBlocks = 0;
        for (Map<String, Set<String>> materials : protectedBlocks.values()) {
            for (Set<String> coords : materials.values()) {
                totalBlocks += coords.size();
            }
        }

        getLogger().info("BroodmotherWebs enabled! Loaded " + totalBlocks + " protected blocks across " + protectedBlocks.size() + " dungeons.");
    }

    private void loadProtectedBlocks() {
        protectedBlocks.clear();
        ConfigurationSection dungeonsSection = config.getConfigurationSection("dungeons");
        
        if (dungeonsSection != null) {
            // Loop through each base dungeon name (e.g., "Broodspire", "BreadSpire")
            for (String dungeonName : dungeonsSection.getKeys(false)) {
                ConfigurationSection materialSection = dungeonsSection.getConfigurationSection(dungeonName);
                Map<String, Set<String>> materialsMap = new HashMap<>();
                
                if (materialSection != null) {
                    // Loop through each material for this dungeon (e.g., "COBWEB", "STONE")
                    for (String materialName : materialSection.getKeys(false)) {
                        List<String> coords = materialSection.getStringList(materialName);
                        materialsMap.put(materialName, new HashSet<>(coords));
                    }
                }
                protectedBlocks.put(dungeonName, materialsMap);
            }
        }
    }

    private void saveProtectedBlocks() {
        config.set("dungeons", null); // Clear old data to overwrite cleanly

        for (Map.Entry<String, Map<String, Set<String>>> dungeonEntry : protectedBlocks.entrySet()) {
            String dungeonName = dungeonEntry.getKey();
            for (Map.Entry<String, Set<String>> materialEntry : dungeonEntry.getValue().entrySet()) {
                String materialName = materialEntry.getKey();
                List<String> coordsList = new ArrayList<>(materialEntry.getValue());
                
                // Set path: dungeons.Broodspire.COBWEB = ["-14,-43,-57", ...]
                config.set("dungeons." + dungeonName + "." + materialName, coordsList);
            }
        }

        try {
            config.save(configFile);
        } catch (IOException e) {
            getLogger().severe("Could not save coordinate map to protected-blocks.yml!");
        }
    }

    /**
     * Strips all non-alphabetical characters from a world name.
     * e.g., "Broodspire_0" -> "Broodspire"
     * e.g., "BreadSpire_12" -> "BreadSpire"
     */
    private String getBaseDungeonName(String rawName) {
        return rawName.replaceAll("[^a-zA-Z]", "");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPunchBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        String worldName = block.getWorld().getName();
        String baseDungeonName = getBaseDungeonName(worldName);

        // Check 1: Is this world base name registered in our system?
        if (!protectedBlocks.containsKey(baseDungeonName)) {
            return; // Not our dungeon. Drop responsibility. Let WorldGuard/Others handle it.
        }

        Map<String, Set<String>> trackedMaterials = protectedBlocks.get(baseDungeonName);
        Material material = block.getType();
        String materialName = material.name();

        // Check 2: Is this specific block material tracked in this dungeon?
        if (!trackedMaterials.containsKey(materialName)) {
            return; // Not a targeted block type for this dungeon. Let WorldGuard/Others handle it.
        }

        Set<String> protectedCoords = trackedMaterials.get(materialName);
        String blockCoords = block.getX() + "," + block.getY() + "," + block.getZ();

        // Check 3: Do the coordinates match?
        if (protectedCoords.contains(blockCoords)) {
            // Match found! DO NOT ALLOW BREAKING.
            event.setCancelled(true);
        } else {
            // No match, but material IS tracked. REPLACE WITH AIR (Boss spawn).
            event.setCancelled(true);
            block.setType(Material.AIR);
        }
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

            Location origin = player.getLocation();
            World world = origin.getWorld();
            
            // Extract the purely alphabetical base name to use as our database key
            String baseDungeonName = getBaseDungeonName(world.getName());

            player.sendMessage(ChatColor.YELLOW + "Scanning for static " + targetMaterial.name() + 
                    " within a " + radius + " block radius in dungeon base: " + ChatColor.AQUA + baseDungeonName);
            
            // Ensure maps exist for this dungeon base
            protectedBlocks.putIfAbsent(baseDungeonName, new HashMap<>());
            Map<String, Set<String>> dungeonMaterials = protectedBlocks.get(baseDungeonName);
            
            // Create a fresh set of coordinates to overwrite old ones for this specific material
            Set<String> coords = new HashSet<>();
            int foundCount = 0;

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block b = world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z);
                        if (b.getType() == targetMaterial) {
                            coords.add(b.getX() + "," + b.getY() + "," + b.getZ());
                            foundCount++;
                        }
                    }
                }
            }

            // Save to memory, then to file
            dungeonMaterials.put(targetMaterial.name(), coords);
            saveProtectedBlocks();
            
            player.sendMessage(ChatColor.GREEN + "Success! Added " + foundCount + " static " + targetMaterial.name() + " blocks to " + baseDungeonName + ".");
            return true;
        }

        player.sendMessage(ChatColor.GOLD + "BroodmotherWebs Generalized Commands:");
        player.sendMessage(ChatColor.YELLOW + "/bmwebs scan <radius> <material> " + ChatColor.WHITE + "- Maps specific blocks to safety list (e.g. /bmwebs scan 50 COBWEB).");
        return true;
    }
}
