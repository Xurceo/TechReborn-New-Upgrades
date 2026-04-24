package trnewupgrades.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import reborncore.common.blockentity.MachineBaseBlockEntity;
import reborncore.common.util.RebornInventory;
import techreborn.blockentity.machine.tier1.ElectricFurnaceBlockEntity;
import trnewupgrades.api.ProcessingStackAccessor;

@Mixin(value = ElectricFurnaceBlockEntity.class, remap = false)
public abstract class ElectricFurnaceBlockEntityMixin {

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

	@Shadow
	private boolean hasAllInputs(SmeltingRecipe recipe) {
		throw new AssertionError();
	}

	@Shadow
	private boolean canAcceptOutput(SmeltingRecipe recipe, int count) {
		throw new AssertionError();
	}

	@Shadow
	private void craftRecipe(SmeltingRecipe recipe) {
		throw new AssertionError();
	}

	@Unique
	private SmeltingRecipe trnu$recipeAtTickStart;

	@Unique
	private int trnu$cookTimeAtTickStart;

	@Unique
	private int trnu$cookTimeTotalAtTickStart;

	@Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lreborncore/common/blockentity/MachineBaseBlockEntity;)V", at = @At("HEAD"), remap = false)
	private void trnu$captureTickState(Level level, BlockPos pos, BlockState state, MachineBaseBlockEntity machineBase, CallbackInfo ci) {
		trnu$recipeAtTickStart = currentRecipe;
		trnu$cookTimeAtTickStart = cookTime;
		trnu$cookTimeTotalAtTickStart = cookTimeTotal;
	}

	@Inject(method = "tick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lreborncore/common/blockentity/MachineBaseBlockEntity;)V", at = @At("TAIL"), remap = false)
	private void trnu$craftAdditionalOnStack(Level level, BlockPos pos, BlockState state, MachineBaseBlockEntity machineBase, CallbackInfo ci) {
		if (level == null || level.isClientSide()) {
			return;
		}
		if (!(this instanceof ProcessingStackAccessor accessor) || !accessor.isProcessingStack()) {
			return;
		}
		if (trnu$recipeAtTickStart == null || trnu$cookTimeTotalAtTickStart <= 0) {
			return;
		}
		if (trnu$cookTimeAtTickStart < trnu$cookTimeTotalAtTickStart) {
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
