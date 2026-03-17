package net.skirata3222.lavalogging.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.FluidDrainable;
import net.minecraft.block.FluidFillable;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;

public interface Lavaloggable extends FluidFillable, FluidDrainable {
	BooleanProperty LAVALOGGED = BooleanProperty.of("lavalogged");

	default BlockState withLavaPlacement(BlockState state, ItemPlacementContext ctx) {
		if (!BlockListRegistry.isAllowed(state.getBlock()))
			return state.with(LAVALOGGED, false);
		FluidState fluidState = ctx.getWorld().getFluidState(ctx.getBlockPos());
		return state.with(LAVALOGGED, fluidState.getFluid() == Fluids.LAVA);
	}

	default BlockState updateLavaNeighbor(BlockState state, WorldAccess world, BlockPos pos) {
		if (state.get(LAVALOGGED) && BlockListRegistry.isAllowed(state.getBlock())) {
			world.scheduleFluidTick(pos, Fluids.LAVA, Fluids.LAVA.getTickRate(world));
		}
		return state;
	}

}
