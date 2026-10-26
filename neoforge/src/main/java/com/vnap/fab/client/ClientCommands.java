package com.vnap.fab.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * NeoForge's client commands (on vanilla's command source) on Fabric: they live in the shim's own dispatcher, and each
 * of their roots is one of Fabric's client commands that hands the line over to it, suggestions too.
 */
public final class ClientCommands {
	private static final CommandDispatcher<CommandSourceStack> OWN = new CommandDispatcher<>();

	private ClientCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((fabric, context) -> {
			OWN.getRoot().getChildren().clear();
			com.vnap.fab.Bridge.fire(new com.vnap.fab.neoforge.client.event.RegisterClientCommandsEvent(OWN, context));
			for (CommandNode<CommandSourceStack> root : OWN.getRoot().getChildren()) {
				String name = root.getName();
				fabric.register(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal(name)
						.executes(command -> run(name))
						.then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("args", StringArgumentType.greedyString())
								.suggests((command, builder) -> suggest(name, builder))
								.executes(command -> run(name + " " + StringArgumentType.getString(command, "args")))));
			}
		});
	}

	/** The player's own source, as NeoForge's client one: what it is told goes to the chat, it has no server or level. */
	@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "NP_NONNULL_PARAM_VIOLATION",
			justification = "A client's command source has no server and no server level (NeoForge's ClientCommandSourceStack neither).")
	private static CommandSourceStack source() {
		var player = java.util.Objects.requireNonNull(Minecraft.getInstance().player, "a client command with no player");
		net.minecraft.commands.CommandSource chat = new net.minecraft.commands.CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				com.vnap.Mc.say(player, message, false);
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		// every permission, as a player who may cheat in a world of their own (from 1.21.11 a set of them)
		//? if >=1.21.11 {
		/*var permissions = net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER;
		*///?} else
		int permissions = 4;
		//? if >=26.3 {
		/*return new CommandSourceStack(chat, player.position(), player.getRotationVector(), null, permissions, null, player);
		*///?} else
		return new CommandSourceStack(chat, player.position(), player.getRotationVector(), null, permissions, player.getName().getString(), player.getDisplayName(), null, player);
	}

	private static int run(String line) {
		try {
			return OWN.execute(line, source());
		} catch (CommandSyntaxException e) {
			var player = Minecraft.getInstance().player;
			if (player != null) com.vnap.Mc.say(player, Component.literal(e.getMessage()).withStyle(net.minecraft.ChatFormatting.RED), false);
			return 0;
		}
	}

	/** The shim's suggestions for the line, each moved to where the argument starts in Fabric's input. */
	private static CompletableFuture<Suggestions> suggest(String name, SuggestionsBuilder builder) {
		String line = name + " " + builder.getRemaining();
		int shift = builder.getStart() - (name.length() + 1);
		return OWN.getCompletionSuggestions(OWN.parse(line, source())).thenApply(found -> {
			var moved = new java.util.ArrayList<com.mojang.brigadier.suggestion.Suggestion>();
			for (var suggestion : found.getList()) {
				var range = suggestion.getRange();
				moved.add(new com.mojang.brigadier.suggestion.Suggestion(com.mojang.brigadier.context.StringRange.between(range.getStart() + shift,
						range.getEnd() + shift), suggestion.getText(), suggestion.getTooltip()));
			}
			return Suggestions.create(builder.getInput(), moved);
		});
	}
}
