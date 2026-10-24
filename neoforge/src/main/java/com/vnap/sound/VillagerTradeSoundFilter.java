package com.vnap.sound;

import com.vnap.VillagerLife;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

import java.util.Set;

/**
 * The merchants' "hmm"/"huh" at a trade and the celebration grunt are dropped on the server: a voiced line answers a
 * trade instead (Trades), and the two over each other sounded like a crowd.
 */
@EventBusSubscriber(modid = VillagerLife.MOD_ID)
public final class VillagerTradeSoundFilter {
	private static final Set<String> GRUNTS = Set.of("entity.villager.yes", "entity.villager.no", "entity.villager.celebrate",
			"entity.wandering_trader.yes", "entity.wandering_trader.no");

	private VillagerTradeSoundFilter() {
	}

	@SubscribeEvent
	public static void onSound(PlayLevelSoundEvent event) {
		if (event.getSound() == null) return;
		ResourceLocation id = com.vnap.Mc.id(event.getSound().value());
		if (id.getNamespace().equals("minecraft") && GRUNTS.contains(id.getPath())) event.setCanceled(true);
	}
}
