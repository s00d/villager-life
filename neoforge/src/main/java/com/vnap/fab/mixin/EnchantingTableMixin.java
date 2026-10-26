package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's enchanting power of a block (IBlockExtension.getEnchantPowerBonus) beside vanilla's tag of bookshelves. */
@SuppressWarnings("unused")
@Mixin(EnchantingTableBlock.class)
public abstract class EnchantingTableMixin {
	@ModifyReturnValue(method = "isValidBookShelf", at = @At("RETURN"))
	private static boolean vnap$shelf(boolean vanilla, Level level, BlockPos table, BlockPos offset) {
		if (vanilla) return true;
		BlockPos shelf = table.offset(offset);
		return level.getBlockState(shelf).getEnchantPowerBonus(level, shelf) > 0
				&& level.getBlockState(table.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2)).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
	}
}
