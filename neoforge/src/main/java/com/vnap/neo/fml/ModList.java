package com.vnap.neo.fml;

import java.lang.annotation.ElementType;
import java.util.stream.Stream;

/** The mods loaded, and the mod files' scan data as NeoForge asks it (the game tests' holders from 1.21.5). */
public final class ModList {
	private static final ModList INSTANCE = new ModList();

	private ModList() {
	}

	public static ModList get() {
		return INSTANCE;
	}

	// Forge's list is static from 26.1
	//? if >=26.1 {
	/*public boolean isLoaded(String modId) {
		return net.minecraftforge.fml.ModList.isLoaded(modId);
	}

	public ModFileInfo getModFileById(String modId) {
		return new ModFileInfo(net.minecraftforge.fml.ModList.getModFileById(modId));
	}
	*///?} else {
	public boolean isLoaded(String modId) {
		return net.minecraftforge.fml.ModList.get().isLoaded(modId);
	}

	public ModFileInfo getModFileById(String modId) {
		return new ModFileInfo(net.minecraftforge.fml.ModList.get().getModFileById(modId));
	}
	//?}

	public record ModFileInfo(net.minecraftforge.forgespi.language.IModFileInfo forge) {
		public ModFile getFile() {
			return new ModFile(forge.getFile());
		}
	}

	public record ModFile(net.minecraftforge.forgespi.locating.IModFile forge) {
		public ScanData getScanResult() {
			return new ScanData(forge.getScanResult());
		}
	}

	public record ScanData(net.minecraftforge.forgespi.language.ModFileScanData forge) {
		/** The classes, methods or fields that carry this annotation (the shim's own: the sources name it as NeoForge's). */
		public Stream<net.minecraftforge.forgespi.language.ModFileScanData.AnnotationData> getAnnotatedBy(Class<? extends java.lang.annotation.Annotation> type, ElementType target) {
			var name = org.objectweb.asm.Type.getType(type);
			return forge.getAnnotations().stream().filter(a -> a.targetType() == target && a.annotationType().equals(name));
		}
	}
}
