package trnewupgrades.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.RecipeUtils;
import reborncore.common.crafting.SizedIngredient;
import reborncore.common.recipes.IUpgradeHandler;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.util.ItemUtils;
import reborncore.common.util.RebornInventory;
import trnewupgrades.api.ProcessingStackAccessor;
import trnewupgrades.util.UpgradeUtils;

@Mixin(value = RecipeCrafter.class, remap = false)
public abstract class RecipeCrafterMixin implements ProcessingStackAccessor {
    /**
     * Mixin for RecipeCrafter: adds processing-stack behavior, computes
     * `craftsPerOperation` at recipe selection, and ensures `completeCraft`
     * uses the precomputed value to avoid races.
     */
    
    /* ----------------
     * Shadows
     * ---------------- */
    @Shadow(remap = false)
    public Optional<IUpgradeHandler> parentUpgradeHandler;

    @Shadow(remap = false)
    public abstract void setInvDirty(boolean value);

    @Shadow(remap = false)
    public BlockEntity blockEntity;

    @Shadow(remap = false)
    public RecipeType<? extends RebornRecipe> recipeType;

    @Shadow(remap = false)
    public RebornRecipe currentRecipe;

    @Shadow(remap = false)
    public int currentTickTime;

    @Shadow(remap = false)
    public int currentNeededTicks;

    @Shadow(remap = false)
    public int[] outputSlots;

    @Shadow(remap = false)
    public int[] inputSlots;

    @Shadow(remap = false)
    public RebornInventory<?> inventory;

    @Shadow(remap = false)
    protected abstract boolean isValidRecipe(RebornRecipe recipe);

    @Shadow(remap = false)
    public abstract boolean hasAllInputs(RebornRecipe recipe);

    @Shadow(remap = false)
    public abstract boolean canFitOutput(ItemStack stack, int slot);

    @Shadow(remap = false)
    public abstract void fitStack(ItemStack stack, int slot);

    @Shadow(remap = false)
    public abstract void useAllInputs();

    @Shadow(remap = false)
    public abstract void setCurrentRecipe(RebornRecipe recipe);

    @Shadow(remap = false)
    public abstract double getSpeedMultiplier();

    // Unique fields
    @Unique
    private boolean processingStack = false;

    /**
     * Resets the local processing flag after construction.
     */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void initReset(CallbackInfo ci) {
        resetProcessingStack();
    }

    @Unique
    public void resetProcessingStack() {
        this.processingStack = false;
    }

    /**
     * Returns whether the crafter should process stacks.
     */
    @Override
    public boolean isProcessingStack() {
        return parentUpgradeHandler
                .map(handler -> handler instanceof ProcessingStackAccessor accessor ? accessor.isProcessingStack() : processingStack)
                .orElse(processingStack);
    }

    /**
     * Updates the local flag and propagates it to the parent upgrade handler.
     */
    @Override
    public void setProcessingStack(boolean value) {
        processingStack = value;
        parentUpgradeHandler.ifPresent(handler -> {
            if (handler instanceof ProcessingStackAccessor accessor) {
                accessor.setProcessingStack(value);
            }
        });
    }

    /**
     * Resets crafter state and clears stack-processing bookkeeping.
     */
    @Overwrite(remap = false)
    protected void resetCrafter() {
        currentTickTime = 0;
        currentNeededTicks = 0;
        craftsPerOperation = 1;
        setCurrentRecipe(null);
    }

    @Unique
    private int craftsPerOperation = 1;

    /**
     * Calculates how many crafts can be completed in one operation.
     *
     * @param recipe the recipe to evaluate for stack-processing capacity
     * @return the number of crafts that can execute in one operation
     */
    @Unique
    private int calculateCraftsPerOperation(RebornRecipe recipe) {
        if (!isProcessingStack()) {
            return 1;
        }
        final List<ItemStack> outputs = recipe.outputs().stream().map(ItemStackTemplate::create).toList();
        if (!isItemRecipe(outputs)) {
            return 1;
        }

        int maxCraftsByInput = Integer.MAX_VALUE;
        for (SizedIngredient ingredient : recipe.ingredients()) {
            int available = 0;
            for (int inputSlot : inputSlots) {
                ItemStack stack = inventory.getItem(inputSlot);
                if (ingredient.test(stack)) {
                    available += stack.getCount();
                }
            }
            int ingredientCrafts = available / ingredient.count();
            maxCraftsByInput = Math.min(maxCraftsByInput, ingredientCrafts);
        }

        int maxCraftsByOutput = Integer.MAX_VALUE;
        ArrayList<Integer> filledSlots = new ArrayList<>();
        for (int i = 0; i < outputs.size(); i++) {
            int outputSlot = outputSlots[i];
            if (filledSlots.contains(outputSlot)) {
                continue;
            }
            filledSlots.add(outputSlot);

            ItemStack output = outputs.get(i);
            if (output.isEmpty()) {
                continue;
            }

            ItemStack existing = inventory.getItem(outputSlot);
            int space;
            if (existing.isEmpty()) {
                space = output.getMaxStackSize();
            } else if (ItemUtils.isItemEqual(existing, output, true, true)) {
                space = Math.max(output.getMaxStackSize() - existing.getCount(), 0);
            } else {
                space = 0;
            }

            int perCraftOutput = Math.max(output.getCount(), 1);
            int outputCrafts = space / perCraftOutput;
            maxCraftsByOutput = Math.min(maxCraftsByOutput, outputCrafts);
        }

        if (maxCraftsByInput == Integer.MAX_VALUE) {
            maxCraftsByInput = 1;
        }
        if (maxCraftsByOutput == Integer.MAX_VALUE) {
            maxCraftsByOutput = 64;
        }

        int crafts = Math.min(Math.min(maxCraftsByInput, maxCraftsByOutput), 64);
        return Math.max(crafts, 1);
    }

    /**
     * Returns whether the recipe outputs match the configured output slots.
     *
     * @param outputs the recipe outputs to validate
     * @return true if all outputs can fit in the output slot map
     */
    @Unique
    private boolean isItemRecipe(List<ItemStack> outputs) {
        return outputSlots != null && outputs.size() <= outputSlots.length;
    }

    /**
     * Resolves the live overclocker tier for stack-processing timing.
     *
     * @return the highest overclocker tier, or 0 if unavailable
     */
    @Unique
    private int getStackOverclockerTier() {
        if (!isProcessingStack() || !(blockEntity instanceof MachineBaseBlockEntity machineBase)) {
            return 0;
        }
        return UpgradeUtils.getOverclockerTier(machineBase.getUpgradeInventory());
    }

    /**
     * Synchronizes the local processing flag from the parent upgrade handler.
     *
     * @param ci callback from the entity update injection
     */
    @Inject(method = "updateEntity", at = @At("HEAD"), remap = false)
    private void injectStackProcessing(CallbackInfo ci) {
        boolean stackProcessingNow = parentUpgradeHandler
                .map(handler -> handler instanceof ProcessingStackAccessor accessor && accessor.isProcessingStack())
                .orElse(processingStack);
        if (processingStack != stackProcessingNow) {
            setInvDirty(true);
        }
        processingStack = stackProcessingNow;
    }

    /**
     * Overwrites recipe selection to cache crafts-per-operation and scale the
     * recipe duration for stack processing.
     */
    @Overwrite(remap = false)
    public void updateCurrentRecipe() {
        for (RebornRecipe recipe : RecipeUtils.getRecipes(blockEntity.getLevel(), recipeType)) {
            if (!isValidRecipe(recipe)) continue;

            // Reset progress if recipe changed
            if (currentRecipe != recipe) {
                currentTickTime = 0;
            }
            // Sets the current recipe
            setCurrentRecipe(recipe);
            craftsPerOperation = calculateCraftsPerOperation(currentRecipe);
            int baseNeededTicks = Math.max((int) (currentRecipe.time() * (1.0 - getSpeedMultiplier())), 1);

            int stackTier = getStackOverclockerTier();
            if (stackTier >= 3) {
                this.currentNeededTicks = 1;
            } else if (stackTier == 2) {
                this.currentNeededTicks = Math.max((baseNeededTicks * craftsPerOperation) / 10, 1);
            } else if (stackTier == 1) {
                this.currentNeededTicks = Math.max((baseNeededTicks * craftsPerOperation) / 5, 1);
            } else {
                this.currentNeededTicks = baseNeededTicks * craftsPerOperation;
            }
            return;
        }

        // No matching recipe. Reset.
        resetCrafter();
    }

    /**
     * Checks whether every output can still fit in its assigned slot.
     *
     * @param outputs the recipe outputs to validate
     * @return true if all outputs fit in their assigned slots
     */
    @Unique
    private boolean canFitAllOutputs(final @NonNull List<ItemStack> outputs) {
        for (int i = 0; i < outputs.size(); i++) {
            if (!canFitOutput(outputs.get(i), outputSlots[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Inserts the crafted outputs into their configured slots.
     *
     * @param outputs the recipe outputs to insert
     */
    @Unique
    private void insertOutputs(@NonNull List<ItemStack> outputs) {
        ArrayList<Integer> filledSlots = new ArrayList<>();
        // Avoid writing the same output slot multiple times for recipes that share slot targets.
        for (int i = 0; i < outputs.size(); i++) {
            if (!filledSlots.contains(outputSlots[i])) {
                fitStack(outputs.get(i).copy(), outputSlots[i]);
                filledSlots.add(outputSlots[i]);
            }
        }
    }

    /**
     * Overwrites craft completion so multiple crafts can be completed atomically
     * using the precomputed stack count.
     */
    @Overwrite(remap = false)
    protected void completeCraft() {
        final List<ItemStack> outputs = currentRecipe.outputs().stream().map(ItemStackTemplate::create).toList();
        // Use the pre-calculated craftsPerOperation from updateCurrentRecipe.
        // Do NOT re-evaluate here; the processingStack flag may have transient state.
        int craftsThisOperation = Math.max(craftsPerOperation, 1);
        int crafted = 0;
        for (int i = 0; i < craftsThisOperation; i++) {
            if (!hasAllInputs(currentRecipe) || !canFitAllOutputs(outputs)) {
                break;
            }
            // Check machine-specific on craft logic for each craft operation.
            if (!currentRecipe.onCraft(blockEntity)) {
                break;
            }
            insertOutputs(outputs);
            useAllInputs();
            crafted++;
        }

        if (crafted == 0) {
            return;
        }
        currentTickTime = 0;
    }
}
