package com.vnap.neo.neoforge.event;

//? if >=1.21.5 {
/*import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.vnap.VillagerLife;
import com.vnap.neo.Bridge;
import com.vnap.neo.bus.api.Event;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

/^*
 * NeoForge's game test registration on Forge, which has none from 1.21.5 (tests are data: test environments and test
 * instances in a registry a data pack fills). Fired as the server's packs are found; what is registered becomes a data
 * pack of its own in a folder of its own, added to them (required, so every world has it).
 ^/
public final class RegisterGameTestsEvent extends Event {
	static {
		Bridge.bind(RegisterGameTestsEvent.class, (priority, sink) -> Bridge.mod(net.minecraftforge.event.AddPackFindersEvent.class, priority,
				RegisterGameTestsEvent::new, event -> {
					if (event.f.getPackType() != PackType.SERVER_DATA) return;
					sink.accept(event);
					event.addPack();
				}));
	}

	private final net.minecraftforge.event.AddPackFindersEvent f;
	private final IdentityHashMap<Holder<TestEnvironmentDefinition>, ResourceLocation> environments = new IdentityHashMap<>();
	private final Map<ResourceLocation, JsonElement> tests = new LinkedHashMap<>();

	RegisterGameTestsEvent(net.minecraftforge.event.AddPackFindersEvent f) {
		super(f);
		this.f = f;
	}

	public Holder<TestEnvironmentDefinition> registerEnvironment(ResourceLocation name, TestEnvironmentDefinition.AllOf definition) {
		Holder<TestEnvironmentDefinition> holder = Holder.direct(definition);
		environments.put(holder, name);
		return holder;
	}

	public void registerTest(ResourceLocation name, Function<TestData<Holder<TestEnvironmentDefinition>>, GameTestInstance> factory,
			TestData<Holder<TestEnvironmentDefinition>> data) {
		ResourceLocation environment = environments.get(data.environment());
		if (environment == null) throw new IllegalArgumentException(name + ": its environment was not registered here");
		var json = GameTestInstance.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, factory.apply(data)).getOrThrow().getAsJsonObject();
		// the environment by its name (without a registry to name it in, the codec writes it out in full)
		json.addProperty("environment", environment.toString());
		tests.put(name, json);
	}

	private void addPack() {
		if (tests.isEmpty()) return;
		Path root;
		try {
			root = Files.createTempDirectory(VillagerLife.MOD_ID + "_tests");
			Files.copy(com.vnap.neo.fml.ModList.get().getModFileById(VillagerLife.MOD_ID).forge().getFile().findResource("pack.mcmeta"), root.resolve("pack.mcmeta"));
			for (var environment : environments.entrySet()) {
				write(root, "test_environment", environment.getValue(),
						TestEnvironmentDefinition.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, environment.getKey().value()).getOrThrow());
			}
			for (var test : tests.entrySet()) write(root, "test_instance", test.getKey(), test.getValue());
		} catch (IOException e) {
			throw new UncheckedIOException("the game tests' data pack", e);
		}
		var info = new PackLocationInfo(VillagerLife.MOD_ID + "_tests", Component.literal("Villager Life game tests"), PackSource.BUILT_IN, Optional.empty());
		Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(root), PackType.SERVER_DATA,
				new PackSelectionConfig(true, Pack.Position.TOP, false));
		if (pack != null) f.addRepositorySource(packs -> packs.accept(pack));
	}

	private static void write(Path root, String kind, ResourceLocation name, JsonElement json) throws IOException {
		Path file = root.resolve("data").resolve(name.getNamespace()).resolve(kind).resolve(name.getPath() + ".json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
	}
}
*///?} else {
/** NeoForge's from 1.21.5 only (Forge's game tests are annotated methods until then). */
public final class RegisterGameTestsEvent {
	private RegisterGameTestsEvent() {
	}
}
//?}
