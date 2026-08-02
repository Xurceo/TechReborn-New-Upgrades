package trnewupgrades.init;

import java.util.HashMap;
import java.util.Objects;

import org.jspecify.annotations.NonNull;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import trnewupgrades.TechRebornNewUpgrades;
import net.minecraft.util.Util;
import net.fabricmc.loader.api.FabricLoader;

public class InitUtil {

    private static final HashMap<@NonNull Object, @NonNull Identifier> objIdentMap = new HashMap<>();

	public static <I extends @NonNull Item> @NonNull I setup(@NonNull I item, @NonNull String name) {
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

    public static void registerIdent(@NonNull Object object, @NonNull Identifier identifier){
		objIdentMap.put(object, identifier);
	}

	public static void registerItem(@NonNull Item item) {
		Identifier identifier = Objects.requireNonNull(objIdentMap.get(item), "Missing identifier for item: " + item);
		registerItem(item, identifier);
	}

	@SuppressWarnings("null")
	public static void registerItem(@NonNull Item item, @NonNull Identifier name) {
		Registry.register(BuiltInRegistries.ITEM, name, item);
	}
}
