package trnewupgrades.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.NonNull;
import reborncore.api.recipe.IRecipeCrafterProvider;
import reborncore.common.powerSystem.PowerAcceptorBlockEntity;
import reborncore.api.blockentity.IUpgrade;
import reborncore.common.recipes.IUpgradeHandler;
import trnewupgrades.api.ProcessingStackAccessor;
import trnewupgrades.config.TRNUConfig;
import trnewupgrades.events.ModRegistry;
import trnewupgrades.item.UpgradeItem;

import java.util.Locale;

public class TRNUContent {
    public interface ItemInfo extends ItemLike {
        String getName();
    }

    public enum Upgrades implements ItemInfo {
        OVERCLOCKERMK2((blockEntity, handler, stack) -> {
            PowerAcceptorBlockEntity powerAcceptor = null;
            if (blockEntity instanceof PowerAcceptorBlockEntity) {
                powerAcceptor = (PowerAcceptorBlockEntity) blockEntity;
            }
            IUpgradeHandler targetHandler = resolveUpgradeTarget(blockEntity, handler);
            if (targetHandler != null) {
                addSpeedMultiplierCapped(targetHandler, TRNUConfig.overclockermk2Speed, 0.9999D);
                targetHandler.addPowerMultiplier(TRNUConfig.overclockermk2Power);
            }
            if (powerAcceptor != null) {
                powerAcceptor.extraPowerInput += powerAcceptor.getMaxInput(null);
                powerAcceptor.extraPowerStorage += powerAcceptor.getBaseMaxPower() * 10;
            }
        }),
        OVERCLOCKERMK3((blockEntity, handler, stack) -> {
            PowerAcceptorBlockEntity powerAcceptor = null;
            if (blockEntity instanceof PowerAcceptorBlockEntity) {
                powerAcceptor = (PowerAcceptorBlockEntity) blockEntity;
            }
            IUpgradeHandler targetHandler = resolveUpgradeTarget(blockEntity, handler);
            if (targetHandler != null) {
                addSpeedMultiplierCapped(targetHandler, TRNUConfig.overclockermk3Speed, 0.999999D);
                targetHandler.addPowerMultiplier(TRNUConfig.overclockermk3Power);
            }
            if (powerAcceptor != null) {
                powerAcceptor.extraPowerInput += powerAcceptor.getMaxInput(null);
            }
        }),
        TRANSFORMERMK2((blockEntity, handler, stack) -> {
            PowerAcceptorBlockEntity powerAcceptor = null;
            if (blockEntity instanceof PowerAcceptorBlockEntity) {
                powerAcceptor = (PowerAcceptorBlockEntity) blockEntity;
            }
            if (powerAcceptor != null) {
                powerAcceptor.extraTier += 2;
            }
        }),
        TRANSFORMERINFINITE((blockEntity, handler, stack) -> {
            PowerAcceptorBlockEntity powerAcceptor = null;
            if (blockEntity instanceof PowerAcceptorBlockEntity) {
                powerAcceptor = (PowerAcceptorBlockEntity) blockEntity;
            }
            if (powerAcceptor != null) {
                powerAcceptor.extraTier += 10;
            }
        }),
        STACK((blockEntity, handler, stack) -> {
            PowerAcceptorBlockEntity powerAcceptor = null;
            if (blockEntity instanceof ProcessingStackAccessor accessor){
                accessor.processStack();
            }
            if (blockEntity instanceof PowerAcceptorBlockEntity) {
                powerAcceptor = (PowerAcceptorBlockEntity) blockEntity;
            }
            if (powerAcceptor != null) {
                powerAcceptor.extraPowerStorage += powerAcceptor.getBaseMaxPower() * 63;
            }
        });

        public final String name;
        public final Item item;

        Upgrades(IUpgrade upgrade) {
            name = this.toString().toLowerCase(Locale.ROOT);
            item = new UpgradeItem(name + "_upgrade", upgrade);
            InitUtil.setup(item, name + "_upgrade");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public @NonNull Item asItem() {
            return item;
        }

        private static void addSpeedMultiplierCapped(IUpgradeHandler handler, double amount, double cap) {
            double remaining = cap - handler.getSpeedMultiplier();
            if (remaining <= 0) {
                return;
            }
            handler.addSpeedMultiplier(Math.min(amount, remaining));
        }

        private static IUpgradeHandler resolveUpgradeTarget(Object blockEntity, IUpgradeHandler fallbackHandler) {
            if (blockEntity instanceof IRecipeCrafterProvider provider && provider.getRecipeCrafter() != null) {
                return provider.getRecipeCrafter();
            }
            return fallbackHandler;
        }

        public static Upgrades fromItem(UpgradeItem item) {
            for (Upgrades upgrade : values()) {
                if (upgrade.item == item) {
                    return upgrade;
                }
            }

            throw new IllegalArgumentException("Item is not an upgrade item");
        }
    }

    public static void register() {
        ModRegistry.register();
        TRNUItemGroup.register();
    }
}
