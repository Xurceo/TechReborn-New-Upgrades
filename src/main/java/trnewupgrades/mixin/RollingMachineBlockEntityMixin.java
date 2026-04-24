package trnewupgrades.mixin;

import net.minecraft.core.BlockPos;
import org.objectweb.asm.Opcodes;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.util.RebornInventory;
import techreborn.blockentity.machine.tier1.RollingMachineBlockEntity;
import techreborn.recipe.recipes.RollingMachineRecipe;
import trnewupgrades.api.ProcessingStackAccessor;

@Mixin(value = RollingMachineBlockEntity.class, remap = false)
public abstract class RollingMachineBlockEntityMixin {

    @Shadow
    public RebornInventory<RollingMachineBlockEntity> inventory;

    @Shadow
    public ItemStack currentRecipeOutput;

    @Shadow
    public RollingMachineRecipe currentRecipe;

    @Shadow
    public int tickTime;

    @Shadow
    public int currentRecipeTime;

    @Shadow
    @Final
    private int outputSlot;

    @Shadow
    private TransientCraftingContainer getCraftingMatrix() {
        throw new AssertionError();
    }

    @Unique
    private RollingMachineRecipe trnu$recipeAtTickStart;

    @Unique
    private int trnu$tickTimeAtTickStart;

    @Unique
    private int trnu$recipeTimeAtTickStart;

    @Unique
    private int trnu$outputPerCraftAtTickStart;

    @Unique
    private int trnu$outputCountAtTickStart;

    @Unique
    private int[] trnu$inputCountsAtTickStart;

    @Unique
    private int trnu$getStackCraftsPerOperation() {
        if (currentRecipe == null || currentRecipeOutput.isEmpty()) {
            return 1;
        }

        TransientCraftingContainer crafting = getCraftingMatrix();
        int maxByInput = Integer.MAX_VALUE;
        boolean hasConsumableInput = false;
        for (int i = 0; i < crafting.getContainerSize(); i++) {
            int count = inventory.getItem(i).getCount();
            if (count > 0) {
                hasConsumableInput = true;
                maxByInput = Math.min(maxByInput, count);
            }
        }
        if (!hasConsumableInput) {
            return 1;
        }

        ItemStack output = inventory.getItem(outputSlot);
        int outputSpace;
        if (output.isEmpty()) {
            outputSpace = currentRecipeOutput.getMaxStackSize();
        } else {
            outputSpace = Math.max(output.getMaxStackSize() - output.getCount(), 0);
        }
        int perCraftOutput = Math.max(currentRecipeOutput.getCount(), 1);
        int maxByOutput = outputSpace / perCraftOutput;

        int crafts = Math.min(Math.min(maxByInput, maxByOutput), 64);
        return Math.max(crafts, 1);
    }

    @Unique
    private int trnu$getScaledRecipeTime(int baseTime) {
        if (baseTime <= 0) {
            return baseTime;
        }
        if (!((Object) this instanceof ProcessingStackAccessor accessor) || !accessor.isProcessingStack()) {
            return baseTime;
        }
        int craftsPerOperation = trnu$getStackCraftsPerOperation();
        return Math.max(baseTime * craftsPerOperation, 1);
    }

    @Redirect(
            method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lreborncore/common/blockentity/MachineBaseBlockEntity;)V",
            at = @At(
                    value = "FIELD",
                    target = "Ltechreborn/blockentity/machine/tier1/RollingMachineBlockEntity;currentRecipeTime:I",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0
            ),
            remap = false
    )
    private int trnu$scaledRecipeTimeForStack(RollingMachineBlockEntity self) {
        return trnu$getScaledRecipeTime(this.currentRecipeTime);
    }

    @Inject(method = "getCurrentRecipeTime()I", at = @At("HEAD"), cancellable = true, remap = false)
    private void trnu$getCurrentRecipeTimeScaled(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(trnu$getScaledRecipeTime(this.currentRecipeTime));
    }

    @Inject(method = "getProgressScaled(I)I", at = @At("HEAD"), cancellable = true, remap = false)
    private void trnu$normalizeProgressScaled(int scale, CallbackInfoReturnable<Integer> cir) {
        int required = trnu$getScaledRecipeTime(this.currentRecipeTime);
        if (tickTime <= 0 || required <= 0) {
            cir.setReturnValue(0);
            return;
        }
        int progress = Math.min(tickTime, required);
        cir.setReturnValue(progress * scale / required);
    }

    @Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lreborncore/common/blockentity/MachineBaseBlockEntity;)V", at = @At("HEAD"), remap = false)
    private void trnu$captureTickState(Level level, BlockPos pos, BlockState state, MachineBaseBlockEntity machineBase, CallbackInfo ci) {
        trnu$recipeAtTickStart = currentRecipe;
        trnu$tickTimeAtTickStart = tickTime;
        trnu$recipeTimeAtTickStart = currentRecipeTime;
        trnu$outputPerCraftAtTickStart = Math.max(currentRecipeOutput.getCount(), 1);
        trnu$outputCountAtTickStart = inventory.getItem(outputSlot).getCount();
        TransientCraftingContainer crafting = getCraftingMatrix();
        trnu$inputCountsAtTickStart = new int[crafting.getContainerSize()];
        for (int i = 0; i < crafting.getContainerSize(); i++) {
            trnu$inputCountsAtTickStart[i] = inventory.getItem(i).getCount();
        }
    }

    @Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lreborncore/common/blockentity/MachineBaseBlockEntity;)V", at = @At("TAIL"), remap = false)
    private void trnu$craftAdditionalOnStack(Level level, BlockPos pos, BlockState state, MachineBaseBlockEntity machineBase, CallbackInfo ci) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!(machineBase instanceof ProcessingStackAccessor accessor) || !accessor.isProcessingStack()) {
            return;
        }
        if (trnu$recipeAtTickStart == null || trnu$recipeTimeAtTickStart <= 0) {
            return;
        }

        ItemStack output = inventory.getItem(outputSlot);
        if (output.isEmpty()) {
            return;
        }

        // Only apply extra crafts when this tick actually completed at least one craft.
        int producedThisTick = output.getCount() - trnu$outputCountAtTickStart;
        if (producedThisTick <= 0) {
            return;
        }

        int outputSpace = Math.max(output.getMaxStackSize() - output.getCount(), 0);
        int maxExtraByOutput = outputSpace / trnu$outputPerCraftAtTickStart;
        if (maxExtraByOutput <= 0) {
            return;
        }

        TransientCraftingContainer crafting = getCraftingMatrix();
        int maxExtraByInput = Integer.MAX_VALUE;
        int consumableSlots = 0;
        for (int i = 0; i < crafting.getContainerSize(); i++) {
            if (trnu$inputCountsAtTickStart == null || i >= trnu$inputCountsAtTickStart.length) {
                continue;
            }
            int startCount = trnu$inputCountsAtTickStart[i];
            int endCount = inventory.getItem(i).getCount();
            int consumedPerCraft = Math.max(startCount - endCount, 0);
            if (consumedPerCraft <= 0) {
                continue;
            }
            consumableSlots++;
            maxExtraByInput = Math.min(maxExtraByInput, endCount / consumedPerCraft);
        }
        if (consumableSlots == 0 || maxExtraByInput == Integer.MAX_VALUE) {
            return;
        }

        int extraCrafts = Math.min(Math.min(maxExtraByOutput, maxExtraByInput), 63);
        if (extraCrafts <= 0) {
            return;
        }

        output.grow(trnu$outputPerCraftAtTickStart * extraCrafts);
        for (int i = 0; i < crafting.getContainerSize(); i++) {
            if (trnu$inputCountsAtTickStart == null || i >= trnu$inputCountsAtTickStart.length) {
                continue;
            }
            int startCount = trnu$inputCountsAtTickStart[i];
            int endCount = inventory.getItem(i).getCount();
            int consumedPerCraft = Math.max(startCount - endCount, 0);
            if (consumedPerCraft > 0) {
                inventory.shrinkSlot(i, consumedPerCraft * extraCrafts);
            }
        }
    }
}
