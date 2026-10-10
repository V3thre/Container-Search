package veth.containersearch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Marking {
	static BlockPos target;
	static String dim;

	static long start;
	private static final long FADE_MS = 1000;

	private static final int[][] FACES = {
			{0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
			{0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0},
			{0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0},
			{0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1},
			{0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
			{1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1}};

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

		Matrix4fStack mv = RenderSystem.getModelViewStack();
		mv.pushMatrix();
		mv.identity();
		FogParameters oldFog = RenderSystem.getShaderFog();
		RenderSystem.setShaderFog(FogParameters.NO_FOG);
		float[] oldColor = RenderSystem.getShaderColor().clone();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

		PoseStack ps = new PoseStack();
		ps.mulPose(ctx.positionMatrix());
		List<AABB> boxes = boxes(blocks, cam);

		if (type != Settings.MarkType.OUTLINE) {
			BufferBuilder fill = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
			Matrix4f pose = ps.last().pose();
			for (AABB box : boxes) {
				float[] lo = {(float) box.minX, (float) box.minY, (float) box.minZ};
				float[] hi = {(float) box.maxX, (float) box.maxY, (float) box.maxZ};
				for (int[] face : FACES) {
					for (int i = 0; i < 12; i += 3) {
						fill.addVertex(pose, (face[i] == 0 ? lo : hi)[0], (face[i + 1] == 0 ? lo : hi)[1], (face[i + 2] == 0 ? lo : hi)[2])
								.setColor(r, gr, b, 0.3f * alpha);
					}
				}
			}
			flush(MarkingRenderType.FILL_THROUGH_WALLS, fill);
		}
		if (type != Settings.MarkType.FILL) {
			BufferBuilder lines = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
			for (AABB box : boxes) {
				ShapeRenderer.renderLineBox(ps, lines, box, r, gr, b, alpha);
			}
			flush(MarkingRenderType.LINES_THROUGH_WALLS, lines);
		}

		RenderSystem.setShaderColor(oldColor[0], oldColor[1], oldColor[2], oldColor[3]);
		RenderSystem.setShaderFog(oldFog);
		mv.popMatrix();
	}

	private static List<AABB> boxes(List<BlockPos> blocks, Vec3 cam) {
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
			out.add(box.move(-cam.x, -cam.y, -cam.z));
		}
		return out;
	}

	private static void flush(RenderType type, BufferBuilder builder) {
		MeshData mesh = builder.build();
		if (mesh == null) return;
		type.setupRenderState();
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(GL11.GL_ALWAYS);
		RenderSystem.depthMask(false);
		BufferUploader.drawWithShader(mesh);
		RenderSystem.depthMask(true);
		type.clearRenderState();
	}
}
