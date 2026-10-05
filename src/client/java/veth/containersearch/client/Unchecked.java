package veth.containersearch.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class Unchecked {
	static KeyMapping toggleKey;

	private record Candidate(BlockPos pos, boolean underground) {}

	private static List<Candidate> cache = List.of();
	private static List<BlockPos> shown = List.of();
	private static int ticks;
	private static final int UNDERGROUND_DEPTH = 8;

	static void register() {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.container-search.toggle_unchecked", InputConstants.UNKNOWN.getValue(), ContainerSearchClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(Unchecked::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(Unchecked::render);
	}

	static int renderDistance() {
		return Minecraft.getInstance().options.renderDistance().get();
	}

	static int effectiveChunks() {
		int rd = renderDistance();
		return Settings.uncheckedChunks <= 0 ? rd : Math.min(Settings.uncheckedChunks, rd);
	}

	private static void tick(Minecraft mc) {
		while (toggleKey.consumeClick()) {
			Settings.showUnchecked = !Settings.showUnchecked;
			Settings.save();
			if (mc.player != null) {
				mc.player.sendOverlayMessage(Component.literal("Unchecked containers: " + (Settings.showUnchecked ? "ON" : "OFF")));
			}
		}
		if (mc.level == null || mc.player == null || !Settings.showUnchecked) {
			cache = List.of();
			shown = List.of();
			return;
		}
		if (++ticks % 10 == 0) rescan(mc);
		if (ticks % 2 == 0) updateShown(mc);
	}

	private static void updateShown(Minecraft mc) {
		List<BlockPos> out = new ArrayList<>();
		for (Candidate c : cache) {
			if (!c.underground() || canSee(mc, c.pos())) out.add(c.pos());
		}
		shown = out;
	}

	private static boolean canSee(Minecraft mc, BlockPos pos) {
		Vec3 from = mc.gameRenderer.mainCamera().position();
		BlockHitResult hit = mc.level.clip(new ClipContext(from, Vec3.atCenterOf(pos),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
		if (hit.getType() == HitResult.Type.MISS) return true;
		BlockPos hp = hit.getBlockPos();
		if (hp.equals(pos)) return true;
		return mc.level.getBlockState(hp).getBlock() instanceof ChestBlock
				&& mc.level.getBlockState(pos).getBlock() instanceof ChestBlock && hp.distManhattan(pos) == 1;
	}

	static boolean isContainer(BlockEntity be) {
		return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity
				|| be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity;
	}

	private static void rescan(Minecraft mc) {
		String dim = mc.level.dimension().identifier().toString();
		BlockPos p = mc.player.blockPosition();
		int pcx = p.getX() >> 4, pcz = p.getZ() >> 4, r = effectiveChunks();

		boolean hasSky = mc.level.dimensionType().hasSkyLight();
		List<Candidate> out = new ArrayList<>();
		for (int cx = pcx - r; cx <= pcx + r; cx++) {
			for (int cz = pcz - r; cz <= pcz + r; cz++) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) continue;
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockPos pos = be.getBlockPos();
					if (!isContainer(be) || ContainerIndex.isChecked(dim, pos, mc.level)) continue;
					boolean underground = hasSky && pos.getY() < chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - UNDERGROUND_DEPTH;
					out.add(new Candidate(pos, underground));
				}
			}
		}
		cache = out;
	}

	private static void render(LevelRenderContext ctx) {
		if (!Settings.showUnchecked || shown.isEmpty()) return;
		Marking.draw(ctx, shown, Settings.uncheckedColor, 1f, Settings.uncheckedType);
	}
}
