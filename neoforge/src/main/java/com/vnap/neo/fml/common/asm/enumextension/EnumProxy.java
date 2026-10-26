package com.vnap.neo.fml.common.asm.enumextension;

import java.util.Arrays;
import java.util.function.Supplier;
import net.minecraft.client.model.HumanoidModel;

/**
 * A NeoForge enum constant the mod adds (its name in enumextensions.json): on Forge the enums that take new constants
 * make them themselves (HumanoidModel.ArmPose.create), under the same name.
 */
public final class EnumProxy<T extends Enum<T>> implements Supplier<T> {
	private static final java.util.Map<Class<?>, String> NAMES = java.util.Map.of(HumanoidModel.ArmPose.class, "VILLAGER_LIFE_TOLL");
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
		if (name == null) throw new IllegalStateException("no Forge extension of " + type.getName() + " " + Arrays.toString(args));
		if (type == HumanoidModel.ArmPose.class) {
			// the transformer is the last argument; from 1.21.11 an arm pose also says whether it moves the off hand
			net.minecraftforge.client.IArmPoseTransformer transformer = (net.minecraftforge.client.IArmPoseTransformer) args[args.length - 1];
			//? if >=1.21.11 {
			/*return HumanoidModel.ArmPose.create(name, (Boolean) args[0], (Boolean) args[1], transformer);
			*///?} else
			return HumanoidModel.ArmPose.create(name, (Boolean) args[0], transformer);
		}
		throw new IllegalStateException(type.getName());
	}
}
