package veth.containersearch.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class SettingsScreen extends Screen {
	private static final int ROW = 22, BOX_W = 100;
	private static final int WHITE = 0xFFFFFFFF, HEADER = 0xFFFFFF55;

	private record Label(String text, int x, int y, int color) {}

	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();
	private int left, boxX, colorY;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Container Search Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		labels.clear();
		left = width / 2 - 155;
		boxX = width / 2 + 55;
		int y = SearchScreen.BAR + 10;

		y = header("Search range", y);
		y = numberRow("Minimum chunks", Settings.minChunks, v -> Settings.minChunks = v, y);
		y = numberRow("Maximum chunks", Settings.maxChunks, v -> Settings.maxChunks = v, y);

		y = header("Marker", y);
		labels.add(new Label("Fill type", left, y + 6, WHITE));
		addRenderableWidget(CycleButton.<Settings.MarkType>builder(t -> Component.literal(t.label))
				.withValues(Settings.MarkType.values())
				.withInitialValue(Settings.markType)
				.displayOnlyValue()
				.create(boxX, y, BOX_W, 20, Component.empty(), (btn, v) -> Settings.markType = v));
		y += ROW;

		labels.add(new Label("Color (hex)", left, y + 6, WHITE));
		colorY = y;
		EditBox color = new EditBox(font, boxX, y, BOX_W, 20, Component.literal("Color"));
		color.setMaxLength(6);
		color.setFilter(s -> s.matches("[0-9a-fA-F]*"));
		color.setValue(String.format("%06X", Settings.markColor));
		color.setResponder(s -> { if (s.length() == 6) Settings.markColor = Integer.parseInt(s, 16); });
		addRenderableWidget(color);
		y += ROW;

		y = numberRow("Fade out (seconds, 0 = never)", Settings.fadeSeconds, v -> Settings.fadeSeconds = v, y);

		y = header("Saving", y);
		y = numberRow("Autosave every (seconds, 0 = off)", Settings.autosaveSeconds, v -> Settings.autosaveSeconds = v, y);

		labels.add(new Label("Backup", left, y + 6, WHITE));
		addRenderableWidget(Button.builder(Component.literal("Backup now"), b -> {})
				.bounds(boxX, y, BOX_W, 20).build());
		y += ROW;
		labels.add(new Label("Restore from backup", left, y + 6, WHITE));
		addRenderableWidget(Button.builder(Component.literal("Load latest"), b -> {})
				.bounds(boxX, y, BOX_W, 20).build());
		y += ROW;
		labels.add(new Label("Delete latest backup", left, y + 6, WHITE));
		addRenderableWidget(Button.builder(Component.literal("Deletes latest"), b -> {})
				.bounds(boxX, y, BOX_W, 20).build());

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());
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
		addRenderableWidget(box);
		return y + ROW;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.renderBackground(g, mouseX, mouseY, delta);
		SearchScreen.drawTitleBar(g, font, title, width);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), l.color());

		int sx = boxX + BOX_W + 6;
		g.fill(sx, colorY, sx + 20, colorY + 20, 0xFFFFFFFF);
		g.fill(sx + 1, colorY + 1, sx + 19, colorY + 19, 0xFF000000 | Settings.markColor);
	}

	@Override
	public void onClose() {
		Settings.save();
		minecraft.setScreen(parent);
	}
}
