package com.vnap.fab.neoforge.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** NeoForge's methods on Block the mod overrides (the build injects this into Block; com.vnap.fab.mixin.EnchantingTableMixin asks it). */
public interface IBlockExtension {
	default float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
		return state.is(BlockTags.ENCHANTMENT_POWER_PROVIDER) ? 1.0F : 0.0F;
	}
}
