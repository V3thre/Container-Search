package veth.containersearch.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.mojang.blaze3d.platform.InputConstants;
import veth.containersearch.ContainerSearch;

import java.util.ArrayList;
import java.util.List;


public class ContainerSearchClient implements ClientModInitializer {
	private static BlockPos lastPos;	static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ContainerSearch.id("main"));
	private static int autosaveTicks;
	private static int pruneTicks;

	private static boolean isContainerAt(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be != null && Unchecked.isContainer(be);
	}

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
			ContainerIndex.load(worldKey);
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ContainerIndex.save();
			Marking.clear();
		});
		Marking.register();
		Unchecked.register();
		Settings.load();

		//forget containers that are no longer there (destroyed)
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null && ++pruneTicks % 20 == 0) ContainerIndex.prune(client.level);
		});

		//autosave
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null || Settings.autosaveSeconds <= 0) { autosaveTicks = 0; return; }
			if (++autosaveTicks >= Settings.autosaveSeconds * 20) {
				autosaveTicks = 0;
				ContainerIndex.save();
			}
		});

		KeyMapping openKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.container-search.open", InputConstants.KEY_G, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) client.gui.setScreen(new SearchScreen());
		});

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			lastPos = hit.getBlockPos().immutable();
			return InteractionResult.PASS;
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AbstractContainerScreen<?> cs ) {
					var level = client.level;
					BlockPos opened = null;
					if (level != null) {
						if (client.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK
								&& isContainerAt(level, bhr.getBlockPos())) opened = bhr.getBlockPos().immutable();
						else if (lastPos != null && isContainerAt(level, lastPos)) opened = lastPos;
					}
					final BlockPos openedPos = opened;
					final String openedDim = level == null ? null : level.dimension().identifier().toString();

				ScreenEvents.remove(screen).register(screen1 -> {
					//screen closed
					AbstractContainerMenu menu = cs.getMenu();
						//no non container "container" screens
						boolean isContainer = menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu
								|| menu instanceof HopperMenu || menu instanceof DispenserMenu;
						if (openedPos == null || !isContainer || client.level == null) return;
					List<ItemStack> items = new ArrayList<>();
					for (Slot slot : cs.getMenu().slots) {
						if (slot.container instanceof Inventory) continue;
						ItemStack st = slot.getItem();
						if (st.isEmpty()) continue;
						items.add(st.copy());
					}
						//store a double chest as the lower position
						BlockPos pos = openedPos, otherHalf = null;
						BlockState state = Minecraft.getInstance().level.getBlockState(pos);
						if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
							otherHalf = pos.relative(ChestBlock.getConnectedDirection(state));
							if (otherHalf.compareTo(pos) < 0) { BlockPos t = pos; pos = otherHalf; otherHalf = t; }
						}
						ContainerIndex.record(openedDim, pos, items);
						if (pos.equals(Marking.target)) Marking.clear();
						if (otherHalf != null) ContainerIndex.remove(openedDim, otherHalf);
						lastPos = null;
				});
			}
		});
	}

}