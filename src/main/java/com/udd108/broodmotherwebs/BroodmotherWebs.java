package com.udd108.broodmotherwebs;

import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.skills.Skill;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.bukkit.BukkitAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public class BroodmotherWebs extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Register the event listener
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BroodmotherWebs successfully enabled!");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPunchWeb(PlayerInteractEvent event) {
        // Check if the action is left-clicking a block
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.COBWEB) return;

        Player player = event.getPlayer();

        // Optional: Restrict this to your specific dungeon worlds
        // if (!player.getWorld().getName().equalsIgnoreCase("dungeon_world")) return;

        // Bypasses WorldGuard / Mythic Dungeons protections instantly
        event.setCancelled(true); 
        block.setType(Material.AIR);

        // Fire your custom MythicMobs skill using the Mythic API
        triggerMythicSkill(player, block);
    }

    private void triggerMythicSkill(Player player, Block block) {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) return;

        // Get the caster profile for the player
SkillCaster caster = MythicProvider.get().getMobManager().getCaster(player);

if (skill != null && caster != null) {
    // Modern API requires a SkillMetadata wrapper instead of passing parameters directly
    io.lumine.mythic.api.skills.SkillMetadata meta = new io.lumine.mythic.core.skills.SkillMetadataImpl(
        io.lumine.mythic.api.skills.SkillTrigger.API, 
        caster, 
        BukkitAdapter.adapt(player)
    );
    
    // Set the specific location target of the block being broken
    meta.setLocationTarget(BukkitAdapter.adapt(block.getLocation()));
    
    // Execute cleanly
    skill.execute(meta);
}
    }
}
