package com.vnap.spire.outer;

import com.vnap.Mc;
import com.vnap.VillagerLife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/** The Outer Gates' blocks: their portals and the Sky Path's crumbling stone. None is the player's to break. */
public final class OuterBlocks {
	private static final Map<ResourceLocation, Block> BLOCKS = new LinkedHashMap<>();

	public static final OuterGateBlock GATE = add("outer_gate", OuterGateBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
			.strength(-1.0F, 3_600_000.0F).noCollission().noOcclusion().noLootTable().sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK)
			.lightLevel(state -> 12));
	/** A Sky Path island's stone that cracks under a foot and falls a second later (it grows back for who comes after). */
	public static final CrumbleBlock CRUMBLE = add("outer_crumble", CrumbleBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
			.strength(-1.0F, 3_600_000.0F).noLootTable().sound(SoundType.TUFF).pushReaction(PushReaction.BLOCK));

	private OuterBlocks() {
	}

	private static <B extends Block> B add(String path, java.util.function.Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceLocation id = VillagerLife.id(path);
		B block = factory.apply(Mc.block(properties, id));
		BLOCKS.put(id, block);
		return block;
	}

	public static void registerBlocks(RegisterEvent.RegisterHelper<Block> helper) {
		BLOCKS.forEach(helper::register);
	}
}
