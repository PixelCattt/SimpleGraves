package com.flummidill.simplegraves;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;


public class CommandHandler implements CommandExecutor {

    private final SimpleGraves plugin;
    private final GraveManager manager;


    public CommandHandler(SimpleGraves plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly Players can run this Command!");

            return true;
        }

        Player player = (Player) sender;

        manager.saveOfflinePlayer(player.getUniqueId(), player.getName());

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "graveinfo":
                if (!player.hasPermission("simplegraves.graveinfo")) {
                    player.sendMessage("§cYou don’t have permission to use this command.");

                    return true;
                }

                if (!(args.length == 1)) {
                    player.sendMessage("Usage: /graveinfo <number>");

                    return true;
                }

                return  handleGraveInfo(player, args);

            case "graveadmin":
                if (!player.hasPermission("simplegraves.graveadmin.show")) {
                    player.sendMessage("§cYou don’t have permission to use this command.");

                    return true;
                }

                if (!(args.length == 2 || args.length == 3)) {
                    player.sendMessage("Usage: /graveadmin <go|list|info|delete> [<player>] [<number>]");
                    return true;
                }

                return handleGraveAdmin(player, args);

            default:
                player.sendMessage("Usage: /graveadmin <go|list|info|delete> [<player>] [<number>]");
                return true;
        }
    }

    private boolean handleGraveInfo(Player player, String[] args) {
        UUID targetUUID = player.getUniqueId();
        int graveNumber;

        try {
            graveNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cGrave must be a Number.");
            return true;
        }

        manager.graveExistsUUID(targetUUID, graveNumber).thenAccept(exists -> {
            if (!exists) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage("§cYou don't have a Grave with Number #" + graveNumber)
                );
                return;
            }

            manager.getGraveLocation(targetUUID, graveNumber).thenAccept(location -> {
                if (location == null || location.getWorld() == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage("§cFailed to retrieve the Grave Location")
                    );
                    return;
                }

                String worldName;
                switch (location.getWorld().getName()) {
                    case "world" -> worldName = "The Overworld";
                    case "world_nether" -> worldName = "The Nether";
                    case "world_the_end" -> worldName = "The End";
                    default -> worldName = location.getWorld().getName();
                }

                manager.getGraveItems(targetUUID, graveNumber).thenAccept(items -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§aGrave #" + graveNumber + " is Located at:" +
                                "\n§9World: §c" + worldName +
                                "\n§9X: §c" + Math.floor(location.getX()) +
                                "\n§9Y: §c" + Math.floor(location.getY()) +
                                "\n§9Z: §c" + Math.floor(location.getZ()));

                        if (items.isEmpty()) {
                            player.sendMessage("The grave is empty!");
                        } else {
                            player.sendMessage("§6Items:");
                            for (ItemStack item : items) {
                                player.sendMessage("" + item.getType().name() + " (" + item.getAmount() + "x)");
                            }
                        }
                    });
                });
            });
        });

        return true;
    }

    private boolean handleGraveAdmin(Player sender, String[] args) {
        String action = args[0].toLowerCase();
        String targetNameArg = args[1];
        String numberStr = (args.length >= 3) ? args[2] : "-1";

        switch (action) {
            case "go":
                if (!sender.hasPermission("simplegraves.graveadmin.go")) {
                    sender.sendMessage("§cYou don’t have permission to use this command.");
                    return true;
                }
                break;
            case "list":
                if (!sender.hasPermission("simplegraves.graveadmin.list")) {
                    sender.sendMessage("§cYou don’t have permission to use this command.");
                    return true;
                }
                break;
            case "info":
                if (!sender.hasPermission("simplegraves.graveadmin.info")) {
                    sender.sendMessage("§cYou don’t have permission to use this command.");
                    return true;
                }
                break;
            case "remove":
                if (!sender.hasPermission("simplegraves.graveadmin.remove")) {
                    sender.sendMessage("§cYou don’t have permission to use this command.");
                    return true;
                }
                break;
            default:
                sender.sendMessage("Usage: /graveadmin <go|list|info|remove> [<player>] [<number>]");
                return true;
        }

        final String targetNameFinal = targetNameArg;
        final String numberStrFinal = numberStr;

        if (targetNameFinal.equals("*")) {
            if (!action.equals("remove")) {
                sender.sendMessage("§cYou can only use Player * with the remove Command.");
                return true;
            }

            if (numberStrFinal.equals("*")) {
                manager.removeEveryGrave();
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§aRemoved all Graves of all Players."));
            } else {
                int graveNumber;
                try {
                    graveNumber = Integer.parseInt(numberStrFinal);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cGrave must be a Number.");
                    return true;
                }
                final int finalGraveNumber = graveNumber;
                manager.removeAllGravesWithNumber(finalGraveNumber);
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage("§aRemoved all Graves with Number #" + finalGraveNumber + "."));
            }
            return true;
        }

        Player onlineTarget = Bukkit.getPlayerExact(targetNameFinal);
        if (onlineTarget != null) {
            final UUID targetUUID = onlineTarget.getUniqueId();

            int graveNumber = -1;
            if (!numberStrFinal.equals("-1") && !numberStrFinal.equals("*")) {
                try {
                    graveNumber = Integer.parseInt(numberStrFinal);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cGrave must be a Number.");
                    return true;
                }
            }
            final int finalGraveNumber = graveNumber;

            switch (action) {
                case "go":
                    manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                        if (!exists) {
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    sender.sendMessage("§c" + targetNameFinal + " doesn't have a Grave with Number #" + finalGraveNumber));
                            return;
                        }
                        manager.getGraveLocation(targetUUID, finalGraveNumber).thenAccept(location -> {
                            if (location != null) {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    sender.teleport(location);
                                    sender.sendMessage("§aTeleported to " + targetNameFinal + "'s Grave #" + finalGraveNumber);
                                });
                            } else {
                                Bukkit.getScheduler().runTask(plugin, () ->
                                        sender.sendMessage("§cFailed to retrieve Grave Location."));
                            }
                        });
                    });
                    break;
                case "list":
                    manager.getGraveNumberListAsync(targetUUID).thenAccept(graveList -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (graveList.isEmpty()) {
                                sender.sendMessage("§c" + targetNameFinal + " currently has no Graves.");
                            } else {
                                sender.sendMessage("§a" + targetNameFinal + "'s Grave List:");
                                for (String graveNum : graveList) {
                                    sender.sendMessage("§c#" + graveNum);
                                }
                            }
                        });
                    });
                    break;
                case "info":
                    manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                        if (!exists) {
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    sender.sendMessage("§c" + targetNameFinal + " doesn't have a Grave with Number #" + finalGraveNumber));
                            return;
                        }
                        manager.getGraveLocation(targetUUID, finalGraveNumber).thenAccept(location -> {
                            if (location == null || location.getWorld() == null) {
                                Bukkit.getScheduler().runTask(plugin, () ->
                                        sender.sendMessage("§cFailed to retrieve the grave location or world."));
                                return;
                            }
                            String worldName;
                            switch (location.getWorld().getName()) {
                                case "world" -> worldName = "The Overworld";
                                case "world_nether" -> worldName = "The Nether";
                                case "world_the_end" -> worldName = "The End";
                                default -> worldName = location.getWorld().getName();
                            }

                            manager.getGraveItems(targetUUID, finalGraveNumber).thenAccept(items -> {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    sender.sendMessage("§a" + targetNameFinal + "'s Grave #" + finalGraveNumber + " is Located at:" +
                                            "\n§9World: §c" + worldName +
                                            "\n§9X: §c" + Math.floor(location.getX()) +
                                            "\n§9Y: §c" + Math.floor(location.getY()) +
                                            "\n§9Z: §c" + Math.floor(location.getZ()));

                                    if (items.isEmpty()) {
                                        sender.sendMessage("The grave is empty!");
                                    } else {
                                        sender.sendMessage("§6Items:");
                                        for (ItemStack item : items) {
                                            sender.sendMessage(item.getType().name() + " (" + item.getAmount() + "x)");
                                        }
                                    }
                                });
                            });
                        });
                    });
                    break;
                case "remove":
                    if (numberStrFinal.equals("*")) {
                        manager.removeAllGraves(targetUUID);
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage("§aRemoved all Graves of " + targetNameFinal + "."));
                    } else {
                        manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                            if (!exists) {
                                Bukkit.getScheduler().runTask(plugin, () ->
                                        sender.sendMessage("§c" + targetNameFinal + " doesn't have a Grave with Number #" + finalGraveNumber));
                                return;
                            }
                            manager.removeGrave(targetUUID, finalGraveNumber, false);
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    sender.sendMessage("§aRemoved " + targetNameFinal + "'s Grave #" + finalGraveNumber));
                        });
                    }
                    break;
            }
            return true;
        }

        // Offline Target handling
        manager.getOfflinePlayerUUIDAsync(targetNameFinal).thenAccept(uuid -> {
            manager.getOfflinePlayerName(uuid).thenAccept(name -> {
                if (uuid == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage("§cPlayer '" + targetNameFinal + "' not found."));
                    return;
                }
                final UUID targetUUID = uuid;
                final String targetName = name;

                int graveNumber = -1;
                if (!numberStrFinal.equals("-1") && !numberStrFinal.equals("*")) {
                    try {
                        graveNumber = Integer.parseInt(numberStrFinal);
                    } catch (NumberFormatException e) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                                sender.sendMessage("§cGrave must be a Number."));
                        return;
                    }
                }
                final int finalGraveNumber = graveNumber;

                switch (action) {
                    case "go":
                        manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                            if (!exists) {
                                Bukkit.getScheduler().runTask(plugin, () ->
                                        sender.sendMessage("§c" + targetName + " doesn't have a Grave with Number #" + finalGraveNumber));
                                return;
                            }
                            manager.getGraveLocation(targetUUID, finalGraveNumber).thenAccept(location -> {
                                if (location != null) {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        sender.teleport(location);
                                        sender.sendMessage("§aTeleported to " + targetName + "'s Grave #" + finalGraveNumber);
                                    });
                                } else {
                                    Bukkit.getScheduler().runTask(plugin, () ->
                                            sender.sendMessage("§cFailed to retrieve Grave Location."));
                                }
                            });
                        });
                        break;
                    case "list":
                        manager.getGraveNumberListAsync(targetUUID).thenAccept(graveList -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (graveList.isEmpty()) {
                                    sender.sendMessage("§c" + targetName + " currently has no Graves.");
                                } else {
                                    sender.sendMessage("§a" + targetName + "'s Grave List:");
                                    for (String graveNum : graveList) {
                                        sender.sendMessage("§c#" + graveNum);
                                    }
                                }
                            });
                        });
                        break;
                    case "info":
                        manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                            if (!exists) {
                                Bukkit.getScheduler().runTask(plugin, () ->
                                        sender.sendMessage("§c" + targetName + " doesn't have a Grave with Number #" + finalGraveNumber));
                                return;
                            }
                            manager.getGraveLocation(targetUUID, finalGraveNumber).thenAccept(location -> {
                                if (location == null || location.getWorld() == null) {
                                    Bukkit.getScheduler().runTask(plugin, () ->
                                            sender.sendMessage("§cFailed to retrieve the grave location or world."));
                                    return;
                                }
                                String worldName;
                                switch (location.getWorld().getName()) {
                                    case "world" -> worldName = "The Overworld";
                                    case "world_nether" -> worldName = "The Nether";
                                    case "world_the_end" -> worldName = "The End";
                                    default -> worldName = location.getWorld().getName();
                                }

                                manager.getGraveItems(targetUUID, finalGraveNumber).thenAccept(items -> {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        sender.sendMessage("§a" + targetName + "'s Grave #" + finalGraveNumber + " is Located at:" +
                                                "\n§9World: §c" + worldName +
                                                "\n§9X: §c" + Math.floor(location.getX()) +
                                                "\n§9Y: §c" + Math.floor(location.getY()) +
                                                "\n§9Z: §c" + Math.floor(location.getZ()));

                                        if (items.isEmpty()) {
                                            sender.sendMessage("§7(empty)");
                                        } else {
                                            for (ItemStack item : items) {
                                                sender.sendMessage("§6Items:");
                                                sender.sendMessage(item.getType().name() + " (" + item.getAmount() + "x)");
                                            }
                                        }
                                    });
                                });
                            });
                        });
                        break;
                    case "remove":
                        if (numberStrFinal.equals("*")) {
                            manager.removeAllGraves(targetUUID);
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    sender.sendMessage("§aRemoved all Graves of " + targetName + "."));
                        } else {
                            manager.graveExistsUUID(targetUUID, finalGraveNumber).thenAccept(exists -> {
                                if (!exists) {
                                    Bukkit.getScheduler().runTask(plugin, () ->
                                            sender.sendMessage("§c" + targetName + " doesn't have a Grave with Number #" + finalGraveNumber));
                                    return;
                                }
                                manager.removeGrave(targetUUID, finalGraveNumber, false);
                                Bukkit.getScheduler().runTask(plugin, () ->
                                        sender.sendMessage("§aRemoved " + targetName + "'s Grave #" + finalGraveNumber));
                            });
                        }
                        break;
                }
            });
        });

        return true;
    }
}