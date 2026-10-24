package com.vnap.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A bot player's connection ({@code /vnap bot}) needs a channel: NeoForge keeps a connection's payload setup on it. */
@Mixin(Connection.class)
public interface ConnectionAccessor {
	@Accessor("channel")
	void vnap$setChannel(Channel channel);
}
