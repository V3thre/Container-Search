package veth.containersearch.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import veth.containersearch.ContainerSearch;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContainerIndex {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Map<String, Entry>> data = new HashMap<>();
    private static Map<String, List<Hit>> byItem = new HashMap<>();
    private static Path file;
    private static boolean dirty;

    record Entry(Map<String, Integer> items, long time) {}
    record Hit(String dim, String pos, int count) {}

    static void load(String worldKey) {
        file = FabricLoader.getInstance().getConfigDir().resolve("containersearch").resolve(worldKey + ".json");
        data = new HashMap<>();
        dirty = false;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                Map<String, Map<String, Entry>> loaded =
                        GSON.fromJson(r, new TypeToken<Map<String, Map<String, Entry>>>() {}.getType());
                if (loaded != null) data = loaded;
            } catch (Exception e) {
                ContainerSearch.LOGGER.error("Failed to load {}", file, e);
            }
        }
        rebuild();
        ContainerSearch.LOGGER.info("LOAD {} exists={} dims={}", file, Files.exists(file), data.size());
    }

    private static void rebuild() {
        byItem.clear();
        for (var dim : data.entrySet()) {
            for (var chest : dim.getValue().entrySet()) {
                for (var item : chest.getValue().items().entrySet()) {
                    byItem.computeIfAbsent(item.getKey(), k -> new ArrayList<>())
                            .add(new Hit(dim.getKey(), chest.getKey(), item.getValue()));
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
    static void record(String dim, BlockPos pos, Map<String, Integer> items) {
        String key = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        Map<String, Entry> inDim = data.computeIfAbsent(dim, d -> new HashMap<>());
        inDim.put(key, new Entry(items, System.currentTimeMillis()));
        rebuild();
        dirty = true;
        ContainerSearch.LOGGER.info("RECORD {} -> {} (dirty)", key, items);
    }
    static void remove(String dim, BlockPos pos) {

    }
    static List<Hit> search(String text) {
        return List.of();
    }
}
