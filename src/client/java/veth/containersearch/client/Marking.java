package veth.containersearch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Marking {
	static BlockPos target;
	static String dim;

	static long start;
	private static final long FADE_MS = 1000;

	static void set(String d, BlockPos pos) { dim = d; target = pos; start = System.currentTimeMillis(); }
	static void clear() { target = null; }

	static void register() {
		MarkingRenderType.init();
		LevelRenderEvents.COLLECT_SUBMITS.register(Marking::render);
	}

	private static void render(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (target == null || mc.level == null) return;
		if (!dim.equals(mc.level.dimension().identifier().toString())) return;
		if (!mc.level.hasChunkAt(target)) return;

		float alpha = 1f;
		if (Settings.fadeSeconds > 0) {
			long hold = Settings.fadeSeconds * 1000L, t = System.currentTimeMillis() - start;
			if (t >= hold + FADE_MS) { clear(); return; }
			if (t > hold) alpha = 1f - (t - hold) / (float) FADE_MS;
		}

		draw(ctx, List.of(target), Settings.markColor, alpha, Settings.markType);
	}

	/*
	 * Renders marking on a box, this is self contained on purpose to avoid issues that can come from rendering mods,
	 * many of which were found while testing, which is why it uses its own PoseStack and buffer, an explicit model-view
	 * matrix, no fog and a neutral shader color.
	 */
	static void draw(LevelRenderContext ctx, List<BlockPos> blocks, int color, float alpha, Settings.MarkType type) {
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		int argb = ((int) (alpha * 255f) << 24) | (color & 0xFFFFFF);
		int fillArgb = ((int) (alpha * 0.3f * 255f) << 24) | (color & 0xFFFFFF);

		PoseStack ps = new PoseStack();
		for (AABB box : boxes(blocks)) {
			ps.pushPose();
			ps.translate(box.minX - cam.x, box.minY - cam.y, box.minZ - cam.z);
			float dx = (float) box.getXsize(), dy = (float) box.getYsize(), dz = (float) box.getZsize();
			if (type != Settings.MarkType.OUTLINE) {
				ctx.submitNodeCollector().submitCustomGeometry(ps, MarkingRenderType.FILL_THROUGH_WALLS, (pose, buffer) -> fillBox(buffer, pose, fillArgb, dx, dy, dz));
			}
			if (type != Settings.MarkType.FILL) {
				ctx.submitNodeCollector().submitShapeOutline(ps, Shapes.create(0, 0, 0, dx, dy, dz), MarkingRenderType.LINES_THROUGH_WALLS, argb, 2.0f, true);
			}
			ps.popPose();
		}
	}

	private static List<AABB> boxes(List<BlockPos> blocks) {
		Level level = Minecraft.getInstance().level;
		Set<BlockPos> done = new HashSet<>();
		List<AABB> out = new ArrayList<>();
		for (BlockPos p : blocks) {
			if (!done.add(p)) continue;
			AABB box = new AABB(p);
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				BlockPos other = p.relative(ChestBlock.getConnectedDirection(s));
				done.add(other);
				box = box.minmax(new AABB(other));
			}
			out.add(box);
		}
		return out;
	}

	private static void fillBox(VertexConsumer b, PoseStack.Pose pose, int argb, float dx, float dy, float dz) {
		float x0 = 0f, y0 = 0f, z0 = 0f, x1 = dx, y1 = dy, z1 = dz;
		quad(b, pose, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(b, pose, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(b, pose, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(b, pose, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(b, pose, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(b, pose, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(VertexConsumer b, PoseStack.Pose pose, int argb, float... v) {
		for (int i = 0; i < 12; i += 3) b.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(argb);
	}
}
