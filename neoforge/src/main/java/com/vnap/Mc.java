package com.vnap;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
//? if <1.21.2 {
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
//?}
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jspecify.annotations.Nullable;

/**
 * Calls whose meaning or arguments differ between the Minecraft versions the mod builds for (Stonecutter: the
 * {@code //?} comments here). Plain renames are Stonecutter replacements in neoforge/build.gradle instead.
 */
public final class Mc {
    private Mc() {
    }

    /** From 1.21.2 an item knows its id before it is built. */
    public static Item.Properties item(Item.Properties properties, ResourceLocation id) {
        //? if >=1.21.2 {
        /*return properties.setId(ResourceKey.create(Registries.ITEM, id));
        *///?} else
        return properties;
    }

    /** A block's item, named like the block (block.namespace.path). */
    public static Item.Properties blockItem(Item.Properties properties, ResourceLocation id) {
        //? if >=1.21.2 {
        /*return item(properties, id).useBlockDescriptionPrefix();
        *///?} else
        return properties;
    }

    public static BlockBehaviour.Properties block(BlockBehaviour.Properties properties, ResourceLocation id) {
        //? if >=1.21.2 {
        /*return properties.setId(ResourceKey.create(Registries.BLOCK, id));
        *///?} else
        return properties;
    }

    public static <T extends Entity> EntityType<T> build(EntityType.Builder<T> builder, String path) {
        //? if >=1.21.2 {
        /*return builder.build(ResourceKey.create(Registries.ENTITY_TYPE, VillagerLife.id(path)));
        *///?} else
        return builder.build(path);
    }

    /** A player moved to another place, in this level or another, facing yRot. */
    public static void teleport(net.minecraft.server.level.ServerPlayer player, ServerLevel level, double x, double y, double z, float yRot, float xRot) {
        //? if >=1.21.2 {
        /*player.teleportTo(level, x, y, z, java.util.Set.of(), yRot, xRot, true);
        *///?} else
        player.teleportTo(level, x, y, z, yRot, xRot);
    }

    public static <T extends Entity> @Nullable T create(EntityType<T> type, net.minecraft.world.level.Level level) {
        //? if >=1.21.2 {
        /*return type.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        *///?} else
        return type.create(level);
    }

    public static @Nullable ItemEntity drop(Entity entity, ItemStack stack) {
        //? if >=1.21.2 {
        /*return entity.level() instanceof ServerLevel level ? entity.spawnAtLocation(level, stack) : null;
        *///?} else
        return entity.spawnAtLocation(stack);
    }

    // Item.use and Block.useItemOn results: from 1.21.2 both are an InteractionResult, sided successes are SUCCESS,
    // and a held item that changes rides on the success.

    //? if >=1.21.2 {
    /*public static InteractionResult success(ItemStack held) {
    *///?} else
    public static InteractionResultHolder<ItemStack> success(ItemStack held) {
        //? if >=1.21.2 {
        /*return InteractionResult.SUCCESS.heldItemTransformedTo(held);
        *///?} else
        return InteractionResultHolder.success(held);
    }

    //? if >=1.21.2 {
    /*public static InteractionResult sidedSuccess(ItemStack held, boolean client) {
    *///?} else
    public static InteractionResultHolder<ItemStack> sidedSuccess(ItemStack held, boolean client) {
        //? if >=1.21.2 {
        /*return InteractionResult.SUCCESS.heldItemTransformedTo(held);
        *///?} else
        return InteractionResultHolder.sidedSuccess(held, client);
    }

    //? if >=1.21.2 {
    /*public static InteractionResult consume(ItemStack held) {
    *///?} else
    public static InteractionResultHolder<ItemStack> consume(ItemStack held) {
        //? if >=1.21.2 {
        /*return InteractionResult.CONSUME.heldItemTransformedTo(held);
        *///?} else
        return InteractionResultHolder.consume(held);
    }

    //? if >=1.21.2 {
    /*public static InteractionResult fail(ItemStack held) {
    *///?} else
    public static InteractionResultHolder<ItemStack> fail(ItemStack held) {
        //? if >=1.21.2 {
        /*return InteractionResult.FAIL;
        *///?} else
        return InteractionResultHolder.fail(held);
    }

    /** Block.useItemOn: let the block's empty-hand use run. */
    //? if >=1.21.2 {
    /*public static InteractionResult passToBlock() {
    *///?} else
    public static ItemInteractionResult passToBlock() {
        //? if >=1.21.2 {
        /*return InteractionResult.TRY_WITH_EMPTY_HAND;
        *///?} else
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    //? if >=1.21.2 {
    /*public static InteractionResult itemSuccess(boolean client) {
    *///?} else
    public static ItemInteractionResult itemSuccess(boolean client) {
        //? if >=1.21.2 {
        /*return InteractionResult.SUCCESS;
        *///?} else
        return ItemInteractionResult.sidedSuccess(client);
    }

    public static InteractionResult success(boolean client) {
        //? if >=1.21.2 {
        /*return InteractionResult.SUCCESS;
        *///?} else
        return InteractionResult.sidedSuccess(client);
    }

    public static boolean onCooldown(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        //? if >=1.21.2 {
        /*return player.getCooldowns().isOnCooldown(stack);
        *///?} else
        return player.getCooldowns().isOnCooldown(stack.getItem());
    }

    public static void cooldown(net.minecraft.world.entity.player.Player player, ItemStack stack, int ticks) {
        //? if >=1.21.2 {
        /*player.getCooldowns().addCooldown(stack, ticks);
        *///?} else
        player.getCooldowns().addCooldown(stack.getItem(), ticks);
    }

    public static ResourceLocation id(net.minecraft.sounds.SoundEvent sound) {
        //? if >=1.21.2 {
        /*return sound.location();
        *///?} else
        return sound.getLocation();
    }

    /** The server's recipes: from 1.21.2 a client level has none (only what the server sent it). */
    public static net.minecraft.world.item.crafting.RecipeManager recipes(ServerLevel level) {
        //? if >=1.21.2 {
        /*return level.recipeAccess();
        *///?} else
        return level.getRecipeManager();
    }

    /** The client's level, for what a client connected to another server asks (set by VillagerLifeClient). */
    public static java.util.function.Supplier<net.minecraft.world.level.@org.jspecify.annotations.Nullable Level> clientLevel = () -> null;

    /** Fuel in a furnace, ticks. */
    public static int burnTime(ItemStack stack) {
        //? if >=26.3 {
        /*// fuel is an item component from 26.3, its burn time resolved in a furnace's context: a vanilla furnace's
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        // a client on another server has no context to resolve it in: only whether it burns (a fuel slot's test)
        if (server == null) return stack.has(net.minecraft.core.component.DataComponents.COOKING_FUEL) ? 1 : 0;
        var furnace = new net.minecraft.world.level.block.entity.FurnaceBlockEntity(net.minecraft.core.BlockPos.ZERO,
                net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(server.overworld())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, furnace.getBlockState())
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY, furnace)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.ZERO)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.CONTAINER, furnace)
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CONTAINER_PROCESS);
        return net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt.getFromItem(stack,
                net.minecraft.core.component.DataComponents.COOKING_FUEL, net.minecraft.world.item.component.CookingFuel::burnTime,
                new net.minecraft.world.level.storage.loot.LootContext.Builder(params).create(java.util.Optional.empty()), 0);
        *///?} else if >=1.21.2 && fabric {
        /*// Fabric's fuels are vanilla's fuel values, built with the ones Fabric API's registry adds
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.fuelValues().burnDuration(stack);
        var level = clientLevel.get();
        return level == null ? 0 : level.fuelValues().burnDuration(stack);
        *///?} else if >=1.21.2 && forge {
        /*// Forge asks the fuel values (its stack's own burn time is only for Forge's fuels)
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) return server.fuelValues().burnDuration(stack, net.minecraft.world.item.crafting.RecipeType.SMELTING);
        var level = clientLevel.get();
        return level == null ? 0 : level.fuelValues().burnDuration(stack, net.minecraft.world.item.crafting.RecipeType.SMELTING);
        *///?} else if >=1.21.2 {
        /*var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) return stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING, server.fuelValues());
        var level = clientLevel.get();
        return level == null ? 0 : stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING, level.fuelValues());
        *///?} else {
        // Forge's stack has a burn time of its own only for its own fuels (vanilla's are -1): Forge's hook has them all
        //? if forge {
        /*return net.minecraftforge.common.ForgeHooks.getBurnTime(stack, net.minecraft.world.item.crafting.RecipeType.SMELTING);
        *///?} else if fabric {
        /*Integer ticks = net.fabricmc.fabric.api.registry.FuelRegistry.INSTANCE.get(stack.getItem());
        return ticks == null ? 0 : ticks;
        *///?} else
        return stack.getBurnTime(net.minecraft.world.item.crafting.RecipeType.SMELTING);
        //?}
    }

    public static boolean repairs(ItemStack tool, ItemStack material) {
        //? if >=1.21.2 {
        /*return tool.isValidRepairItem(material);
        *///?} else
        return tool.getItem().isValidRepairItem(tool, material);
    }

    /** Runs a task on the server thread later (a TickTask's tick). */
    public static void later(net.minecraft.server.MinecraftServer server, net.minecraft.server.TickTask task) {
        //? if >=1.21.2 {
        /*server.schedule(task);
        *///?} else
        server.tell(task);
    }

    /** The spawn egg's (and from 1.21.2 any spawning item's) spawn reason. */
    //? if >=1.21.2 {
    /*public static final net.minecraft.world.entity.EntitySpawnReason SPAWN_EGG = net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE;
    *///?} else
    public static final net.minecraft.world.entity.MobSpawnType SPAWN_EGG = net.minecraft.world.entity.MobSpawnType.SPAWN_EGG;

    public static void clearCooldown(net.minecraft.world.entity.player.Player player, Item item) {
        //? if >=1.21.2 {
        /*player.getCooldowns().removeCooldown(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
        *///?} else
        player.getCooldowns().removeCooldown(item);
    }

    /** A recipe as the prices see it: what it makes, how many, and the choices for each of its ingredients. */
    public record RecipeParts(Item result, int count, java.util.List<java.util.List<Item>> ingredients) {
    }

    /** Every crafting, smelting and stonecutting recipe of the server, in that shape (the prices: Prices.learn). */
    public static java.util.List<RecipeParts> recipeParts(ServerLevel level) {
        java.util.List<RecipeParts> out = new java.util.ArrayList<>();
        //? if <1.21.2 {
        for (var holder : recipes(level).getRecipes()) {
            var recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty()) continue;
            java.util.List<java.util.List<Item>> parts = new java.util.ArrayList<>();
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) continue;
                java.util.List<Item> choices = new java.util.ArrayList<>();
                for (ItemStack choice : ingredient.getItems()) choices.add(choice.getItem());
                if (!choices.isEmpty()) parts.add(choices);
            }
            if (!parts.isEmpty()) out.add(new RecipeParts(result.getItem(), result.getCount(), parts));
        }
        //?} else {
        /*// from 1.21.2 a recipe tells its ingredients by how they are placed and its result by how it is shown
        net.minecraft.util.context.ContextMap shown = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level);
        for (var holder : recipes(level).getRecipes()) {
            var recipe = holder.value();
            if (recipe.display().isEmpty()) continue;
            ItemStack result = recipe.display().getFirst().result().resolveForFirstStack(shown);
            if (result.isEmpty()) continue;
            java.util.List<java.util.List<Item>> parts = new java.util.ArrayList<>();
            for (var ingredient : recipe.placementInfo().ingredients()) {
                java.util.List<Item> choices = ingredient.display().resolveForStacks(shown).stream().map(ItemStack::getItem).distinct().toList();
                if (!choices.isEmpty()) parts.add(choices);
            }
            if (!parts.isEmpty()) out.add(new RecipeParts(result.getItem(), result.getCount(), parts));
        }
        *///?}
        return out;
    }

    public static boolean hasRecipe(ServerLevel level, ResourceLocation id) {
        //? if >=1.21.2 {
        /*return recipes(level).byKey(ResourceKey.create(Registries.RECIPE, id)).isPresent();
        *///?} else
        return recipes(level).byKey(id).isPresent();
    }

    public static ResourceKey<net.minecraft.world.level.storage.loot.LootTable> lootTable(net.minecraft.world.level.block.Block block) {
        //? if >=1.21.2 {
        /*return block.getLootTable().orElseThrow();
        *///?} else
        return block.getLootTable();
    }

    public static ResourceKey<net.minecraft.world.level.storage.loot.LootTable> lootTable(net.minecraft.world.entity.LivingEntity entity) {
        //? if >=1.21.2 {
        /*return entity.getLootTable().orElseThrow();
        *///?} else
        return entity.getLootTable();
    }

    public static void kill(Entity entity) {
        //? if >=1.21.2 {
        /*if (entity.level() instanceof ServerLevel level) entity.kill(level);
        *///?} else
        entity.kill();
    }

    /** A sound to one player only, where it stands (ServerPlayer.playNotifySound is gone from 1.21.11). */
    public static void playTo(net.minecraft.server.level.ServerPlayer player, net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source,
            float volume, float pitch) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, player.getX(), player.getY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()));
    }

    /** Damage dealt on the server: from 1.21.2 only the server side hurts. */
    public static boolean hurt(Entity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
        //? if >=1.21.2 {
        /*return entity.level() instanceof ServerLevel level && entity.hurtServer(level, source, amount);
        *///?} else
        return entity.hurt(source, amount);
    }

    // Entity.moveTo is snapTo from 1.21.5 (a mob's navigation still has its moveTo)

    public static void snap(Entity entity, double x, double y, double z, float yRot, float xRot) {
        //? if >=1.21.5 {
        /*entity.snapTo(x, y, z, yRot, xRot);
        *///?} else
        entity.moveTo(x, y, z, yRot, xRot);
    }

    public static void snap(Entity entity, double x, double y, double z) {
        //? if >=1.21.5 {
        /*entity.snapTo(x, y, z);
        *///?} else
        entity.moveTo(x, y, z);
    }

    public static void snap(Entity entity, net.minecraft.world.phys.Vec3 pos) {
        //? if >=1.21.5 {
        /*entity.snapTo(pos);
        *///?} else
        entity.moveTo(pos);
    }

    // VillagerData is a record of holders from 1.21.5, and VillagerProfession.NONE and the rest are keys

    public static net.minecraft.world.entity.npc.VillagerProfession profession(net.minecraft.world.entity.npc.VillagerData data) {
        //? if >=1.21.5 {
        /*return data.profession().value();
        *///?} else
        return data.getProfession();
    }

    //? if >=1.21.5 {
    /*public static boolean is(net.minecraft.world.entity.npc.VillagerData data, ResourceKey<net.minecraft.world.entity.npc.VillagerProfession> profession) {
        return data.profession().is(profession);
    }

    public static net.minecraft.world.entity.npc.VillagerData withProfession(net.minecraft.world.entity.npc.VillagerData data,
            ResourceKey<net.minecraft.world.entity.npc.VillagerProfession> profession) {
        return data.withProfession(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(profession));
    }
    *///?} else {
    public static boolean is(net.minecraft.world.entity.npc.VillagerData data, net.minecraft.world.entity.npc.VillagerProfession profession) {
        return data.getProfession().equals(profession);
    }
    //?}

    public static net.minecraft.world.entity.npc.VillagerData withProfession(net.minecraft.world.entity.npc.VillagerData data,
            net.minecraft.world.entity.npc.VillagerProfession profession) {
        //? if >=1.21.5 {
        /*return data.withProfession(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(profession));
        *///?} else
        return data.setProfession(profession);
    }

    //? if >=1.21.11 && neoforge {
    /*private static net.minecraft.core.HolderLookup.Provider registries(@Nullable ServerLevel level) {
        if (level == null) throw new IllegalStateException("the mod's saved data is the server's, read with a level");
        return level.registryAccess();
    }
    *///?} else if >=1.21.5 && <1.21.11 {
    /*private static net.minecraft.core.HolderLookup.Provider registries(net.minecraft.world.level.saveddata.SavedData.Context context) {
        return context.levelOrThrow().registryAccess();
    }
    *///?}

    /** Game master (permission level 2): commands, debug settings. */
    public static boolean op(net.minecraft.commands.CommandSourceStack source) {
        //? if >=1.21.11 {
        /*return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        *///?} else
        return source.hasPermission(2);
    }

    public static boolean op(net.minecraft.world.entity.player.Player player) {
        //? if >=1.21.11 {
        /*return player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        *///?} else
        return player.hasPermissions(2);
    }

    /** A SavedData's save, as 1.21.1 overrides it. */
    @FunctionalInterface
    public interface Save<T> {
        net.minecraft.nbt.CompoundTag save(T data, net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries);
    }

    /** The world's saved data of this name: from 1.21.5 a SavedDataType with a codec, which here is the tag the data writes. */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "NP_NONNULL_PARAM_VIOLATION", justification = "no data fixer is vanilla's own null; Forge's and Fabric's packages call every parameter non-null")
    public static <T extends net.minecraft.world.level.saveddata.SavedData> T data(net.minecraft.world.level.storage.DimensionDataStorage storage,
            String name, java.util.function.Supplier<T> fresh,
            java.util.function.BiFunction<net.minecraft.nbt.CompoundTag, net.minecraft.core.HolderLookup.Provider, T> load, Save<T> save) {
        //? if >=26.1 && (forge || fabric) {
        /*// an identifier from 26.1 (data/villager_life/<name>.dat); vanilla's type has no context: the running server's registries
        return storage.computeIfAbsent(new net.minecraft.world.level.saveddata.SavedDataType<>(ResourceLocation.fromNamespaceAndPath(VillagerLife.MOD_ID, name),
                fresh, net.minecraft.nbt.CompoundTag.CODEC.xmap(
                        tag -> load.apply(tag, registries((net.minecraft.world.level.Level) null)),
                        data -> save.save(data, new net.minecraft.nbt.CompoundTag(), registries((net.minecraft.world.level.Level) null))),
                null));
        *///?} else if >=26.1 {
        /*// a saved data's id is an identifier from 26.1: data/villager_life/<name>.dat
        return storage.computeIfAbsent(new net.minecraft.world.level.saveddata.SavedDataType<>(ResourceLocation.fromNamespaceAndPath(VillagerLife.MOD_ID, name),
                context -> fresh.get(), context -> net.minecraft.nbt.CompoundTag.CODEC.xmap(
                        tag -> load.apply(tag, registries(context)),
                        data -> save.save(data, new net.minecraft.nbt.CompoundTag(), registries(context)))));
        *///?} else if >=1.21.11 && (forge || fabric) {
        /*// vanilla's type has no context: the registries are the running server's
        return storage.computeIfAbsent(new net.minecraft.world.level.saveddata.SavedDataType<>(name, fresh,
                net.minecraft.nbt.CompoundTag.CODEC.xmap(
                        tag -> load.apply(tag, registries((net.minecraft.world.level.Level) null)),
                        data -> save.save(data, new net.minecraft.nbt.CompoundTag(), registries((net.minecraft.world.level.Level) null))),
                null)); // no data fixer: the mod keeps its own formats
        *///?} else if >=1.21.5 {
        /*return storage.computeIfAbsent(new net.minecraft.world.level.saveddata.SavedDataType<>(name, context -> fresh.get(),
                context -> net.minecraft.nbt.CompoundTag.CODEC.xmap(
                        tag -> load.apply(tag, registries(context)),
                        data -> save.save(data, new net.minecraft.nbt.CompoundTag(), registries(context))),
                null)); // no data fixer: the mod keeps its own formats
        *///?} else
        return storage.computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(fresh, load, null), name);
    }

    /** Bone meal at the spot, by nobody (a villager's work): what a dispenser does. */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "NP_NONNULL_PARAM_VIOLATION", justification = "no player is what vanilla's own dispenser passes; Forge's packages call every parameter non-null")
    public static void boneMeal(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos at) {
        net.minecraft.world.item.BoneMealItem.applyBonemeal(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE_MEAL), level, at, null);
    }

    // Entities and block entities save through ValueOutput / ValueInput from 1.21.6; the mod's own fields keep their
    // NBT keys at the top of the entity's tag, as before

    // the whole tag at once: NeoForge's store(CompoundTag) and keySet() keep its keys at the top level as before; Forge
    // and Fabric have neither (vanilla's whole-map store and read are deprecated), so there the tag is one value of its own
    //? if >=1.21.6 && (forge || fabric) {
    /*private static final String WHOLE = "VillagerLife";

    public static void put(net.minecraft.world.level.storage.ValueOutput output, net.minecraft.nbt.CompoundTag tag) {
        output.store(WHOLE, net.minecraft.nbt.CompoundTag.CODEC, tag);
    }

    public static net.minecraft.nbt.CompoundTag tagOf(net.minecraft.world.level.storage.ValueInput input) {
        return input.read(WHOLE, net.minecraft.nbt.CompoundTag.CODEC).orElseGet(net.minecraft.nbt.CompoundTag::new);
    }
    *///?} else if >=1.21.6 {
    /*public static void put(net.minecraft.world.level.storage.ValueOutput output, net.minecraft.nbt.CompoundTag tag) {
        output.store(tag);
    }

    public static net.minecraft.nbt.CompoundTag tagOf(net.minecraft.world.level.storage.ValueInput input) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        for (String key : input.keySet()) input.read(key, net.minecraft.util.ExtraCodecs.NBT).ifPresent(value -> tag.put(key, value));
        return tag;
    }
    *///?}

    public static net.minecraft.core.HolderLookup.Provider registries(net.minecraft.world.level.@Nullable Level level) {
        if (level != null) return level.registryAccess();
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.registryAccess() : net.minecraft.core.RegistryAccess.EMPTY;
    }

    public static void saveItems(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.NonNullList<ItemStack> items, net.minecraft.core.HolderLookup.Provider registries) {
        //? if >=1.21.6 {
        /*net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, registries);
        net.minecraft.world.ContainerHelper.saveAllItems(output, items);
        tag.merge(output.buildResult());
        *///?} else
        net.minecraft.world.ContainerHelper.saveAllItems(tag, items, registries);
    }

    public static void loadItems(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.NonNullList<ItemStack> items, net.minecraft.core.HolderLookup.Provider registries) {
        //? if >=1.21.6 {
        /*net.minecraft.world.ContainerHelper.loadAllItems(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, registries, tag), items);
        *///?} else
        net.minecraft.world.ContainerHelper.loadAllItems(tag, items, registries);
    }

    // NBT of things the old NbtUtils / ItemStack wrote and read: from 1.21.5 their codecs (the same tags)

    public static net.minecraft.nbt.Tag saveStack(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        //? if >=1.21.5 {
        /*return ItemStack.CODEC.encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack).getOrThrow();
        *///?} else
        return stack.save(registries);
    }

    public static java.util.Optional<ItemStack> parseStack(net.minecraft.core.HolderLookup.Provider registries, net.minecraft.nbt.Tag tag) {
        //? if >=1.21.5 {
        /*return ItemStack.CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag).result();
        *///?} else
        return ItemStack.parse(registries, tag);
    }

    public static ItemStack parseStackOrEmpty(net.minecraft.core.HolderLookup.Provider registries, net.minecraft.nbt.CompoundTag tag) {
        //? if >=1.21.5 {
        /*return ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag).result().orElse(ItemStack.EMPTY);
        *///?} else
        return ItemStack.parseOptional(registries, tag);
    }

    public static net.minecraft.nbt.Tag uuidTag(java.util.UUID id) {
        //? if >=1.21.5 {
        /*return net.minecraft.core.UUIDUtil.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, id).getOrThrow();
        *///?} else
        return net.minecraft.nbt.NbtUtils.createUUID(id);
    }

    public static java.util.UUID uuid(net.minecraft.nbt.Tag tag) {
        //? if >=1.21.5 {
        /*return net.minecraft.core.UUIDUtil.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
        *///?} else
        return net.minecraft.nbt.NbtUtils.loadUUID(tag);
    }

    public static net.minecraft.nbt.Tag posTag(net.minecraft.core.BlockPos pos) {
        //? if >=1.21.5 {
        /*return net.minecraft.core.BlockPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, pos).getOrThrow();
        *///?} else
        return net.minecraft.nbt.NbtUtils.writeBlockPos(pos);
    }

    public static java.util.Optional<net.minecraft.core.BlockPos> readPos(net.minecraft.nbt.CompoundTag tag, String key) {
        //? if >=1.21.5 {
        /*return tag.read(key, net.minecraft.core.BlockPos.CODEC);
        *///?} else
        return net.minecraft.nbt.NbtUtils.readBlockPos(tag, key);
    }

    public static String text(net.minecraft.nbt.Tag tag) {
        //? if >=1.21.5 {
        /*return tag.asString().orElse("");
        *///?} else
        return tag.getAsString();
    }

    public static long number(net.minecraft.nbt.Tag tag) {
        //? if >=1.21.5 {
        /*return tag.asLong().orElse(0L);
        *///?} else
        return tag instanceof net.minecraft.nbt.NumericTag number ? number.getAsLong() : 0L;
    }

    /** The entity's whole tag, as Entity.saveAsPassenger wrote it before 1.21.6. */
    public static boolean saveEntity(Entity entity, net.minecraft.nbt.CompoundTag tag) {
        //? if >=1.21.6 {
        /*net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, entity.registryAccess());
        if (!entity.saveAsPassenger(output)) return false;
        tag.merge(output.buildResult());
        return true;
        *///?} else
        return entity.saveAsPassenger(tag);
    }

    // Armour, diggers and swords are items with components from 1.21.5, not classes of their own

    public static boolean isArmor(ItemStack stack) {
        //? if >=1.21.5 {
        /*net.minecraft.world.item.equipment.Equippable worn = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        return worn != null && worn.slot().isArmor() && worn.assetId().isPresent();
        *///?} else
        return stack.getItem() instanceof net.minecraft.world.item.ArmorItem;
    }

    public static boolean isDigger(ItemStack stack) {
        //? if >=1.21.5 {
        /*return stack.has(net.minecraft.core.component.DataComponents.TOOL) && !stack.is(net.minecraft.tags.ItemTags.SWORDS);
        *///?} else
        return stack.getItem() instanceof net.minecraft.world.item.DiggerItem;
    }

    public static boolean isSword(ItemStack stack) {
        //? if >=1.21.5 {
        /*return stack.is(net.minecraft.tags.ItemTags.SWORDS);
        *///?} else
        return stack.getItem() instanceof net.minecraft.world.item.SwordItem;
    }

    public static net.minecraft.world.entity.@Nullable EquipmentSlot armorSlot(ItemStack stack) {
        //? if >=1.21.2 {
        /*net.minecraft.world.item.equipment.Equippable worn = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        return isArmor(stack) && worn != null ? worn.slot() : null;
        *///?} else
        return stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor ? armor.getEquipmentSlot() : null;
    }

    /** A heavy blow breaks the player's raised shield for a while (as an axe does). */
    public static void disableShield(net.minecraft.world.entity.player.Player player) {
        //? if >=1.21.5 {
        /*ItemStack shield = player.getUseItem();
        if (shield.has(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS)) {
            player.getCooldowns().addCooldown(shield, 100);
            player.stopUsingItem();
        }
        *///?} else if >=1.21.2 {
        /*player.disableShield(player.getUseItem());
        *///?} else
        player.disableShield();
    }

    public static net.minecraft.sounds.SoundEvent wolfPant() {
        //? if >=26.1 {
        /*return java.util.Objects.requireNonNull(net.minecraft.sounds.SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.CLASSIC)).adultSounds().pantSound().value();
        *///?} else if >=1.21.5 {
        /*return java.util.Objects.requireNonNull(net.minecraft.sounds.SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.CLASSIC)).pantSound().value();
        *///?} else
        return net.minecraft.sounds.SoundEvents.WOLF_PANT;
    }

    public static net.minecraft.sounds.SoundEvent wolfWhine() {
        //? if >=26.1 {
        /*return java.util.Objects.requireNonNull(net.minecraft.sounds.SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.CLASSIC)).adultSounds().whineSound().value();
        *///?} else if >=1.21.5 {
        /*return java.util.Objects.requireNonNull(net.minecraft.sounds.SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.CLASSIC)).whineSound().value();
        *///?} else
        return net.minecraft.sounds.SoundEvents.WOLF_WHINE;
    }

    // cats and chickens sound by their variant from 26.1 (classic, grown)

    public static net.minecraft.sounds.SoundEvent catPurr() {
        //? if >=26.1 {
        /*return java.util.Objects.requireNonNull(net.minecraft.sounds.SoundEvents.CAT_SOUNDS.get(net.minecraft.world.entity.animal.feline.CatSoundVariants.SoundSet.CLASSIC)).adultSounds().purrSound().value();
        *///?} else
        return net.minecraft.sounds.SoundEvents.CAT_PURR;
    }

    public static net.minecraft.sounds.SoundEvent catPurreow() {
        //? if >=26.1 {
        /*return java.util.Objects.requireNonNull(net.minecraft.sounds.SoundEvents.CAT_SOUNDS.get(net.minecraft.world.entity.animal.feline.CatSoundVariants.SoundSet.CLASSIC)).adultSounds().purreowSound().value();
        *///?} else
        return net.minecraft.sounds.SoundEvents.CAT_PURREOW;
    }

    public static net.minecraft.sounds.SoundEvent chickenHurt() {
        //? if >=26.1 {
        /*return java.util.Objects.requireNonNull(net.minecraft.sounds.SoundEvents.CHICKEN_SOUNDS.get(net.minecraft.world.entity.animal.chicken.ChickenSoundVariants.SoundSet.CLASSIC)).adultSounds().hurtSound().value();
        *///?} else
        return net.minecraft.sounds.SoundEvents.CHICKEN_HURT;
    }

    /**
     * The entity's level as a Level: a ServerPlayer's level() is a ServerLevel from 1.21.6, and the mod's
     * "level() instanceof ServerLevel" for players and entities alike would be a mere null check there.
     */
    public static net.minecraft.world.level.Level levelOf(Entity entity) {
        return entity.level();
    }

    public static ServerLevel serverLevel(net.minecraft.server.level.ServerPlayer player) {
        //? if >=1.21.6 {
        /*return player.level();
        *///?} else
        return player.serverLevel();
    }

    public static java.util.Set<String> keys(net.minecraft.nbt.CompoundTag tag) {
        //? if >=1.21.5 {
        /*return tag.keySet();
        *///?} else
        return tag.getAllKeys();
    }

    public static boolean inWater(Entity entity) {
        //? if >=1.21.5 {
        /*return entity.isInWater();
        *///?} else
        return entity.isInWaterOrBubble();
    }

    public static net.minecraft.core.NonNullList<ItemStack> items(net.minecraft.world.entity.player.Inventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getNonEquipmentItems();
        *///?} else
        return inventory.items;
    }

    /** A chat link: the address, underlined, opening in the browser when clicked (a record from 1.21.5). */
    public static net.minecraft.network.chat.MutableComponent link(String url) {
        //? if >=1.21.5 {
        /*net.minecraft.network.chat.ClickEvent click = new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(url));
        *///?} else
        net.minecraft.network.chat.ClickEvent click = new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL, url);
        return net.minecraft.network.chat.Component.literal(url)
                .withStyle(style -> style.withClickEvent(click).withUnderlined(true).withColor(net.minecraft.ChatFormatting.AQUA));
    }

    public static void saveWithoutId(Entity entity, net.minecraft.nbt.CompoundTag tag) {
        //? if >=1.21.6 {
        /*net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                net.minecraft.util.ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        tag.merge(output.buildResult());
        *///?} else
        entity.saveWithoutId(tag);
    }

    public static void load(Entity entity, net.minecraft.nbt.CompoundTag tag) {
        //? if >=1.21.6 {
        /*entity.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, entity.registryAccess(), tag));
        *///?} else
        entity.load(tag);
    }

    /** The direction of a unit step, null for anything else (1.21.1's Direction.fromDelta). */
    public static @Nullable Direction fromDelta(int x, int y, int z) {
        for (Direction direction : Direction.values()) {
            if (direction.getStepX() == x && direction.getStepY() == y && direction.getStepZ() == z) return direction;
        }
        return null;
    }

    // Renamed between versions: one call here instead of a text replacement over the sources

    /** A registry key's id: ResourceKey.location() until 1.21.10, identifier() from 1.21.11. */
    public static ResourceLocation id(ResourceKey<?> key) {
        //? if >=1.21.11 {
        /*return key.identifier();
        *///?} else
        return key.location();
    }

    public static boolean isDay(net.minecraft.world.level.Level level) {
        //? if >=1.21.5 {
        /*return level.isBrightOutside();
        *///?} else
        return level.isDay();
    }

    public static boolean isNight(net.minecraft.world.level.Level level) {
        //? if >=1.21.5 {
        /*return level.isDarkOutside();
        *///?} else
        return level.isNight();
    }

    // a mob's home area: "restriction" until 1.21.4, "home" from 1.21.5

    public static void restrictTo(net.minecraft.world.entity.PathfinderMob mob, net.minecraft.core.BlockPos centre, int radius) {
        //? if >=1.21.5 {
        /*mob.setHomeTo(centre, radius);
        *///?} else
        mob.restrictTo(centre, radius);
    }

    public static boolean hasRestriction(net.minecraft.world.entity.PathfinderMob mob) {
        //? if >=1.21.5 {
        /*return mob.hasHome();
        *///?} else
        return mob.hasRestriction();
    }

    public static net.minecraft.core.BlockPos restrictCenter(net.minecraft.world.entity.PathfinderMob mob) {
        //? if >=1.21.5 {
        /*return mob.getHomePosition();
        *///?} else
        return mob.getRestrictCenter();
    }

    public static float restrictRadius(net.minecraft.world.entity.PathfinderMob mob) {
        //? if >=1.21.5 {
        /*return mob.getHomeRadius();
        *///?} else
        return mob.getRestrictRadius();
    }

    public static void clearRestriction(net.minecraft.world.entity.PathfinderMob mob) {
        //? if >=1.21.5 {
        /*mob.clearHome();
        *///?} else
        mob.clearRestriction();
    }

    public static net.minecraft.world.entity.npc.VillagerData withLevel(net.minecraft.world.entity.npc.VillagerData data, int level) {
        //? if >=1.21.5 {
        /*return data.withLevel(level);
        *///?} else
        return data.setLevel(level);
    }

    public static int villagerLevel(net.minecraft.world.entity.npc.VillagerData data) {
        //? if >=1.21.5 {
        /*return data.level();
        *///?} else
        return data.getLevel();
    }

    public static boolean production() {
        //? if >=1.21.11 {
        /*return net.neoforged.fml.loading.FMLEnvironment.isProduction();
        *///?} else
        return net.neoforged.fml.loading.FMLEnvironment.production;
    }

    /** The player hosting this single player or LAN world. */
    public static boolean hostOf(net.minecraft.server.level.ServerPlayer player) {
        //? if >=1.21.11 {
        /*return serverLevel(player).getServer().isSingleplayerOwner(player.nameAndId());
        *///?} else
        return serverLevel(player).getServer().isSingleplayerOwner(player.getGameProfile());
    }

    public static String name(net.minecraft.world.entity.player.Player player) {
        //? if >=1.21.11 {
        /*return player.getGameProfile().name();
        *///?} else
        return player.getGameProfile().getName();
    }

    public static net.minecraft.core.BlockPos spawnPos(ServerLevel level) {
        //? if >=1.21.11 {
        /*return level.getRespawnData().pos();
        *///?} else
        return level.getSharedSpawnPos();
    }

    public static boolean mobGriefing(ServerLevel level) {
        //? if >=1.21.11 {
        /*return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
        *///?} else
        return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
    }

    public static boolean daylightCycle(ServerLevel level) {
        //? if >=1.21.11 {
        /*return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME);
        *///?} else
        return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
    }

    /** Sends the entity's motion to the clients this tick. */
    public static void moved(Entity entity) {
        //? if >=1.21.11 {
        /*entity.needsSync = true;
        *///?} else
        entity.hasImpulse = true;
    }

    /** The first number of a stack's custom_model_data (0 when it has none): what picks its model's look. */
    public static int customModelData(net.minecraft.world.item.ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        //? if >=1.21.4 {
        /*return data == null || data.floats().isEmpty() ? 0 : Math.round(data.floats().get(0));
        *///?} else
        return data == null ? 0 : data.value();
    }

    public static void setCustomModelData(net.minecraft.world.item.ItemStack stack, int value) {
        if (value == 0) {
            stack.remove(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            return;
        }
        //? if >=1.21.4 {
        /*stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                new net.minecraft.world.item.component.CustomModelData(java.util.List.of((float) value), java.util.List.of(), java.util.List.of(), java.util.List.of()));
        *///?} else
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(value));
    }

    /** Coloured dust (0xRRGGBB). */
    public static net.minecraft.core.particles.ParticleOptions dust(int colour, float scale) {
        //? if >=1.21.2 {
        /*return new net.minecraft.core.particles.DustParticleOptions(colour, scale);
        *///?} else
        return new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(((colour >> 16) & 255) / 255.0F, ((colour >> 8) & 255) / 255.0F, (colour & 255) / 255.0F), scale);
    }

    public static net.minecraft.core.particles.ParticleOptions flash() {
        //? if >=1.21.11 {
        /*return net.minecraft.core.particles.ColorParticleOption.create(net.minecraft.core.particles.ParticleTypes.FLASH, -1);
        *///?} else
        return net.minecraft.core.particles.ParticleTypes.FLASH;
    }

    public static void setDaylightCycle(ServerLevel level, boolean on) {
        //? if >=1.21.11 {
        /*level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, on, level.getServer());
        *///?} else
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(on, level.getServer());
    }

    /** Whether a villager of the profession offers the item at the level (1 novice .. 5 master). */
    public static boolean sells(net.minecraft.world.entity.npc.Villager villager, String profession, int level, net.minecraft.world.item.Item item) {
        //? if >=26.1 {
        /*// trades are data from 26.1: the level's tag of villager trades, what each one gives
        var trades = villager.level().registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
        var tag = net.minecraft.tags.TagKey.create(Registries.VILLAGER_TRADE, ResourceLocation.withDefaultNamespace(profession + "/level_" + level));
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
        for (var trade : trades.getTagOrEmpty(tag)) {
            var json = net.minecraft.world.item.trading.VillagerTrade.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, trade.value()).result();
            if (json.isPresent() && id.equals(json.get().getAsJsonObject().getAsJsonObject("gives").get("id").getAsString())) return true;
        }
        return false;
        *///?} else {
        var key = ResourceKey.create(Registries.VILLAGER_PROFESSION, ResourceLocation.withDefaultNamespace(profession));
        //? if >=1.21.5 {
        /*var byLevel = net.minecraft.world.entity.npc.VillagerTrades.TRADES.get(key);
        *///?} else
        var byLevel = net.minecraft.world.entity.npc.VillagerTrades.TRADES.get(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.get(key));
        var listings = byLevel == null ? null : byLevel.get(level);
        if (listings == null) return false;
        for (var listing : listings) {
            //? if >=1.21.11 {
            /*var offer = listing.getOffer((ServerLevel) villager.level(), villager, villager.getRandom());
            *///?} else
            var offer = listing.getOffer(villager, villager.getRandom());
            if (offer != null && offer.getResult().is(item)) return true;
        }
        return false;
        //?}
    }

    /** Ticks of the overworld's day (one level time until 1.21.11; from 26.1 the overworld's clock). */
    public static long dayTime(net.minecraft.world.level.Level level) {
        //? if >=26.1 {
        /*return level.getOverworldClockTime();
        *///?} else
        return level.getDayTime();
    }

    public static void setDayTime(ServerLevel level, long time) {
        //? if >=26.1 {
        /*level.getServer().clockManager().setTotalTicks(level.registryAccess().getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD), time);
        *///?} else
        level.setDayTime(time);
    }

    /** The entity's command tags (/tag). */
    public static java.util.Set<String> tags(Entity entity) {
        //? if >=26.1 {
        /*return entity.entityTags();
        *///?} else
        return entity.getTags();
    }

    /** A message to the player: over the hotbar or in chat. */
    public static void say(net.minecraft.world.entity.player.Player player, net.minecraft.network.chat.Component message, boolean overlay) {
        //? if >=26.1 {
        /*if (overlay) player.sendOverlayMessage(message);
        else player.sendSystemMessage(message);
        *///?} else
        player.displayClientMessage(message, overlay);
    }

    /** A chunk's key in long sets and maps (ChunkPos.asLong until 1.21.11, pack from 26.1). */
    public static long chunkKey(int x, int z) {
        //? if >=26.1 {
        /*return net.minecraft.world.level.ChunkPos.pack(x, z);
        *///?} else
        return net.minecraft.world.level.ChunkPos.asLong(x, z);
    }

    /** The colour of a dye (DyeItem.getDyeColor until 1.21.11; from 26.1 the dye's component). */
    public static net.minecraft.world.item.DyeColor dyeColor(ItemStack dye) {
        //? if >=26.1 {
        /*return dye.getOrDefault(net.minecraft.core.component.DataComponents.DYE, net.minecraft.world.item.DyeColor.WHITE);
        *///?} else
        return dye.getItem() instanceof net.minecraft.world.item.DyeItem item ? item.getDyeColor() : net.minecraft.world.item.DyeColor.WHITE;
    }

    // the vanilla block and item of a colour, by name (the lookups by colour are gone from 26.1)

    public static Item dye(net.minecraft.world.item.DyeColor color) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color.getName() + "_dye"));
    }

    public static net.minecraft.world.level.block.Block banner(net.minecraft.world.item.DyeColor color) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(color.getName() + "_banner"));
    }

    public static void clearWeather(ServerLevel level) {
        //? if >=26.1 {
        /*level.getServer().setWeatherParameters(6000, 0, false, false);
        *///?} else
        level.setWeatherParameters(6000, 0, false, false);
    }

    /** What is left of a stack used up (a bucket of lava leaves the bucket). */
    public static ItemStack remainder(ItemStack stack) {
        //? if >=26.1 {
        /*var rest = stack.getCraftingRemainder();
        return rest == null ? ItemStack.EMPTY : rest.create();
        *///?} else if fabric {
        /*// Fabric API's say of the stack (an item of a mod may leave something of its own)
        return stack.getRecipeRemainder();
        *///?} else if >=1.21.2 {
        /*return stack.getCraftingRemainder();
        *///?} else
        return stack.getCraftingRemainingItem();
    }

    @SuppressWarnings("UnusedVariable") // a recipe makes its result without the registries from 26.1
    public static <I extends net.minecraft.world.item.crafting.RecipeInput> ItemStack assemble(net.minecraft.world.item.crafting.Recipe<I> recipe, I input,
            net.minecraft.core.HolderLookup.Provider registries) {
        //? if >=26.1 {
        /*return recipe.assemble(input);
        *///?} else
        return recipe.assemble(input, registries);
    }

    /** The player's right click on the entity. */
    public static InteractionResult interactOn(net.minecraft.world.entity.player.Player player, Entity entity) {
        //? if >=26.1 {
        /*return player.interactOn(entity, net.minecraft.world.InteractionHand.MAIN_HAND, entity.position());
        *///?} else
        return player.interactOn(entity, net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** A copy of a written book, none of a copy's copy. */
    public static net.minecraft.world.item.component.@Nullable WrittenBookContent copyOf(net.minecraft.world.item.component.WrittenBookContent book) {
        //? if >=26.1 {
        /*return book.generation() >= 2 ? null : book.craftCopy();
        *///?} else
        return book.tryCraftCopy();
    }

    public static net.minecraft.core.particles.ParticleOptions itemParticle(ItemStack stack) {
        //? if >=26.1 {
        /*return new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, stack.getItem());
        *///?} else
        return new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, stack);
    }

    /** The arm's swing, seen by everyone around. */
    public static void swing(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.InteractionHand hand) {
        //? if >=26.3 {
        /*entity.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        *///?} else
        entity.swing(hand, true);
    }

    /** Drops the stack in front of the player (what does not fit the inventory). */
    public static void toss(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        //? if >=26.3 {
        /*player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else
        player.drop(stack, false);
    }

    public static void placeBack(net.minecraft.world.entity.player.Inventory inventory, ItemStack stack) {
        //? if >=26.3 {
        /*inventory.placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else
        inventory.placeItemBackInInventory(stack);
    }

    /** A line of a sign's front. */
    public static String signLine(net.minecraft.world.level.block.entity.SignBlockEntity sign, int line) {
        return signText(sign, line).getString();
    }

    /** A line of a sign's front as written (a translatable line stays one, for the client to read in its language). */
    public static net.minecraft.network.chat.Component signText(net.minecraft.world.level.block.entity.SignBlockEntity sign, int line) {
        //? if >=26.3 {
        /*return sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).get(line);
        *///?} else
        return sign.getFrontText().getMessage(line, false);
    }

    /** Writes the lines on the sign's front (and its back): the first lines, the rest as they were. */
    public static void writeSign(net.minecraft.world.level.block.entity.SignBlockEntity sign, boolean bothSides, net.minecraft.network.chat.Component... lines) {
        //? if >=26.3 {
        /*var text = sign.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).asMutable();
        for (int i = 0; i < lines.length && i < 4; i++) text.setLine(i, lines[i]);
        sign.setText(text.asImmutable(), net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
        if (bothSides) sign.setText(text.asImmutable(), net.minecraft.world.level.block.entity.SignTextSlot.BACK);
        *///?} else {
        var text = sign.getFrontText();
        for (int i = 0; i < lines.length && i < 4; i++) text = text.setMessage(i, lines[i]);
        sign.setText(text, true);
        if (bothSides) sign.setText(text, false);
        //?}
    }

    public static void invulnerable(Entity entity, boolean on) {
        //? if >=26.3 {
        /*entity.setPermanentlyInvulnerable(on);
        *///?} else
        entity.setInvulnerable(on);
    }

    /** A knock sent to the clients this tick (the entity's new motion). */
    public static void knocked(Entity entity) {
        //? if >=26.3 {
        /*entity.needsSync = true;
        *///?} else
        entity.hurtMarked = true;
    }

    public static boolean randomTeleport(net.minecraft.world.entity.LivingEntity entity, double x, double y, double z) {
        //? if >=26.3 {
        /*return entity.randomTeleport(x, y, z, true, state -> false);
        *///?} else
        return entity.randomTeleport(x, y, z, true);
    }

    /** What the reagent brews the potion into, or null (brewing is recipes from 26.3). */
    public static @Nullable ItemStack brew(ServerLevel level, ItemStack potion, ItemStack reagent) {
        //? if >=26.3 {
        /*var input = new net.minecraft.world.item.crafting.BrewingInput(potion, reagent);
        return level.recipeAccess().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.BREWING, input, level)
                .map(recipe -> recipe.value().assemble(input)).orElse(null);
        *///?} else
        return level.potionBrewing().hasMix(potion, reagent) ? level.potionBrewing().mix(reagent, potion) : null;
    }

    public static void invulnerableTime(Entity entity, int ticks) {
        //? if >=26.3 {
        /*entity.setInvulnerableTime(ticks);
        *///?} else
        entity.invulnerableTime = ticks;
    }

    public static net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templates(ServerLevel level) {
        //? if >=26.3 {
        /*return level.getServer().getStructureTemplateManager();
        *///?} else
        return level.getStructureManager();
    }

    /** The block registry as a lookup (block states read from NBT). */
    public static net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blocks() {
        //? if >=1.21.2 {
        /*return net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        *///?} else
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
    }

    /** Whether one of a brain's memories (BrainAccessor) holds a value. */
    public static boolean remembers(Object memory) {
        //? if >=26.1 {
        /*return memory instanceof net.minecraft.world.entity.ai.memory.MemorySlot<?> slot && slot.hasValue();
        *///?} else
        return memory instanceof java.util.Optional<?> value && value.isPresent();
    }
}
