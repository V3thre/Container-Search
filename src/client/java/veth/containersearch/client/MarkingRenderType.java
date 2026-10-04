package veth.containersearch.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

import java.util.OptionalDouble;

public class MarkingRenderType extends RenderType {
	private MarkingRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
							  boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
	}

	public static final RenderType LINES_THROUGH_WALLS = create(
			"container_search_lines",
			DefaultVertexFormat.POSITION_COLOR_NORMAL,
			VertexFormat.Mode.LINES,
			1536,
			false,
			false,
			CompositeState.builder()
					.setShaderState(RENDERTYPE_LINES_SHADER)
					.setLineState(new LineStateShard(OptionalDouble.of(2.0)))
					.setLayeringState(VIEW_OFFSET_Z_LAYERING)
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setWriteMaskState(COLOR_DEPTH_WRITE)
					.setDepthTestState(NO_DEPTH_TEST)
					.setCullState(NO_CULL)
					.createCompositeState(false));
}
