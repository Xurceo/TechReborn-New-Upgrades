package trnewupgrades.events;

import trnewupgrades.TechRebornNewUpgrades;
import trnewupgrades.init.InitUtil;
import trnewupgrades.init.TRNUContent;

import java.util.Arrays;

public class ModRegistry {

    public static void register() {
        registerItems();
    }

    private static void registerItems() {
        Arrays.stream(TRNUContent.Upgrades.values()).forEach(value -> InitUtil.registerItem(value.asItem()));
        TechRebornNewUpgrades.LOGGER.debug("TechRebornNewUpgrades Items Loaded");
    }
}
