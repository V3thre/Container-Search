package veth.containersearch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
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

	/*
	 * Renders marking on a box, this is self contained on purpose to avoid issues that can come from rendering mods,
	 * many of which were found while testing, which is why it uses its own PoseStack and buffer, an explicit model-view
	 * matrix, no fog and a neutral shader color.
	 */
	static void draw(WorldRenderContext ctx, List<BlockPos> blocks, int color, float alpha, Settings.MarkType type) {
		Vec3 cam = ctx.camera().getPosition();
		float r = (color >> 16 & 255) / 255f;
		float gr = (color >> 8 & 255) / 255f;
		float b = (color & 255) / 255f;

		PoseStack mv = RenderSystem.getModelViewStack();
		mv.pushPose();
		mv.setIdentity();
		RenderSystem.applyModelViewMatrix();
		float oldFogStart = RenderSystem.getShaderFogStart();
		float oldFogEnd = RenderSystem.getShaderFogEnd();
		RenderSystem.setShaderFogStart(1.0E9f);
		RenderSystem.setShaderFogEnd(2.0E9f);
		float[] oldColor = RenderSystem.getShaderColor().clone();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

		PoseStack ps = new PoseStack();
		ps.mulPoseMatrix(ctx.matrixStack().last().pose());

		if (type != Settings.MarkType.OUTLINE) {
			BufferBuilder fill = begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
			for (BlockPos p : blocks) {
				AABB box = new AABB(p).move(-cam.x, -cam.y, -cam.z);
				LevelRenderer.addChainedFilledBoxVertices(ps, fill,
						box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, gr, b, 0.3f * alpha);
			}
			flush(MarkingRenderType.FILL_THROUGH_WALLS, fill);
		}
		if (type != Settings.MarkType.FILL) {
			BufferBuilder lines = begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
			for (BlockPos p : blocks) {
				LevelRenderer.renderLineBox(ps, lines, new AABB(p).move(-cam.x, -cam.y, -cam.z), r, gr, b, alpha);
			}
			flush(MarkingRenderType.LINES_THROUGH_WALLS, lines);
		}

		RenderSystem.setShaderColor(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
		RenderSystem.setShaderFogStart(oldFogStart);
		RenderSystem.setShaderFogEnd(oldFogEnd);
		mv.popPose();
		RenderSystem.applyModelViewMatrix();
	}

	private static BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
		BufferBuilder builder = Tesselator.getInstance().getBuilder();
		builder.begin(mode, format);
		return builder;
	}

	private static void flush(RenderType type, BufferBuilder builder) {
		type.end(builder, VertexSorting.DISTANCE_TO_ORIGIN);
	}
}
