package com.vnap.village.town;

import com.vnap.block.LifeBlocks;
import com.vnap.block.clock.TowerClockBlockEntity;
import com.vnap.village.build.BuildJob;
import com.vnap.village.script.SchematicSpec;
import com.vnap.village.workshop.Workshops;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The town's chapel (structure village/town/chapel, a TownPlots building): a nave under a gable roof and a clock
 * tower before it; its ground kept with the town's first square, built with its second. Its tower clock runs while a
 * keeper winds it every day (ClockBehavior), paid from the treasury.
 */
public final class Chapel {
	public static final String PLAN = TownPlots.Kind.CHAPEL.plan;
	/** Where the keeper stands to wind the clock: the tower's ground floor, under the weights on their chains. */
	static final BlockPos WINDER = new BlockPos(5, 1, 3);

	private Chapel() {
	}

	/** The chapel's clocks that stand (built), each face of the tower. */
	public static List<TowerClockBlockEntity> clocks(ServerLevel level, Roads.Network network) {
		List<TowerClockBlockEntity> out = new ArrayList<>();
		TownPlots.Plot plot = TownPlots.plot(network, TownPlots.Kind.CHAPEL);
		if (plot == null) return out;
		for (SchematicSpec.PlacedBlock block : Workshops.schematic(level, PLAN).blocks) {
			if (!block.state().is(LifeBlocks.TOWER_CLOCK)) continue;
			BlockPos at = BuildJob.at(plot.origin, plot.rotation, block.rel());
			if (level.isLoaded(at) && level.getBlockEntity(at) instanceof TowerClockBlockEntity clock) out.add(clock);
		}
		return out;
	}

	/** Where the keeper winds the clock: the weights on the tower's ground floor. */
	public static @Nullable BlockPos winder(Roads.Network network) {
		return TownPlots.at(network, TownPlots.Kind.CHAPEL, WINDER);
	}
}
