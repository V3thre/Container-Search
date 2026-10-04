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

	static void set(String d, BlockPos pos) { dim = d; target = pos; }
	static void clear() { target = null; }

	static void register() {
		WorldRenderEvents.LAST.register(Marking::render);
	}

	private static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (target == null || mc.level == null) return;
		if (!dim.equals(mc.level.dimension().location().toString())) return;
		if (!mc.level.hasChunkAt(target)) return;

		Vec3 cam = ctx.camera().getPosition();
		PoseStack ps = ctx.matrixStack();
		MultiBufferSource.BufferSource buf = (MultiBufferSource.BufferSource) ctx.consumers();

		ps.pushPose();
		ps.translate(-cam.x, -cam.y, -cam.z);
		ShapeRenderer.renderLineBox(ps, buf.getBuffer(MarkingRenderType.LINES_THROUGH_WALLS), new AABB(target), 1f, 1f, 1f, 1f);
		buf.endBatch(MarkingRenderType.LINES_THROUGH_WALLS);
		ps.popPose();
	}
}
