package veth.containersearch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class Marking {
	static BlockPos target;
	static String dim;

	static long start;
	private static final long FADE_MS = 1000;

	static void set(String d, BlockPos pos) { dim = d; target = pos; start = System.currentTimeMillis(); }
	static void clear() { target = null; }

	static void register() {
		WorldRenderEvents.LAST.register(Marking::render);
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (target == null || mc.level == null) return;
		if (!dim.equals(mc.level.dimension().location().toString())) return;
		if (!mc.level.hasChunkAt(target)) return;

		float alpha = 1f;
		if (Settings.fadeSeconds > 0) {
			long hold = Settings.fadeSeconds * 1000L, t = System.currentTimeMillis() - start;
			if (t >= hold + FADE_MS) { clear(); return; }
			if (t > hold) alpha = 1f - (t - hold) / (float) FADE_MS;
		}

		draw(ctx, List.of(target), Settings.markColor, alpha, Settings.markType);
	}

	static void draw(WorldRenderContext ctx, List<BlockPos> blocks, int color, float alpha, Settings.MarkType type) {
		Vec3 cam = ctx.camera().getPosition();
		PoseStack ps = ctx.matrixStack();
		MultiBufferSource.BufferSource buf = (MultiBufferSource.BufferSource) ctx.consumers();
		float r = (color >> 16 & 255) / 255f;
		float gr = (color >> 8 & 255) / 255f;
		float b = (color & 255) / 255f;

		ps.pushPose();
		ps.translate(-cam.x, -cam.y, -cam.z);

		if (type != Settings.MarkType.OUTLINE) {
			VertexConsumer fill = buf.getBuffer(MarkingRenderType.FILL_THROUGH_WALLS);
			for (BlockPos p : blocks) {
				AABB box = new AABB(p);
				ShapeRenderer.addChainedFilledBoxVertices(ps, fill,
						box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, gr, b, 0.3f * alpha);
			}
			buf.endBatch(MarkingRenderType.FILL_THROUGH_WALLS);
		}
		if (type != Settings.MarkType.FILL) {
			VertexConsumer lines = buf.getBuffer(MarkingRenderType.LINES_THROUGH_WALLS);
			for (BlockPos p : blocks) ShapeRenderer.renderLineBox(ps, lines, new AABB(p), r, gr, b, alpha);
			buf.endBatch(MarkingRenderType.LINES_THROUGH_WALLS);
		}

		ps.popPose();
	}
}
