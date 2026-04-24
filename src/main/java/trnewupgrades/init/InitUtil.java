package trnewupgrades.init;

import java.util.HashMap;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.Validate;
import trnewupgrades.TechRebornNewUpgrades;
import net.minecraft.util.Util;
import net.fabricmc.loader.api.FabricLoader;

public class InitUtil {

    private static final HashMap<Object, Identifier> objIdentMap = new HashMap<>();

    public static <I extends Item> I setup(I item, String name) {
		Identifier identifier = Identifier.fromNamespaceAndPath(TechRebornNewUpgrades.MOD_ID, name);
		registerIdent(item, identifier);

		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			String expect = Util.makeDescriptionId("item", identifier);
			String actual = item.getDescriptionId();

			if (!expect.equals(actual)) {
				boolean isProjectItem = item.getClass().getName().startsWith("trnewupgrades.");
				boolean hasCorrectItemPath = actual.endsWith("." + name);

				if (isProjectItem || !hasCorrectItemPath) {
					// Keep strict checks for local items, but allow external item classes that use their own namespace.
					throw new IllegalStateException("Item translation key mismatch: expected " + expect + ", got " + actual);
				}
			}
		}

		return item;
	}

    public static void registerIdent(Object object, Identifier identifier){
		objIdentMap.put(object, identifier);
	}

	public static void registerItem(Item item) {
		Validate.isTrue(objIdentMap.containsKey(item));
		TechRebornNewUpgrades.LOGGER.info("Items contain keys");
		registerItem(item, (Identifier)objIdentMap.get(item));
		TechRebornNewUpgrades.LOGGER.info("Items registered");
	}

	public static void registerItem(Item item, Identifier name) {
		Registry.register(BuiltInRegistries.ITEM, name, item);
	}
}
