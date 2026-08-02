package trnewupgrades.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.recipes.RecipeCrafter;
import reborncore.common.util.RebornInventory;
import techreborn.recipe.RecyclerRecipeCrafter;
import trnewupgrades.api.ProcessingStackAccessor;
import trnewupgrades.util.UpgradeUtils;

/**
 * Mixin for RecyclerRecipeCrafter: adds stack-processing support.
 *
 * <p>{@code RecyclerRecipeCrafter} overrides {@code updateCurrentRecipe} with
 * its own recipe selection that only computes the per-item time and never
 * computes {@code craftsPerOperation}. Because that override wins over the
 * stack-aware {@code updateCurrentRecipe} overwrite in {@code RecipeCrafterMixin},
 * the inherited {@code completeCraft} overwrite always saw a crafts-per-operation
 * of {@code 1}, so the recycler ignored the STACK upgrade entirely.</p>
 *
 * <p>This mixin recomputes {@code craftsPerOperation} from the actual input and
 * output slots (the recycler recipe has no ingredients) at the tail of
 * {@code updateCurrentRecipe}, feeds it through the inherited
 * {@code completeCraft} loop, and scales {@code currentNeededTicks} the same way
 * as every other stack-aware machine.</p>
 *
 * <p>All state accessed here is inherited from {@link RecipeCrafter}. Mixin's
 * {@code @Shadow} can only resolve members declared in the target class itself,
 * so the public inherited members are reached through a cast to
 * {@code RecipeCrafter}, and the {@code setCraftsPerOperation} bridge is exposed
 * through the {@link ProcessingStackAccessor} interface.</p>
 */
@Mixin(value = RecyclerRecipeCrafter.class, remap = false)
public abstract class RecyclerRecipeCrafterMixin {

	@Unique
	private RecipeCrafter trnu$crafter() {
		return (RecipeCrafter) (Object) this;
	}

	@Unique
	private ProcessingStackAccessor trnu$accessor() {
		return (ProcessingStackAccessor) (Object) this;
	}

	/**
	 * Returns whether stack processing is active, either via the processing
	 * flag set by the STACK upgrade or by the presence of a STACK upgrade in
	 * the machine's upgrade inventory.
	 *
	 * @return true when the recycler should process stacks
	 */
	@Unique
	private boolean trnu$isProcessingStack() {
		if (this instanceof ProcessingStackAccessor accessor && accessor.isProcessingStack()) {
			return true;
		}
		return trnu$crafter().blockEntity instanceof MachineBaseBlockEntity machineBase
				&& UpgradeUtils.hasStackUpgrade(machineBase.getUpgradeInventory());
	}

	/**
	 * Reads the live overclocker tier from the machine's upgrade inventory.
	 *
	 * @return the highest overclocker tier, or 0 when unavailable
	 */
	@Unique
	private int trnu$getOverclockerTier() {
		if (trnu$crafter().blockEntity instanceof MachineBaseBlockEntity machineBase) {
			return UpgradeUtils.getOverclockerTier(machineBase.getUpgradeInventory());
		}
		return 0;
	}

	/**
	 * Computes how many recycler crafts can happen in one operation. The
	 * recycler recipe has no ingredients, so the batch size is derived from
	 * the input slot stack count and the free space in the output slot.
	 *
	 * @return the number of crafts that can execute in a single operation
	 */
	@Unique
	private int trnu$getCraftsPerOperation() {
		RecipeCrafter crafter = trnu$crafter();
		if (crafter.inputSlots.length == 0 || crafter.outputSlots.length == 0) {
			return 1;
		}
		ItemStack input = crafter.inventory.getItem(crafter.inputSlots[0]);
		if (input.isEmpty()) {
			return 1;
		}
		int maxByInput = Math.min(input.getCount(), 64);
		ItemStack output = crafter.inventory.getItem(crafter.outputSlots[0]);
		int maxByOutput;
		if (output.isEmpty()) {
			maxByOutput = 64;
		} else {
			maxByOutput = Math.max(64 - output.getCount(), 0);
		}
		int crafts = Math.min(Math.min(maxByInput, maxByOutput), 64);
		return Math.max(crafts, 1);
	}

	/**
	 * Computes the stack batch size and scaled recipe time after the recycler's
	 * own recipe selection runs.
	 *
	 * @param ci callback from the recipe update injection
	 */
	@Inject(method = "updateCurrentRecipe", at = @At("TAIL"), remap = false)
	private void trnu$applyStackProcessing(CallbackInfo ci) {
		RecipeCrafter crafter = trnu$crafter();
		if (crafter.currentRecipe == null) {
			trnu$accessor().setCraftsPerOperation(1);
			return;
		}
		if (!trnu$isProcessingStack()) {
			// Keep the recipe time in sync when stack processing is off so a
			// stale scaled value cannot linger after the STACK upgrade is removed.
			trnu$accessor().setCraftsPerOperation(1);
			crafter.currentNeededTicks = Math.max((int) (crafter.currentRecipe.time() * (1.0 - crafter.getSpeedMultiplier())), 1);
			return;
		}

		int craftsPerOperation = trnu$getCraftsPerOperation();
		trnu$accessor().setCraftsPerOperation(craftsPerOperation);
		int baseNeededTicks = Math.max((int) (crafter.currentRecipe.time() * (1.0 - crafter.getSpeedMultiplier())), 1);
		int stackTier = trnu$getOverclockerTier();
		if (stackTier >= 3) {
			crafter.currentNeededTicks = 1;
		} else if (stackTier == 2) {
			crafter.currentNeededTicks = Math.max((baseNeededTicks * craftsPerOperation) / 10, 1);
		} else if (stackTier == 1) {
			crafter.currentNeededTicks = Math.max((baseNeededTicks * craftsPerOperation) / 5, 1);
		} else {
			crafter.currentNeededTicks = baseNeededTicks * craftsPerOperation;
		}
	}
}
