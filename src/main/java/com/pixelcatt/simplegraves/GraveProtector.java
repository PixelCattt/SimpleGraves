package com.pixelcatt.simplegraves;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;


public class GraveProtector implements Listener {

    private final SimpleGraves plugin;
    private final GraveManager manager;

    private boolean nonGravePlayerHeadWaterProtection = true;


    public GraveProtector(SimpleGraves plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }


    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isGraveBlock);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isGraveBlock);
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        Block block = event.getToBlock();

        if (nonGravePlayerHeadWaterProtection && block.getType() == Material.PLAYER_HEAD) {
            event.setCancelled(true);
        } else if (isGraveBlock(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (isGraveBlock(block)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (isGraveBlock(block)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();

        if (isGraveBlock(block) && event.getBlockPlaced().getType() != Material.PLAYER_HEAD) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlockClicked().getRelative(event.getBlockFace());

        if (isGraveBlock(block)) {
            event.setCancelled(true);
        }
    }

    private boolean isGraveBlock(Block block) {
        if (manager.graveExistsLoc(block.getLocation())) {
            return true;
        }

        return false;
    }


    public void disableNonGravePlayerHeadWaterProtection() {
        this.nonGravePlayerHeadWaterProtection = false;
    }
}