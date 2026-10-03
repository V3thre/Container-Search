package veth.containersearch.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;
import veth.containersearch.ContainerSearch;

import java.util.ArrayList;
import java.util.List;


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

		KeyMapping openKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.container-search.open", GLFW.GLFW_KEY_G, "key.categories.misc"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) client.setScreen(new SearchScreen());
		});

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
					AbstractContainerMenu menu = cs.getMenu();
						//no non container "container" screens
						boolean isContainer = menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu
								|| menu instanceof HopperMenu || menu instanceof DispenserMenu;
						if (lastPos == null || !isContainer) return;
					List<ItemStack> items = new ArrayList<>();
					for (Slot slot : cs.getMenu().slots) {
						if (slot.container instanceof Inventory) continue;
						ItemStack st = slot.getItem();
						if (st.isEmpty()) continue;
						items.add(st.copy());
					}
					ContainerSearch.LOGGER.info("CLOSED container at {} stacks={}", lastPos, items.size());
					//store a double chest as the lower position
						BlockPos pos = lastPos, otherHalf = null;
						BlockState state = Minecraft.getInstance().level.getBlockState(pos);
						if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
							otherHalf = pos.relative(ChestBlock.getConnectedDirection(state));
							if (otherHalf.compareTo(pos) < 0) { BlockPos t = pos; pos = otherHalf; otherHalf = t; }
						}
						ContainerIndex.record(lastDim.toString(), pos, items);
						if (otherHalf != null) ContainerIndex.remove(lastDim.toString(), otherHalf);   // also clears old duplicates
						lastPos = null;
				});
			}
		});
	}

}