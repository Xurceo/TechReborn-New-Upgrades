package trnewupgrades.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.recipes.RecipeCrafter;
import trnewupgrades.api.ProcessingStackAccessor;

@Mixin(value = MachineBaseBlockEntity.class, remap = false)
public abstract class MachineBaseBlockEntityMixin implements ProcessingStackAccessor {

	@Shadow(remap = false)
	protected abstract java.util.Optional<RecipeCrafter> getOptionalCrafter();

	@Unique
	private boolean processingStack = false;

	/**
	 * Clears the stack-processing flag when the machine is constructed.
	 */
	@Inject(method = "<init>", at = @At("TAIL"))
	private void initReset(CallbackInfo ci) {
		resetProcessingStack();
	}

	/**
	 * Clears the stack-processing flag when upgrades are reset.
	 */
	@Inject(method = "resetUpgrades", at = @At("TAIL"), remap = false)
	private void resetStackUpgradeState(CallbackInfo ci) {
		resetProcessingStack();
	}

	/**
	 * Marks the crafter dirty after upgrades are applied so it can refresh.
	 */
	@Inject(method = "afterUpgradesApplication", at = @At("TAIL"), remap = false)
	private void refreshCrafterAfterUpgrades(CallbackInfo ci) {
		getOptionalCrafter().ifPresent(crafter -> crafter.setInvDirty(true));
	}

	/**
	 * Resets the local stack-processing flag.
	 *
	 * @return none; method void
	 */
	@Unique
	public void resetProcessingStack() {
		this.processingStack = false;
	}

	/**
	 * Returns whether the machine is currently processing stacks.
	 *
	 * @return true if stack processing is active
	 */
	@Override
	public boolean isProcessingStack() { return processingStack; }

	/**
	 * Updates the machine's stack-processing flag.
	 *
	 * @param value the new stack-processing state
	 */
	@Override
	public void setProcessingStack(boolean value) { processingStack = value; }
}