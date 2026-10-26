package com.vnap.fab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.vnap.fab.Bridge;
import com.vnap.fab.neoforge.event.level.ExplosionEvent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21.2 {
/*import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Unique;
*///?} else
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * NeoForge's ExplosionEvent.Detonate: the blocks and entities an explosion is about to hit, which listeners may thin
 * out. From 1.21.2 the server's explosion works the blocks out in explode() and the entities in hurtEntities().
 */
@SuppressWarnings("unused")
//? if >=1.21.2 {
/*@Mixin(net.minecraft.world.level.ServerExplosion.class)
*///?} else
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
	//? if >=1.21.2 {
	/*@Shadow
	@Final
	private ServerLevel level;
	@Unique
	private List<BlockPos> vnap$blocks = List.of();

	@WrapOperation(method = "explode", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;calculateExplodedPositions()Ljava/util/List;"))
	private List<BlockPos> vnap$blocks(net.minecraft.world.level.ServerExplosion self, Operation<List<BlockPos>> vanilla) {
		vnap$blocks = vanilla.call(self);
		return vnap$blocks;
	}

	@WrapOperation(method = "hurtEntities", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private List<Entity> vnap$detonate(ServerLevel self, @Nullable Entity source, AABB box, Operation<List<Entity>> vanilla) {
		List<Entity> hit = vanilla.call(self, source, box);
		Bridge.fire(new ExplosionEvent.Detonate(level, (Explosion) (Object) this, vnap$blocks, hit));
		return hit;
	}
	*///?} else {
	@Shadow
	@Final
	private Level level;

	@Shadow
	@Final
	private ObjectArrayList<BlockPos> toBlow;

	@WrapOperation(method = "explode", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
	private List<Entity> vnap$detonate(Level self, @Nullable Entity source, AABB box, Operation<List<Entity>> vanilla) {
		List<Entity> hit = vanilla.call(self, source, box);
		Bridge.fire(new ExplosionEvent.Detonate(level, (Explosion) (Object) this, toBlow, hit));
		return hit;
	}
	//?}
}
