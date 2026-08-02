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
   public final @NonNull IUpgrade behavior;

   public UpgradeItem(@NonNull String name, @NonNull IUpgrade process) {
      super(item(name).stacksTo(16));
      this.behavior = process;
   }

   public void process(@NonNull MachineBaseBlockEntity blockEntity, @Nullable IUpgradeHandler handler, @NonNull ItemStack stack) {
      this.behavior.process(blockEntity, handler, stack);
   }

   public static @NonNull ResourceKey<Item> key(@NonNull String name) {
      return ResourceKey.create(BuiltInRegistries.ITEM.key(), Identifier.fromNamespaceAndPath("trnewupgrades", name));
   }

   public static Item.@NonNull Properties item(@NonNull String name) {
      return (new Item.Properties()).setId(key(name));
   }

   
}