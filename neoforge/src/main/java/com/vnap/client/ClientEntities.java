package com.vnap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.UUID;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

/** The client knows entities by network id; the voice packets name them by UUID. */
final class ClientEntities {
	private ClientEntities() {
	}

	static @Nullable Entity find(@Nullable UUID uuid) {
		var level = Minecraft.getInstance().level;
		if (level == null || uuid == null) return null;
		return StreamSupport.stream(level.entitiesForRendering().spliterator(), false)
				.filter(entity -> uuid.equals(entity.getUUID())).findAny().orElse(null);
	}
}
