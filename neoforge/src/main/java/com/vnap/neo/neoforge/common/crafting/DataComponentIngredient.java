package com.vnap.neo.neoforge.common.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jspecify.annotations.Nullable;

/**
 * An item with components: the same item whose components hold these (strict: are exactly these). Forge's own
 * ingredients match custom data only, and a potion's kind is a component.
 */
public final class DataComponentIngredient extends net.minecraftforge.common.crafting.ingredients.AbstractIngredient {
	private static final MapCodec<DataComponentIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ItemStack.CODEC.fieldOf("stack").forGetter(ingredient -> ingredient.stack),
			Codec.BOOL.optionalFieldOf("strict", false).forGetter(ingredient -> ingredient.strict)).apply(instance, DataComponentIngredient::new));
	//? if >=26.3 {
	/*private static final net.minecraftforge.common.crafting.ingredients.IIngredientSerializer<DataComponentIngredient> SERIALIZER =
			net.minecraftforge.common.crafting.ingredients.IIngredientSerializer.simple(CODEC, net.minecraft.network.codec.StreamCodec.composite(
					ItemStack.STREAM_CODEC, ingredient -> ingredient.stack, net.minecraft.network.codec.ByteBufCodecs.BOOL, ingredient -> ingredient.strict,
					DataComponentIngredient::new));
	*///?} else {
	private static final net.minecraftforge.common.crafting.ingredients.IIngredientSerializer<DataComponentIngredient> SERIALIZER =
			new net.minecraftforge.common.crafting.ingredients.IIngredientSerializer<>() {
				@Override
				public MapCodec<? extends DataComponentIngredient> codec() {
					return CODEC;
				}

				@Override
				public void write(RegistryFriendlyByteBuf buffer, DataComponentIngredient value) {
					ItemStack.STREAM_CODEC.encode(buffer, value.stack);
					buffer.writeBoolean(value.strict);
				}

				@Override
				public DataComponentIngredient read(RegistryFriendlyByteBuf buffer) {
					return new DataComponentIngredient(ItemStack.STREAM_CODEC.decode(buffer), buffer.readBoolean());
				}
			};
	//?}

	private final ItemStack stack;
	private final boolean strict;
	private final net.minecraft.core.component.DataComponentMap components;

	private DataComponentIngredient(ItemStack stack, boolean strict) {
		//? if >=26.1 {
		/*super(net.minecraft.core.HolderSet.direct(stack.typeHolder()));
		*///?} else if >=1.21.2 {
		/*super(net.minecraft.core.HolderSet.direct(stack.getItemHolder()));
		*///?} else
		super(Stream.of(new Ingredient.ItemValue(stack)));
		this.stack = stack;
		this.strict = strict;
		this.components = net.minecraft.core.component.PatchedDataComponentMap.fromPatch(net.minecraft.core.component.DataComponentMap.EMPTY, stack.getComponentsPatch());
	}

	public static Ingredient of(boolean strict, ItemStack stack) {
		return new DataComponentIngredient(stack.copy(), strict);
	}

	@Override
	public boolean test(@Nullable ItemStack input) {
		if (input == null || !input.is(stack.getItem())) return false;
		if (strict) return input.getComponentsPatch().equals(stack.getComponentsPatch());
		for (var component : components) {
			if (!java.util.Objects.equals(input.get(component.type()), component.value())) return false;
		}
		return true;
	}

	@Override
	public boolean isSimple() {
		return false;
	}

	@Override
	public net.minecraftforge.common.crafting.ingredients.IIngredientSerializer<? extends Ingredient> serializer() {
		return SERIALIZER;
	}
}
