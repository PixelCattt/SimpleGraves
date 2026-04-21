package com.flummidill.simplegraves;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.stream.Collectors;


public class TabCompleter implements org.bukkit.command.TabCompleter {

    private final SimpleGraves plugin;
    private final GraveManager manager;


    public TabCompleter(SimpleGraves plugin, GraveManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (sender instanceof Player) {
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            manager.saveOfflinePlayer(uuid, player.getName());

            String cmd = command.getName().toLowerCase();

            switch (cmd) {
                case "graveinfo", "graveitems":
                    return autocompleteGraveInfoAndItems(player, args);
                case "graveadmin":
                    return autocompleteGraveAdmin(player, args);

                default:
                    return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    private List<String> autocompleteGraveInfoAndItems(Player player, String[] args) {
        if (args.length == 1) {
            return manager.getGraveNumberList(player.getUniqueId());
        }

        return Collections.emptyList();
    }

    private List<String> autocompleteGraveAdmin(Player sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();

            List<String> subcommands = new ArrayList<>();

            if (sender.hasPermission("simplegraves.graveadmin.go")) subcommands.add("go");
            if (sender.hasPermission("simplegraves.graveadmin.list")) subcommands.add("list");
            if (sender.hasPermission("simplegraves.graveadmin.info")) subcommands.add("info");
            if (sender.hasPermission("simplegraves.graveadmin.items")) subcommands.add("items");
            if (sender.hasPermission("simplegraves.graveadmin.remove")) subcommands.add("remove");

            if (prefix.isEmpty()) {
                return subcommands;
            } else {
                return filterStringsByPrefix(subcommands, prefix);
            }
        }
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();

            if (!sender.hasPermission("simplegraves.graveadmin." + subcommand)) {
                return Collections.emptyList();
            }

            List<String> playerNames = new ArrayList<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
                String name = p.getName();
                if (!playerNames.contains(name)) {
                    playerNames.add(name);
                }
            }

            List<String> offlinePlayerNames = manager.getOfflinePlayerNameList();
            for (String name : offlinePlayerNames) {
                if (!playerNames.contains(name)) {
                    playerNames.add(name);
                }
            }

            if (prefix.isEmpty()) {
                return playerNames;
            } else {
                return filterStringsByPrefix(playerNames, prefix);
            }
        }
        if (args.length == 3) {
            String subcommand = args[0].toLowerCase();
            String playerName = args[1];
            Player target = Bukkit.getPlayerExact(playerName);
            UUID playerUUID;

            if (!sender.hasPermission("simplegraves.graveadmin." + subcommand)) {
                return Collections.emptyList();
            }

            if (target != null) {
                playerUUID = target.getUniqueId();
            } else if (manager.getOfflinePlayerUUID(playerName) != null) {
                playerUUID = manager.getOfflinePlayerUUID(playerName);
            } else {
                return Collections.emptyList();
            }

            if (subcommand.equals("list")) {
                return Collections.emptyList();
            }

            List<String> graveNumberList = manager.getGraveNumberList(playerUUID);

            if (graveNumberList.isEmpty()) {
                return Collections.emptyList();
            }

            return graveNumberList;
        }

        return Collections.emptyList();
    }

    private List<String> filterStringsByPrefix(List<String> stringList, String prefix) {
        if (prefix == null || prefix.isEmpty() || stringList == null || stringList.isEmpty()) {
            return Collections.emptyList();
        } else {
            return stringList.stream()
                    .filter(string -> string.toLowerCase().startsWith(prefix.toLowerCase()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
    }
}