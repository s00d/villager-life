package com.vnap.compat.jei;

import com.vnap.block.LifeBlocks;
import com.vnap.client.SchematicPreview;
import com.vnap.block.decor.Machine;
import com.vnap.compat.AnvilCatalog;
import com.vnap.compat.RiteCatalog;
import com.vnap.compat.WorkshopCatalog;
import com.vnap.item.LifeItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

import java.util.List;

@JeiPlugin
@SuppressWarnings("removal") // JEI 20+ deprecates RecipeType for IRecipeType; the old API is what JEI 19 (1.21.1) has
public final class VillageJeiPlugin implements IModPlugin {
    static final RecipeType<RiteCatalog.Rite> RITES = RecipeType.create(RiteCatalog.NS, "village_rites", RiteCatalog.Rite.class);
    static final RecipeType<RiteCatalog.Forge> FORGE = RecipeType.create(RiteCatalog.NS, "tocsin_forge", RiteCatalog.Forge.class);
    static final RecipeType<AnvilCatalog.Work> ANVIL = RecipeType.create(RiteCatalog.NS, "core_anvil", AnvilCatalog.Work.class);
    static final int WIDTH = 176;
    static final int HEIGHT = 112;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(RiteCatalog.NS, "jei");
    }

    private static boolean emiOwnsIt() {
        return ModList.get().isLoaded("emi");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (emiOwnsIt()) {
            return;
        }
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new RiteCategory(gui), new ForgeCategory(gui), new AnvilCategory(gui));
        for (WorkshopCatalog.Station station : WorkshopCatalog.stations()) {
            registration.addRecipeCategories(new MachineCategory(gui, station));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (emiOwnsIt()) {
            return;
        }
        registration.addRecipes(RITES, RiteCatalog.rites());
        registration.addRecipes(FORGE, List.of(RiteCatalog.forge()));
        registration.addRecipes(ANVIL, AnvilCatalog.works());
        registration.addItemStackInfo(new ItemStack(LifeItems.BLESSING_STAFF), info("staff"));
        registration.addItemStackInfo(new ItemStack(LifeItems.TOCSIN_CORE), info("tocsin_core"));
        registration.addItemStackInfo(new ItemStack(LifeBlocks.ALTAR_CORE), info("altar_core"));
        registration.addItemStackInfo(new ItemStack(LifeItems.HANDBOOK), info("handbook"));
        for (WorkshopCatalog.Station station : WorkshopCatalog.stations()) {
            registration.addRecipes(machineType(station), station.machine().examples());
        }
        for (WorkshopCatalog.Info info : WorkshopCatalog.decorInfo()) {
            registration.addItemStackInfo(info.stack(), info.text());
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (emiOwnsIt()) {
            return;
        }
        registration.addRecipeCatalyst(new ItemStack(Items.BELL), RITES, FORGE);
        registration.addRecipeCatalyst(new ItemStack(LifeItems.BLESSING_STAFF), RITES);
        registration.addRecipeCatalyst(new ItemStack(LifeBlocks.ALTAR_CORE), FORGE);
        registration.addRecipeCatalyst(AnvilCatalog.anvil(), ANVIL);
        for (WorkshopCatalog.Station station : WorkshopCatalog.stations()) {
            registration.addRecipeCatalyst(station.block(), machineType(station));
        }
    }

    static RecipeType<Machine.Example> machineType(WorkshopCatalog.Station station) {
        return RecipeType.create(RiteCatalog.NS, station.id(), Machine.Example.class);
    }

    /** Inputs in a row, the arrow, the output, and the time it takes underneath. */
    static final class MachineCategory extends AbstractRecipeCategory<Machine.Example> {
        private final IGuiHelper gui;

        MachineCategory(IGuiHelper gui, WorkshopCatalog.Station station) {
            super(machineType(station), station.title(), gui.createDrawableItemStack(station.block()), 120, 36);
            this.gui = gui;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Machine.Example example, IFocusGroup focuses) {
            for (int i = 0; i < example.inputs().size(); i++) {
                builder.addSlot(RecipeIngredientRole.INPUT, 1 + i * 18, 1).setStandardSlotBackground().addItemStacks(example.inputs().get(i));
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, 98, 1).setOutputSlotBackground().addItemStack(example.output());
        }

        @Override
        public void draw(Machine.Example example, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            gui.getRecipeArrow().draw(graphics, 66, 1);
            graphics.drawString(Minecraft.getInstance().font, WorkshopCatalog.seconds(example.ticks()), 1, 24, 0x404040, false);
        }
    }

    /** The anvil's window: piece + shards + seal → result, the coins under it. */
    static final class AnvilCategory extends AbstractRecipeCategory<AnvilCatalog.Work> {
        private final IGuiHelper gui;

        AnvilCategory(IGuiHelper gui) {
            super(ANVIL, AnvilCatalog.title(), gui.createDrawableItemStack(AnvilCatalog.anvil()), 128, 36);
            this.gui = gui;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, AnvilCatalog.Work work, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStacks(work.pieces());
            builder.addSlot(RecipeIngredientRole.INPUT, 23, 1).setStandardSlotBackground().addItemStack(work.shards());
            var seal = builder.addSlot(RecipeIngredientRole.INPUT, 45, 1).setStandardSlotBackground();
            if (!work.seal().isEmpty()) seal.addItemStack(work.seal());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 101, 1).setOutputSlotBackground().addItemStack(work.result());
        }

        @Override
        public void draw(AnvilCatalog.Work work, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            gui.getRecipeArrow().draw(graphics, 70, 1);
            graphics.drawString(Minecraft.getInstance().font, work.cost(), 1, 24, 0x404040, false);
        }
    }

    static Component info(String key) {
        return Component.translatable("info." + RiteCatalog.NS + "." + key);
    }

    static final class RiteCategory extends AbstractRecipeCategory<RiteCatalog.Rite> {
        RiteCategory(IGuiHelper gui) {
            super(RITES, Component.translatable("jei." + RiteCatalog.NS + ".village_rites"),
                    gui.createDrawableItemStack(new ItemStack(LifeBlocks.FESTIVAL_TOTEM)), WIDTH, HEIGHT);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RiteCatalog.Rite rite, IFocusGroup focuses) {
            List<ItemStack> materials = rite.materials();
            for (int i = 0; i < materials.size(); i++) {
                builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 101 + (i % 4) * 18, 22 + (i / 4) * 18)
                        .setStandardSlotBackground()
                        .addItemStack(materials.get(i));
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, 1, 1).addItemStack(rite.icon());
        }

        @Override
        public void draw(RiteCatalog.Rite rite, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, rite.title(), 21, 1, 0x404040, false);
            graphics.drawString(font, rite.threshold(), 21, 10, 0x404040, false);
            graphics.drawString(font, Component.translatable("jei." + RiteCatalog.NS + ".materials"), 101, 12, 0x404040, false);
            SchematicPreview.render(graphics, rite.schematic(), 0, 20, 96, 92, SchematicPreview.animatedLayers(rite.schematic()));
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, RiteCatalog.Rite rite, IRecipeSlotsView slots, double mouseX, double mouseY) {
            if (mouseX < 96 && mouseY > 20) {
                tooltip.add(rite.title());
                tooltip.add(rite.description().copy().withStyle(ChatFormatting.GRAY));
            }
        }
    }

    static final class ForgeCategory extends AbstractRecipeCategory<RiteCatalog.Forge> {
        private final IGuiHelper gui;

        ForgeCategory(IGuiHelper gui) {
            super(FORGE, Component.translatable("jei." + RiteCatalog.NS + ".tocsin_forge"),
                    gui.createDrawableItemStack(new ItemStack(LifeItems.TOCSIN_CORE)), WIDTH, HEIGHT);
            this.gui = gui;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RiteCatalog.Forge forge, IFocusGroup focuses) {
            for (int i = 0; i < forge.offerings().size(); i++) {
                builder.addSlot(RecipeIngredientRole.INPUT, 101 + i * 18, 22).setStandardSlotBackground()
                        .addItemStack(forge.offerings().get(i));
            }
            List<ItemStack> spoils = forge.spoils();
            for (int i = 0; i < spoils.size(); i++) {
                int index = i;
                builder.addSlot(RecipeIngredientRole.OUTPUT, 101 + (i % 4) * 18, 76 + (i / 4) * 18).setOutputSlotBackground()
                        .addItemStack(spoils.get(i))
                        .addRichTooltipCallback((view, tooltip) -> tooltip.add(spoilNote(index)));
            }
        }

        @Override
        public void draw(RiteCatalog.Forge forge, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, Component.translatable("jei." + RiteCatalog.NS + ".forge_feed"), 101, 12, 0x404040, false);
            gui.getRecipeArrow().draw(graphics, 120, 44);
            graphics.drawString(font, Component.translatable("jei." + RiteCatalog.NS + ".forge_spoils"), 101, 66, 0x404040, false);
            graphics.drawString(font, RiteCatalog.threshold(com.vnap.mood.ScriptKind.SUMMON), 1, 1, 0x404040, false);
            SchematicPreview.render(graphics, forge.schematic(), 0, 14, 96, 96, Integer.MAX_VALUE);
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, RiteCatalog.Forge forge, IRecipeSlotsView slots, double mouseX, double mouseY) {
            if (mouseX < 96 && mouseY > 14) {
                tooltip.add(Component.translatable("jei." + RiteCatalog.NS + ".tocsin_forge"));
                tooltip.add(info("forge_steps").copy().withStyle(ChatFormatting.GRAY));
            }
        }
    }

    static Component spoilNote(int index) {
        String key = switch (index) {
            case 0 -> "spoil_core";
            case 1 -> "spoil_iron";
            case 2 -> "spoil_bell";
            default -> "spoil_staff";
        };
        return Component.translatable("jei." + RiteCatalog.NS + "." + key).withStyle(ChatFormatting.GOLD);
    }
}
