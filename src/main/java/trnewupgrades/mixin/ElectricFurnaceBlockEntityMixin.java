package trnewupgrades.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.screen.BuiltScreenHandler;
import reborncore.common.screen.builder.ScreenHandlerBuilder;
import reborncore.common.util.RebornInventory;
import techreborn.blockentity.machine.tier1.ElectricFurnaceBlockEntity;
import trnewupgrades.api.ProcessingStackAccessor;
import trnewupgrades.util.UpgradeUtils;

@Mixin(value = ElectricFurnaceBlockEntity.class, remap = false)
public abstract class ElectricFurnaceBlockEntityMixin {
    
    // Shadowed fields and methods
	@Shadow
	public RebornInventory<ElectricFurnaceBlockEntity> inventory;

	@Shadow
	private SmeltingRecipe currentRecipe;

	@Shadow
	private int cookTime;

	@Shadow
	private int cookTimeTotal;

	@Shadow
	@Final
	int inputSlot;

	/**
	 * Shadows the furnace input validation check.
	 */
	@Shadow
	private boolean hasAllInputs(SmeltingRecipe recipe) {
		throw new AssertionError();
	}

	/**
	 * Shadows the furnace output fit check.
	 */
	@Shadow
	private boolean canAcceptOutput(SmeltingRecipe recipe, int count) {
		throw new AssertionError();
	}

	/**
	 * Shadows the furnace craft hook that consumes inputs and creates output.
	 */
	@Shadow
	private void craftRecipe(SmeltingRecipe recipe) {}

	// Unique fields
	@Unique
	private MachineBaseBlockEntity trnu$lastMachineBase;

	@Unique
	private SmeltingRecipe trnu$recipeAtTickStart;

	@Unique
	private int trnu$cookTimeTotalAtTickStart;

	@Unique
	private int trnu$outputCountAtTickStart;

	@Unique
	private int trnu$activeScaledCookTimeTotal;

	@Unique
	private boolean trnu$isProcessingStackDuringTick;

	/**
	 * Returns whether the furnace is currently processing stacks.
	 *
	 * @return true when the last captured machine base is processing stacks
	 */
	@Unique
	private boolean trnu$isCurrentlyProcessingStack() {
		return trnu$lastMachineBase instanceof ProcessingStackAccessor accessor && accessor.isProcessingStack();
	}

	/**
	 * Reads the live overclocker tier from the last captured machine base.
	 *
	 * @return the highest detected overclocker tier, or 0 when unavailable
	 */
	@Unique
	private int trnu$getLiveStackOverclockerTier() {
		if (trnu$lastMachineBase != null) {
			return UpgradeUtils.getOverclockerTier(trnu$lastMachineBase.getUpgradeInventory());
		}
		return 0;
	}

	/**
	 * Computes how many furnace crafts can happen in one operation.
	 *
	 * @return the number of crafts that can run in a single operation
	 */
	@Unique
	private int trnu$getStackCraftsPerOperation() {
		// Use the currentRecipe when calculating crafts-per-operation. Sometimes
		// updateCurrentRecipe() runs outside of the tick context, so relying on
		// the tick-start cached recipe (trnu$recipeAtTickStart) can be null.
		if (currentRecipe == null) {
			return 1;
		}
		ItemStack input = inventory.getItem(inputSlot);
		if (input.isEmpty()) {
			return 1;
		}
		int maxByInput = Math.min(input.getCount(), 64);
		ItemStack output = inventory.getItem(1);
		ItemStack result = currentRecipe.assemble(new SingleRecipeInput(inventory.getItem(inputSlot)));
		int outputSpace = output.isEmpty() ? result.getMaxStackSize() : Math.max(result.getMaxStackSize() - output.getCount(), 0);
		int maxByOutput = outputSpace / Math.max(result.getCount(), 1);
		int craftsPerOp = Math.max(Math.min(Math.min(maxByInput, maxByOutput), 64), 1);
		return craftsPerOp;
	}

	/**
	 * Scales the cook-time total for stack processing and overclocker tiers.
	 *
	 * @param baseCookTimeTotal the unscaled cook-time total
	 * @param isProcessingStack whether stack processing is active
	 * @return the scaled cook-time total, or the original value if unchanged
	 */
	@Unique
	private int trnu$getScaledCookTimeTotal(int baseCookTimeTotal, boolean isProcessingStack) {
		if (baseCookTimeTotal <= 0 || !isProcessingStack) {
			return baseCookTimeTotal;
		}
		int craftsPerOperation = trnu$getStackCraftsPerOperation();
		return switch (trnu$getLiveStackOverclockerTier()) {
			case 3 -> 1;
			case 2 -> Math.max((baseCookTimeTotal * craftsPerOperation) / 10, 1);
			case 1 -> Math.max((baseCookTimeTotal * craftsPerOperation) / 5, 1);
			default -> Math.max(baseCookTimeTotal * craftsPerOperation, 1);
		};
	}

	/**
	 * Picks the cook-time total to use for tick-time reads and syncs.
	 *
	 * @return the cached scaled value when available, otherwise the live or
	 *         base cook-time total
	 *
	 */
	@Unique
	private int trnu$getCookTimeTotalForSync() {
		// Prefer an already-computed active scaled cook time (computed at recipe
		// selection). If that's not available, fall back to computing a scaled
		// value when we detect the machine is processing a stack. Otherwise,
		// return the base cookTimeTotal.
		if (trnu$activeScaledCookTimeTotal > 0) {
			return trnu$activeScaledCookTimeTotal;
		}
		// If we don't have a cached active scaled time, compute whether this
		// recipe would process as a stack now and return a scaled value if so.
		int craftsNow = trnu$getStackCraftsPerOperation();
		if (craftsNow > 1) {
			return trnu$getScaledCookTimeTotal(this.cookTimeTotal, true);
		}
		if (trnu$isCurrentlyProcessingStack()) {
			return trnu$getScaledCookTimeTotal(this.cookTimeTotal, true);
		}
		return this.cookTimeTotal;
	}

	/**
	 * Picks the cook-time total used by the furnace progress UI.
	 *
	 * @return the live, cached, or snapshot cook-time total
	 */
	@Unique
	private int trnu$getLiveCookTimeTotalForUI() {
		if (inventory.getItem(inputSlot).isEmpty()) {
			return 0;
		}
		if (trnu$activeScaledCookTimeTotal > 0) {
			return trnu$activeScaledCookTimeTotal;
		}
		return cookTimeTotal > 0 ? cookTimeTotal : trnu$cookTimeTotalAtTickStart;
	}

	/**
	 * Caches the active scaled cook-time total after recipe selection.
	 *
	 * @param ci callback from the recipe update injection
	 */
	@Inject(method = "updateCurrentRecipe", at = @At("TAIL"), remap = false)
	private void trnu$captureActiveCookTimeTotal(CallbackInfo ci) {
		if (currentRecipe == null || cookTimeTotal <= 0) {
			trnu$activeScaledCookTimeTotal = 0;
			return;
		}

		// Determine whether this recipe will be processed as a stack by inspecting
		// the crafts-per-operation at the time the recipe is selected. Relying on
		// `trnu$isCurrentlyProcessingStack()` here can be racy because that flag is
		// captured during the tick and `updateCurrentRecipe()` may run earlier.
		int craftsPerOp = trnu$getStackCraftsPerOperation();
		boolean willProcessStack = craftsPerOp > 1;
		trnu$activeScaledCookTimeTotal = willProcessStack
			? trnu$getScaledCookTimeTotal(cookTimeTotal, true)
			: cookTimeTotal;
	}

	/**
	 * Clears the cached scaled cook-time total when the furnace resets.
	 *
	 * @param ci callback from the reset injection
	 */
	@Inject(method = "resetCrafter", at = @At("TAIL"), remap = false)
	private void trnu$clearActiveCookTimeTotal(CallbackInfo ci) {
		trnu$activeScaledCookTimeTotal = 0;
	}

	/**
	 * Captures tick-start state for the tail injection and UI sync.
	 *
	 * @param level current level instance
	 * @param pos machine position
	 * @param state machine block state
	 * @param machineBase current machine base
	 * @param ci callback from the tick injection
	 */
	@Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lreborncore/common/blockentity/MachineBaseBlockEntity;)V", at = @At("HEAD"), remap = false)
	private void trnu$captureTickState(Level level, BlockPos pos, BlockState state, MachineBaseBlockEntity machineBase, CallbackInfo ci) {
		trnu$lastMachineBase = machineBase;
		trnu$recipeAtTickStart = currentRecipe;
		trnu$cookTimeTotalAtTickStart = cookTimeTotal;
		trnu$outputCountAtTickStart = inventory.getItem(1).getCount();
		trnu$isProcessingStackDuringTick = machineBase instanceof ProcessingStackAccessor accessor && accessor.isProcessingStack();
	}

	/* ----------------
	 * Redirects
	 * ---------------- */
	/**
	 * Redirects tick-time cook-time reads to the scaled or cached value.
	 *
	 * @param self the furnace block entity being ticked
	 * @return the cook-time total that should be read during tick execution
	 */
	@Redirect(
			method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lreborncore/common/blockentity/MachineBaseBlockEntity;)V",
			at = @At(value = "FIELD", target = "Ltechreborn/blockentity/machine/tier1/ElectricFurnaceBlockEntity;cookTimeTotal:I", opcode = org.objectweb.asm.Opcodes.GETFIELD),
			remap = false
	)
	private int trnu$scaledCookTimeTotalForStack(ElectricFurnaceBlockEntity self) {
		return trnu$getCookTimeTotalForSync();
	}

	/**
	 * Makes the public cook-time total getter return the scaled value when stack
	 * processing is active.
	 *
	 * @param cir callback for the getter injection
	 */
	@Inject(method = "getCookTimeTotal()I", at = @At("HEAD"), cancellable = true, remap = false)
	private void trnu$getCookTimeTotalScaled(CallbackInfoReturnable<Integer> cir) {
		if (trnu$activeScaledCookTimeTotal > 0 || trnu$isCurrentlyProcessingStack()) {
			cir.setReturnValue(trnu$getCookTimeTotalForSync());
		}
	}

	/**
	 * Overwrites screen handler creation so the scaled cook-time values are
	 * synced through the existing builder hooks.
	 *
	 * @param syncID screen sync id
	 * @param player player opening the handler
	 * @return the built screen handler
	 */
	@Overwrite(remap = false)
	public BuiltScreenHandler createScreenHandler(int syncID, Player player) {
		ElectricFurnaceBlockEntity furnace = (ElectricFurnaceBlockEntity) (Object) this;
		return new ScreenHandlerBuilder("electricfurnace").player(player.getInventory()).inventory().hotbar().addInventory()
				.blockEntity(furnace).slot(0, 55, 45).outputSlot(1, 101, 45).energySlot(2, 8, 72).syncEnergyValue()
				.sync(ByteBufCodecs.INT, furnace::getCookTime, furnace::setCookTime)
				.sync(ByteBufCodecs.INT, furnace::getCookTimeTotal, furnace::setCookTimeTotal)
				.addInventory().create(furnace, syncID);
	}

	/**
	 * Normalizes furnace progress rendering to use the scaled cook-time total.
	 *
	 * @param scale the display scale
	 * @param cir callback for the progress getter injection
	 */
	@Inject(method = "getProgressScaled(I)I", at = @At("HEAD"), cancellable = true, remap = false)
	private void trnu$normalizeProgressScaledForFurnace(int scale, CallbackInfoReturnable<Integer> cir) {
		int required = trnu$getLiveCookTimeTotalForUI();
		if (cookTime <= 0 || required <= 0) {
			cir.setReturnValue(0);
			return;
		}
		cir.setReturnValue(cookTime * scale / Math.max(required, 1));
	}

	/**
	 * Performs the remaining stack crafts at tick tail using the captured
	 * snapshot state.
	 *
	 * @param level current level instance
	 * @param pos machine position
	 * @param state machine block state
	 * @param machineBase current machine base
	 * @param ci callback from the tick injection
	 */
	@Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lreborncore/common/blockentity/MachineBaseBlockEntity;)V", at = @At("TAIL"), remap = false)
	private void trnu$craftAdditionalOnStack(Level level, BlockPos pos, BlockState state, MachineBaseBlockEntity machineBase, CallbackInfo ci) {
		if (level == null || level.isClientSide()) {
			return;
		}
		if (machineBase == null || !(machineBase instanceof ProcessingStackAccessor accessor) || !accessor.isProcessingStack()) {
			return;
		}
		if (trnu$recipeAtTickStart == null || trnu$cookTimeTotalAtTickStart <= 0) {
			return;
		}

		ItemStack output = inventory.getItem(1);
		int producedThisTick = output.getCount() - trnu$outputCountAtTickStart;
		if (producedThisTick <= 0) {
			return;
		}

		ItemStack input = inventory.getItem(inputSlot);
		int maxExtraByInput = Math.min(input.getCount(), 63);
		if (maxExtraByInput <= 0) {
			return;
		}

		for (int i = 0; i < maxExtraByInput; i++) {
			if (!hasAllInputs(trnu$recipeAtTickStart) || !canAcceptOutput(trnu$recipeAtTickStart, 1)) {
				break;
			}
			craftRecipe(trnu$recipeAtTickStart);
        }
    }
}
