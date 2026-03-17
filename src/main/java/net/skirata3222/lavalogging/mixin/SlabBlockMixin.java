package net.skirata3222.lavalogging.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.Direction;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

import net.skirata3222.lavalogging.util.BlockListRegistry;
import net.skirata3222.lavalogging.util.Lavaloggable;

@Mixin(SlabBlock.class)
public abstract class SlabBlockMixin implements Lavaloggable {

	@ModifyArgs(method = "appendProperties", at = @At(value = "INVOKE", target = "Lnet/minecraft/state/StateManager$Builder;add([Lnet/minecraft/state/property/Property;)Lnet/minecraft/state/StateManager$Builder;"))
	private void modifyAppendPropertiesArgs(Args args) {
		Property<?>[] original = args.get(0);
		// Find WATERLOGGED’s index
		int waterIdx = -1;
		for (int i = 0; i < original.length; i++) {
			if (original[i] == Properties.WATERLOGGED) {
				waterIdx = i;
				break;
			}
		}
		Property<?>[] extended = new Property[original.length + 1];

		if (waterIdx >= 0) {
			// Copy everything up to WATERLOGGED
			System.arraycopy(original, 0, extended, 0, waterIdx + 1);
			// Insert LAVALOGGED right after WATERLOGGED
			extended[waterIdx + 1] = Lavaloggable.LAVALOGGED;
			System.arraycopy(original, waterIdx + 1, extended, waterIdx + 2, original.length - (waterIdx + 1));
		} else {
			// Fallback: just append at the end
			System.arraycopy(original, 0, extended, 0, original.length);
			extended[original.length] = Lavaloggable.LAVALOGGED;
		}
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
			return;
		} else {
			cir.setReturnValue(state.with(LAVALOGGED, false));
			return;
		}
	}

	@Inject(method = "getStateForNeighborUpdate", at = @At("TAIL"))
	private void lavalogNeighbor(BlockState state, Direction direction, BlockState neighborState, WorldAccess world,
			BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
		if (state.contains(LAVALOGGED) && state.get(LAVALOGGED)) {
			world.scheduleFluidTick(pos, Fluids.LAVA, Fluids.LAVA.getTickRate(world));
		}
	}

	@Inject(method = "canFillWithFluid", at = @At("HEAD"), cancellable = true)
	private void canFillFixed(@Nullable PlayerEntity player, BlockView world, BlockPos pos, BlockState state,
			Fluid fluid, CallbackInfoReturnable<Boolean> cir) {
		if (fluid == Fluids.LAVA && state.contains(LAVALOGGED) && BlockListRegistry.isAllowed(state.getBlock())
				&& !state.get(LAVALOGGED) && !state.get(Properties.WATERLOGGED)) {
			if (state.get(Properties.SLAB_TYPE) != SlabType.DOUBLE)
				cir.setReturnValue(true);
			return;
		}
		if (fluid == Fluids.WATER && state.contains(LAVALOGGED) && state.get(LAVALOGGED)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "tryFillWithFluid", at = @At("HEAD"), cancellable = true)
	private void tryFillFixed(WorldAccess world, BlockPos pos, BlockState state, FluidState fluidState,
			CallbackInfoReturnable<Boolean> cir) {
		if (fluidState.getFluid() == Fluids.LAVA && state.contains(LAVALOGGED)
				&& BlockListRegistry.isAllowed(state.getBlock()) && !state.get(LAVALOGGED)
				&& !state.get(Properties.WATERLOGGED)) {
			if (state.get(Properties.SLAB_TYPE) != SlabType.DOUBLE) {
				if (!world.isClient()) {
					world.setBlockState(pos, state.with(LAVALOGGED, true), Block.NOTIFY_ALL);
					world.scheduleFluidTick(pos, Fluids.LAVA, Fluids.LAVA.getTickRate(world));
				}
				cir.setReturnValue(true);
				return;
			}
		}
		if (fluidState.getFluid() == Fluids.WATER && state.contains(Properties.WATERLOGGED) && !state.get(LAVALOGGED)
				&& !state.get(Properties.WATERLOGGED)) {
			if (state.get(Properties.SLAB_TYPE) != SlabType.DOUBLE) {
				if (!world.isClient()) {
					world.setBlockState(pos, state.with(Properties.WATERLOGGED, true), Block.NOTIFY_ALL);
					world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
				}
				cir.setReturnValue(true);
				return;
			}
		}
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

	@Shadow
	protected static VoxelShape BOTTOM_SHAPE;
	@Shadow
	protected static VoxelShape TOP_SHAPE;

	@Inject(method = "getOutlineShape", at = @At("HEAD"), cancellable = true)
	private void handleLavalogged(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		if (state.contains(LAVALOGGED)) {
			SlabType type = state.get(Properties.SLAB_TYPE);
			switch (type) {
				case DOUBLE -> cir.setReturnValue(VoxelShapes.fullCube());
				case TOP -> cir.setReturnValue(TOP_SHAPE);
				default -> cir.setReturnValue(BOTTOM_SHAPE);
			}
		}

	}

	@Inject(method = "getFluidState", at = @At("RETURN"), cancellable = true)
	private void fixFluidGetting(BlockState state, CallbackInfoReturnable<FluidState> cir) {
		if (state.contains(LAVALOGGED) && state.get(LAVALOGGED)) {
			cir.setReturnValue(Fluids.LAVA.getStill(false));
			return;
		}
		if (state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)) {
			cir.setReturnValue(Fluids.WATER.getStill(false));
			return;
		}
	}

}
