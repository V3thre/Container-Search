package veth.containersearch.client;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import veth.containersearch.ContainerSearch;

public class MarkingRenderType {
	private static final DepthStencilState ALWAYS = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

	private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
					.withLocation(ContainerSearch.id("pipeline/lines_through_walls"))
					.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
					.withDepthStencilState(ALWAYS)
					.build());

	private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(ContainerSearch.id("pipeline/fill_through_walls"))
					.withDepthStencilState(ALWAYS)
					.withCull(false)
					.build());

	private static final OitPipelineSet LINES_OIT = RenderPipelines.register(
			OitPipelineSet.builder("container_search_lines", RenderPipeline.builder(RenderPipelines.OIT_LINES_SNIPPET))
					.withoutDepthTest()
					.build());

	private static final OitPipelineSet FILL_OIT = RenderPipelines.register(
			OitPipelineSet.builder("container_search_fill", RenderPipeline.builder(RenderPipelines.OIT_DEBUG_FILLED_SNIPPET))
					.withoutDepthTest()
					.build());

	public static final RenderType LINES_THROUGH_WALLS =
			RenderType.create("container_search_lines", RenderSetup.builder(LINES_PIPELINE).setOitPipelines(LINES_OIT).createRenderSetup());

	public static final RenderType FILL_THROUGH_WALLS =
			RenderType.create("container_search_fill", RenderSetup.builder(FILL_PIPELINE).setOitPipelines(FILL_OIT).createRenderSetup());

	static void init() {}
}
