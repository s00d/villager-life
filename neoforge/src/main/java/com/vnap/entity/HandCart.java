package com.vnap.entity;

import com.vnap.Nbt;
import com.vnap.item.LifeItems;
import com.vnap.sound.GolemSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A workshop's hand cart: it stands by the workshop's chest; a worker going to the stall with more than it can carry
 * hitches it and pulls it there, the load in it for everyone to see (Chores.sell, Produce). It belongs to the job
 * site it stands for ({@link #home}): when that is gone, the cart breaks up into its item and its load. Nobody but a
 * creative player can hurt it; a player's click only says what it carries.
 */
public final class HandCart extends Entity {
	/** Item slots of the load (Produce.CART items at most go in). */
	public static final int SLOTS = 9;
	/** How far behind its puller the cart rolls. */
	private static final double BEHIND = 1.6;
	private static final double SPEED = 0.45;
	private static final double LOST = 24.0;
	private static final EntityDataAccessor<Integer> FILL = SynchedEntityData.defineId(HandCart.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<ItemStack> TOP = SynchedEntityData.defineId(HandCart.class, EntityDataSerializers.ITEM_STACK);

	private final SimpleContainer load = new SimpleContainer(SLOTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			sync();
		}
	};
	private BlockPos home = BlockPos.ZERO;
	private BlockPos park = BlockPos.ZERO;
	private @Nullable UUID puller;
	private int lerpSteps;
	private double lerpX;
	private double lerpY;
	private double lerpZ;
	private float lerpYRot;
	/** Client: how far the wheels have turned (radians). */
	public float wheel;
	public float wheelO;

	public HandCart(EntityType<? extends HandCart> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	/** A new cart for the workshop whose job site is {@code home}, standing at {@code park}. */
	public static @Nullable HandCart place(ServerLevel level, BlockPos home, BlockPos park, float yaw) {
		HandCart cart = com.vnap.Mc.create(VillageEntities.HAND_CART, level);
		if (cart == null) return null;
		cart.home = home.immutable();
		cart.park = park.immutable();
		com.vnap.Mc.snap(cart, park.getX() + 0.5, park.getY(), park.getZ() + 0.5, yaw, 0.0F);
		level.addFreshEntity(cart);
		return cart;
	}

	/** The cart of the workshop whose job site is {@code home}, when it is loaded. */
	public static @Nullable HandCart of(ServerLevel level, BlockPos home) {
		List<HandCart> carts = level.getEntities(VillageEntities.HAND_CART, new AABB(home).inflate(48.0), cart -> cart.isAlive() && cart.home.equals(home));
		return carts.isEmpty() ? null : carts.get(0);
	}

	public SimpleContainer load() {
		return load;
	}

	public int count() {
		int n = 0;
		for (int i = 0; i < load.getContainerSize(); i++) n += load.getItem(i).getCount();
		return n;
	}

	public BlockPos park() {
		return park;
	}

	public BlockPos home() {
		return home;
	}

	public boolean pulled() {
		return puller != null;
	}

	/** The worker takes the cart's shafts: from now on it rolls behind it. */
	public void hitch(Entity by) {
		puller = by.getUUID();
	}

	/** Back on its stand by the chest, let go. */
	public void parkNow() {
		puller = null;
		com.vnap.Mc.snap(this, park.getX() + 0.5, park.getY(), park.getZ() + 0.5, getYRot(), 0.0F);
	}

	/** How full it looks, 0..4, and what lies on top: synced for the renderer. */
	private void sync() {
		if (level().isClientSide()) return;
		int n = count();
		entityData.set(FILL, n == 0 ? 0 : Mth.clamp((int) Math.ceil(n * 4.0 / com.vnap.village.market.Produce.CART), 1, 4));
		Map<Item, Integer> kinds = new LinkedHashMap<>();
		for (int i = 0; i < load.getContainerSize(); i++) {
			ItemStack stack = load.getItem(i);
			if (!stack.isEmpty()) kinds.merge(stack.getItem(), stack.getCount(), Integer::sum);
		}
		Item top = kinds.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
		entityData.set(TOP, top == null ? ItemStack.EMPTY : new ItemStack(top));
	}

	public int fill() {
		return entityData.get(FILL);
	}

	public ItemStack top() {
		return entityData.get(TOP);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FILL, 0);
		builder.define(TOP, ItemStack.EMPTY);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			clientTick();
			return;
		}
		ServerLevel level = (ServerLevel) level();
		UUID by = puller;
		Entity pulling = by == null ? null : level.getEntity(by);
		if (by != null && (pulling == null || !pulling.isAlive() || pulling.distanceTo(this) > LOST)) {
			// the worker was called away or is gone: the cart rolls home by itself (nobody steals from it)
			parkNow();
			return;
		}
		if (pulling != null) follow(level, pulling);
		if (tickCount % 200 == 0 && level.isLoaded(home) && level.getPoiManager().getType(home).isEmpty()) breakUp();
	}

	private void follow(ServerLevel level, Entity pulling) {
		float yaw = pulling instanceof net.minecraft.world.entity.LivingEntity living ? living.yBodyRot : pulling.getYRot();
		Vec3 back = Vec3.directionFromRotation(0.0F, yaw).scale(-BEHIND);
		Vec3 target = pulling.position().add(back.x, 0.0, back.z);
		Vec3 to = target.subtract(position());
		double dist = to.horizontalDistance();
		Vec3 next = dist > 8.0 ? target : dist > SPEED ? position().add(to.scale(SPEED / dist)) : target;
		double y = Mth.lerp(0.5, getY(), pulling.getY());
		Vec3 face = pulling.position().subtract(next);
		float turn = face.horizontalDistanceSqr() < 0.01 ? getYRot() : (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90.0F;
		boolean moving = position().distanceToSqr(next.x, y, next.z) > 0.0004;
		com.vnap.Mc.snap(this, next.x, y, next.z, Mth.rotLerp(0.4F, getYRot(), turn), 0.0F);
		if (moving && tickCount % 30 == 0) level.playSound(null, blockPosition(), GolemSounds.CART_ROLL, SoundSource.NEUTRAL, 0.5F, 0.9F + random.nextFloat() * 0.2F);
	}

	/** The workshop is gone: the cart and its load are dropped where it stands. */
	private void breakUp() {
		com.vnap.Mc.drop(this, new ItemStack(LifeItems.HAND_CART));
		for (int i = 0; i < load.getContainerSize(); i++) {
			ItemStack stack = load.removeItemNoUpdate(i);
			if (!stack.isEmpty()) com.vnap.Mc.drop(this, stack);
		}
		discard();
	}

	//? if >=26.3 {
	/*// from 26.3 an entity's common tick runs its interpolation
	@Override
	protected net.minecraft.world.entity.InterpolationHandler createInterpolationHandler() {
		return net.minecraft.world.entity.LinearInterpolationHandler.create(this, 3);
	}
	*///?} else if >=1.21.5 {
	/*private final net.minecraft.world.entity.InterpolationHandler interpolation = new net.minecraft.world.entity.InterpolationHandler(this, 3);

	@Override
	public net.minecraft.world.entity.InterpolationHandler getInterpolation() {
		return interpolation;
	}
	*///?}

	private void clientTick() {
		Vec3 before = position();
		//? if >=1.21.5 <26.3
		/*interpolation.interpolate();*/
		if (lerpSteps > 0) {
			double x = getX() + (lerpX - getX()) / lerpSteps;
			double y = getY() + (lerpY - getY()) / lerpSteps;
			double z = getZ() + (lerpZ - getZ()) / lerpSteps;
			setYRot(getYRot() + Mth.wrapDegrees(lerpYRot - getYRot()) / lerpSteps);
			lerpSteps--;
			setPos(x, y, z);
		}
		wheelO = wheel;
		// rim 5 px: a turn per 2π·5/16 blocks
		wheel += (float) (position().subtract(before).horizontalDistance() / (5.0 / 16.0));
	}

	//? if <1.21.5 {
	@Override
	public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
		lerpX = x;
		lerpY = y;
		lerpZ = z;
		lerpYRot = yRot;
		lerpSteps = 3;
	}
	//?}

	@Override
	//? if >=26.1 {
	/*public InteractionResult interact(Player player, InteractionHand hand, net.minecraft.world.phys.Vec3 location) {
	*///?} else
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (!(level() instanceof ServerLevel)) return InteractionResult.SUCCESS;
		ItemStack top = top();
		com.vnap.Mc.say(player, top.isEmpty() ? Component.translatable("entity.villager_life.hand_cart.empty")
				: Component.translatable("entity.villager_life.hand_cart.load", count(), top.getHoverName()), true);
		return InteractionResult.CONSUME;
	}

	//? if >=1.21.2 {
	/*@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
	*///?} else {
	@Override
	public boolean hurt(DamageSource source, float amount) {
	//?}
		if (level().isClientSide() || isRemoved() || !(source.getEntity() instanceof Player player) || !player.getAbilities().instabuild) return false;
		discard();
		return true;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	//? if >=1.21.5 {
	/*public boolean canBeCollidedWith(net.minecraft.world.entity.@Nullable Entity entity) {
	*///?} else
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(LifeItems.HAND_CART);
	}

	@Override
	//? if >=1.21.6 {
	/*protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
		CompoundTag tag = com.vnap.Mc.tagOf(input);
	*///?} else {
	protected void readAdditionalSaveData(CompoundTag tag) {
	//?}
		home = BlockPos.of(Nbt.getLong(tag, "Home"));
		park = BlockPos.of(Nbt.getLong(tag, "Park"));
		NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		com.vnap.Mc.loadItems(tag, items, registryAccess());
		for (int i = 0; i < SLOTS; i++) load.setItem(i, items.get(i));
		sync();
	}

	@Override
	//? if >=1.21.6 {
	/*protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
		CompoundTag tag = new CompoundTag();
	*///?} else {
	protected void addAdditionalSaveData(CompoundTag tag) {
	//?}
		tag.putLong("Home", home.asLong());
		tag.putLong("Park", park.asLong());
		NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
		for (int i = 0; i < SLOTS; i++) items.set(i, load.getItem(i));
		com.vnap.Mc.saveItems(tag, items, registryAccess());
		//? if >=1.21.6
		/*com.vnap.Mc.put(output, tag);*/
	}
}
