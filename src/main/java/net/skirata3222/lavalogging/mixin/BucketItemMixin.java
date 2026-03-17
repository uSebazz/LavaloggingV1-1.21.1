package net.skirata3222.lavalogging.mixin;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

import net.skirata3222.lavalogging.Lavalogging;
import net.skirata3222.lavalogging.util.BlockListRegistry;
import net.skirata3222.lavalogging.util.Lavaloggable;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin {

	@Shadow
	protected abstract void playEmptyingSound(@Nullable PlayerEntity player, WorldAccess world, BlockPos pos);

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void injectLavaUse(World world, PlayerEntity user, Hand hand,
			CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
		ItemStack itemStack = user.getStackInHand(hand);

		try {
			if (itemStack.getItem() == Items.LAVA_BUCKET) {
				BlockHitResult hit = ItemInvoker.callRaycast(world, user, RaycastContext.FluidHandling.NONE);
				if (hit.getType() == HitResult.Type.BLOCK) {
					BlockPos pos = hit.getBlockPos();
					BlockPos adjacent = pos.offset(hit.getSide());
					BlockPos filledPos = null;
					if (tryLavaLog(world, pos)) {
						filledPos = pos;
					} else if (tryLavaLog(world, adjacent)) {
						filledPos = adjacent;
					}

					if (filledPos != null) {
						this.playEmptyingSound(user, world, filledPos);
						((BucketItem) (Object) this).onEmptied(user, world, itemStack, filledPos);
						if (user instanceof ServerPlayerEntity) {
							Criteria.PLACED_BLOCK.trigger((ServerPlayerEntity) user, filledPos, itemStack);
						}
						user.incrementStat(Stats.USED.getOrCreateStat((BucketItem) (Object) this));
						ItemStack itemStack2 = ItemUsage.exchangeStack(itemStack, user,
								BucketItem.getEmptiedStack(itemStack, user));
						user.setStackInHand(hand, itemStack2);
						cir.setReturnValue(TypedActionResult.success(itemStack2, world.isClient()));
						return;
					}
				}
			}
			if (itemStack.getItem() == Items.WATER_BUCKET) {
				BlockHitResult hit = ItemInvoker.callRaycast(world, user, RaycastContext.FluidHandling.NONE);
				if (hit.getType() == HitResult.Type.BLOCK) {
					Boolean fail1 = false;
					Boolean fail2 = false;
					BlockPos pos = hit.getBlockPos();
					BlockState state = world.getBlockState(pos);
					BlockPos adjacent = pos.offset(hit.getSide());
					BlockState adjState = world.getBlockState(adjacent);
					if (state.contains(Lavaloggable.LAVALOGGED) && state.get(Lavaloggable.LAVALOGGED))
						fail1 = true;
					if (adjState.contains(Lavaloggable.LAVALOGGED) && adjState.get(Lavaloggable.LAVALOGGED))
						fail2 = true;
					if (fail1 && fail2) {
						// if both the clicked block AND the block next to it where the water would
						// otherwise go are lavalogged, just don't place the water
						cir.setReturnValue(TypedActionResult.fail(itemStack));
						return;
					}
				}
			}
		} catch (Throwable t) {
			Lavalogging.LOGGER.error("Error in custom bucket handling, falling back to vanilla behavior", t);
		}
	}

	private boolean tryLavaLog(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (!state.contains(Lavaloggable.LAVALOGGED)) {
			return false;
		}
		if (!BlockListRegistry.isAllowed(state.getBlock())) {
			return false;
		}
		if (state.get(Lavaloggable.LAVALOGGED)) {
			return false;
		}
		if (state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)) {
			return false;
		}

		if (!world.isClient()) {
			world.setBlockState(pos, state.with(Lavaloggable.LAVALOGGED, true), Block.NOTIFY_ALL);
			world.scheduleFluidTick(pos, Fluids.LAVA, Fluids.LAVA.getTickRate(world));
		}
		return true;
	}

}
