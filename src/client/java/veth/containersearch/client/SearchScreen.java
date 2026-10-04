package veth.containersearch.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class SearchScreen extends Screen {
	private static final int CELL = 18, MARGIN = 10, BAR = 24;
	private static final int STEPS = 63;
	private static int range = 16;

	private List<ItemStack> stacks = List.of();
	private double scroll = 0;

	public SearchScreen() {
		super(Component.literal("Container Search"));
	}

	@Override
	protected void init() {
		refresh();
		addRenderableWidget(new RangeSlider(width / 2 - 100, height - BAR + 2, 200, 20));
	}

	private void refresh() {
		Minecraft mc = Minecraft.getInstance();
		BlockPos p = mc.player.blockPosition();
		stacks = ContainerIndex.allItems(mc.level.dimension().location().toString(), p.getX() >> 4, p.getZ() >> 4, range);
		scroll = Math.min(scroll, maxScroll());
	}

	private int listTop() { return BAR; }
	private int listBottom() { return height - BAR; }
	private int cols() { return Math.max(1, (width - MARGIN * 2) / CELL); }
	private int rows() { return (stacks.size() + cols() - 1) / cols(); }
	private int maxScroll() { return Math.max(0, rows() * CELL - (listBottom() - listTop())); }

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - scrollY * CELL));
		return true;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.renderBackground(g, mouseX, mouseY, delta);
		g.fill(0, 0, width, BAR, 0x47404040);
		g.fill(0, height - BAR, width, height, 0x47404040);
		g.drawCenteredString(font, title, width / 2, (BAR - font.lineHeight) / 2, 0xFFFFFFFF);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);               // background, bars and slider
		int cols = cols();
		ItemStack hovered = null;

		g.enableScissor(MARGIN, listTop(), width - MARGIN, listBottom());
		for (int i = 0; i < stacks.size(); i++) {
			int x = MARGIN + (i % cols) * CELL;
			int y = listTop() + (i / cols) * CELL - (int) scroll;
			if (y + CELL < listTop() || y > listBottom()) continue;
			ItemStack st = stacks.get(i);
			g.renderItem(st, x, y);
			g.renderItemDecorations(font, st, x, y);
			if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
					&& mouseY >= listTop() && mouseY < listBottom()) hovered = st;
		}
		g.disableScissor();

		if (hovered != null) g.renderTooltip(font, hovered, mouseX, mouseY);
	}

	private class RangeSlider extends AbstractSliderButton {
		RangeSlider(int x, int y, int w, int h) {
			super(x, y, w, h, Component.empty(), (range == ContainerIndex.INFINITE ? STEPS : range - 2) / (double) STEPS);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(range == ContainerIndex.INFINITE ? "Range: Infinite" : "Range: " + range + " chunks"));
		}

		@Override
		protected void applyValue() {
			int i = (int) Math.round(value * STEPS);
			range = (i == STEPS) ? ContainerIndex.INFINITE : i + 2;
			refresh();
		}
	}
}
