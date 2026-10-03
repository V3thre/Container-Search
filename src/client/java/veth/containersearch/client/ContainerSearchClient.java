package veth.containersearch.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import veth.containersearch.ContainerSearch;

import java.util.HashMap;
import java.util.Map;


public class ContainerSearchClient implements ClientModInitializer {
	private static BlockPos lastPos;
	private static ResourceLocation lastDim;
	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
			Minecraft client = Minecraft.getInstance();
			String worldKey;
			if (client.getSingleplayerServer() != null) {
				worldKey = "sp_" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize().getFileName();
			} else if (client.getCurrentServer() != null) {
				worldKey = "mp_" + client.getCurrentServer().ip;
			} else {
				worldKey = "unknown";
			}
			worldKey = worldKey.replaceAll("[^A-Za-z0-9._-]", "_");

			ContainerSearch.LOGGER.info("JOIN key={} multiplayer={}", worldKey, client.getCurrentServer() != null);
			ContainerIndex.load(worldKey);
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ContainerIndex.save());

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			lastPos = hit.getBlockPos();
			lastDim = level.dimension().location();
			ContainerSearch.LOGGER.info("CLICK {} in {}", lastPos, lastDim);
			return InteractionResult.PASS;
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AbstractContainerScreen<?> cs ) {

				ScreenEvents.remove(screen).register(screen1 -> {
					//screen closed
					if (lastPos == null) return;
					Map<String, Integer> items = new HashMap<>();
					for (Slot slot : cs.getMenu().slots) {
						if (slot.container instanceof Inventory) continue;
						ItemStack st = slot.getItem();
						if (st.isEmpty()) continue;
						String name = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
						items.merge(name, st.getCount(), Integer::sum);
					}
					ContainerSearch.LOGGER.info("CLOSED container at {} items={}", lastPos, items);
					ContainerIndex.record(lastDim.toString(), lastPos, items);
				});
			}
		});
	}

}