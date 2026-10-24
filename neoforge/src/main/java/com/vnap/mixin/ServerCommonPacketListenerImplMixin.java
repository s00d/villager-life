package com.vnap.mixin;

import com.vnap.command.Bots;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nothing is sent to a bot player ({@code /vnap bot}): NeoForge refuses a mod's payload to a connection that never
 * negotiated its channels, and threw from the login event.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
@SuppressWarnings("unused")
public abstract class ServerCommonPacketListenerImplMixin {
	//? if >=1.21.6 {
	/*@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
	private void vnap$nothingToABot(Packet<?> packet, io.netty.channel.@Nullable ChannelFutureListener listener, CallbackInfo ci) {
	*///?} else {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
	private void vnap$nothingToABot(Packet<?> packet, @Nullable PacketSendListener listener, CallbackInfo ci) {
	//?}
		if ((Object) this instanceof ServerCommonPacketListenerImpl self && Bots.isBot(self.getConnection())) ci.cancel();
	}
}
