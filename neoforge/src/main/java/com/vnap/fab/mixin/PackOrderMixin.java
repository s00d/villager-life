package com.vnap.fab.mixin;

//? if >=26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * The mod's data over vanilla's feature packs, as on NeoForge: Fabric puts the mods' packs right over vanilla, and the
 * villager trade rebalance's tags (which replace) then wipe out the trades the mod adds to them.
 ^/
@SuppressWarnings("unused")
@Mixin(PackRepository.class)
public abstract class PackOrderMixin {
	@ModifyReturnValue(method = "rebuildSelected", at = @At("RETURN"))
	private List<Pack> vnap$order(List<Pack> selected) {
		int own = -1;
		int lastFeature = -1;
		for (int i = 0; i < selected.size(); i++) {
			if (selected.get(i).getId().equals(com.vnap.VillagerLife.MOD_ID)) own = i;
			if (PackSource.FEATURE.equals(selected.get(i).getPackSource())) lastFeature = i;
		}
		if (own < 0 || lastFeature < own) return selected;
		List<Pack> ordered = new ArrayList<>(selected);
		ordered.add(lastFeature, ordered.remove(own));
		return ordered;
	}
}
*///?} else {
/** Villager trades are data from 26.1 only (the build leaves this mixin out before). */
public final class PackOrderMixin {
	private PackOrderMixin() {
	}
}
//?}
