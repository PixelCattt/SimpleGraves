package com.pixelcatt.simplegraves;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;


public class BlockBreakListener implements Listener {

    private final SimpleGraves plugin;
    private final GraveManager manager;

    public boolean graveStealing = true;


    public BlockBreakListener(SimpleGraves plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void disableGraveStealing() {
        this.graveStealing = false;
    }


    @EventHandler
    public void onGraveBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        manager.saveOfflinePlayer(player.getUniqueId(), player.getName());

        if (loc.getBlock().getType() == Material.PLAYER_HEAD) {
            if (manager.graveExistsLoc(loc)) {
                if (graveStealing) {
                    manager.breakGrave(loc);
                } else {
                    if (manager.getGraveOwnerUUID(loc).equals(player.getUniqueId())) {
                        manager.breakGrave(loc);
                    } else {
                        player.sendMessage("§cYou cannot break other Player's Graves!");
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
}