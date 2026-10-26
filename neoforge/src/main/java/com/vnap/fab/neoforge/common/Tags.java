package com.vnap.fab.neoforge.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** NeoForge's common tags the mod asks: the same "c" tags Fabric's conventions fill. */
public final class Tags {
	private Tags() {
	}

	public static final class Items {
		public static final TagKey<Item> RAW_MATERIALS = TagKey.create(Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "raw_materials"));
		public static final TagKey<Item> ORES = TagKey.create(Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "ores"));
		public static final TagKey<Item> DYES = TagKey.create(Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "dyes"));

		private Items() {
		}
	}

	//? if >=26.3 {
	/*/^* The tags of a block and of its item at once (vanilla's pair from 26.3). ^/
	public static final class BlockItems {
		public static final net.minecraft.tags.BlockItemTagId ORES = net.minecraft.tags.BlockItemTagId.create("c", "ores");
		public static final net.minecraft.tags.BlockItemTagId GLASS_BLOCKS = net.minecraft.tags.BlockItemTagId.create("c", "glass_blocks");
		public static final net.minecraft.tags.BlockItemTagId GLASS_PANES = net.minecraft.tags.BlockItemTagId.create("c", "glass_panes");

		private BlockItems() {
		}
	}

	*///?}
	public static final class Blocks {
		public static final TagKey<Block> GLASS_BLOCKS = TagKey.create(Registries.BLOCK, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "glass_blocks"));
		public static final TagKey<Block> GLASS_PANES = TagKey.create(Registries.BLOCK, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "glass_panes"));

		private Blocks() {
		}
	}
}
