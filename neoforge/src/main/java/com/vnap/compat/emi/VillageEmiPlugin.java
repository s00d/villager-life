package com.vnap.compat.emi;

import com.vnap.block.LifeBlocks;
import com.vnap.client.SchematicPreview;
import com.vnap.block.decor.Machine;
import com.vnap.compat.AnvilCatalog;
import com.vnap.compat.RiteCatalog;
import com.vnap.compat.WorkshopCatalog;
import com.vnap.item.LifeItems;
import com.vnap.mood.ScriptKind;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

@EmiEntrypoint
public final class VillageEmiPlugin implements EmiPlugin {
    static final EmiRecipeCategory RITES = new EmiRecipeCategory(id("village_rites"), EmiStack.of(LifeBlocks.FESTIVAL_TOTEM));
    static final EmiRecipeCategory FORGE = new EmiRecipeCategory(id("tocsin_forge"), EmiStack.of(LifeItems.TOCSIN_CORE));
    static final int WIDTH = 176;
    static final int HEIGHT = 112;

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(RiteCatalog.NS, path);
    }

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(RITES);
        registry.addCategory(FORGE);
        registry.addWorkstation(RITES, EmiStack.of(Items.BELL));
        registry.addWorkstation(RITES, EmiStack.of(LifeItems.BLESSING_STAFF));
        registry.addWorkstation(FORGE, EmiStack.of(LifeBlocks.ALTAR_CORE));
        for (RiteCatalog.Rite rite : RiteCatalog.rites()) {
            registry.addRecipe(new RiteRecipe(rite));
        }
        registry.addRecipe(new ForgeRecipe(RiteCatalog.forge()));
        for (WorkshopCatalog.Station station : WorkshopCatalog.stations()) {
            EmiRecipeCategory category = new EmiRecipeCategory(id(station.id()), EmiStack.of(station.block())) {
                @Override
                public Component getName() {
                    return station.title();
                }
            };
            registry.addCategory(category);
            registry.addWorkstation(category, EmiStack.of(station.block()));
            List<Machine.Example> examples = station.machine().examples();
            for (int i = 0; i < examples.size(); i++) {
                registry.addRecipe(new MachineRecipe(category, id("/" + station.id() + "/" + i), examples.get(i)));
            }
        }
        EmiRecipeCategory anvil = new EmiRecipeCategory(id("core_anvil"), EmiStack.of(AnvilCatalog.anvil())) {
            @Override
            public Component getName() {
                return AnvilCatalog.title();
            }
        };
        registry.addCategory(anvil);
        registry.addWorkstation(anvil, EmiStack.of(AnvilCatalog.anvil()));
        for (AnvilCatalog.Work work : AnvilCatalog.works()) registry.addRecipe(new AnvilRecipe(anvil, work));
        List<WorkshopCatalog.Info> infos = WorkshopCatalog.decorInfo();
        for (int i = 0; i < infos.size(); i++) {
            WorkshopCatalog.Info info = infos.get(i);
            registry.addRecipe(new EmiInfoRecipe(List.of(EmiStack.of(info.stack())), List.of(info.text()), id("/workshop_info/" + i)));
        }
    }

    static final class MachineRecipe extends BasicEmiRecipe {
        private final Machine.Example example;

        MachineRecipe(EmiRecipeCategory category, ResourceLocation id, Machine.Example example) {
            super(category, id, 120, 36);
            this.example = example;
            this.inputs = example.inputs().stream()
                    .map(list -> EmiIngredient.of(list.stream().map(EmiStack::of).toList())).toList();
            this.outputs = List.of(EmiStack.of(example.output()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            for (int i = 0; i < inputs.size(); i++) {
                widgets.addSlot(inputs.get(i), i * 18, 0);
            }
            widgets.addFillingArrow(66, 1, example.ticks() * 50);
            widgets.addSlot(outputs.get(0), 97, 0).recipeContext(this);
            widgets.addText(WorkshopCatalog.seconds(example.ticks()), 1, 24, 0x404040, false);
        }
    }

    /** The anvil's window: piece + shards + seal → result, the coins under it. */
    static final class AnvilRecipe extends BasicEmiRecipe {
        private final AnvilCatalog.Work work;

        AnvilRecipe(EmiRecipeCategory category, AnvilCatalog.Work work) {
            super(category, id("/core_anvil/" + work.id()), 128, 36);
            this.work = work;
            this.inputs = List.of(EmiIngredient.of(work.pieces().stream().map(EmiStack::of).toList()), EmiStack.of(work.shards()),
                    work.seal().isEmpty() ? EmiStack.EMPTY : EmiStack.of(work.seal()));
            this.outputs = List.of(EmiStack.of(work.result()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(inputs.get(0), 0, 0);
            widgets.addSlot(inputs.get(1), 22, 0);
            widgets.addSlot(inputs.get(2), 44, 0);
            widgets.addFillingArrow(68, 1, 2000);
            widgets.addSlot(outputs.get(0), 100, 0).recipeContext(this);
            widgets.addText(work.cost(), 1, 24, 0x404040, false);
        }
    }

    static final class RiteRecipe extends BasicEmiRecipe {
        private final RiteCatalog.Rite rite;

        RiteRecipe(RiteCatalog.Rite rite) {
            super(RITES, id("/rite/" + rite.kind().name().toLowerCase(Locale.ROOT)), WIDTH, HEIGHT);
            this.rite = rite;
            this.outputs = List.of(EmiStack.of(rite.icon()));
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(EmiStack.of(rite.icon()), 0, 0).recipeContext(this);
            widgets.addText(rite.title(), 21, 1, 0x404040, false);
            widgets.addText(rite.threshold(), 21, 10, 0x404040, false);
            widgets.addText(Component.translatable("jei." + RiteCatalog.NS + ".materials"), 100, 12, 0x404040, false);
            List<ItemStack> materials = rite.materials();
            for (int i = 0; i < materials.size(); i++) {
                widgets.addSlot(EmiStack.of(materials.get(i)), 100 + (i % 4) * 18, 21 + (i / 4) * 18);
            }
            widgets.addDrawable(0, 20, 96, 92, (graphics, mouseX, mouseY, delta) -> SchematicPreview.render(graphics,
                    rite.schematic(), 0, 0, 96, 92, SchematicPreview.animatedLayers(rite.schematic())));
            widgets.addTooltipText(List.of(rite.title(), rite.description().copy().withStyle(ChatFormatting.GRAY)), 0, 20, 96, 92);
        }
    }

    static final class ForgeRecipe extends BasicEmiRecipe {
        private final RiteCatalog.Forge forge;

        ForgeRecipe(RiteCatalog.Forge forge) {
            super(FORGE, id("/tocsin_forge"), WIDTH, HEIGHT);
            this.forge = forge;
            this.inputs = forge.offerings().stream().map(s -> (dev.emi.emi.api.stack.EmiIngredient) EmiStack.of(s)).toList();
            this.outputs = forge.spoils().stream().map(EmiStack::of).toList();
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            widgets.addText(RiteCatalog.threshold(ScriptKind.SUMMON), 1, 1, 0x404040, false);
            widgets.addText(Component.translatable("jei." + RiteCatalog.NS + ".forge_feed"), 100, 12, 0x404040, false);
            for (int i = 0; i < forge.offerings().size(); i++) {
                widgets.addSlot(EmiStack.of(forge.offerings().get(i)), 100 + i * 18, 21);
            }
            widgets.addFillingArrow(120, 44, 5000);
            widgets.addText(Component.translatable("jei." + RiteCatalog.NS + ".forge_spoils"), 100, 66, 0x404040, false);
            List<ItemStack> spoils = forge.spoils();
            for (int i = 0; i < spoils.size(); i++) {
                widgets.addSlot(EmiStack.of(spoils.get(i)), 100 + (i % 4) * 18, 75 + (i / 4) * 18)
                        .recipeContext(this)
                        .appendTooltip(note(i));
            }
            widgets.addDrawable(0, 14, 96, 96, (graphics, mouseX, mouseY, delta) -> SchematicPreview.render(graphics,
                    forge.schematic(), 0, 0, 96, 96, Integer.MAX_VALUE));
            widgets.addTooltipText(List.of(Component.translatable("jei." + RiteCatalog.NS + ".tocsin_forge"),
                    Component.translatable("info." + RiteCatalog.NS + ".forge_steps").withStyle(ChatFormatting.GRAY)), 0, 14, 96, 96);
        }

        private static Component note(int index) {
            String key = switch (index) {
                case 0 -> "spoil_core";
                case 1 -> "spoil_iron";
                case 2 -> "spoil_bell";
                default -> "spoil_staff";
            };
            return Component.translatable("jei." + RiteCatalog.NS + "." + key).withStyle(ChatFormatting.GOLD);
        }
    }
}
