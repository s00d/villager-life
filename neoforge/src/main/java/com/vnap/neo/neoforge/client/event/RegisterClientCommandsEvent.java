package com.vnap.neo.neoforge.client.event;

import com.mojang.brigadier.CommandDispatcher;
import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

public final class RegisterClientCommandsEvent extends Event {
	static {
		Bridge.bind(RegisterClientCommandsEvent.class, (priority, sink) -> Bridge.game(net.minecraftforge.client.event.RegisterClientCommandsEvent.class, priority,
				RegisterClientCommandsEvent::new, sink));
	}

	private final net.minecraftforge.client.event.RegisterClientCommandsEvent f;

	RegisterClientCommandsEvent(net.minecraftforge.client.event.RegisterClientCommandsEvent f) {
		super(f);
		this.f = f;
	}

	public CommandDispatcher<CommandSourceStack> getDispatcher() {
		return f.getDispatcher();
	}

	public CommandBuildContext getBuildContext() {
		return f.getBuildContext();
	}
}
