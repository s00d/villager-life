package com.vnap.neo.client;

//? if >=1.21.2 {
/*import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/^*
 * A model of the mod's own, baked on Forge (from 1.21.2) as vanilla bakes the item frame's: a state definition with no
 * properties, its blockstate file blockstates/vnap_standalone/<the model's path>.json (written by the build), its one
 * variant the model. Each model has a definition of its own: from 1.21.5 the baked model is found by its block state.
 ^/
public final class Standalone {
	private static final Map<ResourceLocation, StateDefinition<Block, BlockState>> DEFINITIONS = new ConcurrentHashMap<>();

	private Standalone() {
	}

	public static StateDefinition<Block, BlockState> definition(ResourceLocation model) {
		return DEFINITIONS.computeIfAbsent(model,
				name -> new StateDefinition.Builder<Block, BlockState>(Blocks.AIR).create(Block::defaultBlockState, BlockState::new));
	}

	public static ResourceLocation state(ResourceLocation model) {
		return ResourceLocation.fromNamespaceAndPath(model.getNamespace(), "vnap_standalone/" + model.getPath());
	}

	public static BlockState blockState(ResourceLocation model) {
		return definition(model).any();
	}
}
*///?} else {
/** Forge from 1.21.2 only (before, a model's location names it). */
public final class Standalone {
	private Standalone() {
	}
}
//?}
