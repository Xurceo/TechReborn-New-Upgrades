package trnewupgrades.client;

import net.fabricmc.api.ClientModInitializer;
import trnewupgrades.client.events.StackToolTipHandler;

public class TechRebornNewUpgradesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		StackToolTipHandler.setup();
	}
}