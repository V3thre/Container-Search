package veth.containersearch.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import veth.containersearch.ContainerSearch;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContainerIndex {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Map<String, Entry>> data = new HashMap<>();
    private static Map<String, List<Hit>> byItem = new HashMap<>();
    private static Path file;
    private static boolean dirty;

    record Entry(List<JsonElement> stacks, long time) {}
    record Hit(String dim, String pos, int count) {}

    static void load(String worldKey) {
        file = FabricLoader.getInstance().getConfigDir().resolve("containersearch").resolve(worldKey + ".json");
        data = new HashMap<>();
        dirty = false;
        if (Files.exists(file)) {
            try {
                Map<String, Map<String, Entry>> loaded = read(file);
                if (loaded != null) data = loaded;
            } catch (Exception e) {
                ContainerSearch.LOGGER.error("Failed to load {}", file, e);
            }
        }
        rebuild();
        ContainerSearch.LOGGER.info("LOAD {} exists={} dims={}", file, Files.exists(file), data.size());
    }

    private static Map<String, Map<String, Entry>> read(Path p) throws IOException {
        try (Reader r = Files.newBufferedReader(p)) {
            return GSON.fromJson(r, new TypeToken<Map<String, Map<String, Entry>>>() {}.getType());
        }
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    static boolean openConfigFolder() {
        Util.getPlatform().openPath(FabricLoader.getInstance().getConfigDir());
        return true;
    }

    static boolean openSavesFolder() {
        try {
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("containersearch");
            Files.createDirectories(dir);
            Util.getPlatform().openPath(dir);
            return true;
        } catch (IOException e) {
            ContainerSearch.LOGGER.error("Could not open saves folder", e);
            return false;
        }
    }

    private static Path backupDir() {
        String name = file.getFileName().toString().replaceFirst("\\.json$", "");
        return file.getParent().resolve("backups").resolve(name);
    }

    static boolean openBackupFolder() {
        if (file == null) return false;
        try {
            Files.createDirectories(backupDir());
            Util.getPlatform().openPath(backupDir());
            return true;
        } catch (IOException e) {
            ContainerSearch.LOGGER.error("Could not open backup folder", e);
            return false;
        }
    }

    static boolean backup() {
        if (file == null) return false;
        try {
            Files.createDirectories(backupDir());
            Path out = backupDir().resolve(LocalDateTime.now().format(STAMP) + ".json");
            Files.writeString(out, GSON.toJson(data));
            ContainerSearch.LOGGER.info("BACKUP {}", out);
            return true;
        } catch (IOException e) {
            ContainerSearch.LOGGER.error("Backup failed", e);
            return false;
        }
    }

    static boolean restoreLatest() {
        if (file == null || !Files.isDirectory(backupDir())) return false;
        try (var files = Files.list(backupDir())) {
            Path latest = files.filter(p -> p.toString().endsWith(".json"))
                    .max(Comparator.comparing(p -> p.getFileName().toString())).orElse(null);
            if (latest == null) return false;
            Map<String, Map<String, Entry>> loaded = read(latest);
            if (loaded == null) return false;
            data = loaded;
            rebuild();
            dirty = true;
            save();
            ContainerSearch.LOGGER.info("RESTORED {}", latest);
            return true;
        } catch (Exception e) {
            ContainerSearch.LOGGER.error("Restore failed", e);
            return false;
        }
    }

    static boolean deleteLatest() {
        if (file == null || !Files.isDirectory(backupDir())) return false;
        try (var files = Files.list(backupDir())) {
            Path latest = files.filter(p -> p.toString().endsWith(".json"))
                    .max(Comparator.comparing(p -> p.getFileName().toString())).orElse(null);
            if (latest == null) return false;
            Files.deleteIfExists(latest);
            return true;
        } catch (IOException e) {
            ContainerSearch.LOGGER.error("deletion failed", e);
            return false;
        }
    }

    private static void rebuild() {
        byItem.clear();
        for (var dim : data.entrySet()) {
            for (var chest : dim.getValue().entrySet()) {
                if (chest.getValue().stacks() == null) continue;
                for (JsonElement el : chest.getValue().stacks()) {
                    JsonObject o = el.getAsJsonObject();
                    int count = o.has("count") ? o.get("count").getAsInt() : 1;
                    byItem.computeIfAbsent(o.get("id").getAsString(), k -> new ArrayList<>())
                            .add(new Hit(dim.getKey(), chest.getKey(), count));
                }
            }
        }
    }

    static void save() {
        if (!dirty || file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(data));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
            ContainerSearch.LOGGER.info("SAVED {}", file);
        } catch (IOException e) {
            ContainerSearch.LOGGER.error("Failed to save {}", file, e);
        }
    }
    private static RegistryOps<JsonElement> ops() {
        return Minecraft.getInstance().level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    static void record(String dim, BlockPos pos, List<ItemStack> stacks) {
        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        List<JsonElement> encoded = new ArrayList<>();
        for (ItemStack st : stacks) {
            ItemStack.OPTIONAL_CODEC.encodeStart(ops(), st).result().ifPresent(encoded::add);
        }
        Map<String, Entry> inDim = data.computeIfAbsent(dim, d -> new HashMap<>());
        inDim.put(key, new Entry(encoded, System.currentTimeMillis()));
        rebuild();
        dirty = true;    }
    static void remove(String dim, BlockPos pos) {
        Map<String, Entry> inDim = data.get(dim);
        if (inDim == null) return;
        if (inDim.remove(pos.getX() + "," + pos.getY() + "," + pos.getZ()) != null) {
            rebuild();
            dirty = true;
        }
    }

    static List<Hit> search(String text) {
        return List.of();
    }

    static boolean isChecked(String dim, BlockPos pos, Level level) {
        Map<String, Entry> inDim = data.get(dim);
        if (inDim == null) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (other.compareTo(pos) < 0) pos = other;
        }
        return inDim.containsKey(pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    static final int INFINITE = -1;

    record Found(ItemStack stack, String dim, BlockPos pos) {}

    static List<Found> allItems(String playerDim, int chunkX, int chunkZ, int range) {
        List<Found> out = new ArrayList<>();
        for (var dim : data.entrySet()) {
            if (range != INFINITE && !dim.getKey().equals(playerDim)) continue;
            for (var chest : dim.getValue().entrySet()) {
                if (chest.getValue().stacks() == null) continue;
                String[] p = chest.getKey().split(",");
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                if (range != INFINITE) {
                    int dx = Math.abs((pos.getX() >> 4) - chunkX);
                    int dz = Math.abs((pos.getZ() >> 4) - chunkZ);
                    if (Math.max(dx, dz) > range) continue;
                }
                for (JsonElement el : chest.getValue().stacks()) {
                    ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(ops(), el).result().orElse(ItemStack.EMPTY);
                    if (!stack.isEmpty()) out.add(new Found(stack, dim.getKey(), pos));
                }
            }
        }
        return out;
    }
}
