package veth.containersearch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix4fStack;

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
		LevelRenderEvents.END_MAIN.register(Marking::render);
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
		CameraRenderState camera = ctx.levelState().cameraRenderState;
		Vec3 cam = camera.pos;
		int argb = ((int) (alpha * 255f) << 24) | (color & 0xFFFFFF);
		int fillArgb = ((int) (alpha * 0.3f * 255f) << 24) | (color & 0xFFFFFF);

		Matrix4fStack mv = RenderSystem.getModelViewStack();
		mv.pushMatrix();
		mv.identity();

		PoseStack ps = new PoseStack();
		ps.mulPose(camera.viewRotationMatrix);

		if (type != Settings.MarkType.OUTLINE) {
			BufferBuilder fill = begin(MarkingRenderType.FILL_THROUGH_WALLS);
			for (BlockPos p : blocks) {
				fillBox(fill, ps.last(), p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z, fillArgb);
			}
			flush(MarkingRenderType.FILL_THROUGH_WALLS, fill);
		}
		if (type != Settings.MarkType.FILL) {
			BufferBuilder lines = begin(MarkingRenderType.LINES_THROUGH_WALLS);
			for (BlockPos p : blocks) {
				ShapeRenderer.renderShape(ps, lines, Shapes.block(), p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z, argb, 2.0f);
			}
			flush(MarkingRenderType.LINES_THROUGH_WALLS, lines);
		}

		mv.popMatrix();
	}

	private static BufferBuilder begin(RenderType type) {
		return Tesselator.getInstance().begin(type.mode(), type.format());
	}

	private static void flush(RenderType type, BufferBuilder builder) {
		MeshData mesh = builder.build();
		if (mesh != null) type.draw(mesh);
	}

	private static void fillBox(BufferBuilder b, PoseStack.Pose pose, double x, double y, double z, int argb) {
		float x0 = (float) x, y0 = (float) y, z0 = (float) z, x1 = x0 + 1f, y1 = y0 + 1f, z1 = z0 + 1f;
		quad(b, pose, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(b, pose, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(b, pose, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(b, pose, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(b, pose, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(b, pose, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(BufferBuilder b, PoseStack.Pose pose, int argb, float... v) {
		for (int i = 0; i < 12; i += 3) b.addVertex(pose, v[i], v[i + 1], v[i + 2]).setColor(argb);
	}
}
