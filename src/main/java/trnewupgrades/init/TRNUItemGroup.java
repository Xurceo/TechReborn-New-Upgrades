package trnewupgrades.init;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import trnewupgrades.TechRebornNewUpgrades;

public class TRNUItemGroup {
    private static final ResourceKey<CreativeModeTab> ITEM_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(TechRebornNewUpgrades.MOD_ID, "item_group"));

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ITEM_GROUP, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.trnewupgrades.item_group"))
                .icon(() -> new ItemStack(TRNUContent.Upgrades.OVERCLOCKERMK3))
                .build());

        CreativeModeTabEvents.modifyOutputEvent(ITEM_GROUP).register(TRNUItemGroup::entries);
    }

    private static void entries(FabricCreativeModeTabOutput entries) {
        addContent(TRNUContent.Upgrades.values(), entries);
    }

    private static void addContent(ItemLike[] items, FabricCreativeModeTabOutput entries) {
        for (ItemLike item : items) {
            entries.accept(item);
        }
    }
}
