package veth.containersearch.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.JsonOps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
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

    record Entry(List<JsonElement> stacks, long time) {}
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
        dirty = true;
        ContainerSearch.LOGGER.info("RECORD {} -> {} stacks (dirty)", key, encoded.size());
    }
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

    static List<ItemStack> allItems() {
        List<ItemStack> out = new ArrayList<>();
        for (var dim : data.values()) {
            for (var chest : dim.values()) {
                if (chest.stacks() == null) continue;
                for (JsonElement el : chest.stacks()) {
                    ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(ops(), el).result().orElse(ItemStack.EMPTY);
                    if (!stack.isEmpty()) out.add(stack);
                }
            }
        }
        return out;
    }
}
