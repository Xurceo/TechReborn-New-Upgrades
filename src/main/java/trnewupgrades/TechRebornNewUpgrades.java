package trnewupgrades;

import net.fabricmc.api.ModInitializer;
import trnewupgrades.init.TRNUContent;

public class TechRebornNewUpgrades implements ModInitializer {
	public static final String MOD_ID = "trnewupgrades";

	@Override
	public void onInitialize() {
		TRNUContent.register();
	}
}