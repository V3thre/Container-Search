package veth.containersearch.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends Screen {
	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Component.literal("Container Search Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		//settings widgets
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, 20).build());
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.renderBackground(g, mouseX, mouseY, delta);
		SearchScreen.drawTitleBar(g, font, title, width);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
