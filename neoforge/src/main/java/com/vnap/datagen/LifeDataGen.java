package com.vnap.datagen;

import com.vnap.VillagerLife;
import com.vnap.block.AltarCoreBlock;
import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * {@code runData}: the boilerplate around the hand-made models, from the block registry into src/generated/resources
 * (blockstates, block item models, loot, tool and group tags). Models, textures, recipes and lang stay hand-made.
 */
public final class LifeDataGen {
    private static final List<Block> RITES = List.of(LifeBlocks.MONUMENT_BASE, LifeBlocks.MONUMENT_PILLAR, LifeBlocks.MONUMENT_PLAQUE,
            LifeBlocks.FESTIVAL_TOTEM, LifeBlocks.MOURNING_TOTEM, LifeBlocks.ALTAR_BASE, LifeBlocks.ALTAR_PILLAR, LifeBlocks.ALTAR_CORE);

    private LifeDataGen() {
    }

    public static void gather(GatherDataEvent event) {
        PackOutput output = event.getGenerator().getPackOutput();
        ExistingFileHelper files = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();
        event.getGenerator().addProvider(event.includeClient(), new States(output, files));
        event.getGenerator().addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(Loot::new, LootContextParamSets.BLOCK)), lookup));
        BlockTags blockTags = new BlockTags(output, lookup, files);
        event.getGenerator().addProvider(event.includeServer(), blockTags);
        event.getGenerator().addProvider(event.includeServer(), new ItemTags(output, lookup, blockTags, files));
    }

    static List<Block> all() {
        List<Block> blocks = new ArrayList<>(RITES);
        blocks.addAll(LifeBlocks.DECOR);
        blocks.addAll(LifeBlocks.MACHINES);
        blocks.addAll(LifeBlocks.HOME_DECOR);
        return blocks;
    }

    private static TagKey<Block> blockTag(String name) {
        return TagKey.create(Registries.BLOCK, VillagerLife.id(name));
    }

    private static TagKey<Item> itemTag(String name) {
        return TagKey.create(Registries.ITEM, VillagerLife.id(name));
    }

    private static final class States extends BlockStateProvider {
        private final ExistingFileHelper files;

        States(PackOutput output, ExistingFileHelper files) {
            super(output, VillagerLife.MOD_ID, files);
            this.files = files;
        }

        @Override
        protected void registerStatesAndModels() {
            for (Block block : RITES) {
                if (block.equals(LifeBlocks.ALTAR_CORE)) continue;
                single(block);
            }
            getVariantBuilder(LifeBlocks.ALTAR_CORE).forAllStates(state -> {
                int heat = state.getValue(AltarCoreBlock.HEAT);
                return ConfiguredModel.builder().modelFile(model("block/altar_core" + (heat == 0 ? "" : "_heat" + heat))).build();
            });
            for (List<DecorBlock> group : List.of(LifeBlocks.DECOR, LifeBlocks.MACHINES, LifeBlocks.HOME_DECOR)) {
                for (DecorBlock block : group) decor(block);
            }
            // the double bed: one model per half and end, turned by its facing (occupied makes no difference)
            getVariantBuilder(LifeBlocks.DOUBLE_BED).forAllStatesExcept(state -> ConfiguredModel.builder()
                    .modelFile(model("block/double_bed_" + state.getValue(net.minecraft.world.level.block.BedBlock.PART).getSerializedName()
                            + "_" + state.getValue(com.vnap.block.DoubleBedBlock.SIDE).getSerializedName()))
                    .rotationY(((int) state.getValue(net.minecraft.world.level.block.BedBlock.FACING).toYRot() + 180) % 360).build(),
                    net.minecraft.world.level.block.BedBlock.OCCUPIED);
            itemModels().getBuilder("double_bed").parent(model("block/double_bed_item"));
            getVariantBuilder(LifeBlocks.ARROW_TURRET).forAllStates(state -> ConfiguredModel.builder().modelFile(model("block/arrow_turret"))
                    .rotationY(((int) state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot() + 180) % 360).build());
            simpleBlockItem(LifeBlocks.ARROW_TURRET, model("block/arrow_turret"));
            horizontalBlock(LifeBlocks.TOWER_CLOCK, model("block/tower_clock"));
            getVariantBuilder(LifeBlocks.TAVERN_COUNTER).forAllStates(state -> ConfiguredModel.builder().modelFile(model("block/decor/market_counter"))
                    .rotationY(((int) state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot() + 180) % 360).build());
            simpleBlockItem(LifeBlocks.TAVERN_COUNTER, model("block/decor/market_counter"));
            horizontalBlock(LifeBlocks.NOTICE_BOARD, model("block/town/notice_board"));
            getVariantBuilder(LifeBlocks.MAILBOX).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(model(state.getValue(com.vnap.block.town.MailboxBlock.FLAG) ? "block/town/mailbox_flag" : "block/town/mailbox"))
                    .rotationY(((int) state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot() + 180) % 360).build());
            simpleBlockItem(LifeBlocks.MAILBOX, model("block/town/mailbox"));
            simpleBlockItem(LifeBlocks.NOTICE_BOARD, model("block/town/notice_board"));
            simpleBlockItem(LifeBlocks.TOWER_CLOCK, model("block/tower_clock"));
            simpleBlock(LifeBlocks.CHIMNEY_POT, model("block/town/chimney_pot"));
            simpleBlockItem(LifeBlocks.CHIMNEY_POT, model("block/town/chimney_pot"));
            simpleBlock(LifeBlocks.SMALL_CHIMNEY_POT, model("block/town/small_chimney_pot"));
            simpleBlockItem(LifeBlocks.SMALL_CHIMNEY_POT, model("block/town/small_chimney_pot"));
            getVariantBuilder(LifeBlocks.HOUSE_SIGN).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(model(state.getValue(com.vnap.block.town.HouseSignBlock.POST) ? "block/town/house_sign_post" : "block/town/house_sign"))
                    .rotationY(((int) state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot() + 180) % 360).build());
            simpleBlockItem(LifeBlocks.HOUSE_SIGN, model("block/town/house_sign_post"));
            getVariantBuilder(LifeBlocks.PARCEL_BOX).forAllStates(state -> ConfiguredModel.builder().modelFile(model("block/town/parcel_box"))
                    .rotationY(((int) state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot() + 180) % 360).build());
            simpleBlockItem(LifeBlocks.PARCEL_BOX, model("block/town/parcel_box"));
            // the spire's: a portal lit or not, a note on its stand, a floor's chest, a hanging shard of the Core
            getVariantBuilder(com.vnap.spire.SpireBlocks.PORTAL).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(model(state.getValue(com.vnap.spire.SpirePortalBlock.LIT) ? "block/spire/portal" : "block/spire/portal_dark")).build());
            simpleBlock(com.vnap.spire.SpireBlocks.NOTE, model("block/spire/note"));
            getVariantBuilder(com.vnap.spire.SpireBlocks.CHEST).forAllStates(state -> ConfiguredModel.builder()
                    .modelFile(model(state.getValue(com.vnap.spire.SpireChestBlock.OPEN) ? "block/spire/chest_open" : "block/spire/chest")).build());
            simpleBlock(com.vnap.spire.SpireBlocks.SHARD, model("block/spire/shard"));
            simpleBlock(com.vnap.spire.SpireBlocks.RIFT, model("block/spire/rift"));
            simpleBlock(com.vnap.spire.SpireBlocks.BRAZIER, model("block/spire/brazier"));
            simpleBlock(com.vnap.spire.SpireBlocks.RUNE, model("block/spire/rune"));
            simpleBlock(com.vnap.spire.SpireBlocks.CRACKED_WALL, model("block/spire/cracked_wall"));
            simpleBlock(com.vnap.spire.SpireBlocks.AMBER, model("block/spire/amber"));
            simpleBlock(com.vnap.spire.SpireBlocks.ICE_MIRROR, model("block/spire/ice_mirror"));
            simpleBlock(com.vnap.spire.SpireBlocks.LAMP, model("block/spire/lamp"));
            horizontalBlock(com.vnap.spire.SpireBlocks.SCONCE, model("block/spire/sconce"));
            simpleBlockItem(com.vnap.spire.SpireBlocks.SCONCE, model("block/spire/sconce"));
            horizontalBlock(com.vnap.spire.SpireBlocks.TAPESTRY, model("block/spire/tapestry"));
            horizontalBlock(com.vnap.spire.SpireBlocks.CORE_ANVIL, model("block/spire/core_anvil"));
            simpleBlockItem(com.vnap.spire.SpireBlocks.CORE_ANVIL, model("block/spire/core_anvil"));
            // spire gear and the chapter seals: flat items, held like tools; a bow with its three drawn frames, as the
            // vanilla bow; every piece in its honed looks, picked by the custom_model_data SpireGear.look sets
            for (com.vnap.spire.gear.GearTier tier : com.vnap.spire.gear.GearTier.values()) {
                for (com.vnap.spire.gear.GearPiece piece : com.vnap.spire.gear.GearPiece.values()) {
                    net.minecraft.world.item.Item item = com.vnap.spire.gear.SpireGearItems.get(tier, piece);
                    if (piece == com.vnap.spire.gear.GearPiece.BOW) bow(item);
                    else staged(item, piece.melee() ? "item/handheld" : "item/generated");
                }
                itemModels().basicItem(com.vnap.spire.gear.SpireGearItems.seal(tier));
                itemModels().basicItem(com.vnap.spire.Draughts.reagent(tier));
                itemModels().basicItem(com.vnap.spire.Draughts.draught(tier));
            }
        }

        private static final int STAGES = 3;
        private static final net.minecraft.resources.ResourceLocation STAGE = net.minecraft.resources.ResourceLocation.withDefaultNamespace("custom_model_data");

        /** A flat item and its honed looks (<name>_s1..3), the later winning as overrides do. */
        private void staged(net.minecraft.world.item.Item item, String parent) {
            String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
            ModelFile flat = new ModelFile.UncheckedModelFile(net.minecraft.resources.ResourceLocation.withDefaultNamespace(parent));
            var builder = itemModels().getBuilder(name).parent(flat).texture("layer0", VillagerLife.id("item/" + name));
            for (int stage = 1; stage <= STAGES; stage++) {
                String look = name + "_s" + stage;
                ModelFile model = itemModels().getBuilder(look).parent(flat).texture("layer0", VillagerLife.id("item/" + look));
                builder.override().predicate(STAGE, stage).model(model).end();
            }
        }

        private void bow(net.minecraft.world.item.Item item) {
            String name = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
            ModelFile vanilla = new ModelFile.UncheckedModelFile(net.minecraft.resources.ResourceLocation.withDefaultNamespace("item/bow"));
            var builder = itemModels().getBuilder(name).parent(vanilla).texture("layer0", VillagerLife.id("item/" + name));
            float[] pulls = {0.0F, 0.65F, 0.9F};
            for (int stage = 0; stage <= STAGES; stage++) {
                String look = stage == 0 ? name : name + "_s" + stage;
                if (stage > 0) {
                    ModelFile still = itemModels().getBuilder(look).parent(vanilla).texture("layer0", VillagerLife.id("item/" + look));
                    builder.override().predicate(STAGE, stage).model(still).end();
                }
                for (int frame = 0; frame < pulls.length; frame++) {
                    String pulling = look + "_pulling_" + frame;
                    ModelFile model = itemModels().getBuilder(pulling).parent(vanilla).texture("layer0", VillagerLife.id("item/" + pulling));
                    var override = builder.override().predicate(net.minecraft.resources.ResourceLocation.withDefaultNamespace("pulling"), 1.0F);
                    if (pulls[frame] > 0.0F) override.predicate(net.minecraft.resources.ResourceLocation.withDefaultNamespace("pull"), pulls[frame]);
                    if (stage > 0) override.predicate(STAGE, stage);
                    override.model(model).end();
                }
            }
        }

        private void single(Block block) {
            String name = name(block);
            simpleBlock(block, model("block/" + name));
            simpleBlockItem(block, model("block/" + name));
        }

        /** Turned by its facing; {@code _fill1..4} models show how full it is, a {@code _bare} model that it holds things. */
        private void decor(DecorBlock block) {
            String base = "block/decor/" + block.name();
            boolean levels = exists(base + "_fill1");
            // a rack or shelf: fill 1 is its bare frame, the real items on it drawn by DecorRenderer
            boolean bare = exists(base + "_bare");
            boolean fills = levels || bare;
            getVariantBuilder(block).forAllStatesExcept(state -> {
                int fill = fills ? state.getValue(DecorBlock.FILL) : 0;
                Direction facing = state.getValue(DecorBlock.FACING);
                String path = fill == 0 ? base : !bare ? base + "_fill" + fill : fill == 1 ? base + "_bare" : base;
                return ConfiguredModel.builder().modelFile(model(path))
                        .rotationY(((int) facing.toYRot() + 180) % 360).build();
            }, fills ? new net.minecraft.world.level.block.state.properties.Property<?>[]{DecorBlock.WATERLOGGED}
                    : new net.minecraft.world.level.block.state.properties.Property<?>[]{DecorBlock.WATERLOGGED, DecorBlock.FILL});
            simpleBlockItem(block, model(base));
        }

        private boolean exists(String path) {
            return files.exists(VillagerLife.id(path), PackType.CLIENT_RESOURCES, ".json", "models");
        }

        private ModelFile model(String path) {
            return models().getExistingFile(VillagerLife.id(path));
        }

        private static String name(Block block) {
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
        }
    }

    /** The megastructures' blocks that drop themselves (not the gate's field: it is the gate's). */
    private static List<Block> mega() {
        List<Block> out = new ArrayList<>(com.vnap.mega.MegaBlocks.blocks().values());
        out.remove(com.vnap.mega.MegaBlocks.GATE_FIELD);
        return out;
    }

    private static final class Loot extends BlockLootSubProvider {
        Loot(HolderLookup.Provider lookup) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), lookup);
        }

        @Override
        protected void generate() {
            all().forEach(this::dropSelf);
            dropSelf(LifeBlocks.ARROW_TURRET);
            dropSelf(LifeBlocks.TOWER_CLOCK);
            dropSelf(LifeBlocks.TAVERN_COUNTER);
            dropSelf(LifeBlocks.NOTICE_BOARD);
            dropSelf(LifeBlocks.MAILBOX);
            dropSelf(LifeBlocks.CHIMNEY_POT);
            dropSelf(LifeBlocks.SMALL_CHIMNEY_POT);
            dropSelf(LifeBlocks.HOUSE_SIGN);
            dropSelf(LifeBlocks.PARCEL_BOX);
            dropSelf(com.vnap.spire.SpireBlocks.CORE_ANVIL);
            dropSelf(com.vnap.spire.SpireBlocks.SCONCE);
            for (Block block : mega()) dropSelf(block);
            // each half is a bed: its head gives a bed back (the half left standing is a single bed of its own)
            add(LifeBlocks.DOUBLE_BED, block -> net.minecraft.world.level.storage.loot.LootTable.lootTable().withPool(applyExplosionCondition(block,
                    net.minecraft.world.level.storage.loot.LootPool.lootPool().setRolls(net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(1.0F))
                            .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(net.minecraft.world.item.Items.RED_BED)
                                    .when(net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                                            .setProperties(net.minecraft.advancements.critereon.StatePropertiesPredicate.Builder.properties()
                                                    .hasProperty(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD)))))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            List<Block> known = new ArrayList<>(all());
            known.add(LifeBlocks.DOUBLE_BED);
            known.add(LifeBlocks.ARROW_TURRET);
            known.add(LifeBlocks.TOWER_CLOCK);
            known.add(LifeBlocks.TAVERN_COUNTER);
            known.add(LifeBlocks.NOTICE_BOARD);
            known.add(LifeBlocks.MAILBOX);
            known.add(LifeBlocks.CHIMNEY_POT);
            known.add(LifeBlocks.SMALL_CHIMNEY_POT);
            known.add(LifeBlocks.HOUSE_SIGN);
            known.add(LifeBlocks.PARCEL_BOX);
            known.add(com.vnap.spire.SpireBlocks.CORE_ANVIL);
            known.add(com.vnap.spire.SpireBlocks.SCONCE);
            known.addAll(mega());
            return known;
        }
    }

    private static final class BlockTags extends BlockTagsProvider {
        BlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, @Nullable ExistingFileHelper files) {
            super(output, lookup, VillagerLife.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider lookup) {
            for (List<DecorBlock> group : List.of(LifeBlocks.DECOR, LifeBlocks.MACHINES, LifeBlocks.HOME_DECOR)) {
                for (DecorBlock block : group) {
                    // LifeBlocks colours hard decor (stone, metal, glass) as metal: a pickaxe's; wood and wool an axe's
                    boolean soft = !block.defaultBlockState().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
                            .equals(net.minecraft.world.level.material.MapColor.METAL);
                    tag(soft ? net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE : net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE).add(block);
                }
            }
            tag(net.minecraft.tags.BlockTags.BEDS).add(LifeBlocks.DOUBLE_BED);
            tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE).add(LifeBlocks.DOUBLE_BED);
            tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE).add(LifeBlocks.ARROW_TURRET, LifeBlocks.TOWER_CLOCK, LifeBlocks.CHIMNEY_POT, LifeBlocks.SMALL_CHIMNEY_POT);
            tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE).add(LifeBlocks.TAVERN_COUNTER, LifeBlocks.NOTICE_BOARD, LifeBlocks.MAILBOX, LifeBlocks.HOUSE_SIGN);
            tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE).add(com.vnap.spire.SpireBlocks.CORE_ANVIL, com.vnap.spire.SpireBlocks.SCONCE);
            tag(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL).add(com.vnap.spire.SpireBlocks.CORE_ANVIL);
            for (Block block : mega()) {
                tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE).add(block);
                tag(net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL).add(block);
            }
            LifeBlocks.DECOR.forEach(tag(blockTag("profession_decor"))::add);
            LifeBlocks.MACHINES.forEach(tag(blockTag("profession_machines"))::add);
            LifeBlocks.HOME_DECOR.forEach(tag(blockTag("home_decor"))::add);
            RITES.forEach(tag(blockTag("rite_blocks"))::add);
        }
    }

    private static final class ItemTags extends ItemTagsProvider {
        ItemTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, BlockTagsProvider blocks, @Nullable ExistingFileHelper files) {
            super(output, lookup, blocks.contentsGetter(), VillagerLife.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider lookup) {
            for (String name : List.of("profession_decor", "profession_machines", "home_decor")) copy(blockTag(name), itemTag(name));
            // spire gear takes the enchantments of its kind (SpireGear keeps them to its list)
            for (com.vnap.spire.gear.GearTier tier : com.vnap.spire.gear.GearTier.values()) {
                for (com.vnap.spire.gear.GearPiece piece : com.vnap.spire.gear.GearPiece.values()) {
                    Item item = com.vnap.spire.gear.SpireGearItems.get(tier, piece);
                    switch (piece) {
                        case SWORD -> tag(net.minecraft.tags.ItemTags.SWORDS).add(item);
                        case AXE -> tag(net.minecraft.tags.ItemTags.AXES).add(item);
                        case BOW -> {
                            tag(net.minecraft.tags.ItemTags.BOW_ENCHANTABLE).add(item);
                            tag(net.minecraft.tags.ItemTags.DURABILITY_ENCHANTABLE).add(item);
                        }
                        case HELMET -> tag(net.minecraft.tags.ItemTags.HEAD_ARMOR).add(item);
                        case CHESTPLATE -> tag(net.minecraft.tags.ItemTags.CHEST_ARMOR).add(item);
                        case LEGGINGS -> tag(net.minecraft.tags.ItemTags.LEG_ARMOR).add(item);
                        case BOOTS -> tag(net.minecraft.tags.ItemTags.FOOT_ARMOR).add(item);
                    }
                }
            }
        }
    }
}
