package com.vnap.neo.neoforge.event;

public class RegisterCommandsEvent extends com.vnap.neo.bus.api.Event {
	static {
		com.vnap.neo.Bridge.bind(RegisterCommandsEvent.class, (priority, sink) -> com.vnap.neo.Bridge.game(net.minecraftforge.event.RegisterCommandsEvent.class, priority, RegisterCommandsEvent::new, sink));
	}

	private final net.minecraftforge.event.RegisterCommandsEvent f;

	RegisterCommandsEvent(net.minecraftforge.event.RegisterCommandsEvent f) {
		super(f);
		this.f = f;
	}

	public com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> getDispatcher() {
		return f.getDispatcher();
	}

	public net.minecraft.commands.CommandBuildContext getBuildContext() {
		return f.getBuildContext();
	}

	public net.minecraft.commands.Commands.CommandSelection getCommandSelection() {
		return f.getCommandSelection();
	}
}
