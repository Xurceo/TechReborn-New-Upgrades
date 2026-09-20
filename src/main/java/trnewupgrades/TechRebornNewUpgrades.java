package trnewupgrades;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import trnewupgrades.init.TRNUContent;

public class TechRebornNewUpgrades implements ModInitializer {
	public static final String MOD_ID = "trnewupgrades";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		TRNUContent.register();
	}
}