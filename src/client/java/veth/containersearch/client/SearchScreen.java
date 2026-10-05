package veth.containersearch.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SearchScreen extends Screen {
	private static final int CELL = 18, MARGIN = 10;
	static final int BAR = 24;
	private static int steps() { return Settings.maxChunks - Settings.minChunks + 1; }
	private static int range = 16;

	private List<ContainerIndex.Found> stacks = List.of();
	private double scroll = 0;
	private String query = "";

	public SearchScreen() {
		super(Component.literal("Container Search"));
	}

	@Override
	protected void init() {
		if (range != ContainerIndex.INFINITE) range = Math.max(Settings.minChunks, Math.min(Settings.maxChunks, range));
		refresh();
		addRenderableWidget(new RangeSlider(width / 2 - 100, height - BAR + 2, 200, 20));
		addRenderableWidget(Button.builder(Component.literal("Settings"), b -> minecraft.setScreen(new SettingsScreen(this)))
				.bounds(width - 74, 2, 70, 20).build());

		//search bar
		int boxW = Math.max(60, Math.min(170, width / 2 - 70 - MARGIN));
		EditBox search = new EditBox(font, MARGIN, 2, boxW, 20, Component.literal("Search"));
		search.setHint(Component.literal("Search items..."));
		search.setValue(query);
		search.setResponder(s -> { query = s; scroll = 0; refresh(); });
		addRenderableWidget(search);
		setInitialFocus(search);
	}

	private static boolean matches(ItemStack st, String q) {
		if (st.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
				|| BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().contains(q.replace(' ', '_'))) return true;
		CompoundTag tag = st.getTagElement("BlockEntityTag");
		if (tag != null && tag.contains("Items", Tag.TAG_LIST)) {
			NonNullList<ItemStack> contents = NonNullList.withSize(27, ItemStack.EMPTY);
			ContainerHelper.loadAllItems(tag, contents);
			for (ItemStack inner : contents) {
				if (!inner.isEmpty() && matches(inner, q)) return true;
			}
		}
		return false;
	}

	private void refresh() {
		Minecraft mc = Minecraft.getInstance();
		BlockPos p = mc.player.blockPosition();
		String dim = mc.level.dimension().location().toString();
		stacks = new ArrayList<>(ContainerIndex.allItems(dim, p.getX() >> 4, p.getZ() >> 4, range));
		//closets container first
		stacks.sort(Comparator.comparingDouble(f -> f.dim().equals(dim)
				? mc.player.distanceToSqr(f.pos().getX() + 0.5, f.pos().getY() + 0.5, f.pos().getZ() + 0.5)
				: Double.MAX_VALUE));
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (!q.isEmpty()) {
			stacks.removeIf(f -> !matches(f.stack(), q));
		}
		scroll = Math.min(scroll, maxScroll());
	}

	static void drawTitleBar(GuiGraphics g, Font font, Component title, int width) {
		g.fill(0, 0, width, BAR, 0x47404040);
		g.drawCenteredString(font, title, width / 2, (BAR - font.lineHeight) / 2, 0xFFFFFFFF);
	}

	private int listTop() { return BAR; }
	private int listBottom() { return height - BAR; }
	private int cols() { return Math.max(1, (width - MARGIN * 2) / CELL); }
	private int rows() { return (stacks.size() + cols() - 1) / cols(); }
	private int maxScroll() { return Math.max(0, rows() * CELL - (listBottom() - listTop())); }

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - scrollY * CELL));
		return true;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) return true;
		if (button != 0 || mouseY < listTop() || mouseY >= listBottom()) return false;
		int cols = cols();
		for (int i = 0; i < stacks.size(); i++) {
			int x = MARGIN + (i % cols) * CELL;
			int y = listTop() + (i / cols) * CELL - (int) scroll;
			if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL) {
				ContainerIndex.Found f = stacks.get(i);
				Minecraft mc = Minecraft.getInstance();
				if (f.dim().equals(mc.level.dimension().location().toString())) {
					Marking.set(f.dim(), f.pos());
					if (Settings.autoLook) mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(f.pos()));
					onClose();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g) {
		super.renderBackground(g);
		drawTitleBar(g, font, title, width);
		g.fill(0, height - BAR, width, height, 0x47404040);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		renderBackground(g);
		super.render(g, mouseX, mouseY, delta);
		int cols = cols();
		ContainerIndex.Found hovered = null;

		g.enableScissor(MARGIN, listTop(), width - MARGIN, listBottom());
		for (int i = 0; i < stacks.size(); i++) {
			int x = MARGIN + (i % cols) * CELL;
			int y = listTop() + (i / cols) * CELL - (int) scroll;
			if (y + CELL < listTop() || y > listBottom()) continue;
			ContainerIndex.Found f = stacks.get(i);
			g.renderItem(f.stack(), x, y);
			g.renderItemDecorations(font, f.stack(), x, y);
			if (mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL
					&& mouseY >= listTop() && mouseY < listBottom()) {
				hovered = f;
				g.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
			}
		}
		g.disableScissor();

		if (hovered != null) renderFoundTooltip(g, hovered, mouseX, mouseY);
	}

	private void renderFoundTooltip(GuiGraphics g, ContainerIndex.Found f, int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		List<Component> lines = new ArrayList<>(getTooltipFromItem(mc, f.stack()));

		BlockPos p = f.pos();
		if (f.dim().equals(mc.level.dimension().location().toString())) {
			double dist = Math.sqrt(mc.player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5));
			lines.add(Component.literal(String.format("Distance: %.1f blocks", dist)).withStyle(ChatFormatting.GRAY));
		} else {
			lines.add(Component.literal("Distance: another dimension (" + f.dim() + ")").withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.literal("Position: " + p.getX() + ", " + p.getY() + ", " + p.getZ()).withStyle(ChatFormatting.GRAY));

		g.renderTooltip(font, lines, f.stack().getTooltipImage(), mouseX, mouseY);
	}

	private class RangeSlider extends AbstractSliderButton {
		RangeSlider(int x, int y, int w, int h) {
			super(x, y, w, h, Component.empty(), (range == ContainerIndex.INFINITE ? steps() : range - Settings.minChunks) / (double) steps());
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(range == ContainerIndex.INFINITE ? "Range: Infinite" : "Range: " + range + " chunks"));
		}

		@Override
		protected void applyValue() {
			int i = (int) Math.round(value * steps());
			range = (i == steps()) ? ContainerIndex.INFINITE : i + Settings.minChunks;
			refresh();
		}
	}
}
