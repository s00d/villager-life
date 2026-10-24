package com.vnap.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

/** An unseen seat on a bench (a step of stairs): a player sits on it with an empty hand; it goes when they get up. */
public final class Seat extends Entity {
	public Seat(EntityType<? extends Seat> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	/** A player right-clicked a step with an empty hand: sits on it when it is a bench (a lower step, free above). */
	public static boolean sit(ServerLevel level, Player player, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof StairBlock) || state.getValue(StairBlock.HALF) != Half.BOTTOM || player.isPassenger()
				|| player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty() || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 9.0
				|| !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				|| !level.getEntitiesOfClass(Seat.class, new net.minecraft.world.phys.AABB(pos)).isEmpty()) return false;
		Seat seat = com.vnap.Mc.create(VillageEntities.SEAT, level);
		if (seat == null) return false;
		com.vnap.Mc.snap(seat, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, state.getValue(StairBlock.FACING).getOpposite().toYRot(), 0.0F);
		level.addFreshEntity(seat);
		player.startRiding(seat);
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) return;
		BlockState state = level().getBlockState(blockPosition());
		if (getPassengers().isEmpty() || !(state.getBlock() instanceof StairBlock)) {
			ejectPassengers();
			discard();
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return Vec3.atBottomCenterOf(blockPosition().above());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	//? if >=1.21.6 {
	/*protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
	*///?} else {
	protected void readAdditionalSaveData(CompoundTag tag) {
	//?}
	}

	@Override
	//? if >=1.21.6 {
	/*protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
	*///?} else {
	protected void addAdditionalSaveData(CompoundTag tag) {
	//?}
	}

	//? if >=1.21.2 {
	/*@Override
	public boolean hurtServer(net.minecraft.server.level.ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
		return false;
	}
	*///?}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
