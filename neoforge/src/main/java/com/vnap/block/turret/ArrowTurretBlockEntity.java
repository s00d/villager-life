package com.vnap.block.turret;

import com.vnap.block.LifeBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/** The crossbow's store (arrows only) and its aim: the nearest monster in sight within range, never a friend in the way. */
public final class ArrowTurretBlockEntity extends BlockEntity implements Container {
	public static final int SLOTS = 9;
	public static final double RANGE = 16.0;
	/** Ticks between shots. */
	static final int RELOAD = 30;
	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
	private int cooldown;

	public ArrowTurretBlockEntity(BlockPos pos, BlockState state) {
		super(LifeBlocks.TURRET_BE, pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, ArrowTurretBlockEntity turret) {
		if (!(level instanceof ServerLevel server) || --turret.cooldown > 0) return;
		turret.cooldown = 10;
		// only where the world runs: a turret in a border chunk keeps its arrows
		if (!server.isPositionEntityTicking(pos) || turret.arrows() == 0) return;
		Vec3 muzzle = Vec3.atCenterOf(pos).add(0.0, 0.3, 0.0);
		LivingEntity target = turret.target(server, muzzle);
		if (target == null) return;
		Arrow arrow = new Arrow(server, muzzle.x, muzzle.y, muzzle.z, new ItemStack(Items.ARROW), null);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		double dx = target.getX() - muzzle.x;
		double dy = target.getY(0.3333) - muzzle.y;
		double dz = target.getZ() - muzzle.z;
		double flat = Math.sqrt(dx * dx + dz * dz);
		arrow.shoot(dx, dy + flat * 0.2, dz, 1.6F, 4.0F);
		server.addFreshEntity(arrow);
		server.playSound(null, pos, com.vnap.sound.GolemSounds.TURRET_SHOT, SoundSource.BLOCKS, 1.0F, 1.0F);
		turret.take();
		turret.cooldown = RELOAD;
	}

	/** The nearest monster within range it can see, with nobody it must not hit near the line of fire. */
	@Nullable LivingEntity target(ServerLevel level, Vec3 muzzle) {
		AABB around = new AABB(getBlockPos()).inflate(RANGE);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, around, mob -> mob instanceof Enemy && mob.isAlive())) {
			double distance = mob.distanceToSqr(muzzle);
			if (distance > RANGE * RANGE || distance >= bestDistance) continue;
			Vec3 at = mob.getEyePosition();
			if (level.clip(new ClipContext(muzzle, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType() != HitResult.Type.MISS) {
				continue;
			}
			if (!level.getEntities((Entity) null, new AABB(muzzle, at).inflate(1.0), ArrowTurretBlockEntity::friend).isEmpty()) continue;
			best = mob;
			bestDistance = distance;
		}
		return best;
	}

	private static boolean friend(Entity entity) {
		return entity instanceof AbstractVillager || entity instanceof Player || entity instanceof IronGolem
				|| (entity instanceof TamableAnimal pet && pet.isTame());
	}

	public int arrows() {
		int n = 0;
		for (ItemStack stack : items) n += stack.getCount();
		return n;
	}

	private void take() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				stack.shrink(1);
				setChanged();
				return;
			}
		}
	}

	/** Puts arrows in; what does not fit comes back. */
	public ItemStack load(ItemStack stack) {
		if (!stack.is(Items.ARROW)) return stack;
		for (int slot = 0; slot < SLOTS && !stack.isEmpty(); slot++) {
			ItemStack there = items.get(slot);
			if (there.isEmpty()) {
				items.set(slot, stack.copy());
				stack.setCount(0);
			} else if (there.getCount() < there.getMaxStackSize()) {
				int moved = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
				there.grow(moved);
				stack.shrink(moved);
			}
		}
		setChanged();
		return stack;
	}

	@Override
	//? if >=1.21.6 {
	/*protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
		super.loadAdditional(input);
		CompoundTag tag = com.vnap.Mc.tagOf(input);
		HolderLookup.Provider registries = com.vnap.Mc.registries(level);
	*///?} else {
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
	//?}
		items.clear();
		com.vnap.Mc.loadItems(tag, items, registries);
	}

	@Override
	//? if >=1.21.6 {
	/*protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		super.saveAdditional(output);
		CompoundTag tag = new CompoundTag();
		HolderLookup.Provider registries = com.vnap.Mc.registries(level);
	*///?} else {
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
	//?}
		com.vnap.Mc.saveItems(tag, items, registries);
		//? if >=1.21.6
		/*com.vnap.Mc.put(output, tag);*/
	}

	//? if >=1.21.5 {
	/*@Override
	public void preRemoveSideEffects(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		if (level != null) net.minecraft.world.Containers.dropContents(level, pos, this);
	}
	*///?}

	@Override
	public int getContainerSize() {
		return SLOTS;
	}

	@Override
	public boolean isEmpty() {
		return arrows() == 0;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack out = ContainerHelper.removeItem(items, slot, amount);
		if (!out.isEmpty()) setChanged();
		return out;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.is(Items.ARROW);
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
	}

	/** The way it faces (where it was set to look out). */
	public net.minecraft.core.Direction facing() {
		return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
	}
}
