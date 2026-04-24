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
	@Shadow(remap = false) @Mutable
	public static double SPEED_CAP;

	@Shadow(remap = false)
	protected abstract java.util.Optional<RecipeCrafter> getOptionalCrafter();

	@Unique
	private boolean processingStack = false;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void initReset(CallbackInfo ci) {
		resetProcessingStack();
	}

	@Inject(method = "resetUpgrades", at = @At("TAIL"), remap = false)
	private void resetStackUpgradeState(CallbackInfo ci) {
		resetProcessingStack();
	}

	@Inject(method = "afterUpgradesApplication", at = @At("TAIL"), remap = false)
	private void refreshCrafterAfterUpgrades(CallbackInfo ci) {
		getOptionalCrafter().ifPresent(crafter -> crafter.setInvDirty(true));
	}

	@Unique
	public void resetProcessingStack() {
		this.processingStack = false;
	}

	@Override
	public boolean isProcessingStack() { return processingStack; }

	@Override
	public void setProcessingStack(boolean value) { processingStack = value; }
}