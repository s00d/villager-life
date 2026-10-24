package com.vnap.mixin;

import com.mojang.serialization.Codec;
import com.vnap.Nbt;
import com.vnap.dialogue.FamilyState;
import com.vnap.entity.VillagerLifeData;
import com.vnap.village.workshop.WorkshopState;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The mod's own data on every villager, saved under VillagerLife* keys; the four look flags and the spire clothes are synced to clients. */
@Mixin(Villager.class)
@SuppressWarnings("unused")
public abstract class VillagerDataMixin implements VillagerLifeData {
	//? if >=1.21.6 {
	/*@Unique
	private static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Boolean>> VNAP_FEMALE = com.vnap.entity.LookAttachments.FEMALE;
	@Unique
	private static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Boolean>> VNAP_ELDER = com.vnap.entity.LookAttachments.ELDER;
	@Unique
	private static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Boolean>> VNAP_MAYOR = com.vnap.entity.LookAttachments.MAYOR;
	@Unique
	private static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Boolean>> VNAP_SHOPKEEPER = com.vnap.entity.LookAttachments.SHOPKEEPER;

	@Unique
	private int vnap$outfit() {
		return vnap$self().getData(com.vnap.entity.LookAttachments.SPIRE_OUTFIT);
	}

	@Unique
	private void vnap$outfit(int value) {
		vnap$self().setData(com.vnap.entity.LookAttachments.SPIRE_OUTFIT, value);
	}

	@Unique
	private boolean vnap$flag(java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Boolean>> flag) {
		return vnap$self().getData(flag);
	}

	@Unique
	private void vnap$flag(java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Boolean>> flag, boolean value) {
		vnap$self().setData(flag, value);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void vnap$save(net.minecraft.world.level.storage.ValueOutput output, CallbackInfo ci) {
		CompoundTag tag = new CompoundTag();
		vnap$write(tag);
		com.vnap.Mc.put(output, tag);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void vnap$load(net.minecraft.world.level.storage.ValueInput input, CallbackInfo ci) {
		vnap$read(com.vnap.Mc.tagOf(input));
	}
	*///?} else {
	@Unique
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> VNAP_FEMALE = net.minecraft.network.syncher.SynchedEntityData.defineId(Villager.class,
			net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
	@Unique
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> VNAP_ELDER = net.minecraft.network.syncher.SynchedEntityData.defineId(Villager.class,
			net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
	@Unique
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> VNAP_MAYOR = net.minecraft.network.syncher.SynchedEntityData.defineId(Villager.class,
			net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
	@Unique
	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> VNAP_SHOPKEEPER = net.minecraft.network.syncher.SynchedEntityData.defineId(Villager.class,
			net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

	@Unique
	private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> VNAP_OUTFIT = net.minecraft.network.syncher.SynchedEntityData.defineId(Villager.class,
			net.minecraft.network.syncher.EntityDataSerializers.INT);

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void vnap$defineData(net.minecraft.network.syncher.SynchedEntityData.Builder builder, CallbackInfo ci) {
		for (var flag : java.util.List.of(VNAP_FEMALE, VNAP_ELDER, VNAP_MAYOR, VNAP_SHOPKEEPER)) builder.define(flag, false);
		builder.define(VNAP_OUTFIT, 0);
	}

	@Unique
	private int vnap$outfit() {
		return vnap$self().getEntityData().get(VNAP_OUTFIT);
	}

	@Unique
	private void vnap$outfit(int value) {
		vnap$self().getEntityData().set(VNAP_OUTFIT, value);
	}

	@Unique
	private boolean vnap$flag(net.minecraft.network.syncher.EntityDataAccessor<Boolean> flag) {
		return vnap$self().getEntityData().get(flag);
	}

	@Unique
	private void vnap$flag(net.minecraft.network.syncher.EntityDataAccessor<Boolean> flag, boolean value) {
		vnap$self().getEntityData().set(flag, value);
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void vnap$save(CompoundTag tag, CallbackInfo ci) {
		vnap$write(tag);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void vnap$load(CompoundTag tag, CallbackInfo ci) {
		vnap$read(tag);
	}
	//?}

	@Unique
	private FamilyState vnap$family = new FamilyState();
	@Unique
	private @Nullable WorkshopState vnap$workshop;
	@Unique
	private @Nullable BlockPos vnap$homeBed;
	@Unique
	private long vnap$choreDay = -1L;
	@Unique
	private int vnap$decor;
	@Unique
	private long vnap$hurtDay = -1L;
	@Unique
	private long vnap$hurtAt;
	@Unique
	private long vnap$thanksDay = -1L;
	@Unique
	private int vnap$seenGeneration = -1;
	@Unique
	private @Nullable VillagerData vnap$tradeBefore;
	@Unique
	private @Nullable MerchantOffers vnap$offersBefore;

	@SuppressFBWarnings(value = "BC_IMPOSSIBLE_CAST", justification = "the mixin is merged into Villager; SpotBugs cannot see it")
	@Unique
	private Villager vnap$self() {
		return (Villager) (Object) this;
	}

	@Unique
	private void vnap$write(CompoundTag tag) {
		tag.put("VillagerLifeFamily", vnap$family.save());
		if (vnap$workshop != null) tag.put("VillagerLifeWorkshop", vnap$workshop.save());
		if (vnap$homeBed != null) tag.putLong("VillagerLifeHomeBed", vnap$homeBed.asLong());
		tag.putLong("VillagerLifeHomeChoreDay", vnap$choreDay);
		tag.putInt("VillagerLifeHomeDecor", vnap$decor);
		tag.putLong("VillagerLifeHomeHurtDay", vnap$hurtDay);
		tag.putLong("VillagerLifeHomeHurtPos", vnap$hurtAt);
		tag.putLong("VillagerLifeHomeThanksDay", vnap$thanksDay);
		tag.putBoolean("VillagerLifeFemale", vnap$female());
		tag.putBoolean("VillagerLifeElder", vnap$elder());
		tag.putBoolean("VillagerLifeMayor", vnap$mayor());
		tag.putBoolean("VillagerLifeShopkeeper", vnap$shopkeeper());
		tag.putInt("VillagerLifeSpireOutfit", vnap$outfit());
		if (vnap$tradeBefore != null && vnap$offersBefore != null) {
			vnap$encode(tag, "VillagerLifeOriginalData", VillagerData.CODEC, vnap$tradeBefore);
			vnap$encode(tag, "VillagerLifeOriginalOffers", MerchantOffers.CODEC, vnap$offersBefore);
		}
	}

	@Unique
	private void vnap$read(CompoundTag tag) {
		vnap$family = tag.contains("VillagerLifeFamily") ? FamilyState.load(Nbt.getCompound(tag, "VillagerLifeFamily")) : new FamilyState();
		vnap$workshop = tag.contains("VillagerLifeWorkshop") ? WorkshopState.load(Nbt.getCompound(tag, "VillagerLifeWorkshop")) : null;
		vnap$homeBed = tag.contains("VillagerLifeHomeBed") ? BlockPos.of(Nbt.getLong(tag, "VillagerLifeHomeBed")) : null;
		vnap$choreDay = vnap$long(tag, "VillagerLifeHomeChoreDay");
		vnap$decor = Nbt.getInt(tag, "VillagerLifeHomeDecor");
		vnap$hurtDay = vnap$long(tag, "VillagerLifeHomeHurtDay");
		vnap$hurtAt = Nbt.getLong(tag, "VillagerLifeHomeHurtPos");
		vnap$thanksDay = vnap$long(tag, "VillagerLifeHomeThanksDay");
		vnap$setFemale(Nbt.getBoolean(tag, "VillagerLifeFemale"));
		vnap$setElder(Nbt.getBoolean(tag, "VillagerLifeElder"));
		vnap$setMayor(Nbt.getBoolean(tag, "VillagerLifeMayor"));
		vnap$setShopkeeper(Nbt.getBoolean(tag, "VillagerLifeShopkeeper"));
		vnap$outfit(Nbt.getInt(tag, "VillagerLifeSpireOutfit"));
		VillagerData trade = vnap$decode(tag, "VillagerLifeOriginalData", VillagerData.CODEC);
		MerchantOffers offers = vnap$decode(tag, "VillagerLifeOriginalOffers", MerchantOffers.CODEC);
		boolean both = trade != null && offers != null;
		vnap$tradeBefore = both ? trade : null;
		vnap$offersBefore = both ? offers : null;
	}

	/** A day stored as -1 when it never happened (an old save without the key reads the same). */
	@Unique
	private static long vnap$long(CompoundTag tag, String key) {
		return tag.contains(key) ? Nbt.getLong(tag, key) : -1L;
	}

	@Unique
	private RegistryOps<Tag> vnap$ops() {
		return vnap$self().level().registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	@Unique
	private <T> void vnap$encode(CompoundTag tag, String key, Codec<T> codec, T value) {
		codec.encodeStart(vnap$ops(), value).result().ifPresent(encoded -> tag.put(key, encoded));
	}

	@Unique
	private <T> @Nullable T vnap$decode(CompoundTag tag, String key, Codec<T> codec) {
		Tag stored = tag.get(key);
		return stored == null ? null : codec.parse(vnap$ops(), stored).result().orElse(null);
	}

	/** Children come to married couples only: nobody else looks for a partner. */
	@Inject(method = "canBreed", at = @At("RETURN"), cancellable = true)
	private void vnap$onlyMarriedBreed(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !vnap$family.married()) cir.setReturnValue(false);
	}

	/** The mayor and the shopkeeper take no trade, whatever the work block nearby says. */
	@ModifyVariable(method = "setVillagerData", at = @At("HEAD"), argsOnly = true)
	private VillagerData vnap$noTradeInOffice(VillagerData value) {
		if (!vnap$mayor() && !vnap$shopkeeper()) return value;
		return com.vnap.Mc.withLevel(com.vnap.Mc.withProfession(value, VillagerProfession.NONE), 1);
	}

	@Override
	public boolean vnap$hasOriginalVillagerState() {
		return vnap$tradeBefore != null && vnap$offersBefore != null;
	}

	@Override
	public void vnap$captureOriginalVillagerState() {
		if (vnap$hasOriginalVillagerState()) return;
		vnap$tradeBefore = vnap$self().getVillagerData();
		vnap$offersBefore = vnap$copy(vnap$self().getOffers());
	}

	@Override
	public void vnap$restoreOriginalVillagerState() {
		VillagerData trade = vnap$tradeBefore;
		MerchantOffers offers = vnap$offersBefore;
		if (trade == null || offers == null) return;
		vnap$tradeBefore = null;
		vnap$offersBefore = null;
		vnap$self().setVillagerData(trade);
		vnap$self().getOffers().clear();
		vnap$self().getOffers().addAll(vnap$copy(offers));
	}

	/** A deep copy through the codec: the offers object itself keeps changing with the villager's trades. */
	@Unique
	private MerchantOffers vnap$copy(MerchantOffers offers) {
		RegistryOps<Tag> ops = vnap$ops();
		return MerchantOffers.CODEC.encodeStart(ops, offers).flatMap(tag -> MerchantOffers.CODEC.parse(ops, tag)).result()
				.orElseGet(MerchantOffers::new);
	}

	@Override
	public FamilyState vnap$family() {
		return vnap$family;
	}

	@Override
	public @Nullable WorkshopState vnap$workshop() {
		return vnap$workshop;
	}

	@Override
	public void vnap$setWorkshop(@Nullable WorkshopState state) {
		vnap$workshop = state;
	}

	@Override
	public @Nullable BlockPos vnap$homeBed() {
		return vnap$homeBed;
	}

	@Override
	public void vnap$setHomeBed(@Nullable BlockPos pos) {
		vnap$homeBed = pos == null ? null : pos.immutable();
	}

	@Override
	public long vnap$homeChoreDay() {
		return vnap$choreDay;
	}

	@Override
	public void vnap$setHomeChoreDay(long day) {
		vnap$choreDay = day;
	}

	@Override
	public int vnap$homeDecor() {
		return vnap$decor;
	}

	@Override
	public void vnap$setHomeDecor(int count) {
		vnap$decor = count;
	}

	@Override
	public long vnap$homeHurtDay() {
		return vnap$hurtDay;
	}

	@Override
	public long vnap$homeHurtPos() {
		return vnap$hurtAt;
	}

	@Override
	public void vnap$setHomeHurt(long day, long pos) {
		vnap$hurtDay = day;
		vnap$hurtAt = pos;
	}

	@Override
	public long vnap$homeThanksDay() {
		return vnap$thanksDay;
	}

	@Override
	public void vnap$setHomeThanksDay(long day) {
		vnap$thanksDay = day;
	}

	@Override
	public int vnap$homeSeenGen() {
		return vnap$seenGeneration;
	}

	@Override
	public void vnap$setHomeSeenGen(int generation) {
		vnap$seenGeneration = generation;
	}

	@Override
	public boolean vnap$female() {
		return vnap$flag(VNAP_FEMALE);
	}

	@Override
	public void vnap$setFemale(boolean value) {
		vnap$flag(VNAP_FEMALE, value);
	}

	@Override
	public boolean vnap$elder() {
		return vnap$flag(VNAP_ELDER);
	}

	@Override
	public void vnap$setElder(boolean value) {
		vnap$flag(VNAP_ELDER, value);
	}

	@Override
	public boolean vnap$mayor() {
		return vnap$flag(VNAP_MAYOR);
	}

	@Override
	public void vnap$setMayor(boolean value) {
		vnap$flag(VNAP_MAYOR, value);
	}

	@Override
	public boolean vnap$shopkeeper() {
		return vnap$flag(VNAP_SHOPKEEPER);
	}

	@Override
	public void vnap$setShopkeeper(boolean value) {
		vnap$flag(VNAP_SHOPKEEPER, value);
	}

	@Override
	public int vnap$spireOutfit() {
		return vnap$outfit();
	}

	@Override
	public void vnap$setSpireOutfit(int outfit) {
		vnap$outfit(outfit);
	}
}
