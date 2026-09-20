package trnewupgrades.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.crafting.RebornRecipe;
import reborncore.common.crafting.RecipeUtils;
import reborncore.common.crafting.SizedIngredient;
import reborncore.common.recipes.IUpgradeHandler;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.util.ItemUtils;
import reborncore.common.util.RebornInventory;
import trnewupgrades.TechRebornNewUpgrades;
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

    @Shadow(remap = false)
    protected abstract void resetCrafter();

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
    @WrapMethod(method = "resetCrafter", remap = false)
    private void trnu$wrapResetCrafter(Operation<Void> original) {
        original.call();
        craftsPerOperation = 1;
    }

    @Unique
    private int craftsPerOperation = 1;

    /**
     * Sets the precomputed crafts-per-operation value used by the
     * {@code completeCraft} overwrite. Exposed so subclasses of
     * {@code RecipeCrafter} that override {@code updateCurrentRecipe}
     * (e.g. the recycler) can still drive stack processing.
     *
     * @param value the number of crafts per operation
     */
    @Override
    public void setCraftsPerOperation(int value) {
        craftsPerOperation = value;
    }

    /**
     * Calculates how many crafts can be completed in one operation.
     *
     * @param recipe the recipe to evaluate for stack-processing capacity
     * @return the number of crafts that can execute in one operation
     */
    @Unique
    private int calculateCraftsPerOperation(RebornRecipe recipe) {
        if (!isProcessingStack()) {
            // Also allow stack-processing when a STACK upgrade is physically present
            // in the machine's upgrade inventory even if the local flag wasn't set.
            boolean hasStackUpgrade = blockEntity instanceof MachineBaseBlockEntity machineBase
                    && UpgradeUtils.hasStackUpgrade(machineBase.getUpgradeInventory());
            if (!hasStackUpgrade) {
                TechRebornNewUpgrades.LOGGER.debug("calculateCraftsPerOperation: craft=1, stack processing inactive (stackUpgrade={})",
                        hasStackUpgrade);
                return 1;
            }
        }
        final List<ItemStack> outputs = new ArrayList<>();
        for (ItemStackTemplate template : recipe.outputs()) {
            outputs.add(template.create());
        }
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
        TechRebornNewUpgrades.LOGGER.debug("calculateCraftsPerOperation: recipe={} inputLimit={} outputLimit={} crafts={}",
                recipe.getType(), maxCraftsByInput, maxCraftsByOutput, crafts);
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
        if (!(blockEntity instanceof MachineBaseBlockEntity machineBase)) {
            return 0;
        }
        // Only provide accelerated timings when either the processing flag is set
        // or a STACK upgrade is actually present in the machine's upgrade slots.
        if (!isProcessingStack() && !UpgradeUtils.hasStackUpgrade(machineBase.getUpgradeInventory())) {
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
     * Replaces recipe selection to cache crafts-per-operation and scale the
     * recipe duration for stack processing.
     */
    @WrapMethod(method = "updateCurrentRecipe", remap = false)
    public void trnu$wrapUpdateCurrentRecipe(Operation<Void> original) {
        BlockEntity currentBlockEntity = Objects.requireNonNull(blockEntity);
        Level level = currentBlockEntity.getLevel();
        if (level == null) {
            resetCrafter();
            return;
        }

        RecipeType<? extends RebornRecipe> currentRecipeType = Objects.requireNonNull(recipeType);
        for (RebornRecipe recipe : RecipeUtils.getRecipes(level, currentRecipeType)) {
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
            TechRebornNewUpgrades.LOGGER.debug("updateCurrentRecipe: recipe={} craftsPerOperation={} stackTier={} neededTicks={}",
                    recipe.getType(), craftsPerOperation, stackTier, this.currentNeededTicks);
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
     * Replaces craft completion so multiple crafts can be completed atomically
     * using the precomputed stack count.
     */
    @WrapMethod(method = "completeCraft", remap = false)
    protected void trnu$wrapCompleteCraft(Operation<Void> original) {
        if (currentRecipe == null) {
            TechRebornNewUpgrades.LOGGER.error("completeCraft: currentRecipe is null, nothing to craft");
            return;
        }
        final List<ItemStack> outputs = new ArrayList<>();
        for (ItemStackTemplate template : currentRecipe.outputs()) {
            outputs.add(template.create());
        }
        // Use the pre-calculated craftsPerOperation from updateCurrentRecipe.
        // Do NOT re-evaluate here; the processingStack flag may have transient state.
        int craftsThisOperation = Math.max(craftsPerOperation, 1);
        TechRebornNewUpgrades.LOGGER.debug("completeCraft: recipe={} craftsPerOperation={}", currentRecipe.getType(), craftsThisOperation);
        int crafted = 0;
        BlockEntity currentBlockEntity = Objects.requireNonNull(blockEntity);
        for (int i = 0; i < craftsThisOperation; i++) {
            if (!hasAllInputs(currentRecipe) || !canFitAllOutputs(outputs)) {
                TechRebornNewUpgrades.LOGGER.debug("completeCraft: stopped at {}/{} (input or output space exhausted)", crafted, craftsThisOperation);
                break;
            }
            // Check machine-specific on craft logic for each craft operation.
            if (!currentRecipe.onCraft(currentBlockEntity)) {
                TechRebornNewUpgrades.LOGGER.debug("completeCraft: stopped at {}/{} (onCraft refused)", crafted, craftsThisOperation);
                break;
            }
            insertOutputs(outputs);
            useAllInputs();
            crafted++;
        }

        if (crafted == 0) {
            return;
        }
        TechRebornNewUpgrades.LOGGER.debug("completeCraft: crafted {}/{}", crafted, craftsThisOperation);
        currentTickTime = 0;
    }
}
