package trnewupgrades.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.NonNull;
import reborncore.api.recipe.IRecipeCrafterProvider;
import reborncore.common.blockentity.MachineBaseBlockEntity;
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
            applyOverclockerMk3(blockEntity, handler);
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
            PowerAcceptorBlockEntity powerAcceptor = blockEntity instanceof PowerAcceptorBlockEntity
                ? (PowerAcceptorBlockEntity) blockEntity
                : null;
            applyStackProcessing(blockEntity, handler);
            if (powerAcceptor != null) {
                powerAcceptor.extraPowerStorage += powerAcceptor.getBaseMaxPower() * 63;
            }
        }),
        OMNI((blockEntity, handler, stack) -> {
            applyStackProcessing(blockEntity, handler);
            applyOverclockerMk3(blockEntity, handler);
            applyTransformerInfinite(blockEntity);
        });

        public final String name;
        public final @NonNull Item item;

        Upgrades(@NonNull IUpgrade upgrade) {
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

        private static void applyOverclockerMk3(Object blockEntity, IUpgradeHandler handler) {
            PowerAcceptorBlockEntity powerAcceptor = blockEntity instanceof PowerAcceptorBlockEntity
                ? (PowerAcceptorBlockEntity) blockEntity
                : null;
            IUpgradeHandler targetHandler = resolveUpgradeTarget(blockEntity, handler);
            if (targetHandler != null) {
                addSpeedMultiplierCapped(targetHandler, TRNUConfig.overclockermk3Speed, 0.999999D);
                targetHandler.addPowerMultiplier(TRNUConfig.overclockermk3Power);
            }
            if (powerAcceptor != null) {
                powerAcceptor.extraPowerInput += powerAcceptor.getMaxInput(null);
                powerAcceptor.extraPowerStorage += powerAcceptor.getBaseMaxPower() * 40;
            }
        }

        private static void applyStackProcessing(Object blockEntity, IUpgradeHandler handler) {
            // Prefer resolving the recipe crafter upgrade handler so we can
            // set the processing flag on the object that actually controls
            // recipe execution. Fallback to the blockEntity itself.
            IUpgradeHandler targetHandler = resolveUpgradeTarget(blockEntity, handler);
            if (targetHandler instanceof ProcessingStackAccessor handlerAccessor) {
                handlerAccessor.processStack();
            } else if (blockEntity instanceof ProcessingStackAccessor accessor) {
                accessor.processStack();
            }
            if (targetHandler != null) {
                targetHandler.addPowerMultiplier(resolveStackPowerMultiplier(blockEntity));
            }
        }

        private static void applyTransformerInfinite(Object blockEntity) {
            if (blockEntity instanceof PowerAcceptorBlockEntity powerAcceptor) {
                powerAcceptor.extraTier += 10;
                powerAcceptor.extraPowerStorage += powerAcceptor.getBaseMaxPower() * 63;
                powerAcceptor.extraPowerInput += powerAcceptor.getMaxInput(null);
            }
        }

        private static double resolveStackPowerMultiplier(Object blockEntity) {
            if (blockEntity instanceof MachineBaseBlockEntity machineBase) {
                if (trnewupgrades.util.UpgradeUtils.hasOmniUpgrade(machineBase.getUpgradeInventory())) {
                    return TRNUConfig.omniCraftsPerOperation;
                }
                if (trnewupgrades.util.UpgradeUtils.hasStackUpgrade(machineBase.getUpgradeInventory())) {
                    return TRNUConfig.stackCraftsPerOperation;
                }
            }
            return 1.0D;
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
