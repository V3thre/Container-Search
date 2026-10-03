package veth.containersearch.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;


public class ContainerSearchClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
			Minecraft client = Minecraft.getInstance();
            boolean isMultiplayer = client.getCurrentServer() != null;
			boolean isRealm = client.getCurrentServer().isRealm();
		});
	}


}