package veth.containersearch.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class SettingsScreen extends Screen {
	private static final int ROW = 22, BOX_W = 100;
	private static final int WHITE = 0xFFFFFFFF, HEADER = 0xFFFFFF55;

	private record Label(String text, int x, int y, int color) {}
	private record Item(AbstractWidget widget, int y) {}
	private record Swatch(int y, IntSupplier color) {}

	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();
	private final List<Item> items = new ArrayList<>();
	private final List<Swatch> swatches = new ArrayList<>();
	private int left, boxX, contentBottom;
	private double scroll = 0;
	private boolean draggingBar;
	private Button done;
	private Button keyButton;
	private boolean listening;
	private String status = "";

	public SettingsScreen(Screen parent) {
		super(Component.literal("Container Search Settings"));
		this.parent = parent;
	}

	private int viewTop() { return SearchScreen.BAR; }
	private int viewBottom() { return height - 48; }
	private int maxScroll() { return Math.max(0, contentBottom + 6 - viewBottom()); }

	private <T extends AbstractWidget> T add(T widget) {
		items.add(new Item(widget, widget.getY()));
		addWidget(widget);
		return widget;
	}

	@Override
	protected void init() {
		labels.clear();
		items.clear();
		swatches.clear();
		left = width / 2 - 155;
		boxX = width / 2 + 55;
		int y = SearchScreen.BAR + 10;

		y = header("Folders", y);
		labels.add(new Label("Config folder", left, y + 6, WHITE));
		add(Button.builder(Component.literal("Open config"),
						b -> status = ContainerIndex.openConfigFolder() ? "Opened the config folder." : "Could not open the folder.")
				.bounds(boxX, y, BOX_W, 20).build());
		y += ROW;
		labels.add(new Label("Saves folder", left, y + 6, WHITE));
		add(Button.builder(Component.literal("Open folder"),
						b -> status = ContainerIndex.openSavesFolder() ? "Opened the saves folder." : "Could not open the folder.")
				.bounds(boxX, y, BOX_W, 20).build());
		y += ROW;
		labels.add(new Label("Backup folder", left, y + 6, WHITE));
		add(Button.builder(Component.literal("Open folder"),
						b -> status = ContainerIndex.openBackupFolder() ? "Opened the backup folder." : "Could not open the folder.")
				.bounds(boxX, y, BOX_W, 20).build());
		y += ROW;

		y = header("Search range", y);
		y = numberRow("Minimum chunks", Settings.minChunks, v -> Settings.minChunks = v, y);
		y = numberRow("Maximum chunks", Settings.maxChunks, v -> Settings.maxChunks = v, y);

		y = header("Marker", y);
		labels.add(new Label("Fill type", left, y + 6, WHITE));
		add(CycleButton.<Settings.MarkType>builder(t -> Component.literal(t.label))
				.withValues(Settings.MarkType.values())
				.withInitialValue(Settings.markType)
				.displayOnlyValue()
				.create(boxX, y, BOX_W, 20, Component.empty(), (btn, v) -> Settings.markType = v));
		y += ROW;
		y = colorRow("Color (hex)", Settings.markColor, v -> Settings.markColor = v, () -> Settings.markColor, y);
		y = numberRow("Fade out (seconds, 0 = never)", Settings.fadeSeconds, v -> Settings.fadeSeconds = v, y);

		y = header("Unchecked containers", y);
		labels.add(new Label("Show unchecked", left, y + 6, WHITE));
		add(CycleButton.onOffBuilder(Settings.showUnchecked)
				.displayOnlyValue()
				.create(boxX, y, BOX_W, 20, Component.empty(), (btn, v) -> Settings.showUnchecked = v));
		y += ROW;
		labels.add(new Label("Fill type", left, y + 6, WHITE));
		add(CycleButton.<Settings.MarkType>builder(t -> Component.literal(t.label))
				.withValues(Settings.MarkType.values())
				.withInitialValue(Settings.uncheckedType)
				.displayOnlyValue()
				.create(boxX, y, BOX_W, 20, Component.empty(), (btn, v) -> Settings.uncheckedType = v));
		y += ROW;
		y = colorRow("Color (hex)", Settings.uncheckedColor, v -> Settings.uncheckedColor = v, () -> Settings.uncheckedColor, y);
		labels.add(new Label("Range", left, y + 6, WHITE));
		add(new UncheckedRangeSlider(boxX, y, BOX_W, 20));
		y += ROW;
		labels.add(new Label("Toggle key", left, y + 6, WHITE));
		keyButton = add(Button.builder(keyLabel(), b -> { listening = true; keyButton.setMessage(keyLabel()); })
				.bounds(boxX, y, BOX_W, 20).build());
		y += ROW;

		y = header("Saving", y);
		y = numberRow("Autosave every (seconds, 0 = off)", Settings.autosaveSeconds, v -> Settings.autosaveSeconds = v, y);

		labels.add(new Label("Backup", left, y + 6, WHITE));
		add(Button.builder(Component.literal("Backup now"),
						b -> status = ContainerIndex.backup() ? "Backup created." : "Backup failed.")
				.bounds(boxX, y, BOX_W, 20).build());
		y += ROW;
		labels.add(new Label("Restore from backup", left, y + 6, WHITE));
		add(Button.builder(Component.literal("Load latest"),
						b -> status = ContainerIndex.restoreLatest() ? "Loaded the latest backup." : "No backup to load.")
				.bounds(boxX, y, BOX_W, 20).build());
		y += ROW;
		labels.add(new Label("Delete latest backup", left, y + 6, WHITE));
		add(Button.builder(Component.literal("Deletes latest"),
						b -> status = ContainerIndex.deleteLatest() ? "Deleted latest backup." : "No backup to delete.")
				.bounds(boxX, y, BOX_W, 20).build());
		contentBottom = y + 20;

		done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());

		scroll = Math.min(scroll, maxScroll());
	}

	private int header(String text, int y) {
		labels.add(new Label(text, left, y, HEADER));
		return y + 14;
	}

	private int numberRow(String label, int value, IntConsumer onChange, int y) {
		labels.add(new Label(label, left, y + 6, WHITE));
		EditBox box = new EditBox(font, boxX, y, BOX_W, 20, Component.literal(label));
		box.setMaxLength(4);
		box.setFilter(s -> s.matches("\\d*"));
		box.setValue(String.valueOf(value));
		box.setResponder(s -> { if (!s.isEmpty()) onChange.accept(Integer.parseInt(s)); });
		add(box);
		return y + ROW;
	}

	private int colorRow(String label, int value, IntConsumer onChange, IntSupplier current, int y) {
		labels.add(new Label(label, left, y + 6, WHITE));
		swatches.add(new Swatch(y, current));
		EditBox box = new EditBox(font, boxX, y, BOX_W, 20, Component.literal(label));
		box.setMaxLength(6);
		box.setFilter(s -> s.matches("[0-9a-fA-F]*"));
		box.setValue(String.format("%06X", value));
		box.setResponder(s -> { if (s.length() == 6) onChange.accept(Integer.parseInt(s, 16)); });
		add(box);
		return y + ROW;
	}

	private Component keyLabel() {
		return listening ? Component.literal("> press a key <") : Unchecked.toggleKey.getTranslatedKeyMessage();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (listening) {
			InputConstants.Key key = keyCode == GLFW.GLFW_KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(keyCode, scanCode);
			Unchecked.toggleKey.setKey(key);
			KeyMapping.resetMapping();
			minecraft.options.save();
			listening = false;
			keyButton.setMessage(keyLabel());
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private class UncheckedRangeSlider extends AbstractSliderButton {
		UncheckedRangeSlider(int x, int y, int w, int h) {
			super(x, y, w, h, Component.empty(), (Unchecked.effectiveChunks() - 1) / (double) Math.max(1, Unchecked.renderDistance() - 1));
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal(Settings.uncheckedChunks <= 0 ? "Render distance" : Settings.uncheckedChunks + " chunks"));
		}

		@Override
		protected void applyValue() {
			int rd = Unchecked.renderDistance();
			int n = (int) Math.round(value * Math.max(1, rd - 1)) + 1;
			Settings.uncheckedChunks = n >= rd ? 0 : n;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - scrollY * ROW));
		return true;
	}

	private void dragTo(double mouseY) {
		double frac = (mouseY - viewTop()) / (viewBottom() - viewTop());
		scroll = Math.max(0, Math.min(maxScroll(), frac * maxScroll()));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && maxScroll() > 0 && mouseX >= width - 10 && mouseY >= viewTop() && mouseY < viewBottom()) {
			draggingBar = true;
			dragTo(mouseY);
			return true;
		}
		if (mouseY >= viewTop() && mouseY < viewBottom()) return super.mouseClicked(mouseX, mouseY, button);
		return done.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (draggingBar) { dragTo(mouseY); return true; }
		return super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingBar = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.renderBackground(g, mouseX, mouseY, delta);
		SearchScreen.drawTitleBar(g, font, title, width);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		int off = (int) scroll;

		g.enableScissor(0, viewTop(), width, viewBottom());
		for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y() - off, l.color());

		int sx = boxX + BOX_W + 6;
		for (Swatch s : swatches) {
			int sy = s.y() - off;
			g.fill(sx, sy, sx + 20, sy + 20, 0xFFFFFFFF);
			g.fill(sx + 1, sy + 1, sx + 19, sy + 19, 0xFF000000 | s.color().getAsInt());
		}

		for (Item it : items) {
			it.widget().setY(it.y() - off);
			it.widget().render(g, mouseX, mouseY, delta);
		}
		g.disableScissor();

		g.drawCenteredString(font, status, width / 2, height - 42, 0xFFAAAAAA);

		if (maxScroll() > 0) {
			int viewH = viewBottom() - viewTop();
			int thumbH = Math.max(20, viewH * viewH / (viewH + maxScroll()));
			int thumbY = viewTop() + (int) ((viewH - thumbH) * scroll / maxScroll());
			g.fill(width - 8, viewTop(), width - 4, viewBottom(), 0x60000000);
			g.fill(width - 8, thumbY, width - 4, thumbY + thumbH, 0xFFAAAAAA);
		}
	}

	@Override
	public void onClose() {
		Settings.save();
		minecraft.setScreen(parent);
	}
}
