package com.vnap.village.script;

import com.vnap.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SchematicSpec {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchematicSpec.class);
    private static final String NAMESPACE = "villager_life";
    public static final String STRUCTURES = "structure/village/";

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    public final List<BlockPos> workerSlots;
    public final List<PlacedBlock> blocks;
    public final BlockPos focal;
    /** The focal point came from a marker in the file (not the plan's middle). */
    public final boolean focalMarked;

    public record PlacedBlock(BlockPos rel, BlockState state) {
    }

    public SchematicSpec(int sizeX, int sizeY, int sizeZ, List<BlockPos> workerSlots,
                         List<PlacedBlock> blocks, BlockPos focal) {
        this(sizeX, sizeY, sizeZ, workerSlots, blocks, focal, false);
    }

    public SchematicSpec(int sizeX, int sizeY, int sizeZ, List<BlockPos> workerSlots,
                         List<PlacedBlock> blocks, BlockPos focal, boolean focalMarked) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.workerSlots = workerSlots;
        this.blocks = blocks;
        this.focal = focal;
        this.focalMarked = focalMarked;
    }

    /** Whether the mod has this structure (asking load would log a missing one as a warning). */
    public static boolean exists(ResourceManager manager, String name) {
        return manager.getResource(ResourceLocation.fromNamespaceAndPath(NAMESPACE, STRUCTURES + name + ".nbt")).isPresent();
    }

    /**
     * A vanilla structure file ({@code data/<ns>/structure/village/<name>.nbt}, what a structure block saves):
     * air is "nothing here", a barrier is "clear this cell" (an extension's doorway), and data-mode structure
     * blocks are markers: {@code worker} where a rite worker stands, {@code focal dx dy dz} the plan's centre at
     * that offset from the marker (the centre itself is usually a block).
     */
    public static SchematicSpec load(ResourceManager manager, String name) {
        Optional<Resource> structure = manager.getResource(ResourceLocation.fromNamespaceAndPath(NAMESPACE, STRUCTURES + name + ".nbt"));
        if (structure.isEmpty()) {
            LOGGER.warn("Missing structure {}", name);
            return empty();
        }
        try (var stream = structure.get().open()) {
            return parse(NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap()));
        } catch (Exception exception) {
            LOGGER.warn("Failed to load structure {}", name, exception);
            return empty();
        }
    }

    /** Reads a schematic shipped in the mod jar; recipe viewers and the handbook use it without a server. */
    public static SchematicSpec loadBundled(String name) {
        try (var structure = SchematicSpec.class.getResourceAsStream("/data/" + NAMESPACE + "/" + STRUCTURES + name + ".nbt")) {
            if (structure == null) {
                LOGGER.warn("Missing bundled structure {}", name);
                return empty();
            }
            return parse(NbtIo.readCompressed(structure, NbtAccounter.unlimitedHeap()));
        } catch (Exception exception) {
            LOGGER.warn("Failed to load bundled structure {}", name, exception);
            return empty();
        }
    }

    /** A structure file saved by an older game, its blocks renamed since (minecraft:chain is iron_chain from 1.21.11) by the game's fixers. */
    public static CompoundTag upToDate(CompoundTag root) {
        return net.minecraft.util.datafix.DataFixTypes.STRUCTURE.updateToCurrentVersion(net.minecraft.util.datafix.DataFixers.getDataFixer(), root,
                net.minecraft.nbt.NbtUtils.getDataVersion(root, 500));
    }

    public static SchematicSpec parse(CompoundTag file) {
        CompoundTag root = upToDate(file);
        ListTag size = Nbt.getList(root, "size", Tag.TAG_INT);
        int sx = Nbt.getInt(size, 0);
        int sy = Nbt.getInt(size, 1);
        int sz = Nbt.getInt(size, 2);
        ListTag paletteTag = Nbt.getList(root, "palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) {
            CompoundTag entry = Nbt.getCompound(paletteTag, i);
            // the game's own reader: a block state's keys are Name/Properties until 26.2, id/properties from 26.3
            BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(com.vnap.Mc.blocks(), entry);
            if (state.isAir() && !entry.toString().contains(":air")) LOGGER.warn("Unknown structure block {}", entry);
            palette.add(state);
        }
        List<BlockPos> slots = new ArrayList<>();
        List<PlacedBlock> blocks = new ArrayList<>();
        BlockPos focal = new BlockPos(sx / 2, Math.max(1, sy / 2), sz / 2);
        boolean marked = false;
        ListTag list = Nbt.getList(root, "blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = Nbt.getCompound(list, i);
            ListTag p = Nbt.getList(entry, "pos", Tag.TAG_INT);
            BlockPos rel = new BlockPos(Nbt.getInt(p, 0), Nbt.getInt(p, 1), Nbt.getInt(p, 2));
            BlockState state = palette.get(Nbt.getInt(entry, "state"));
            if (state.is(Blocks.STRUCTURE_BLOCK)) {
                List<String> marker = com.google.common.base.Splitter.on(com.google.common.base.CharMatcher.whitespace())
                        .omitEmptyStrings().splitToList(Nbt.getString(Nbt.getCompound(entry, "nbt"), "metadata"));
                String kind = marker.isEmpty() ? "" : marker.get(0);
                if (kind.equals("worker")) {
                    slots.add(rel);
                } else if (kind.equals("focal")) {
                    marked = true;
                    focal = marker.size() == 4
                            ? rel.offset(Integer.parseInt(marker.get(1)), Integer.parseInt(marker.get(2)), Integer.parseInt(marker.get(3))) : rel;
                } else {
                    LOGGER.warn("Unknown structure marker {}", Nbt.getString(Nbt.getCompound(entry, "nbt"), "metadata"));
                }
                continue;
            }
            if (state.isAir()) continue;
            blocks.add(new PlacedBlock(rel, state.is(Blocks.BARRIER) ? Blocks.AIR.defaultBlockState() : state));
        }
        sortForBuilding(blocks);
        return new SchematicSpec(sx, sy, sz, slots, blocks, focal, marked);
    }

    /** Bottom-up, except hanging blocks: they go last and top-down so their support already stands. */
    private static void sortForBuilding(List<PlacedBlock> blocks) {
        blocks.sort((a, b) -> {
            boolean ha = hanging(a.state());
            boolean hb = hanging(b.state());
            if (ha != hb) return ha ? 1 : -1;
            if (ha) return Integer.compare(b.rel().getY(), a.rel().getY());
            int cy = Integer.compare(a.rel().getY(), b.rel().getY());
            if (cy != 0) return cy;
            int cx = Integer.compare(a.rel().getX(), b.rel().getX());
            if (cx != 0) return cx;
            return Integer.compare(a.rel().getZ(), b.rel().getZ());
        });
    }

    private static boolean hanging(BlockState state) {
        return (state.hasProperty(BlockStateProperties.HANGING) && state.getValue(BlockStateProperties.HANGING))
                || (state.hasProperty(BlockStateProperties.BELL_ATTACHMENT)
                && state.getValue(BlockStateProperties.BELL_ATTACHMENT) == BellAttachType.CEILING);
    }

    private static SchematicSpec empty() {
        return new SchematicSpec(1, 1, 1, List.of(BlockPos.ZERO), List.of(), BlockPos.ZERO);
    }
}
