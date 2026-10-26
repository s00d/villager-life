package com.vnap.fab.neoforge.client.event;

import com.mojang.brigadier.CommandDispatcher;
import com.vnap.fab.bus.api.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

/** The mod's client commands: in the shim's own dispatcher, which Fabric's client commands hand their input to (com.vnap.fab.client.ClientCommands). */
public final class RegisterClientCommandsEvent extends Event {
	private final CommandDispatcher<CommandSourceStack> dispatcher;
	private final CommandBuildContext context;

	public RegisterClientCommandsEvent(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
		this.dispatcher = dispatcher;
		this.context = context;
	}

	public CommandDispatcher<CommandSourceStack> getDispatcher() {
		return dispatcher;
	}

	public CommandBuildContext getBuildContext() {
		return context;
	}
}
