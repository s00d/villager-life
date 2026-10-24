package com.vnap.gametest;

import com.vnap.block.LifeBlocks;
import com.vnap.dialogue.Family;
import com.vnap.mood.VillageMoodSavedData;
import com.vnap.village.build.BuildJob;
import com.vnap.village.home.HomeProjects;
import com.vnap.village.home.HouseProject;
import com.vnap.village.home.Mayors;
import com.vnap.village.home.Placements;
import com.vnap.village.market.Coins;
import com.vnap.village.town.Roads;
import com.vnap.village.town.TownFlag;
import com.vnap.village.town.TownRules;
import com.vnap.village.town.Travellers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The town's look and its guests: the flag drawn at the board and hung by hand, travellers at the tavern, a dressed house. */
@GameTestHolder("villager_life")
@PrefixGameTestTemplate(false)
public final class TownLookGameTests {
	private static final BlockPos BELL = new BlockPos(50, 2, 50);

	private TownLookGameTests() {
	}

	private static Roads.Network town(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BELL, Blocks.BELL);
		BlockPos bell = helper.absolutePos(BELL);
		close(level, bell);
		Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), bell);
		return Roads.get(level.getServer()).of(level.dimension(), office.bell);
	}

	private static void close(ServerLevel level, BlockPos bell) {
		Mayors.get(level.getServer()).offices().removeIf(office -> office.bell.closerThan(bell, TownRules.SPAN));
		Roads.get(level.getServer()).networks().removeIf(network -> network.bell.closerThan(bell, TownRules.SPAN));
		VillageMoodSavedData.get(level).houses().removeIf(house -> house.bell.closerThan(bell, TownRules.SPAN));
		Placements.get(level.getServer()).jobs().removeIf(job -> job.pos().closerThan(bell, TownRules.SPAN));
	}

	@GameTest(batch = "look", template = "home_platform", timeoutTicks = 200)
	public static void aFlagDrawnAtTheBoardIsHungByHandAndRepainted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			int code = TownFlag.code(DyeColor.BLUE, new int[]{5, 10, 0}, new DyeColor[]{DyeColor.WHITE, DyeColor.YELLOW, DyeColor.RED});
			Checks.assertTrue(helper, TownFlag.base(code) == DyeColor.BLUE && TownFlag.pattern(code, 0) == 5 && TownFlag.colour(code, 1) == DyeColor.YELLOW
					&& TownFlag.pattern(code, 2) == 0, "the flag's code holds what was drawn");
			BlockPos board = helper.absolutePos(BELL).offset(4, 0, 0);
			level.setBlockAndUpdate(board.below(), Blocks.STONE.defaultBlockState());
			level.setBlockAndUpdate(board, LifeBlocks.NOTICE_BOARD.defaultBlockState());
			network.features.add(board);
			TownFlag.raise(level, network, code);
			Placements.Job job = Placements.get(level.getServer()).jobs().stream().filter(one -> one.pos().closerThan(board, 2)).findFirst().orElse(null);
			Checks.assertTrue(helper, job != null && job.state().getBlock() instanceof BannerBlock, "a banner by the board, to be set by hand");
			if (job == null) return;
			Placements.place(level, job);
			Checks.assertTrue(helper, level.getBlockState(job.pos()).is(Blocks.BLUE_BANNER), "blue, as drawn: " + level.getBlockState(job.pos()));
			Checks.assertTrue(helper, level.getBlockEntity(job.pos()) instanceof BannerBlockEntity banner && banner.getPatterns().layers().size() == 2, "its two patterns");
			int next = TownFlag.code(DyeColor.RED, new int[]{9, 0, 0}, new DyeColor[]{DyeColor.BLACK, DyeColor.RED, DyeColor.RED});
			TownFlag.raise(level, network, next);
			Checks.assertTrue(helper, level.getBlockState(job.pos()).is(Blocks.RED_BANNER), "a new flag repaints the banner up");
			Checks.assertTrue(helper, level.getBlockEntity(job.pos()) instanceof BannerBlockEntity banner && banner.getPatterns().layers().size() == 1, "with its one pattern");
			ItemStack picture = TownFlag.item(level.registryAccess(), next);
			Checks.assertTrue(helper, picture.is(Items.RED_BANNER) && picture.has(DataComponents.BANNER_PATTERNS), "the board's picture of it");
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}

	@GameTest(batch = "look", template = "home_platform", timeoutTicks = 200)
	public static void aTravellerStaysForWhatItAsksAndIsNobodysTillThen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			Villager traveller = Travellers.spawn(level, helper.absolutePos(BELL.offset(3, 0, 3)), Family.day(level), net.minecraft.util.RandomSource.create(7));
			Checks.assertTrue(helper, traveller != null && Travellers.passing(traveller), "a traveller in from the road");
			if (traveller == null) return;
			traveller.setNoAi(true);
			Checks.assertFalse(helper, traveller.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT), "nobody's yet");
			Player player = helper.makeMockPlayer(GameType.SURVIVAL);
			ItemStack wrong = new ItemStack(Items.DIRT);
			Family.give(level, player, traveller, wrong);
			Checks.assertTrue(helper, Travellers.passing(traveller) && wrong.getCount() == 1, "dirt does not do it");
			ItemStack asked = Travellers.asks(traveller);
			Checks.assertTrue(helper, !asked.isEmpty() && Travellers.price(traveller) >= TownRules.TRAVELLER_LEAST_PRICE, "it asks for something, or coins");
			Coins.give(player, Travellers.price(traveller));
			ItemStack coins = com.vnap.Mc.items(player.getInventory()).stream().filter(Coins::isCoin).findFirst().orElse(ItemStack.EMPTY);
			Family.give(level, player, traveller, coins);
			Checks.assertFalse(helper, Travellers.passing(traveller), "talked into staying");
			Checks.assertTrue(helper, Coins.count(player.getInventory()) == 0, "the price paid");
			Checks.assertTrue(helper, traveller.getBrain().getMemory(MemoryModuleType.MEETING_POINT).map(GlobalPos::pos).filter(network.bell::equals).isPresent(), "the town's now");
			Mayors.Office office = Mayors.get(level.getServer()).officeFor(level.dimension(), network.bell);
			Checks.assertTrue(helper, office.residents.containsKey(traveller.getUUID()), "one of its people");
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}

	@GameTest(batch = "look", template = "home_platform", timeoutTicks = 200)
	public static void aFinishedHouseIsDressedWithShuttersAndALanternByHand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Roads.Network network = town(helper);
		try {
			Villager villager = helper.spawn(EntityType.VILLAGER, BELL.offset(2, 0, 2));
			villager.setNoAi(true);
			villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, GlobalPos.of(level.dimension(), network.bell));
			HouseProject house = HomeProjects.start(level, villager, Family.day(level));
			Checks.assertTrue(helper, house != null, "a house");
			if (house == null) return;
			HomeProjects.buildNow(level, house);
			Roads.Segment dressing = network.segments.stream().filter(segment -> segment.label.equals("dressing")).findFirst().orElse(null);
			Checks.assertTrue(helper, dressing != null, "its dressing is to be done by hand");
			if (dressing == null) return;
			List<net.minecraft.world.level.block.state.BlockState> states = dressing.spec.blocks.stream().map(block -> block.state()).toList();
			Checks.assertTrue(helper, states.stream().anyMatch(state -> state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN)), "shutters by its windows");
			BuildJob job = dressing.job();
			job.site = null;
			while (!job.done()) {
				job.skipDone(level);
				if (!job.done()) job.placeNext(level, null);
			}
			// every shutter and the door's lantern stand where the stretch put them
			for (var block : dressing.spec.blocks) {
				BlockPos pos = dressing.origin.offset(block.rel());
				if (block.state().getBlock() instanceof TrapDoorBlock || block.state().is(Blocks.LANTERN)) {
					Checks.assertTrue(helper, level.getBlockState(pos).is(block.state().getBlock()), "set by hand: " + block.state() + " at " + pos);
				}
			}
			helper.succeed();
		} finally {
			close(level, network.bell);
		}
	}
}
