package com.vnap.fab;

/** A mob whose FinalizeSpawnEvent was cancelled: it is not added to its level (com.vnap.fab.mixin.SpawnMixin). */
public interface SpawnCancelled {
	boolean vnap$spawnCancelled();

	void vnap$spawnCancelled(boolean cancelled);
}
