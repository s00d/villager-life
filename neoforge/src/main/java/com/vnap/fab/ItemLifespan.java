package com.vnap.fab;

/** NeoForge's lifespan of an item on the ground, in ticks (com.vnap.fab.mixin.ItemEntityMixin: it goes when its age reaches it; Loom injects it). */
public interface ItemLifespan {
	default void vnapLifespan(int ticks) {
		throw new AssertionError("com.vnap.fab.mixin.ItemEntityMixin");
	}
}
