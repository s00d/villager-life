package com.vnap.command;

import com.mojang.authlib.GameProfile;
import com.vnap.mixin.ConnectionAccessor;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jspecify.annotations.Nullable;

/**
 * {@code /vnap bot <name> <pos>}: a player with no client behind it, in the player list like a real one (Carpet's fake
 * players), so a dedicated server with nobody on it lives as it would round a player: the town is held only near a
 * player who is no spectator, and mobs spawn and sieges come only near a player. Creative: nothing kills it.
 * Everything sent to it is dropped (ServerCommonPacketListenerImplMixin), and since no client answers a teleport, the
 * chunk map is told where it is once a second.
 */
public final class Bots {
    private Bots() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (event.getServer().getTickCount() % 20 != 0) return;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (!isBot(player)) continue;
                ServerLevel level = com.vnap.Mc.serverLevel(player);
                // no client moves it: it lands as a creative player who is not flying would (a spectator stays up)
                BlockPos at = player.blockPosition();
                if (!player.isSpectator() && level.isLoaded(at)) {
                    int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
                    if (player.getY() > ground) player.teleportTo(player.getX(), ground, player.getZ());
                }
                level.getChunkSource().move(player);
            }
        });
    }

    static boolean isBot(ServerPlayer player) {
        return isBot(player.connection.getConnection());
    }

    public static boolean isBot(Connection connection) {
        return connection instanceof BotConnection;
    }

    static int place(CommandSourceStack source, String name, Vec3 pos) {
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        ServerPlayer bot = server.getPlayerList().getPlayerByName(name);
        if (bot == null) {
            GameProfile profile = new GameProfile(UUIDUtil.createOfflinePlayerUUID(name), name);
            bot = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
            BotConnection connection = new BotConnection();
            server.getPlayerList().placeNewPlayer(connection, bot, new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE));
            connection.player = bot;
        } else if (!isBot(bot)) {
            source.sendFailure(Component.literal(name + " is a real player"));
            return 0;
        }
        bot.setGameMode(GameType.CREATIVE);
        //? if >=1.21.2 {
        /*bot.teleportTo(level, pos.x, pos.y, pos.z, java.util.Set.of(), bot.getYRot(), bot.getXRot(), true);
        *///?} else
        bot.teleportTo(level, pos.x, pos.y, pos.z, bot.getYRot(), bot.getXRot());
        level.getChunkSource().move(bot);
        String where = name + " at " + (int) pos.x + " " + (int) pos.y + " " + (int) pos.z;
        source.sendSuccess(() -> Component.literal("bot " + where), true);
        return 1;
    }

    static int remove(CommandSourceStack source, String name) {
        ServerPlayer bot = source.getServer().getPlayerList().getPlayerByName(name);
        if (bot == null || !isBot(bot)) {
            source.sendFailure(Component.literal("no bot " + name));
            return 0;
        }
        bot.connection.disconnect(Component.literal("bot removed"));
        source.sendSuccess(() -> Component.literal("bot " + name + " removed"), true);
        return 1;
    }

    /** A connection on a channel to nowhere: sends nothing, and a disconnect only takes the player out of the game. */
    static class BotConnection extends Connection {
        @Nullable ServerPlayer player;
        private boolean gone;

        BotConnection() {
            super(PacketFlow.SERVERBOUND);
            ((ConnectionAccessor) (Object) this).vnap$setChannel(new EmbeddedChannel());
        }

        @Override
        //? if >=1.21.6 {
        /*public void send(Packet<?> packet, io.netty.channel.@Nullable ChannelFutureListener listener, boolean flush) {
        *///?} else
        public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
        }

        @Override
        public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T listener) {
        }

        @Override
        public void setReadOnly() {
        }

        @Override
        public void handleDisconnection() {
            if (gone || player == null) return;
            gone = true;
            player.connection.onDisconnect(new DisconnectionDetails(Component.literal("bot removed")));
        }
    }
}
