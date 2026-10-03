package veth.containersearch.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class SearchScreen extends Screen {
	private static final int CELL = 18;
	private static final int MARGIN = 10;

	private final List<ItemStack> stacks = ContainerIndex.allItems();
	private double scroll = 0;

	public SearchScreen() {
		super(Component.literal("Container Search"));
	}

	private int cols() { return Math.max(1, (width - MARGIN * 2) / CELL); }
	private int rows() { return (stacks.size() + cols() - 1) / cols(); }
	private int viewHeight() { return height - MARGIN * 2; }
	private int maxScroll() { return Math.max(0, rows() * CELL - viewHeight()); }

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - scrollY * CELL));
		return true;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		int cols = cols();
		ItemStack hovered = null;

		g.enableScissor(MARGIN, MARGIN, width - MARGIN, height - MARGIN);
		for (int i = 0; i < stacks.size(); i++) {
			int x = MARGIN + (i % cols) * CELL;
			int y = MARGIN + (i / cols) * CELL - (int) scroll;
			if (y + CELL < MARGIN || y > height - MARGIN) continue;
			ItemStack st = stacks.get(i);
			g.renderItem(st, x, y);
			g.renderItemDecorations(font, st, x, y);
			if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
					&& mouseY >= MARGIN && mouseY < height - MARGIN) hovered = st;
		}
		g.disableScissor();

		if (hovered != null) g.renderTooltip(font, hovered, mouseX, mouseY);
	}
}
