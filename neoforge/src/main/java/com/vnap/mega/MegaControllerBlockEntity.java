package com.vnap.mega;

import com.vnap.Nbt;
import com.vnap.config.LifeSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A megastructure's controller: whether it stands whole, and its work (a Waygate's key, a Quarry's dig). */
public final class MegaControllerBlockEntity extends BlockEntity {
	/** Ticks between two looks at the structure. */
	static final int LOOK_EVERY = 40;
	/** Ticks since it came alive, while its waking runs through the structure (-1 when not waking). */
	private int waking = -1;
	/** How long the wave of light takes to run from the controller to the farthest block. */
	static final int WAKE = 40;
	private @Nullable DyeColor key;
	public final Quarry.Dig dig = new Quarry.Dig();
	/** The Smelter's smeltings of fuel left; the Well's lore; the Drain's next column. */
	public int fuel;
	public int store;
	/** Who set it down (paid for what its town takes of its work), what the town owes them, and whether the village thanked them. */
	java.util.@Nullable UUID owner;
	int owed;
	boolean thanked;
	/** The Sawmill's logs on earth that are no tree (a log house's corner): passed over (not kept: looked at anew after a restart). */
	final java.util.Set<BlockPos> notTrees = new java.util.HashSet<>();

	public MegaControllerBlockEntity(BlockPos pos, BlockState state) {
		super(MegaBlocks.CONTROLLER_BE, pos, state);
	}

	public MegaKind kind() {
		return getBlockState().getBlock() instanceof MegaControllerBlock block ? block.kind : MegaKind.QUARRY;
	}

	public Direction facing() {
		return getBlockState().getValue(HorizontalDirectionalBlock.FACING);
	}

	public boolean formed() {
		return getBlockState().getValue(MegaControllerBlock.FORMED);
	}

	public @Nullable DyeColor key() {
		return key;
	}

	/** The layer the quarry digs now (its own floor before it began), for the client's drawing of its head. */
	public int digLayer() {
		return dig.layer == Integer.MIN_VALUE ? worldPosition.getY() - 1 : dig.layer;
	}

	public boolean digging() {
		return formed() && !dig.done;
	}

	/** The middle of the quarry's square. */
	public BlockPos digCentre() {
		return Quarry.centre(this);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, MegaControllerBlockEntity controller) {
		if (!(level instanceof ServerLevel server)) return;
		if ((server.getGameTime() + pos.asLong()) % LOOK_EVERY == 0) controller.look(server);
		if (controller.waking >= 0) controller.wake(server);
		if (controller.formed() && controller.waking < 0) {
			if (controller.kind() == MegaKind.QUARRY) Quarry.tick(server, controller);
			else if (controller.kind() != MegaKind.GATE) MegaWork.tick(server, controller);
		}
	}

	/** Whether the structure stands as its plan says (and the server lets megastructures be): lit or dark. */
	public void look(ServerLevel level) {
		boolean whole = LifeSettings.megastructures() && kind().shape().wrong(level, worldPosition, facing()) == null;
		if (whole != formed()) {
			level.setBlock(worldPosition, getBlockState().setValue(MegaControllerBlock.FORMED, whole), 3);
			if (whole) {
				waking = 0;
				TownWork.thanked(level, this);
				for (net.minecraft.server.level.ServerPlayer player : level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
						new net.minecraft.world.phys.AABB(worldPosition).inflate(16))) {
					com.vnap.progress.VillageAdvancements.award(player, "mega/formed");
					com.vnap.progress.Discoveries.discover(player, "mega_" + kind().id());
					boolean all = true;
					for (MegaKind every : MegaKind.values()) all &= com.vnap.progress.Discoveries.of(player).contains("mega_" + every.id());
					if (all) com.vnap.progress.VillageAdvancements.award(player, "mega/all");
				}
				level.playSound(null, worldPosition, com.vnap.sound.SpireSounds.MEGA_FORMED, SoundSource.BLOCKS, 1.4F, 1.0F);
			} else {
				waking = -1;
				level.playSound(null, worldPosition, com.vnap.sound.SpireSounds.MEGA_BROKEN, SoundSource.BLOCKS, 1.2F, 1.0F);
				for (Shape.Cell cell : kind().shape().cells(worldPosition, facing())) {
					if (cell.what() != '.') level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, cell.pos().getX() + 0.5, cell.pos().getY() + 0.5,
							cell.pos().getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
				}
			}
		}
		if (kind() == MegaKind.GATE) Waygates.look(level, this, whole);
		if (kind() == MegaKind.CALM) MegaWork.calm(this, whole);
	}

	/**
	 * Coming alive (Astral Sorcery's and Botania's multiblocks, an End portal frame filling): a wave of light runs out
	 * from the controller through every block of the structure, each part flaring in its portal's colour as it is
	 * reached, and at the end a flash over the whole and the work begins.
	 */
	private void wake(ServerLevel level) {
		java.util.List<Shape.Cell> cells = kind().shape().cells(worldPosition, facing());
		double far = 1.0;
		for (Shape.Cell cell : cells) far = Math.max(far, Math.sqrt(cell.pos().distSqr(worldPosition)));
		double from = far * waking / WAKE;
		double to = far * (waking + 1) / WAKE;
		for (Shape.Cell cell : cells) {
			double d = Math.sqrt(cell.pos().distSqr(worldPosition));
			if (cell.what() == '.' || d < from || d >= to) continue;
			double x = cell.pos().getX() + 0.5;
			double y = cell.pos().getY() + 0.5;
			double z = cell.pos().getZ() + 0.5;
			level.sendParticles(com.vnap.Mc.dust(glow(cell.what()), 1.4F), x, y, z, 8, 0.4, 0.4, 0.4, 0.0);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, x, y, z, 2, 0.3, 0.3, 0.3, 0.02);
			if (cell.what() != 'F') level.playSound(null, cell.pos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 0.8F + (float) (d / far));
		}
		if (++waking <= WAKE) return;
		waking = -1;
		BlockPos middle = kind() == MegaKind.QUARRY ? Quarry.centre(this) : worldPosition.above(2);
		level.sendParticles(com.vnap.Mc.flash(), middle.getX() + 0.5, middle.getY() + 1.0, middle.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, middle.getX() + 0.5, middle.getY() + 1.0, middle.getZ() + 0.5, 60, 1.2, 1.2, 1.2, 0.15);
		level.playSound(null, worldPosition, switch (kind()) {
			case QUARRY -> com.vnap.sound.SpireSounds.QUARRY_START;
			case GATE -> com.vnap.sound.SpireSounds.GATE_OPEN;
			case SMELTER -> com.vnap.sound.SpireSounds.SMELTER_START;
			case CALM -> com.vnap.sound.SpireSounds.CALM_START;
			case DOME -> com.vnap.sound.SpireSounds.DOME_START;
			case WELL -> com.vnap.sound.SpireSounds.WELL_START;
			case DRAIN -> com.vnap.sound.SpireSounds.DRAIN_START;
			case SAWMILL -> com.vnap.sound.SpireSounds.SAWMILL_START;
		}, SoundSource.BLOCKS, 1.4F, 1.0F);
	}

	/** The colour a block of the plan flares in as it wakes: the parts their portals', the frame brass. */
	private static int glow(char what) {
		return switch (what) {
			case 'C' -> 0xE0503A;
			case 'P' -> 0x8AD0F4;
			case 'N' -> 0xB884EC;
			case '@' -> 0xFFFFFF;
			default -> 0xF0CE80;
		};
	}

	/** A click: how it stands, or the first block that is wrong (where, and what it should be). */
	void tell(Player player) {
		if (!(level instanceof ServerLevel server)) return;
		if (!LifeSettings.megastructures()) {
			com.vnap.Mc.say(player, Component.translatable("mega.villager_life.off"), false);
			return;
		}
		TownWork.collect(server, this, player);
		Shape.Cell wrong = kind().shape().wrong(server, worldPosition, facing());
		if (wrong != null) {
			Component want = wrong.what() == '.' ? Component.translatable("mega.villager_life.open") : java.util.Objects.requireNonNull(Shape.block(wrong.what())).getName();
			com.vnap.Mc.say(player, Component.translatable("mega.villager_life.wrong", want, wrong.pos().getX(), wrong.pos().getY(), wrong.pos().getZ()), false);
			return;
		}
		switch (kind()) {
			case QUARRY -> Quarry.tell(server, this, player);
			case GATE -> Waygates.tell(server, this, player);
			default -> MegaWork.tell(server, this, player);
		}
	}

	/** A keystone put in (the one there before comes out to the player). */
	public void key(Player player, ItemStack stack) {
		DyeColor colour = MegaBlocks.colourOf(stack);
		if (colour == null || !(level instanceof ServerLevel server)) return;
		if (key != null) give(player, new ItemStack(MegaBlocks.key(key)));
		key = colour;
		if (!player.getAbilities().instabuild) stack.shrink(1);
		server.playSound(null, worldPosition, com.vnap.sound.SpireSounds.KEYSTONE, SoundSource.BLOCKS, 1.0F, 1.0F);
		server.sendParticles(com.vnap.Mc.dust(colour.getTextureDiffuseColor() & 0xFFFFFF, 1.2F), worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
				worldPosition.getZ() + 0.5, 16, 0.3, 0.2, 0.3, 0.0);
		shown();
		look(server);
		Waygates.tell(server, this, player);
	}

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) com.vnap.Mc.toss(player, stack);
	}

	/** Broken: the keystone drops, the gate leaves the book, its field goes. */
	void broken() {
		if (!(level instanceof ServerLevel server)) return;
		if (key != null) net.minecraft.world.Containers.dropItemStack(server, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
				new ItemStack(MegaBlocks.key(key)));
		key = null;
		if (kind() == MegaKind.GATE) Waygates.look(server, this, false);
		if (kind() == MegaKind.CALM) MegaWork.calm(this, false);
	}

	GlobalPos where() {
		return GlobalPos.of(java.util.Objects.requireNonNull(level).dimension(), worldPosition);
	}

	//? if >=1.21.5 {
	/*@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		broken();
	}
	*///?}

	@Override
	//? if >=1.21.6 {
	/*protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
		super.saveAdditional(output);
		CompoundTag tag = new CompoundTag();
	*///?} else {
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
	//?}
		if (key != null) tag.putString("Key", key.getName());
		dig.save(tag);
		tag.putInt("Fuel2", fuel);
		tag.putInt("Store", store);
		if (owner != null) tag.put("Owner", com.vnap.Mc.uuidTag(owner));
		tag.putInt("Owed", owed);
		tag.putBoolean("Thanked", thanked);
		//? if >=1.21.6
		/*com.vnap.Mc.put(output, tag);*/
	}

	@Override
	//? if >=1.21.6 {
	/*protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
		super.loadAdditional(input);
		CompoundTag tag = com.vnap.Mc.tagOf(input);
	*///?} else {
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
	//?}
		key = tag.contains("Key") ? DyeColor.byName(Nbt.getString(tag, "Key"), null) : null;
		dig.load(tag);
		fuel = Nbt.getInt(tag, "Fuel2");
		store = Nbt.getInt(tag, "Store");
		owner = tag.contains("Owner") ? com.vnap.Mc.uuid(java.util.Objects.requireNonNull(tag.get("Owner"))) : null;
		owed = Nbt.getInt(tag, "Owed");
		thanked = Nbt.getBoolean(tag, "Thanked");
	}

	/** The client draws the dig's head at its layer and the keystone in its socket (MegaControllerRenderer). */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	/** Told to the clients that see it: a new keystone, a new layer of the dig. */
	void shown() {
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
	}

	/** What its renderer draws: the structure, and a Quarry's dig down to its layer. */
	public net.minecraft.world.phys.AABB renderBox() {
		if (kind() != MegaKind.QUARRY) return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(1.0);
		BlockPos centre = digCentre();
		return new net.minecraft.world.phys.AABB(centre.getX() - 2, digLayer() - 1, centre.getZ() - 2, centre.getX() + 3, getBlockPos().getY() + 3, centre.getZ() + 3);
	}
	//? if forge {

	/*@Override
	public net.minecraft.world.phys.AABB getRenderBoundingBox() {
		return renderBox();
	}
	*///?}
}
