package com.vnap.fab.neoforge.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** NeoForge's methods on BlockState the mod calls: its block's (the build injects this into BlockState). */
public interface IBlockStateExtension {
	default float getEnchantPowerBonus(LevelReader level, BlockPos pos) {
		BlockState self = (BlockState) this;
		return self.getBlock().getEnchantPowerBonus(self, level, pos);
	}
}
