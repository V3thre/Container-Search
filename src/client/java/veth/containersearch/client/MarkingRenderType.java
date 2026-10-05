package veth.containersearch.client;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import veth.containersearch.ContainerSearch;

public class MarkingRenderType {
	private static final DepthStencilState ALWAYS = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

	private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
					.withLocation(ContainerSearch.id("pipeline/lines_through_walls"))
					.withDepthStencilState(ALWAYS)
					.build());

	private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(ContainerSearch.id("pipeline/fill_through_walls"))
					.withDepthStencilState(ALWAYS)
					.withCull(false)
					.build());

	public static final RenderType LINES_THROUGH_WALLS =
			RenderType.create("container_search_lines", RenderSetup.builder(LINES_PIPELINE).createRenderSetup());

	public static final RenderType FILL_THROUGH_WALLS =
			RenderType.create("container_search_fill", RenderSetup.builder(FILL_PIPELINE).createRenderSetup());

	static void init() {}
}
