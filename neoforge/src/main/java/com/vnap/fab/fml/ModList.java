package com.vnap.fab.fml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** The mods loaded, as Fabric Loader knows them. */
public final class ModList {
	private static final ModList INSTANCE = new ModList();

	private ModList() {
	}

	public static ModList get() {
		return INSTANCE;
	}

	public boolean isLoaded(String modId) {
		return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
	}

	/** The mod's own file: only what NeoForge's scan of it tells (the mod's classes by their annotations). */
	public ModFile getModFileById(String modId) {
		return new ModFile();
	}

	public static final class ModFile {
		ModFile() {
		}

		public ModFile getFile() {
			return this;
		}

		public ScanResult getScanResult() {
			return new ScanResult();
		}
	}

	/** NeoForge's scan of a mod's classes: on Fabric the lists the build writes of the classes it marks. */
	public static final class ScanResult {
		private static final String[] LISTS = {"/META-INF/vnap-subscribers.txt", "/META-INF/vnap-gametests.txt"};

		ScanResult() {
		}

		public Stream<AnnotationData> getAnnotatedBy(Class<? extends Annotation> annotation, ElementType type) {
			List<AnnotationData> found = new ArrayList<>();
			for (String list : LISTS) {
				for (String name : names(list)) {
					try {
						if (Class.forName(name, false, ModList.class.getClassLoader()).isAnnotationPresent(annotation)) found.add(new AnnotationData(new ClassName(name)));
					} catch (ClassNotFoundException e) {
						// a class the version's build leaves out
					}
				}
			}
			return found.stream().distinct();
		}

		private static List<String> names(String list) {
			try (InputStream in = ModList.class.getResourceAsStream(list)) {
				if (in == null) return List.of();
				return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().filter(line -> !line.isBlank()).toList();
			} catch (IOException e) {
				throw new UncheckedIOException(list, e);
			}
		}
	}

	public record AnnotationData(ClassName clazz) {
	}

	public record ClassName(String getClassName) {
	}
}
