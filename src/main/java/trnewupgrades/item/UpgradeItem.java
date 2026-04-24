package trnewupgrades.item;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import reborncore.api.blockentity.IUpgrade;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.recipes.IUpgradeHandler;

public class UpgradeItem extends Item implements IUpgrade {
   public final IUpgrade behavior;

   public UpgradeItem(String name, IUpgrade process) {
      super(item(name).stacksTo(16));
      this.behavior = process;
   }

   public void process(MachineBaseBlockEntity blockEntity, @Nullable IUpgradeHandler handler, @NonNull ItemStack stack) {
      this.behavior.process(blockEntity, handler, stack);
   }

   public static ResourceKey<Item> key(String name) {
      return ResourceKey.create(BuiltInRegistries.ITEM.key(), Identifier.fromNamespaceAndPath("trnewupgrades", name));
   }

   public static Item.Properties item(String name) {
      return (new Item.Properties()).setId(key(name));
   }

   
}