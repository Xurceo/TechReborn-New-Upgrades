package trnewupgrades.util;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import trnewupgrades.init.TRNUContent;

public final class UpgradeUtils {

    /**
     * Determines the highest overclocker tier present in the supplied upgrade
     * inventory.
     *
     * @param upgradeInventory the upgrade container to scan for overclockers
     * @return the highest tier detected (3, 2, 1, or 0 if none present)
     */
    public static int getOverclockerTier(Container upgradeInventory) {
        int tier = 0;
        for (int i = 0; i < upgradeInventory.getContainerSize(); i++) {
            ItemStack stack = upgradeInventory.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == TRNUContent.Upgrades.OVERCLOCKERMK3.asItem()) return 3;
            if (stack.getItem() == TRNUContent.Upgrades.OVERCLOCKERMK2.asItem()) {
                tier = Math.max(tier, 2);
                continue;
            }
            if (isBaseOverclocker(stack)) {
                tier = Math.max(tier, 1);
            }
        }
        return tier;
    }

    /**
     * Checks whether the given ItemStack is a base Tech Reborn overclocker.
     *
     * @param stack the item stack to check
     * @return true if the stack is a Tech Reborn overclocker_upgrade item
     */
    public static boolean isBaseOverclocker(ItemStack stack) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return "techreborn".equals(key.getNamespace()) && "overclocker_upgrade".equals(key.getPath());
    }
}
