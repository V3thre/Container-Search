package veth.containersearch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.List;

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
		for (BlockPos p : blocks) {
			ps.pushPose();
			ps.translate(p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z);
			if (type != Settings.MarkType.OUTLINE) {
				ctx.submitNodeCollector().submitCustomGeometry(ps, MarkingRenderType.FILL_THROUGH_WALLS, (pose, buffer) -> fillBox(buffer, pose, fillArgb));
			}
			if (type != Settings.MarkType.FILL) {
				ctx.submitNodeCollector().submitShapeOutline(ps, Shapes.block(), MarkingRenderType.LINES_THROUGH_WALLS, argb, 2.0f, true);
			}
			ps.popPose();
		}
	}

	private static void fillBox(VertexConsumer b, PoseStack.Pose pose, int argb) {
		float x0 = 0f, y0 = 0f, z0 = 0f, x1 = 1f, y1 = 1f, z1 = 1f;
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
