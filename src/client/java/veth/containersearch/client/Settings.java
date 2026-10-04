package veth.containersearch.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import veth.containersearch.ContainerSearch;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public class Settings {
	enum MarkType {
		OUTLINE("Outline"), FILL("Fill"), BOTH("Outline + Fill");
		final String label;
		MarkType(String label) { this.label = label; }
	}

	static int minChunks = 2, maxChunks = 64;
	static MarkType markType = MarkType.OUTLINE;
	static int markColor = 0xFFFFFF;
	static int fadeSeconds = 0; //0 means it never fades
	static int autosaveSeconds = 60;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("container-search.json");

	private record Data(int minChunks, int maxChunks, MarkType markType, int markColor, int fadeSeconds, int autosaveSeconds) {}

	static void load() {
		if (!Files.exists(FILE)) return;
		try (Reader r = Files.newBufferedReader(FILE)) {
			Data d = GSON.fromJson(r, Data.class);
			if (d == null) return;
			minChunks = d.minChunks();
			maxChunks = d.maxChunks();
			markType = d.markType();
			markColor = d.markColor();
			fadeSeconds = d.fadeSeconds();
			autosaveSeconds = d.autosaveSeconds();
			normalize();
		} catch (Exception e) {
			ContainerSearch.LOGGER.error("Failed to load {}", FILE, e);
		}
	}

	static void save() {
		normalize();
		try {
			Files.writeString(FILE, GSON.toJson(new Data(minChunks, maxChunks, markType, markColor, fadeSeconds, autosaveSeconds)));
		} catch (IOException e) {
			ContainerSearch.LOGGER.error("Failed to save {}", FILE, e);
		}
	}

	static void normalize() {
		minChunks = Math.max(1, minChunks);
		maxChunks = Math.max(minChunks, maxChunks);
		if (markType == null) markType = MarkType.OUTLINE;
		fadeSeconds = Math.max(0, fadeSeconds);
		autosaveSeconds = Math.max(0, autosaveSeconds);
	}
}
