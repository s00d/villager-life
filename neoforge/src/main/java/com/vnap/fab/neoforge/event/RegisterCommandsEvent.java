package com.vnap.fab.neoforge.event;

/** The server's commands (Fabric API CommandRegistrationCallback). */
public class RegisterCommandsEvent extends com.vnap.fab.bus.api.Event {
	private final com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher;
	private final net.minecraft.commands.CommandBuildContext buildContext;
	private final net.minecraft.commands.Commands.CommandSelection commandSelection;

	public RegisterCommandsEvent(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext buildContext, net.minecraft.commands.Commands.CommandSelection commandSelection) {
		this.dispatcher = dispatcher;
		this.buildContext = buildContext;
		this.commandSelection = commandSelection;
	}

	public com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> getDispatcher() {
		return dispatcher;
	}

	public net.minecraft.commands.CommandBuildContext getBuildContext() {
		return buildContext;
	}

	public net.minecraft.commands.Commands.CommandSelection getCommandSelection() {
		return commandSelection;
	}
}
