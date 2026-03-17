package net.skirata3222.lavalogging.mixin;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

import net.skirata3222.lavalogging.util.BlockListRegistry;
import net.skirata3222.lavalogging.util.Lavaloggable;

@Mixin(AnvilBlock.class)
public abstract class AnvilMixin implements Lavaloggable {

	@ModifyArgs(method = "appendProperties", at = @At(value = "INVOKE", target = "Lnet/minecraft/state/StateManager$Builder;add([Lnet/minecraft/state/property/Property;)Lnet/minecraft/state/StateManager$Builder;"))
	private void modifyAppendPropertiesArgs(Args args) {
		Property<?>[] original = args.get(0);
		Property<?>[] extended = new Property[original.length + 2];

		System.arraycopy(original, 0, extended, 0, original.length);
		extended[original.length] = Properties.WATERLOGGED;
		extended[original.length + 1] = LAVALOGGED;

		args.set(0, extended);
	}

	@Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
	private void injectLavaPlacement(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
		BlockState state = cir.getReturnValue();
		if (state == null) {
			return;
		}
		FluidState fluidState = ctx.getWorld().getFluidState(ctx.getBlockPos());
		if (fluidState.getFluid() == Fluids.LAVA && state.contains(LAVALOGGED)
				&& BlockListRegistry.isAllowed(state.getBlock())) {
			cir.setReturnValue(state.with(LAVALOGGED, true));
		}
		if (fluidState.getFluid() == Fluids.WATER && state.contains(Properties.WATERLOGGED)) {
			cir.setReturnValue(state.with(Properties.WATERLOGGED, true));
		}
	}

	@Override
	public boolean canFillWithFluid(@Nullable PlayerEntity player, BlockView world, BlockPos pos, BlockState state,
			Fluid fluid) {
		if (fluid == Fluids.LAVA && state.contains(Lavaloggable.LAVALOGGED)
				&& BlockListRegistry.isAllowed(state.getBlock()) && !state.get(Lavaloggable.LAVALOGGED)
				&& !state.get(Properties.WATERLOGGED)) {
			return true;
		}
		if (fluid == Fluids.WATER && state.contains(Lavaloggable.LAVALOGGED) && state.get(Lavaloggable.LAVALOGGED)) {
			return false;
		}
		return fluid == Fluids.WATER && !state.get(Properties.WATERLOGGED);
	}

	@Override
	public boolean tryFillWithFluid(WorldAccess world, BlockPos pos, BlockState state, FluidState fluidState) {
		if (fluidState.getFluid() == Fluids.LAVA && state.contains(Lavaloggable.LAVALOGGED)
				&& BlockListRegistry.isAllowed(state.getBlock()) && !state.get(Lavaloggable.LAVALOGGED)
				&& !state.get(Properties.WATERLOGGED)) {
			if (!world.isClient()) {
				world.setBlockState(pos, state.with(Lavaloggable.LAVALOGGED, true), Block.NOTIFY_ALL);
				world.scheduleFluidTick(pos, Fluids.LAVA, Fluids.LAVA.getTickRate(world));
			}
			return true;
		}
		if (fluidState.getFluid() == Fluids.WATER && state.contains(Lavaloggable.LAVALOGGED)
				&& !state.get(Lavaloggable.LAVALOGGED) && !state.get(Properties.WATERLOGGED)) {
			if (!world.isClient()) {
				world.setBlockState(pos, state.with(Properties.WATERLOGGED, true), Block.NOTIFY_ALL);
				world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
			}
			return true;
		}
		return false;
	}

	@Override
	public ItemStack tryDrainFluid(@Nullable PlayerEntity player, WorldAccess world, BlockPos pos, BlockState state) {
		if (state.get(LAVALOGGED)) {
			world.setBlockState(pos, state.with(LAVALOGGED, false), Block.NOTIFY_ALL);
			if (!state.canPlaceAt(world, pos)) {
				world.breakBlock(pos, true);
			}
			return new ItemStack(Items.LAVA_BUCKET);
		}
		if (state.get(Properties.WATERLOGGED)) {
			world.setBlockState(pos, state.with(Properties.WATERLOGGED, false), Block.NOTIFY_ALL);
			if (!state.canPlaceAt(world, pos)) {
				world.breakBlock(pos, true);
			}
			return new ItemStack(Items.WATER_BUCKET);
		}
		return ItemStack.EMPTY;
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void afterDefaultState(CallbackInfo ci) {
		BlockState adjusted = ((Block) (Object) this).getDefaultState().with(LAVALOGGED, false);
		((BlockAccessor) (Object) this).invokeSetDefaultState(adjusted);
	}

	@Override
	public Optional<SoundEvent> getBucketFillSound() {
		if (((Block) (Object) this).getDefaultState().contains(Properties.WATERLOGGED)) {
			return Optional.of(SoundEvents.ITEM_BUCKET_FILL);
		}
		if (((Block) (Object) this).getDefaultState().contains(LAVALOGGED)) {
			return Optional.of(SoundEvents.ITEM_BUCKET_FILL_LAVA);
		}
		return Optional.empty();
	}

}
