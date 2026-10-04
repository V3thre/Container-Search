package veth.containersearch.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

		Vec3 cam = ctx.camera().getPosition();
		PoseStack ps = ctx.matrixStack();
		MultiBufferSource.BufferSource buf = (MultiBufferSource.BufferSource) ctx.consumers();

		ps.pushPose();
		ps.translate(-cam.x, -cam.y, -cam.z);
		AABB box = new AABB(target);
		float r = (Settings.markColor >> 16 & 255) / 255f;
		float gr = (Settings.markColor >> 8 & 255) / 255f;
		float b = (Settings.markColor & 255) / 255f;

		if (Settings.markType != Settings.MarkType.OUTLINE) {
			ShapeRenderer.addChainedFilledBoxVertices(ps, buf.getBuffer(MarkingRenderType.FILL_THROUGH_WALLS),
					box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, gr, b, 0.3f * alpha);
			buf.endBatch(MarkingRenderType.FILL_THROUGH_WALLS);
		}
		if (Settings.markType != Settings.MarkType.FILL) {
			ShapeRenderer.renderLineBox(ps, buf.getBuffer(MarkingRenderType.LINES_THROUGH_WALLS), box, r, gr, b, alpha);
			buf.endBatch(MarkingRenderType.LINES_THROUGH_WALLS);
		}
		ps.popPose();
	}
}
