package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.AddReloadListenerEvent;
import com.vnap.fab.neoforge.event.AddServerReloadListenersEvent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's events that add the mod's listeners to the server's data reload, after vanilla's. */
@SuppressWarnings("unused")
@Mixin(ReloadableServerResources.class)
public abstract class ServerResourcesMixin {
	@ModifyReturnValue(method = "listeners", at = @At("RETURN"))
	private List<PreparableReloadListener> vnap$listeners(List<PreparableReloadListener> vanilla) {
		List<PreparableReloadListener> all = new ArrayList<>(vanilla);
		Bridge.fire(new AddReloadListenerEvent(all));
		Bridge.fire(new AddServerReloadListenersEvent(all));
		return all;
	}
}
