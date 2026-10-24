package com.vnap.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vnap.VillagerLife;
import com.vnap.block.DecorBlock;
import com.vnap.block.LifeBlocks;
import com.vnap.block.decor.DecorBlockEntity;
import com.vnap.block.decor.DecorMechanics;
import com.vnap.block.decor.Machine;
import com.vnap.block.decor.MachineMenu;
import com.vnap.block.decor.MachineWork;
import com.vnap.block.decor.Machines;
import com.vnap.block.decor.Sharpening;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public class WorkshopBlockGameTests {
	private static final String NS = VillagerLife.MOD_ID;

	@GameTest(batch = "workshop_blocks", template = "platform")
	public static void racksAndMachinesWorkOnlyWithTime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DecorBlockEntity rack = place(helper, "meat_rack", 2, 2);
		rack.items.setItem(0, new ItemStack(Items.BEEF));
		for (int second = 0; second < 299; second++) rack.mechanic().tick(level, rack);
		Checks.assertTrue(helper, rack.items.getItem(0).is(Items.BEEF), "meat is still raw after 299 seconds");
		rack.mechanic().tick(level, rack);
		Checks.assertTrue(helper, rack.items.getItem(0).is(Items.COOKED_BEEF), "meat is cured after 300 seconds, was " + rack.items.getItem(0));

		DecorBlockEntity kiln = place(helper, "kiln", 4, 2);
		kiln.items.setItem(0, new ItemStack(Items.CLAY_BALL, 2));
		tick(level, kiln, 400);
		Checks.assertTrue(helper, kiln.items.getItem(2).isEmpty() && kiln.items.getItem(0).getCount() == 2, "a kiln without fuel fires nothing");
		kiln.items.setItem(1, new ItemStack(Items.COAL));
		tick(level, kiln, 299);
		Checks.assertTrue(helper, kiln.items.getItem(2).isEmpty(), "the first brick is not done before 300 ticks");
		Checks.assertTrue(helper, kiln.items.getItem(1).isEmpty(), "the coal was burnt when work began");
		tick(level, kiln, 1);
		Checks.assertTrue(helper, kiln.items.getItem(2).is(Items.BRICK) && kiln.items.getItem(2).getCount() == 1,
				"one brick after 300 ticks, was " + kiln.items.getItem(2));
		tick(level, kiln, 300);
		Checks.assertTrue(helper, kiln.items.getItem(2).getCount() == 2 && kiln.items.getItem(0).isEmpty(),
				"the second brick runs on the same coal");

		DecorBlockEntity wheel = place(helper, "spinning_wheel", 6, 2);
		wheel.items.setItem(0, new ItemStack(Items.WHITE_WOOL));
		tick(level, wheel, 399);
		Checks.assertTrue(helper, wheel.items.getItem(1).isEmpty(), "spinning takes 400 ticks");
		tick(level, wheel, 1);
		Checks.assertTrue(helper, wheel.items.getItem(1).is(Items.STRING) && wheel.items.getItem(1).getCount() == 3, "one wool spins three string");
		helper.succeed();
	}

	@GameTest(batch = "workshop_blocks", template = "platform")
	public static void stocksAndShelvesKeepTheirLimits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		DecorBlockEntity basket = place(helper, "feather_basket", 2, 2);
		Checks.assertTrue(helper, basket.addStock(new ItemStack(Items.FEATHER, 20)) == 16, "a feather basket holds 16");
		Checks.assertTrue(helper, !basket.canStock(new ItemStack(Items.FEATHER)), "a full basket takes no more");

		DecorBlockEntity sacks = place(helper, "seed_sacks", 4, 2);
		Checks.assertTrue(helper, !sacks.canStock(new ItemStack(Items.DIRT)), "seed sacks refuse dirt");
		sacks.addStock(new ItemStack(Items.WHEAT_SEEDS, 64));
		Checks.assertTrue(helper, !sacks.canStock(new ItemStack(Items.BEETROOT_SEEDS)), "seed sacks keep one kind of seed");

		DecorBlockEntity books = place(helper, "book_stack", 6, 2);
		BlockPos booksPos = books.getBlockPos();
		Checks.assertTrue(helper, level.getBlockState(booksPos).getEnchantPowerBonus(level, booksPos) == 0.0F, "an empty stack adds no power");
		for (int i = 0; i < 4; i++) {
			ItemStack book = new ItemStack(Items.BOOK);
			Checks.assertTrue(helper, books.mechanic().handles(books, player, book), "book " + (i + 1) + " fits");
			books.mechanic().use(level, books, player, InteractionHand.MAIN_HAND, book);
		}
		Checks.assertTrue(helper, !books.mechanic().handles(books, player, new ItemStack(Items.BOOK)), "a fifth book does not fit");
		BlockState full = level.getBlockState(booksPos);
		Checks.assertTrue(helper, full.getValue(DecorBlock.FILL) == 4, "four books show four books, fill " + full.getValue(DecorBlock.FILL));
		Checks.assertTrue(helper, full.getEnchantPowerBonus(level, booksPos) == 1.0F, "a stack of books adds one enchanting power");

		DecorBlockEntity tools = place(helper, "tool_rack", 8, 2);
		Checks.assertTrue(helper, !tools.mechanic().handles(tools, player, new ItemStack(Items.IRON_CHESTPLATE)), "a tool rack refuses armor");
		for (int i = 0; i < 4; i++) tools.mechanic().use(level, tools, player, InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		Checks.assertTrue(helper, !tools.mechanic().handles(tools, player, new ItemStack(Items.IRON_PICKAXE)), "a tool rack holds four");

		DecorBlockEntity incense = place(helper, "incense_burner", 10, 2);
		int lit = 0;
		while (incense.mechanic().handles(incense, player, new ItemStack(Items.BLAZE_POWDER)) && lit < 10) {
			incense.mechanic().use(level, incense, player, InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_POWDER));
			lit++;
		}
		Checks.assertTrue(helper, lit == 4, "an incense burner takes four blaze powder, took " + lit);
		Checks.assertTrue(helper, level.getBlockState(incense.getBlockPos()).getValue(DecorBlock.FILL) == 1, "a fuelled burner is lit");

		DecorBlockEntity pallet = place(helper, "brick_pallet", 12, 2);
		pallet.addStock(new ItemStack(Items.BRICK, 64));
		int quarter = level.getBlockState(pallet.getBlockPos()).getValue(DecorBlock.FILL);
		for (int i = 0; i < 4; i++) pallet.addStock(new ItemStack(Items.BRICK, 64));
		Checks.assertTrue(helper, pallet.stock() == 256, "a pallet holds 256 bricks, has " + pallet.stock());
		Checks.assertTrue(helper, quarter == 1 && level.getBlockState(pallet.getBlockPos()).getValue(DecorBlock.FILL) == 4, "the brick stack grows");
		helper.succeed();
	}

	@GameTest(batch = "workshop_blocks", template = "platform")
	public static void machineMenusKeepTheirItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		DecorBlockEntity wheel = place(helper, "spinning_wheel", 2, 2);
		if (!(wheel.createMenu(1, player.getInventory(), player) instanceof MachineMenu menu)) {
			Checks.fail(helper, "a spinning wheel opens a menu");
			return;
		}
		Checks.assertTrue(helper, !menu.slots.get(0).mayPlace(new ItemStack(Items.DIRT)), "the input slot refuses dirt");
		menu.slots.get(0).set(new ItemStack(Items.WHITE_WOOL, 3));
		menu.removed(player);
		Checks.assertTrue(helper, wheel.items.getItem(0).getCount() == 3, "the wool stays in the wheel after closing");
		Checks.assertTrue(helper, wheel.createMenu(2, player.getInventory(), player) instanceof MachineMenu again
				&& again.slots.get(0).getItem().getCount() == 3, "reopening shows the same wool");

		BlockPos table = helper.absolutePos(new BlockPos(4, 1, 2));
		level.setBlock(table, Blocks.FLETCHING_TABLE.defaultBlockState(), 3);
		SimpleContainer container = new SimpleContainer(Machines.FLETCHING.inputs() + 1);
		MachineWork work = new MachineWork();
		MachineMenu fletching = new MachineMenu(3, player.getInventory(), Machines.FLETCHING, container, work,
				ContainerLevelAccess.create(level, table), p -> true, work);
		fletching.slots.get(0).set(new ItemStack(Items.FLINT, 2));
		fletching.slots.get(1).set(new ItemStack(Items.STICK));
		fletching.slots.get(2).set(new ItemStack(Items.FEATHER));
		for (int t = 0; t < 60; t++) fletching.broadcastChanges();
		Checks.assertTrue(helper, container.getItem(3).is(Items.ARROW) && container.getItem(3).getCount() == 6,
				"the fletching table makes six arrows in 60 ticks, made " + container.getItem(3));
		fletching.removed(player);
		Checks.assertTrue(helper, player.getInventory().countItem(Items.ARROW) == 6 && player.getInventory().countItem(Items.FLINT) == 1,
				"closing the fletching table hands everything back");
		helper.succeed();
	}

	@GameTest(batch = "workshop_blocks", template = "platform")
	public static void everyWorkshopBlockHasModelTextureAndName(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		JsonObject en = json("/assets/" + NS + "/lang/en_us.json");
		JsonObject ru = json("/assets/" + NS + "/lang/ru_ru.json");
		List<String> problems = new ArrayList<>();
		Checks.assertTrue(helper, LifeBlocks.DECOR.size() == 26, "26 decor blocks, have " + LifeBlocks.DECOR.size());
		Checks.assertTrue(helper, LifeBlocks.MACHINES.size() == 12, "12 machines, have " + LifeBlocks.MACHINES.size());
		TagKey<Block> machineTag = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(NS, "profession_machines"));
		List<DecorBlock> all = new ArrayList<>(LifeBlocks.DECOR);
		all.addAll(LifeBlocks.MACHINES);
		for (DecorBlock block : all) {
			String id = block.name();
			JsonObject states = json("/assets/" + NS + "/blockstates/" + id + ".json");
			if (states == null) {
				problems.add(id + " has no blockstate");
			} else {
				for (Map.Entry<String, JsonElement> variant : states.getAsJsonObject("variants").entrySet()) {
					checkModel(variant.getValue().getAsJsonObject().get("model").getAsString(), problems, id);
				}
			}
			JsonObject item = json("/assets/" + NS + "/models/item/" + id + ".json");
			if (item == null) problems.add(id + " has no item model");
			else checkModel(item.get("parent").getAsString(), problems, id);
			for (String key : List.of("block." + NS + "." + id, "tooltip." + NS + "." + id)) {
				if (en == null || !en.has(key)) problems.add("en lacks " + key);
				if (ru == null || !ru.has(key)) problems.add("ru lacks " + key);
			}
			if (level.getServer().reloadableRegistries().getLootTable(com.vnap.Mc.lootTable(block)).equals(LootTable.EMPTY)) problems.add(id + " drops nothing");
			if (!com.vnap.Mc.hasRecipe(level, VillagerLife.id(id))) problems.add(id + " has no recipe");
			if (block.isMachine()) {
				if (!block.defaultBlockState().is(machineTag)) problems.add(id + " is not tagged as a machine");
				if (block.mechanic().machine() == null || Machines.get(id) == null) problems.add(id + " has no recipe book");
				if (!soldByMaster(helper, block)) problems.add(id + " is not sold by an expert " + block.profession());
			} else if (block.mechanic().equals(DecorMechanics.NONE)) {
				problems.add(id + " has no mechanic");
			}
		}
		for (Machine machine : Machines.all().values()) {
			if (machine.examples().isEmpty()) problems.add(machine.id() + " shows no recipes in JEI/EMI");
		}
		Checks.assertTrue(helper, problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	@GameTest(batch = "workshop_blocks", template = "platform")
	public static void aFishTrapNeedsWaterAndBait(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DecorBlockEntity trap = place(helper, "fish_trap", 2, 2);
		BlockPos pos = trap.getBlockPos();
		trap.items.setItem(0, new ItemStack(Items.BREAD));
		Checks.assertTrue(helper, Machines.FISH_TRAP.match(level, pos, trap.items) == null, "a dry trap catches nothing");
		level.setBlock(pos, level.getBlockState(pos).setValue(DecorBlock.WATERLOGGED, true), 3);
		Checks.assertTrue(helper, Machines.FISH_TRAP.match(level, pos, trap.items) != null, "a baited trap in water fishes");
		trap.items.setItem(0, new ItemStack(Items.DIRT));
		Checks.assertTrue(helper, Machines.FISH_TRAP.match(level, pos, trap.items) == null, "dirt is no bait");
		trap.items.setItem(0, new ItemStack(Items.BREAD));
		tick(level, trap, 2400);
		Checks.assertTrue(helper, !trap.items.getItem(1).isEmpty() && trap.items.getItem(0).isEmpty(), "two minutes in water bring a fish");
		helper.succeed();
	}

	@GameTest(batch = "workshop_blocks", template = "platform")
	public static void benchesMendAndBarrelsSharpen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		DecorBlockEntity forge = place(helper, "weapon_forge", 2, 2);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.setDamageValue(200);
		forge.items.setItem(0, sword);
		forge.items.setItem(1, new ItemStack(Items.IRON_INGOT));
		tick(level, forge, 600);
		ItemStack mended = forge.items.getItem(2);
		Checks.assertTrue(helper, mended.is(Items.IRON_SWORD) && mended.getDamageValue() == 200 - mended.getMaxDamage() / 4,
				"the forge mends a quarter, damage now " + mended.getDamageValue());
		Checks.assertTrue(helper, Sharpening.charges(mended) == Sharpening.CHARGES, "the forge sharpens for 20 hits");

		DecorBlockEntity bench = place(helper, "armor_bench", 4, 2);
		bench.items.setItem(0, new ItemStack(Items.IRON_CHESTPLATE));
		bench.items.setItem(1, new ItemStack(Items.IRON_INGOT));
		Checks.assertTrue(helper, Machines.ARMOR_BENCH.match(level, bench.getBlockPos(), bench.items) == null, "unworn armor needs no mending");
		bench.items.setItem(1, new ItemStack(Items.LEATHER));
		ItemStack worn = new ItemStack(Items.IRON_CHESTPLATE);
		worn.setDamageValue(50);
		bench.items.setItem(0, worn);
		Checks.assertTrue(helper, Machines.ARMOR_BENCH.match(level, bench.getBlockPos(), bench.items) == null, "iron armor is not mended with leather");

		DecorBlockEntity barrel = place(helper, "sword_barrel", 6, 2);
		ItemStack blade = new ItemStack(Items.STONE_SWORD);
		Checks.assertTrue(helper, !barrel.mechanic().handles(barrel, player, blade), "an empty barrel cannot sharpen");
		barrel.addStock(new ItemStack(Items.FLINT, 2));
		barrel.mechanic().use(level, barrel, player, InteractionHand.MAIN_HAND, blade);
		Checks.assertTrue(helper, Sharpening.charges(blade) == Sharpening.CHARGES && barrel.stock() == 1, "one flint sharpens a sword");
		Checks.assertTrue(helper, !barrel.mechanic().handles(barrel, player, blade), "a sharp sword needs no flint");
		Sharpening.set(blade, 0);
		Checks.assertTrue(helper, Sharpening.charges(blade) == 0 && !blade.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
				"a dull sword carries no data");
		helper.succeed();
	}

	private static boolean soldByMaster(GameTestHelper helper, DecorBlock block) {
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(90, 1, 90));
		boolean sold = com.vnap.Mc.sells(villager, block.profession(), 4, block.asItem());
		villager.discard();
		return sold;
	}

	private static void checkModel(String model, List<String> problems, String owner) {
		ResourceLocation id = ResourceLocation.parse(model);
		if (!id.getNamespace().equals(NS)) return;
		JsonObject json = json("/assets/" + NS + "/models/" + id.getPath() + ".json");
		if (json == null) {
			problems.add(owner + " misses model " + model);
			return;
		}
		if (!json.has("textures")) return;
		for (Map.Entry<String, JsonElement> texture : json.getAsJsonObject("textures").entrySet()) {
			ResourceLocation tex = ResourceLocation.parse(texture.getValue().getAsString());
			// from 1.21.11 the build aliases vanilla item sprites as villager_life:block/item/* in the block atlas
			if (tex.getNamespace().equals(NS) && !tex.getPath().startsWith("block/item/") && WorkshopBlockGameTests.class.getResource("/assets/" + NS + "/textures/" + tex.getPath() + ".png") == null) {
				problems.add(owner + " misses texture " + tex);
			}
		}
	}

	private static @Nullable JsonObject json(String path) {
		try (InputStream stream = WorkshopBlockGameTests.class.getResourceAsStream(path)) {
			return stream == null ? null : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			return null;
		}
	}

	private static DecorBlockEntity place(GameTestHelper helper, String id, int x, int z) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(x, 1, z));
		level.setBlock(pos, BuiltInRegistries.BLOCK.get(VillagerLife.id(id)).defaultBlockState(), 3);
		if (!(level.getBlockEntity(pos) instanceof DecorBlockEntity be)) {
			throw new IllegalStateException(id + " has no block entity");
		}
		return be;
	}

	private static void tick(ServerLevel level, DecorBlockEntity be, int ticks) {
		for (int t = 0; t < ticks; t++) {
			DecorBlockEntity.tick(level, be.getBlockPos(), level.getBlockState(be.getBlockPos()), be);
		}
	}
}
