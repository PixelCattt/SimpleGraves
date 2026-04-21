package com.pixelcatt.simplegraves;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.io.File;
import java.sql.*;
import java.util.*;


public class GraveManager {

    private final SimpleGraves plugin;

    public final Executor dbWorker;

    private final File dbFile;
    private Connection connection;

    private int xpLimit = 910;
    private boolean delete_vanishing_items = false;


    public GraveManager(SimpleGraves plugin, DatabaseWorker dbWorker) {
        this.plugin = plugin;

        this.dbWorker = query -> dbWorker.getDatabaseExecutor().execute(query);
        this.dbFile = new File(plugin.getDataFolder(), "graves.db");
        openConnection();
        createTables();
    }


    private void openConnection() {
        dbWorker.execute(() -> {
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            } catch (Exception e) {
                plugin.getLogger().severe("Could not connect to SQLite database!");
                e.printStackTrace();
            }
        });
    }

    private void createTables() {
        dbWorker.execute(() -> {
            try (Statement stmt = connection.createStatement()) {
                // Graves Table
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS graves (" +
                        "uuid TEXT NOT NULL," +
                        "grave_num INT NOT NULL," +
                        "world TEXT NOT NULL," +
                        "x DOUBLE NOT NULL," +
                        "y DOUBLE NOT NULL," +
                        "z DOUBLE NOT NULL," +
                        "pitch DOUBLE NOT NULL," +
                        "yaw DOUBLE NOT NULL," +
                        "items TEXT NOT NULL," +
                        "xp DOUBLE NOT NULL)");

                // Offline-Players Table
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS offline_players (" +
                        "uuid TEXT NOT NULL," +
                        "plr_name TEXT NOT NULL UNIQUE," +
                        "PRIMARY KEY(uuid))");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create tables in SQLite database.");
                e.printStackTrace();
            }
        });
    }

    public void createGrave(Player player, Location loc) {
        UUID uuid = player.getUniqueId();
        int graveNum = 1;

        // Save Grave in the Database
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO graves(uuid, grave_num, world, x, y, z, pitch, yaw, items, xp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

            // Player's UUID
            ps.setString(1, uuid.toString());

            // Grave Number
            try (PreparedStatement checkGraveNum = connection.prepareStatement(
                    "SELECT COALESCE(MAX(grave_num), 0) FROM graves WHERE uuid = ?")) {
                checkGraveNum.setString(1, uuid.toString());

                ResultSet rs = checkGraveNum.executeQuery();

                if (rs.next()) {
                    graveNum = rs.getInt(1) + 1;
                }
                ps.setInt(2, graveNum);
            }

            // Player's Dimension
            if (loc.getWorld() == null) {
                player.sendMessage("§cFailed to create Grave!");
                plugin.getLogger().warning("Failed to create Grave for Player \"" + uuid + "\": Failed to determine Player's Dimension");
                return;
            } else {
                ps.setString(3, loc.getWorld().getName());
            }

            // Center of Block
            double x = Math.floor(loc.getX()) + 0.5;
            double y = Math.floor(loc.getY()) + 0.5;
            double z = Math.floor(loc.getZ()) + 0.5;

            ps.setDouble(4, x);
            ps.setDouble(5, y);
            ps.setDouble(6, z);

            // Facing Direction
            BlockFace graveRotation = BlockFace.NORTH;
            double pitch = 0f;
            double yaw = 0f;

            if (loc.getYaw() >= 135 && loc.getYaw() <= -135) {
                // NORTH
                yaw = 180f;
                graveRotation = BlockFace.SOUTH;
            } else if (loc.getYaw() < 135 && loc.getYaw() > 45) {
                // WEST
                yaw = 90f;
                graveRotation = BlockFace.EAST;
            } else if (loc.getYaw() <= 45 && loc.getYaw() >= -45) {
                // SOUTH
                yaw = 0f;
                graveRotation = BlockFace.NORTH;
            } else if (loc.getYaw() < -45 && loc.getYaw() > -135) {
                // EAST
                yaw = -90f;
                graveRotation = BlockFace.WEST;
            }

            ps.setDouble(7, pitch);
            ps.setDouble(8, yaw);

            // Player's Items
            String player_items = Arrays.asList(player.getInventory().getContents()).stream()
                    .filter(Objects::nonNull)
                    .filter(item -> !(delete_vanishing_items && item.getEnchantments().containsKey(Enchantment.VANISHING_CURSE)))
                    .map(item -> {
                        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
                            oos.writeObject(item);
                            return Base64.getEncoder().encodeToString(baos.toByteArray());
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("|"));
            ps.setString(9, player_items);

            // Player's XP
            double player_xp = getTotalXP(player);
            if (player_xp > xpLimit) {
                player_xp = xpLimit;
            }
            ps.setDouble(10, player_xp);

            ps.executeUpdate();

            // Clear Inventory + XP
            player.getInventory().clear();
            player.setTotalExperience(0);
            player.setLevel(0);
            player.setExp(0);

            // Place Gravestone
            String worldName;

            switch (loc.getWorld().getName()) {
                case "world":
                    worldName = "{\"text\":\"The Overworld\",\"color\":\"green\"}";
                    break;
                case "world_nether":
                    worldName = "{\"text\":\"The Nether\",\"color\":\"red\"}";
                    break;
                case "world_the_end":
                    worldName = "{\"text\":\"The End\",\"color\":\"#ffffaa\"}";
                    break;
                default:
                    worldName = "{\"text\":\"" + loc.getWorld().getName() + "\",\"color\":\"light_purple\"}";
                    break;
            }

            plugin.executeConsoleCommand("tellraw " + player.getName() + " " + "[{\"text\":\"Your Grave \",\"color\":\"white\"},{\"text\":\"#" + graveNum + "\",\"color\":\"gold\"},{\"text\":\" is Located at \",\"color\":\"white\"},{\"text\":\"" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "\",\"color\":\"gold\"},{\"text\":\" in \",\"color\":\"white\"}," + worldName + "]");

            Block block = loc.getBlock();
            block.setType(Material.PLAYER_HEAD);
            Rotatable rot = (Rotatable) block.getBlockData();
            rot.setRotation(graveRotation);
            block.setBlockData(rot);

            Skull skull = (Skull) block.getState();
            skull.setOwningPlayer(player);
            skull.update();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<Boolean> graveExistsUUID(UUID uuid, int graveNum) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM graves WHERE uuid = ? AND grave_num = ?")) {

                ps.setString(1, uuid.toString());
                ps.setInt(2, graveNum);

                // Grave Exists?
                return ps.executeQuery().next();

            } catch (SQLException e) {
                e.printStackTrace();
            }

            return false;

        }, dbWorker);
    }

    public boolean graveExistsLoc(Location loc) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM graves WHERE world = ? AND x =? AND y = ? AND z = ?")) {

            // World
            if (loc.getWorld() == null) {
                plugin.getLogger().warning("Failed to determine Dimension of broken Block.");

                return false;
            } else {
                ps.setString(1, loc.getWorld().getName());
            }

            // Center of Block
            double x = Math.floor(loc.getX()) + 0.5;
            double y = Math.floor(loc.getY()) + 0.5;
            double z = Math.floor(loc.getZ()) + 0.5;

            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);

            // Grave Exists?
            return ps.executeQuery().next();

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    public List<String> getGraveNumberList(UUID uuid) {
        List<String> graveNumberList = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT grave_num FROM graves WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int graveNum = rs.getInt("grave_num");

                graveNumberList.add(String.valueOf(graveNum));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return graveNumberList;
    }

    public CompletableFuture<List<String>> getGraveNumberListAsync(UUID uuid) {
        final UUID finalUUID = uuid;
        CompletableFuture<List<String>> future = new CompletableFuture<>();

        dbWorker.execute(() -> {
            List<String> graveNumberList = new ArrayList<>();

            try (PreparedStatement ps = connection.prepareStatement("SELECT grave_num FROM graves WHERE uuid = ?")) {
                ps.setString(1, finalUUID.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    int graveNum = rs.getInt("grave_num");
                    graveNumberList.add(String.valueOf(graveNum));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            Bukkit.getScheduler().runTask(plugin, () -> future.complete(graveNumberList));
        });

        return future;
    }

    public CompletableFuture<Location> getGraveLocation(UUID uuid, int graveNum) {
        final UUID finalUUID = uuid;
        final int finalGraveNum = graveNum;
        CompletableFuture<Location> future = new CompletableFuture<>();

        dbWorker.execute(() -> {
            Location graveLocation = null;

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT world, x, y, z, yaw, pitch FROM graves WHERE uuid = ? AND grave_num = ?")) {
                ps.setString(1, finalUUID.toString());
                ps.setInt(2, finalGraveNum);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString("world"));
                    if (world != null) {
                        graveLocation = new Location(world,
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                (float) rs.getDouble("yaw"),
                                (float) rs.getDouble("pitch"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            Location result = graveLocation;
            Bukkit.getScheduler().runTask(plugin, () -> future.complete(result));
        });

        return future;
    }

    public CompletableFuture<List<Location>> getAllGraveLocations(UUID uuid) {
        final UUID finalUUID = uuid;
        CompletableFuture<List<Location>> future = new CompletableFuture<>();

        dbWorker.execute(() -> {
            List<Location> graveLocations = new ArrayList<>();

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT world, x, y, z, yaw, pitch FROM graves WHERE uuid = ?")) {
                ps.setString(1, finalUUID.toString());
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString("world"));
                    if (world == null) continue;

                    Location location = new Location(world,
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            (float) rs.getDouble("yaw"),
                            (float) rs.getDouble("pitch"));
                    graveLocations.add(location);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            Bukkit.getScheduler().runTask(plugin, () -> future.complete(graveLocations));
        });

        return future;
    }

    public CompletableFuture<List<Location>> getAllGraveLocationsWithNumber(int graveNum) {
        final int finalGraveNum = graveNum;
        CompletableFuture<List<Location>> future = new CompletableFuture<>();

        dbWorker.execute(() -> {
            List<Location> graveLocations = new ArrayList<>();

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT world, x, y, z, yaw, pitch FROM graves WHERE grave_num = ?")) {
                ps.setInt(1, finalGraveNum);
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString("world"));
                    if (world == null) continue;

                    Location location = new Location(world,
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            (float) rs.getDouble("yaw"),
                            (float) rs.getDouble("pitch"));
                    graveLocations.add(location);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            Bukkit.getScheduler().runTask(plugin, () -> future.complete(graveLocations));
        });

        return future;
    }

    public CompletableFuture<List<Location>> getEveryGraveLocation() {
        CompletableFuture<List<Location>> future = new CompletableFuture<>();

        dbWorker.execute(() -> {
            List<Location> graveLocations = new ArrayList<>();

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT world, x, y, z, yaw, pitch FROM graves")) {
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    World world = Bukkit.getWorld(rs.getString("world"));
                    if (world == null) continue;

                    Location location = new Location(world,
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            (float) rs.getDouble("yaw"),
                            (float) rs.getDouble("pitch"));
                    graveLocations.add(location);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            Bukkit.getScheduler().runTask(plugin, () -> future.complete(graveLocations));
        });

        return future;
    }

    public CompletableFuture<List<ItemStack>> getGraveItems(UUID uuid, int graveNum) {
        return CompletableFuture.supplyAsync(() -> {
            List<ItemStack> items = new ArrayList<>();

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT items FROM graves WHERE uuid = ? AND grave_num = ?")) {

                ps.setString(1, uuid.toString());
                ps.setInt(2, graveNum);

                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    String serializedItems = rs.getString("items");

                    if (serializedItems != null && !serializedItems.isEmpty()) {
                        String[] base64Items = serializedItems.split("\\|");

                        for (String base64 : base64Items) {
                            if (base64.isEmpty()) continue;

                            try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
                                 BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {

                                ItemStack item = (ItemStack) ois.readObject();
                                if (item != null && item.getType() != Material.AIR) {
                                    items.add(item);
                                }

                            } catch (IOException | ClassNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }

            return items;
        }, dbWorker);
    }

    public UUID getGraveOwnerUUID(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }

        String worldName = loc.getWorld().getName();
        double x = Math.floor(loc.getX()) + 0.5;
        double y = Math.floor(loc.getY()) + 0.5;
        double z = Math.floor(loc.getZ()) + 0.5;

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid FROM graves WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            ps.setString(1, worldName);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void breakGrave(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        final String worldName = loc.getWorld().getName();
        final double x = Math.floor(loc.getX()) + 0.5;
        final double y = Math.floor(loc.getY()) + 0.5;
        final double z = Math.floor(loc.getZ()) + 0.5;

        dbWorker.execute(() -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT uuid, grave_num, items, xp FROM graves WHERE world = ? AND x = ? AND y = ? AND z = ?")) {

                select.setString(1, worldName);
                select.setDouble(2, x);
                select.setDouble(3, y);
                select.setDouble(4, z);

                ResultSet rs = select.executeQuery();

                if (!rs.next()) return;

                final String uuid = rs.getString("uuid");
                final String serializedItems = rs.getString("items");
                final int graveNum = rs.getInt("grave_num");
                final double xpAmount = rs.getDouble("xp");

                Bukkit.getScheduler().runTask(plugin, () -> {
                    World world = Bukkit.getWorld(worldName);
                    Location dropLoc = new Location(world, x, y, z);

                    // Player's Items
                    String[] base64Items = serializedItems.split("\\|");
                    for (String base64 : base64Items) {
                        if (base64.isEmpty()) continue;

                        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
                             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {

                            ItemStack item = (ItemStack) ois.readObject();
                            if (item != null && item.getType() != Material.AIR && world != null) {
                                world.dropItemNaturally(dropLoc, item);
                            }

                        } catch (IOException | ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }

                    // Player's XP
                    int xpOrbCount;
                    int xpPerOrb;
                    int xpLeftOver;

                    if (xpAmount > 0 && xpAmount <= 25) {
                        xpOrbCount = (int) Math.floor(xpAmount);
                        xpPerOrb = 1;
                        xpLeftOver = 0;
                    } else if (xpAmount > 25) {
                        xpOrbCount = 25;
                        xpPerOrb = (int) Math.floor(xpAmount / 25);
                        xpLeftOver = (int) xpAmount - (xpPerOrb * 25);
                    } else {
                        xpOrbCount = 0;
                        xpPerOrb = 0;
                        xpLeftOver = 0;
                    }

                    if (world != null) {
                        for (int i = 0; i < xpOrbCount; i++) {
                            world.spawn(dropLoc, ExperienceOrb.class).setExperience(xpPerOrb);
                        }
                        if (xpLeftOver > 0) {
                            world.spawn(dropLoc, ExperienceOrb.class).setExperience(xpLeftOver);
                        }
                    }
                });

                // Remove Grave from Database
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM graves WHERE uuid = ? AND grave_num = ?")) {
                    delete.setString(1, uuid);
                    delete.setInt(2, graveNum);
                    delete.executeUpdate();
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public void removeGrave(UUID uuid, int graveNum, boolean dropContents) {
        getGraveLocation(uuid, graveNum).thenAccept(graveLocation -> {
            if (graveLocation == null) return;

            World world = graveLocation.getWorld();
            if (world == null) return;

            Chunk chunk = graveLocation.getChunk();
            boolean wasLoaded = chunk.isLoaded();

            dbWorker.execute(() -> {
                if (!wasLoaded) {
                    world.loadChunk(chunk);
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Block graveBlock = graveLocation.getBlock();
                    if (graveBlock.getType() != Material.AIR) {
                        graveBlock.setType(Material.AIR);
                    }

                    if (dropContents) {
                        breakGrave(graveLocation);
                    }

                    if (!wasLoaded) {
                        world.unloadChunk(chunk);
                    }

                    dbWorker.execute(() -> {
                        try (PreparedStatement ps = connection.prepareStatement(
                                "DELETE FROM graves WHERE uuid = ? AND grave_num = ?")) {
                            ps.setString(1, uuid.toString());
                            ps.setInt(2, graveNum);
                            ps.executeUpdate();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    });
                });
            });
        });
    }

    public void removeAllGraves(UUID uuid) {
        getAllGraveLocations(uuid).thenAccept(graveLocations -> {
            if (graveLocations.isEmpty()) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Location graveLocation : graveLocations) {
                    if (graveLocation == null) continue;

                    World world = graveLocation.getWorld();
                    if (world == null) continue;

                    Chunk chunk = graveLocation.getChunk();
                    boolean wasLoaded = chunk.isLoaded();

                    if (!wasLoaded) {
                        world.loadChunk(chunk);
                    }

                    Block graveBlock = graveLocation.getBlock();
                    if (graveBlock.getType() != Material.AIR) {
                        graveBlock.setType(Material.AIR);
                    }

                    if (!wasLoaded) {
                        world.unloadChunk(chunk);
                    }
                }
            });

            dbWorker.execute(() -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM graves WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    public void removeAllGravesWithNumber(int graveNum) {
        getAllGraveLocationsWithNumber(graveNum).thenAccept(graveLocations -> {
            if (graveLocations.isEmpty()) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Location graveLocation : graveLocations) {
                    if (graveLocation == null) continue;

                    World world = graveLocation.getWorld();
                    if (world == null) continue;

                    Chunk chunk = graveLocation.getChunk();
                    boolean wasLoaded = chunk.isLoaded();

                    if (!wasLoaded) {
                        world.loadChunk(chunk);
                    }

                    Block graveBlock = graveLocation.getBlock();
                    if (graveBlock.getType() != Material.AIR) {
                        graveBlock.setType(Material.AIR);
                    }

                    if (!wasLoaded) {
                        world.unloadChunk(chunk);
                    }
                }
            });

            dbWorker.execute(() -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM graves WHERE grave_num = ?")) {
                    ps.setInt(1, graveNum);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    public void removeEveryGrave() {
        getEveryGraveLocation().thenAccept(graveLocations -> {
            if (graveLocations.isEmpty()) return;

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Location graveLocation : graveLocations) {
                    if (graveLocation == null) continue;

                    World world = graveLocation.getWorld();
                    if (world == null) continue;

                    Chunk chunk = graveLocation.getChunk();
                    boolean wasLoaded = chunk.isLoaded();

                    if (!wasLoaded) {
                        world.loadChunk(chunk);
                    }

                    Block graveBlock = graveLocation.getBlock();
                    if (graveBlock.getType() != Material.AIR) {
                        graveBlock.setType(Material.AIR);
                    }

                    if (!wasLoaded) {
                        world.unloadChunk(chunk);
                    }
                }
            });

            dbWorker.execute(() -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM graves")) {
                    ps.executeUpdate();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    public void setMaxStordXP(int maxLevels) {
        if (maxLevels <= 16) {
            xpLimit = (maxLevels * maxLevels) + 6 * maxLevels;
        } else if (maxLevels <= 31) {
            xpLimit = (int) Math.floor(2.5 * (maxLevels * maxLevels) - 40.5 * maxLevels + 360);
        } else {
            xpLimit = (int) Math.floor(4.5 * (maxLevels * maxLevels) - 162.5 * maxLevels + 2220);
        }
    }

    public int getTotalXP(Player player) {
        int XPlevel = player.getLevel();

        if (XPlevel <= 16) {
            return (XPlevel * XPlevel) + 6 * XPlevel;
        } else if (XPlevel <= 31) {
            return (int) Math.floor(2.5 * (XPlevel * XPlevel) - 40.5 * XPlevel + 360);
        } else {
            return (int) Math.floor(4.5 * (XPlevel * XPlevel) - 162.5 * XPlevel + 2220);
        }
    }

    public void saveOfflinePlayer(UUID uuid, String playerName) {
        CompletableFuture.runAsync(() -> {
            try {
                // Check If UUID already exists
                boolean uuidExists = false;
                PreparedStatement ps1 = connection.prepareStatement(
                        "SELECT 1 FROM offline_players WHERE uuid = ? LIMIT 1");
                ps1.setString(1, uuid.toString());
                ResultSet rs1 = ps1.executeQuery();
                if (rs1.next()) {
                    uuidExists = true;
                }

                // Check If the Player Name already exists
                boolean nameExists = false;
                PreparedStatement ps2 = connection.prepareStatement(
                        "SELECT 1 FROM offline_players WHERE plr_name = ? LIMIT 1");
                ps2.setString(1, playerName);
                ResultSet rs2 = ps2.executeQuery();
                if (rs2.next()) {
                    nameExists = true;
                }

                // Delete Row with UUID if uuidExists = true
                if (uuidExists) {
                    PreparedStatement ps3 = connection.prepareStatement(
                            "DELETE FROM offline_players WHERE uuid = ?");
                    ps3.setString(1, uuid.toString());
                    ps3.executeUpdate();
                }

                // Delete Row with Name if nameExists = true
                if (nameExists) {
                    PreparedStatement ps4 = connection.prepareStatement(
                            "DELETE FROM offline_players WHERE plr_name = ?");
                    ps4.setString(1, playerName);
                    ps4.executeUpdate();
                }

                // Insert new UUID and Name
                PreparedStatement ps5 = connection.prepareStatement(
                        "REPLACE INTO offline_players(uuid, plr_name) VALUES (?, ?)");
                ps5.setString(1, uuid.toString());
                ps5.setString(2, playerName);
                ps5.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public List<String> getOfflinePlayerNameList() {
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT DISTINCT plr_name FROM offline_players")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String name = rs.getString("plr_name");
                if (name != null) {
                    list.add(name);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public UUID getOfflinePlayerUUID(String playerName) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT uuid FROM offline_players WHERE plr_name = ?")) {
            ps.setString(1, playerName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String uuid = rs.getString("uuid");
                if (uuid != null) {
                    return UUID.fromString(uuid);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CompletableFuture<UUID> getOfflinePlayerUUIDAsync(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT uuid FROM offline_players WHERE plr_name = ?")) {
                ps.setString(1, playerName);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String uuid = rs.getString("uuid");
                    if (uuid != null) {
                        return UUID.fromString(uuid);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public CompletableFuture<String> getOfflinePlayerName(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM offline_players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getString("plr_name");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public void deleteVanishingItems() {
        this.delete_vanishing_items = true;
    }
}