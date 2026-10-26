package com.vnap.fab.fml.common.asm.enumextension;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.client.model.HumanoidModel;

/**
 * A NeoForge enum constant the mod adds (its name in enumextensions.json): on Fabric the shim makes it itself, a real
 * constant of the enum (com.vnap.fab.client.ArmPoses), under the same name.
 */
public final class EnumProxy<T extends Enum<T>> implements Supplier<T> {
	private static final Map<Class<?>, String> NAMES = Map.of(HumanoidModel.ArmPose.class, "VILLAGER_LIFE_TOLL");
	/** The transformers of the arm poses made here (com.vnap.fab.mixin.client.HumanoidModelMixin applies them). */
	public static final Map<HumanoidModel.ArmPose, com.vnap.fab.neoforge.client.IArmPoseTransformer> ARM_POSES = new ConcurrentHashMap<>();
	private final Class<T> type;
	private final Object[] args;
	private volatile @org.jspecify.annotations.Nullable T value;

	public EnumProxy(Class<T> type, Object... args) {
		this.type = type;
		this.args = args.clone();
	}

	@Override
	public T get() {
		return getValue();
	}

	@SuppressWarnings("unchecked")
	public T getValue() {
		T made = value;
		if (made == null) {
			synchronized (this) {
				made = value;
				if (made == null) {
					made = (T) make();
					value = made;
				}
			}
		}
		return made;
	}

	private Object make() {
		String name = NAMES.get(type);
		if (name == null || type != HumanoidModel.ArmPose.class) throw new IllegalStateException("no Fabric extension of " + type.getName() + " " + Arrays.toString(args));
		// the transformer is the last argument; from 1.21.11 an arm pose also says whether it moves the off hand
		var transformer = (com.vnap.fab.neoforge.client.IArmPoseTransformer) args[args.length - 1];
		//? if >=1.21.11 {
		/*HumanoidModel.ArmPose pose = com.vnap.fab.client.ArmPoses.add(name, (Boolean) args[0], (Boolean) args[1]);
		*///?} else
		HumanoidModel.ArmPose pose = com.vnap.fab.client.ArmPoses.add(name, (Boolean) args[0]);
		ARM_POSES.put(pose, transformer);
		return pose;
	}
}
