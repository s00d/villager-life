package com.vnap.client.guide;

import com.vnap.VillagerLife;
import com.vnap.client.McClient;
import com.vnap.client.SchematicPreview;
import com.vnap.village.script.SchematicSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jspecify.annotations.Nullable;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Villager Life guide: an open book of two pages, after the best mod manuals (Patchouli, GuideME): a landing
 * spread with the chapters, a chapter spread with its entries, and entries of pages turned two at a time. Pages are
 * text, an item in the spotlight, a crafting grid read from the game's own recipes, a picture, a grid of items,
 * links, a turning building, or a door into the reactions catalogue and settings.
 */
public final class GuideScreen extends Screen {
    private static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(VillagerLife.MOD_ID, "textures/gui/guide/book.png");
    private static final int ATLAS_W = 512;
    private static final int ATLAS_H = 256;
    private static final int BOOK_W = 292;
    /** Chapter tabs down the right edge; more go down the left one. */
    private static final int TABS_PER_SIDE = 8;
    private static final int BOOK_H = 184;
    private static final int PAGE_W = 116;
    private static final int LEFT_X = 19;
    private static final int RIGHT_X = 157;
    private static final int SPINE = 146;
    private static final int TOP = 15;
    private static final int BOTTOM = 160;
    private static final int ROW = 18;
    private static final int INK = 0xFF3A2818;
    private static final int FAINT = 0xFF7A6448;
    private static final int ACCENT = 0xFF1D6B38;
    private static final int TITLE = 0xFF2A1A0C;
    private static final int RIBBON_TEXT = 0xFFFBF3DC;
    private static final int EMERALD = 0x2E9B5A;
    private static final long FLIP_MS = 360L;

    /** Where the book was when it was closed: it opens there again, as a real book would. */
    private static @Nullable State last;

    private enum Kind { LANDING, CATEGORY, ENTRY }

    private record State(Kind kind, @Nullable String id, int spread) {
    }

    /** A clickable or hoverable area of the last frame. */
    private record Hit(int x, int y, int w, int h, @Nullable Runnable action, @Nullable ItemStack item, @Nullable Component text) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final GuideBook book;
    private final GuideProgress progress;
    private final Deque<State> history = new ArrayDeque<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, List<View>> laidOut = new HashMap<>();
    /** Every spread of the book in reading order: the corners turn through the whole book, not one entry. */
    private final List<State> order = new ArrayList<>();
    /** How far each chapter tab is pulled out, 0..1: they slide, not jump. */
    private final float[] tabOut;
    private State state;
    /** The spread being turned away from, shown on the leaf until it lands. */
    private State turnedFrom;
    private int turnDirection = 1;
    private long turnedAt;
    private long lastFrame;
    private int left;
    private int top;
    private int mouseX;
    private int mouseY;

    public GuideScreen() {
        super(Component.translatable("item.villager_life.handbook"));
        this.book = GuideBook.load();
        this.progress = GuideProgress.load();
        this.tabOut = new float[book.categories.size()];
        State remembered = last;
        this.state = remembered != null && valid(remembered) ? remembered : new State(Kind.LANDING, null, 0);
        this.turnedFrom = state;
    }

    /** The guide opened at an entry (or a chapter) and spread: /vnapguide, for checking every page by hand. */
    public static GuideScreen at(String id, int spread) {
        GuideScreen screen = new GuideScreen();
        if (screen.book.entries.containsKey(id)) {
            screen.state = new State(Kind.ENTRY, id, Math.max(0, spread));
        } else if (screen.category(id) != null) {
            screen.state = new State(Kind.CATEGORY, id, Math.max(0, spread));
        } else {
            screen.state = new State(Kind.LANDING, null, 0);
        }
        return screen;
    }

    /** Entry ids in book order, for tab completion and for walking through the book. */
    public static java.util.List<String> ids() {
        GuideBook book = GuideBook.load();
        java.util.List<String> out = new ArrayList<>();
        for (GuideBook.Category category : book.categories) {
            out.add(category.id());
            for (GuideBook.Entry entry : category.entries()) out.add(entry.id());
        }
        return out;
    }

    private boolean valid(State remembered) {
        return switch (remembered.kind()) {
            case LANDING -> true;
            case CATEGORY -> book.categories.stream().anyMatch(category -> category.id().equals(remembered.id()) && category.open());
            case ENTRY -> remembered.id() != null && book.entries.containsKey(remembered.id()) && reachable(book.entries.get(remembered.id()));
        };
    }

    @Override
    protected void init() {
        // the chapter tabs stick out on the right (and on the left too once the right edge is full): the book and its
        // tabs together are centred
        left = book.categories.size() > TABS_PER_SIDE ? (width - BOOK_W) / 2 : (width - BOOK_W - 10) / 2;
        top = Math.max(8, (height - BOOK_H - 14) / 2);
        laidOut.clear();
        // a spread past the end (a remembered one of a text that got shorter, or /vnapguide) is the entry's last
        state = new State(state.kind(), state.id(), Math.min(state.spread(), spreads(state) - 1));
        turnedFrom = state;
        order.clear();
        order.add(new State(Kind.LANDING, null, 0));
        for (GuideBook.Category category : book.categories) {
            if (!category.open()) continue;
            State chapter = new State(Kind.CATEGORY, category.id(), 0);
            for (int i = 0; i < spreads(chapter); i++) order.add(new State(Kind.CATEGORY, category.id(), i));
            for (GuideBook.Entry entry : category.entries()) {
                if (!entry.open()) continue;
                State first = new State(Kind.ENTRY, entry.id(), 0);
                for (int i = 0; i < spreads(first); i++) order.add(new State(Kind.ENTRY, entry.id(), i));
            }
        }
    }

    private static void remember(State where) {
        last = where;
    }

    @Override
    public void onClose() {
        remember(state);
        super.onClose();
    }

    // ---------------------------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        hits.clear();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        long now = Util.getMillis();
        renderTabs(graphics, now);
        // the cover is 4 px larger than the spread on every side; it casts a soft shadow
        graphics.fill(left - 1, top + 2, left + BOOK_W + 8, top + BOOK_H + 9, 0x30000000);
        graphics.fill(left, top + 3, left + BOOK_W + 7, top + BOOK_H + 8, 0x30000000);
        McClient.blit(graphics, ATLAS, left - 4, top - 4, 0, 0, BOOK_W + 8, BOOK_H + 8, ATLAS_W, ATLAS_H);
        float turn = Math.min(1.0F, (now - turnedAt) / (float) FLIP_MS);
        if (turn < 1.0F) {
            renderTurning(graphics, turn);
        } else {
            renderSpread(graphics, state);
        }
        renderFooter(graphics);
        lastFrame = now;
        for (Hit hit : hits) {
            if (!hit.contains(mouseX, mouseY)) continue;
            ItemStack shownItem = hit.item();
            Component shownText = hit.text();
            if (shownItem != null) McClient.tooltip(graphics, font, shownItem, mouseX, mouseY);
            else if (shownText != null) McClient.tooltip(graphics, font, shownText, mouseX, mouseY);
            break;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // the book draws its own dimmed background in render: no blur behind a page of text
    }

    private void renderSpread(GuiGraphics graphics, State spread) {
        State current = state;
        state = spread;
        switch (spread.kind()) {
            case LANDING -> renderLanding(graphics);
            case CATEGORY -> renderCategory(graphics);
            case ENTRY -> renderEntry(graphics);
        }
        // pages are numbered through the whole book
        int index = order.indexOf(spread);
        if (index >= 0) {
            for (int side = 0; side < 2; side++) {
                Component number = Component.literal(String.valueOf(index * 2 + side + 1));
                int x = left + (side == 0 ? LEFT_X : RIGHT_X);
                graphics.drawString(font, number, x + PAGE_W / 2 - font.width(number) / 2, top + BOTTOM + 6, FAINT, false);
            }
        }
        state = current;
    }

    /**
     * A page being turned: the leaf stands up from one page, crosses the spine and lies down on the other, its page
     * squeezed towards the spine as it rises. Left of the leaf is the left page of the spread it leaves (turning
     * forward) or comes to (turning back), right of it the other one; the leaf carries the old page, then the new.
     */
    private void renderTurning(GuiGraphics graphics, float turn) {
        float eased = turn * turn * (3.0F - 2.0F * turn);
        int full = 134;
        int spine = left + SPINE;
        int offset = Math.round(full * (1.0F - 2.0F * eased));
        int leaf = Math.abs(offset);
        boolean leafOnRight = (turnDirection > 0) == (offset > 0);
        int leafX = leafOnRight ? spine : spine - leaf;
        boolean forward = turnDirection > 0;
        State leftSide = forward ? turnedFrom : state;
        State rightSide = forward ? state : turnedFrom;
        int hitsBefore = hits.size();
        graphics.enableScissor(left, top, leafOnRight ? spine : leafX, top + BOOK_H);
        renderSpread(graphics, leftSide);
        graphics.disableScissor();
        graphics.enableScissor(leafOnRight ? spine + leaf : spine, top, left + BOOK_W, top + BOOK_H);
        renderSpread(graphics, rightSide);
        graphics.disableScissor();
        if (leaf > 0) {
            // the leaf is paper seen at a slant: darker the more it stands up
            float light = 0.8F + 0.2F * leaf / full;
            McClient.setColor(graphics, light, light, light, 1.0F);
            McClient.blit(graphics, ATLAS, leafX, top + 8, leaf, 168, leafOnRight ? 152 : 14, 12, 134, 168, ATLAS_W, ATLAS_H);
            McClient.setColor(graphics, 1.0F, 1.0F, 1.0F, 1.0F);
            graphics.enableScissor(leafX, top, leafX + leaf, top + BOOK_H);
            McClient.push(graphics);
            McClient.translate(graphics, spine, 0.0F, 0.0F);
            McClient.scale(graphics, leaf / (float) full, 1.0F, 1.0F);
            McClient.translate(graphics, -spine, 0.0F, 0.0F);
            renderSpread(graphics, offset > 0 ? turnedFrom : state);
            McClient.pop(graphics);
            graphics.disableScissor();
            // shadow in the fold at the spine, and the shadow the leaf casts on the page it uncovers
            for (int i = 0; i < Math.min(leaf, 8); i++) {
                int x = leafOnRight ? leafX + i : leafX + leaf - 1 - i;
                graphics.fill(x, top + 8, x + 1, top + 176, (int) ((8 - i) * 6.0F) << 24);
            }
        }
        int free = leafOnRight ? spine + leaf : spine - leaf;
        for (int i = 0; i < 6; i++) {
            int x = leafOnRight ? free + i : free - 1 - i;
            graphics.fill(x, top + 8, x + 1, top + 176, (int) ((6 - i) * 9 * (1.0F - Math.abs(0.5F - eased))) << 24);
        }
        // pages in motion are looked at, not clicked
        hits.subList(hitsBefore, hits.size()).clear();
    }

    /**
     * The chapter tabs down the fore-edge, in their colours; the open chapter's and the hovered one stand out. The
     * right edge holds up to {@link #TABS_PER_SIDE}; with more, the chapters are shared out evenly and the second half
     * gets tabs down the left edge, mirrored.
     */
    private void renderTabs(GuiGraphics graphics, long now) {
        float step = lastFrame == 0L ? 1.0F : Math.min(1.0F, (now - lastFrame) / 70.0F);
        String open = openCategory();
        for (int i = 0; i < book.categories.size(); i++) {
            GuideBook.Category category = book.categories.get(i);
            int right = rightTabs();
            boolean leftSide = i >= right;
            int y = top + 12 + (leftSide ? i - right : i) * 20;
            int x0 = leftSide ? left - 20 : left + BOOK_W - 6;
            int hitX0 = leftSide ? x0 - 4 : x0 + 10;
            boolean current = category.id().equals(open);
            boolean over = mouseX >= hitX0 && mouseX < hitX0 + 20 && mouseY >= y && mouseY < y + 18;
            // the open chapter's tab stands well out and bright, a hovered one peeks out halfway
            tabOut[i] += ((current ? 1.0F : over ? 0.45F : 0.0F) - tabOut[i]) * step;
            int out = Math.round(tabOut[i] * 7.0F);
            int x = leftSide ? x0 - out : x0 + out;
            boolean shut = !category.open();
            // a chapter not come upon yet keeps its tab and its colour, in shadow
            tint(graphics, category.color(), shut ? 0.32F : 0.6F + 0.4F * tabOut[i]);
            if (leftSide) {
                // mirrored by its texture coordinates, not a flipped pose: a flipped quad faced away and was culled, and
                // from 1.21.6 the GUI drops a rectangle turned inside out
                McClient.blit(graphics, ATLAS, x, y, 26, 18, 326.0F, 20.0F, -26, 18, ATLAS_W, ATLAS_H);
            } else {
                McClient.blit(graphics, ATLAS, x, y, 300, 20, 26, 18, ATLAS_W, ATLAS_H);
            }
            McClient.setColor(graphics, 1.0F, 1.0F, 1.0F, 1.0F);
            int iconX = leftSide ? x + 3 : x + 11;
            McClient.push(graphics);
            McClient.translate(graphics, iconX, y + 3, 0);
            McClient.scale(graphics, 0.75F, 0.75F, 1.0F);
            graphics.renderItem(stack(category.icon()), 0, 0);
            if (shut) shade(graphics, 0, 0, 16, 16, 0xB0201814);
            McClient.pop(graphics);
            hits.add(new Hit(leftSide ? x : x + 10, y, 16, 18, shut ? () -> { } : () -> open(new State(Kind.CATEGORY, category.id(), 0)), null,
                    shut ? locked(category.unlock()) : Component.literal(category.title())));
        }
    }

    private int rightTabs() {
        int count = book.categories.size();
        return count <= TABS_PER_SIDE ? count : (count + 1) / 2;
    }

    private @Nullable String openCategory() {
        return switch (state.kind()) {
            case LANDING -> null;
            case CATEGORY -> state.id();
            case ENTRY -> {
                GuideBook.Category category = state.id() == null ? null : book.categoryOf.get(state.id());
                yield category == null ? null : category.id();
            }
        };
    }

    private int colourOf(@Nullable String entryId) {
        GuideBook.Category category = entryId == null ? null : book.categoryOf.get(entryId);
        return category == null ? EMERALD : category.color();
    }

    private static void tint(GuiGraphics graphics, int rgb, float k) {
        McClient.setColor(graphics, (rgb >> 16 & 255) / 255.0F * k, (rgb >> 8 & 255) / 255.0F * k, (rgb & 255) / 255.0F * k, 1.0F);
    }

    private void renderLanding(GuiGraphics graphics) {
        int x = left + LEFT_X;
        int y = top + TOP;
        y = ribbon(graphics, Component.literal(book.title), x, y, EMERALD, 1.3F);
        y = centred(graphics, Component.literal(book.subtitle).withStyle(ChatFormatting.ITALIC), x, y + 4, FAINT, 1.0F);
        sprig(graphics, x, y);
        drawText(graphics, wrap(book.landing, PAGE_W), x, y + 11, top + BOTTOM - 18 - (y + 11));
        int total = (int) book.entries.values().stream().filter(this::reachable).count();
        long read = book.entries.values().stream().filter(this::reachable).map(GuideBook.Entry::id).filter(progress::read).count();
        Component count = Component.translatable("guide.villager_life.progress", read, total);
        graphics.drawString(font, count, x + PAGE_W / 2 - font.width(count) / 2, top + BOTTOM - 10, FAINT, false);
        McClient.blit(graphics, ATLAS, x, top + BOTTOM, 0, 200, PAGE_W, 6, ATLAS_W, ATLAS_H);
        McClient.blit(graphics, ATLAS, x, top + BOTTOM, 0, 208, (int) (PAGE_W * read / Math.max(1, total)), 6, ATLAS_W, ATLAS_H);

        int rx = left + RIGHT_X;
        int ry = centred(graphics, Component.translatable("guide.villager_life.chapters").withStyle(ChatFormatting.BOLD), rx, top + TOP, TITLE, 1.0F);
        sprig(graphics, rx, ry);
        ry += 11;
        // more chapters than rows of the full height: the rows close up to fit the page
        int rowStep = Math.min(ROW, (top + BOTTOM - ry) / Math.max(1, book.categories.size()));
        for (GuideBook.Category category : book.categories) {
            if (!category.open()) {
                shut(graphics, rx, ry, category.icon(), locked(category.unlock()), rowStep);
                ry += rowStep;
                continue;
            }
            boolean unread = category.entries().stream().anyMatch(entry -> entry.open() && !progress.read(entry.id()));
            row(graphics, rx, ry, category.icon(), Component.literal(category.title()), unread, () -> open(new State(Kind.CATEGORY, category.id(), 0)), rowStep);
            ry += rowStep;
        }
    }

    private void renderCategory(GuiGraphics graphics) {
        GuideBook.Category category = category(state.id());
        if (category == null) return;
        int x = left + LEFT_X;
        int y = top + TOP;
        McClient.push(graphics);
        McClient.translate(graphics, x + PAGE_W / 2.0F - 12, y, 0);
        McClient.scale(graphics, 1.5F, 1.5F, 1.0F);
        graphics.renderItem(stack(category.icon()), 0, 0);
        McClient.pop(graphics);
        y = ribbon(graphics, Component.literal(category.title()), x, y + 28, category.color(), 1.0F);
        drawText(graphics, wrap(category.description(), PAGE_W), x, y + 6, BOTTOM - (y - top) - 6);

        int perPage = (BOTTOM - TOP - 12) / ROW;
        List<GuideBook.Entry> entries = category.entries();
        int page = Math.min(state.spread(), spreads(state) - 1);
        int rx = left + RIGHT_X;
        int ry = centred(graphics, Component.translatable("guide.villager_life.entries").withStyle(ChatFormatting.BOLD), rx, top + TOP, TITLE, 1.0F);
        sprig(graphics, rx, ry);
        ry += 11;
        for (int i = page * perPage; i < Math.min(entries.size(), (page + 1) * perPage); i++) {
            GuideBook.Entry entry = entries.get(i);
            if (!entry.open()) {
                shut(graphics, rx, ry, entry.icon(), locked(entry.unlock()), ROW);
            } else {
                row(graphics, rx, ry, entry.icon(), Component.literal(entry.title()), !progress.read(entry.id()),
                        () -> open(new State(Kind.ENTRY, entry.id(), 0)));
            }
            ry += ROW;
        }
    }

    private void renderEntry(GuiGraphics graphics) {
        GuideBook.Entry entry = state.id() == null ? null : book.entries.get(state.id());
        if (entry == null) return;
        List<View> views = views(entry);
        int first = state.spread() * 2;
        for (int side = 0; side < 2; side++) {
            int index = first + side;
            int x = left + (side == 0 ? LEFT_X : RIGHT_X);
            if (index >= views.size()) {
                // the blank page at the end of an entry gets a drawing of the village, not a bare sheet
                McClient.blit(graphics, ATLAS, x + (PAGE_W - 96) / 2, top + (TOP + BOTTOM) / 2 - 28, 300, 76, 96, 56, ATLAS_W, ATLAS_H);
                break;
            }
            int y = top + TOP;
            if (index == 0) y = ribbon(graphics, Component.literal(entry.title()), x, y, colourOf(entry.id()), 1.0F) + 5;
            views.get(index).render(graphics, x, y, top + BOTTOM - y);
        }
    }

    /** Page corners turn the pages; the bookmarks hanging at the spine go back and to the contents. */
    private void renderFooter(GuiGraphics graphics) {
        int index = order.indexOf(state);
        if (index > 0) {
            button(graphics, left + 10, top + 156, 374, 0, 20, 20, 22, 0, Component.translatable("guide.villager_life.previous"), () -> turn(-1));
        }
        if (index >= 0 && index < order.size() - 1) {
            button(graphics, left + 262, top + 156, 330, 0, 20, 20, 22, 0, Component.translatable("guide.villager_life.next"), () -> turn(1));
        }
        if (state.kind() != Kind.LANDING) {
            button(graphics, left + SPINE - 21, top + BOOK_H - 2, 420, 0, 18, 16, 20, 2, Component.translatable("guide.villager_life.back"), this::back);
            button(graphics, left + SPINE + 3, top + BOOK_H - 2, 460, 0, 18, 16, 20, 2, Component.translatable("guide.villager_life.home"),
                    () -> open(new State(Kind.LANDING, null, 0)));
        }
    }

    /** An atlas button; hovered, it shows the sprite {@code hoverU} further on and moves {@code drop} down. */
    private void button(GuiGraphics graphics, int x, int y, int u, int v, int w, int h, int hoverU, int drop, Component tip, Runnable action) {
        boolean over = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h + drop;
        McClient.blit(graphics, ATLAS, x, y + (over ? drop : 0), u + (over ? hoverU : 0), v, w, h, ATLAS_W, ATLAS_H);
        hits.add(new Hit(x, y, w, h + drop, action, null, tip));
    }

    private void row(GuiGraphics graphics, int x, int y, String icon, Component title, boolean unread, Runnable action) {
        row(graphics, x, y, icon, title, unread, action, ROW);
    }

    /** A row {@code step} high: under two lines' height (the contents closed up) the title keeps to one line, smaller. */
    private void row(GuiGraphics graphics, int x, int y, String icon, Component title, boolean unread, Runnable action, int step) {
        Hit hit = new Hit(x - 2, y - 1, PAGE_W + 4, step - 1, action, null, null);
        hits.add(hit);
        if (hit.contains(mouseX, mouseY)) graphics.fill(x - 2, y - 1, x + PAGE_W + 2, y + step - 2, 0x1E2E9B5A);
        graphics.renderItem(stack(icon), x, y);
        // clear of the unread dot at the row's end
        int room = PAGE_W - 30;
        if (step < ROW) {
            float scale = Math.min(1.0F, room / (float) font.width(title));
            McClient.push(graphics);
            McClient.translate(graphics, x + 19, y + 4 + (1.0F - scale) * 4.0F, 0);
            McClient.scale(graphics, scale, scale, 1.0F);
            graphics.drawString(font, title, 0, 0, INK, false);
            McClient.pop(graphics);
        } else {
            List<FormattedCharSequence> lines = font.split(title, room);
            int ty = lines.size() > 1 ? y : y + 4;
            for (int i = 0; i < Math.min(2, lines.size()); i++) graphics.drawString(font, lines.get(i), x + 19, ty + i * 9, INK, false);
        }
        if (unread) McClient.blit(graphics, ATLAS, x + PAGE_W - 4, y + 5, 488, 40, 5, 5, ATLAS_W, ATLAS_H);
    }

    private int centred(GuiGraphics graphics, Component text, int x, int y, int color, float scale) {
        List<FormattedCharSequence> lines = font.split(text, (int) (PAGE_W / scale));
        for (FormattedCharSequence line : lines) {
            McClient.push(graphics);
            McClient.translate(graphics, x + PAGE_W / 2.0F - font.width(line) * scale / 2.0F, y, 0);
            McClient.scale(graphics, scale, scale, 1.0F);
            graphics.drawString(font, line, 0, 0, color, false);
            McClient.pop(graphics);
            y += (int) (10 * scale);
        }
        return y;
    }

    /** Lines of a title on a ribbon: one, or two for a long one. */
    private List<FormattedCharSequence> ribbonLines(Component text, float scale) {
        return font.split(text.copy().withStyle(ChatFormatting.BOLD), (int) ((PAGE_W - 6) / scale));
    }

    /** A title on a ribbon of the chapter's colour, with forked tails over the page's margins. Returns the y under it. */
    private int ribbon(GuiGraphics graphics, Component text, int x, int y, int rgb, float scale) {
        List<FormattedCharSequence> lines = ribbonLines(text, scale);
        int lineHeight = Math.round(10 * scale);
        int h = 7 + lineHeight * lines.size();
        int body = 0xFF000000 | rgb;
        int dark = 0xFF000000 | shade(rgb, 0.62F);
        int light = 0xFF000000 | shade(rgb, 1.25F);
        for (int r = 2; r < h + 2; r++) {
            // the tails behind the ribbon, each cut with a notch
            int notch = Math.max(0, 3 - Math.abs(r - (h + 3) / 2));
            graphics.fill(x - 7 + notch, y + r, x, y + r + 1, dark);
            graphics.fill(x + PAGE_W, y + r, x + PAGE_W + 7 - notch, y + r + 1, dark);
        }
        graphics.fill(x - 1, y, x + PAGE_W + 1, y + h, body);
        graphics.fill(x - 1, y, x + PAGE_W + 1, y + 1, light);
        graphics.fill(x - 1, y + h - 2, x + PAGE_W + 1, y + h, dark);
        graphics.fill(x - 1, y + h, x + 1, y + h + 2, 0xFF000000 | shade(rgb, 0.4F));
        graphics.fill(x + PAGE_W - 1, y + h, x + PAGE_W + 1, y + h + 2, 0xFF000000 | shade(rgb, 0.4F));
        int ty = y + 4;
        for (FormattedCharSequence line : lines) {
            McClient.push(graphics);
            McClient.translate(graphics, x + PAGE_W / 2.0F - font.width(line) * scale / 2.0F, ty, 0);
            McClient.scale(graphics, scale, scale, 1.0F);
            graphics.drawString(font, line, 0, 0, RIBBON_TEXT, true);
            McClient.pop(graphics);
            ty += lineHeight;
        }
        return y + h + 2;
    }

    private static int shade(int rgb, float k) {
        int r = Math.min(255, (int) ((rgb >> 16 & 255) * k));
        int g = Math.min(255, (int) ((rgb >> 8 & 255) * k));
        int b = Math.min(255, (int) ((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    /** The wheat sprig under a heading. */
    private void sprig(GuiGraphics graphics, int x, int y) {
        McClient.blit(graphics, ATLAS, x + 3, y, 300, 62, 110, 9, ATLAS_W, ATLAS_H);
    }

    // ---------------------------------------------------------------------------------- navigation

    private int spreads(State of) {
        return switch (of.kind()) {
            case LANDING -> 1;
            case CATEGORY -> {
                GuideBook.Category category = category(of.id());
                int perPage = (BOTTOM - TOP - 12) / ROW;
                yield category == null ? 1 : Math.max(1, (category.entries().size() + perPage - 1) / perPage);
            }
            case ENTRY -> {
                GuideBook.Entry entry = of.id() == null ? null : book.entries.get(of.id());
                yield entry == null ? 1 : Math.max(1, (views(entry).size() + 1) / 2);
            }
        };
    }

    private void open(State next) {
        history.push(state);
        go(next);
        if (next.kind() == Kind.ENTRY && next.id() != null) progress.markRead(next.id());
    }

    private void openEntry(GuideBook.Entry entry) {
        // a link or an item into what the player has not come upon yet leads nowhere
        if (!reachable(entry)) return;
        open(new State(Kind.ENTRY, entry.id(), 0));
    }

    /** An entry the player may read: it and its chapter are open. */
    private boolean reachable(GuideBook.Entry entry) {
        GuideBook.Category category = book.categoryOf.get(entry.id());
        return entry.open() && (category == null || category.open());
    }

    /** What a closed chapter or entry says of where to look: the hint of its first key. */
    private static Component locked(List<String> unlock) {
        return Component.translatable("guide.villager_life.locked." + (unlock.isEmpty() ? "town" : unlock.get(0)));
    }

    /** A row of something not come upon yet: a blank seal, "???" and the hint under the mouse. */
    private void shut(GuiGraphics graphics, int x, int y, String icon, Component hint, int step) {
        Hit hit = new Hit(x - 2, y - 1, PAGE_W + 4, step - 1, () -> { }, null, hint);
        hits.add(hit);
        // its own icon in shadow, as if under the page's paper still; its name not yet written in
        graphics.renderItem(stack(icon), x, y);
        shade(graphics, x, y, 16, 16, 0xC8E9DCC0);
        graphics.drawString(font, Component.literal("? ? ?"), x + 19, y + 4, FAINT, false);
    }

    /** A wash over something drawn (an item), above it: the item renders forward of the page. */
    private static void shade(GuiGraphics graphics, int x, int y, int w, int h, int argb) {
        McClient.push(graphics);
        McClient.translate(graphics, 0, 0, 200);
        graphics.fill(x, y, x + w, y + h, argb);
        McClient.pop(graphics);
    }

    private void back() {
        if (!history.isEmpty()) {
            go(history.pop());
        } else if (state.kind() == Kind.ENTRY) {
            GuideBook.Category category = state.id() == null ? null : book.categoryOf.get(state.id());
            go(category == null ? new State(Kind.LANDING, null, 0) : new State(Kind.CATEGORY, category.id(), 0));
        } else {
            go(new State(Kind.LANDING, null, 0));
        }
    }

    /** The next or previous spread of the book: from an entry's last spread into the next entry or chapter. */
    private void turn(int by) {
        int index = order.indexOf(state) + by;
        if (index < 0 || index >= order.size()) return;
        State next = order.get(index);
        go(next);
        if (next.kind() == Kind.ENTRY && next.id() != null) progress.markRead(next.id());
    }

    /** Turns to a spread: a leaf turns forward or back (as the book's order says), with the sound of a page. */
    private void go(State next) {
        int from = order.indexOf(state);
        int to = order.indexOf(next);
        // a turn while a leaf is still in the air starts from where the reader now is
        turnedFrom = state;
        state = next;
        turnDirection = from >= 0 && to >= 0 && to < from ? -1 : 1;
        turnedAt = Util.getMillis();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        return click(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return click(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }
    //?}

    private boolean click(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : List.copyOf(hits)) {
                if (hit.action() != null && hit.contains(mouseX, mouseY)) {
                    hit.action().run();
                    return true;
                }
            }
        }
        if (button == 1 && state.kind() != Kind.LANDING) {
            back();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        turn(scrollY < 0 ? 1 : -1);
        return true;
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return key(event.key()) || super.keyPressed(event);
    }
    *///?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return key(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    private boolean key(int keyCode) {
        switch (keyCode) {
            case InputConstants.KEY_RIGHT, InputConstants.KEY_PAGEDOWN -> turn(1);
            case InputConstants.KEY_LEFT, InputConstants.KEY_PAGEUP -> turn(-1);
            case InputConstants.KEY_BACKSPACE -> back();
            default -> {
                return false;
            }
        }
        return true;
    }

    private GuideBook.@Nullable Category category(@Nullable String id) {
        for (GuideBook.Category category : book.categories) {
            if (category.id().equals(id)) return category;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------- pages

    /** One page of an entry as it is drawn: a JSON page, or the part of a long text that went over a page. */
    private interface View {
        void render(GuiGraphics graphics, int x, int y, int height);
    }

    private List<View> views(GuideBook.Entry entry) {
        return laidOut.computeIfAbsent(entry.id(), id -> layout(entry));
    }

    private List<View> layout(GuideBook.Entry entry) {
        List<View> views = new ArrayList<>();
        int firstHeight = BOTTOM - TOP - (7 + 10 * ribbonLines(Component.literal(entry.title()), 1.0F).size() + 2) - 5;
        for (GuideBook.Page page : entry.pages()) {
            switch (page.type()) {
                case "spotlight" -> views.add(spotlight(page));
                case "crafting" -> views.add(crafting(page));
                case "image" -> views.add(image(page));
                case "items" -> views.add(items(page));
                case "links" -> views.add(links(page));
                case "structure" -> views.add(structure(page));
                case "screen" -> views.add(screenPage(page));
                default -> textPages(page, views, firstHeight);
            }
        }
        if (views.isEmpty()) views.add((graphics, x, y, height) -> { });
        return views;
    }

    /**
     * A text page, carried over to as many pages as it needs. A paragraph or list item moves to the next page whole
     * when it does not fit; only one longer than a page is broken inside.
     */
    private void textPages(GuideBook.Page page, List<View> views, int firstHeight) {
        List<Line> lines = new ArrayList<>();
        if (!page.title().isEmpty()) {
            lines.add(new Line(FormattedCharSequence.forward(page.title(), Style.EMPTY.withBold(true).withColor(TITLE)), 0, false, true));
            lines.add(new Line(FormattedCharSequence.EMPTY, 0, true, true));
        }
        lines.addAll(wrap(page.text(), PAGE_W));
        List<List<Line>> blocks = new ArrayList<>();
        for (Line line : lines) {
            if (line.start() || blocks.isEmpty()) blocks.add(new ArrayList<>());
            blocks.get(blocks.size() - 1).add(line);
        }
        List<Line> current = new ArrayList<>();
        int room = views.isEmpty() ? firstHeight : BOTTOM - TOP;
        int used = 0;
        for (List<Line> block : blocks) {
            int height = block.stream().mapToInt(Line::height).sum();
            if (used + height > room && !current.isEmpty() && height <= BOTTOM - TOP) {
                List<Line> done = current;
                views.add((graphics, x, y, h) -> drawText(graphics, done, x, y, h));
                current = new ArrayList<>();
                used = 0;
                room = BOTTOM - TOP;
                if (block.get(0).gap()) continue;
            }
            for (Line line : block) {
                if (used + line.height() > room && !current.isEmpty()) {
                    List<Line> done = current;
                    views.add((graphics, x, y, h) -> drawText(graphics, done, x, y, h));
                    current = new ArrayList<>();
                    used = 0;
                    room = BOTTOM - TOP;
                }
                current.add(line);
                used += line.height();
            }
        }
        List<Line> done = current;
        views.add((graphics, x, y, h) -> drawText(graphics, done, x, y, h));
    }

    private View spotlight(GuideBook.Page page) {
        ItemStack item = stack(page.item());
        List<Line> text = wrap(page.text(), PAGE_W);
        Component name = page.title().isEmpty() ? item.getHoverName() : Component.literal(page.title());
        return (graphics, x, y, height) -> {
            int cx = x + PAGE_W / 2 - 16;
            graphics.fill(cx - 4, y, cx + 36, y + 40, 0x22000000);
            McClient.push(graphics);
            McClient.translate(graphics, cx, y + 4, 0);
            McClient.scale(graphics, 2.0F, 2.0F, 1.0F);
            graphics.renderItem(item, 0, 0);
            McClient.pop(graphics);
            hits.add(new Hit(cx - 4, y, 40, 40, null, item, null));
            int ty = centred(graphics, name.copy().withStyle(ChatFormatting.BOLD), x, y + 44, ACCENT, 1.0F);
            drawText(graphics, text, x, ty + 3, height - (ty - y) - 3);
        };
    }

    private View crafting(GuideBook.Page page) {
        List<Line> text = wrap(page.text(), PAGE_W);
        ResourceLocation id = ResourceLocation.tryParse(page.recipe());
        return (graphics, x, y, height) -> {
            Grid grid = id == null || minecraft == null || minecraft.level == null ? null : grid(id, minecraft.level.registryAccess());
            if (grid == null) {
                graphics.drawString(font, Component.literal("? " + page.recipe()), x, y, 0xFFAA2222, false);
                return;
            }
            ItemStack result = grid.result();
            // the grid is headed by what it makes, as in Patchouli
            Component heading = page.title().isEmpty() ? result.getHoverName() : Component.literal(page.title());
            y = centred(graphics, heading.copy().withStyle(ChatFormatting.BOLD), x, y, TITLE, 1.0F) + 2;
            int gridX = x + 6;
            List<List<ItemStack>> ingredients = grid.cells();
            int columns = grid.columns();
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    int sx = gridX + column * 18;
                    int sy = y + row * 18;
                    McClient.blit(graphics, ATLAS, sx, sy, 430, 40, 18, 18, ATLAS_W, ATLAS_H);
                    int index = row * columns + column;
                    if (column >= columns || index >= ingredients.size()) continue;
                    ItemStack shown = cycle(ingredients.get(index));
                    if (shown.isEmpty()) continue;
                    graphics.renderItem(shown, sx + 1, sy + 1);
                    hits.add(new Hit(sx + 1, sy + 1, 16, 16, linkFor(shown), shown, null));
                }
            }
            McClient.blit(graphics, ATLAS, gridX + 58, y + 19, 452, 40, 22, 15, ATLAS_W, ATLAS_H);
            int rx = gridX + 84;
            McClient.blit(graphics, ATLAS, rx, y + 17, 430, 40, 18, 18, ATLAS_W, ATLAS_H);
            graphics.renderItem(result, rx + 1, y + 18);
            graphics.renderItemDecorations(font, result, rx + 1, y + 18);
            hits.add(new Hit(rx + 1, y + 18, 16, 16, null, result, null));
            drawText(graphics, text, x, y + 60, height - 60);
        };
    }

    private View image(GuideBook.Page page) {
        ResourceLocation texture = ResourceLocation.tryParse(page.image());
        List<Line> caption = wrap(page.text(), PAGE_W);
        return (graphics, x, y, height) -> {
            if (texture == null) return;
            if (!page.title().isEmpty()) y = centred(graphics, Component.literal(page.title()).withStyle(ChatFormatting.BOLD), x, y, TITLE, 1.0F) + 2;
            int w = PAGE_W;
            int h = Math.round(PAGE_W * page.imageHeight() / (float) page.imageWidth());
            graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF6E4A28);
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFF3E7C6);
            McClient.blit(graphics, texture, x, y, w, h, 0, 0, page.imageWidth(), page.imageHeight(), page.imageWidth(), page.imageHeight());
            drawText(graphics, caption, x, y + h + 5, height - h - 5);
        };
    }

    private View items(GuideBook.Page page) {
        List<ItemStack> stacks = page.items().stream().map(GuideScreen::stack).toList();
        List<Line> text = wrap(page.text(), PAGE_W);
        return (graphics, x, y, height) -> {
            if (!page.title().isEmpty()) y = centred(graphics, Component.literal(page.title()).withStyle(ChatFormatting.BOLD), x, y, TITLE, 1.0F) + 2;
            int perRow = PAGE_W / 19;
            for (int i = 0; i < stacks.size(); i++) {
                int sx = x + (i % perRow) * 19;
                int sy = y + (i / perRow) * 19;
                McClient.blit(graphics, ATLAS, sx, sy, 430, 40, 18, 18, ATLAS_W, ATLAS_H);
                graphics.renderItem(stacks.get(i), sx + 1, sy + 1);
                hits.add(new Hit(sx + 1, sy + 1, 16, 16, linkFor(stacks.get(i)), stacks.get(i), null));
            }
            int rows = (stacks.size() + perRow - 1) / perRow;
            drawText(graphics, text, x, y + rows * 19 + 4, height - rows * 19 - 4);
        };
    }

    private View links(GuideBook.Page page) {
        return (graphics, x, y, height) -> {
            if (!page.title().isEmpty()) {
                y = centred(graphics, Component.literal(page.title()).withStyle(ChatFormatting.BOLD), x, y, TITLE, 1.0F);
                sprig(graphics, x, y);
                y += 11;
            }
            for (String id : page.links()) {
                GuideBook.Entry target = book.entries.get(id);
                if (target == null) continue;
                row(graphics, x, y, target.icon(), Component.literal(target.title()), !progress.read(target.id()), () -> openEntry(target));
                y += ROW;
            }
        };
    }

    private View structure(GuideBook.Page page) {
        // "mega:<kind>": a megastructure's plan, drawn from the code that checks it (mega/Shape)
        SchematicSpec spec = page.schematic().startsWith("mega:") ? com.vnap.mega.Shape.of(page.schematic().substring(5)).spec()
                : SchematicSpec.loadBundled(page.schematic());
        List<Line> caption = wrap(page.text(), PAGE_W);
        return (graphics, x, y, height) -> {
            if (!page.title().isEmpty()) y = centred(graphics, Component.literal(page.title()).withStyle(ChatFormatting.BOLD), x, y, TITLE, 1.0F) + 2;
            int size = 84;
            graphics.fill(x + (PAGE_W - size) / 2 - 1, y - 1, x + (PAGE_W + size) / 2 + 1, y + size + 1, 0x22000000);
            SchematicPreview.render(graphics, spec, x + (PAGE_W - size) / 2, y, size, size, SchematicPreview.animatedLayers(spec));
            drawText(graphics, caption, x, y + size + 5, height - size - 5);
        };
    }

    private View screenPage(GuideBook.Page page) {
        List<Line> text = wrap(page.text(), PAGE_W);
        return (graphics, x, y, height) -> {
            int end = drawText(graphics, text, x, y, height - 24);
            Component label = Component.literal(page.title().isEmpty() ? "→" : page.title());
            int bw = Math.min(PAGE_W, font.width(label) + 16);
            int bx = x + (PAGE_W - bw) / 2;
            int by = end + 6;
            graphics.fill(bx, by, bx + bw, by + 16, 0xFF6E4A28);
            graphics.fill(bx + 1, by + 1, bx + bw - 1, by + 15, 0xFFF3E7C6);
            graphics.drawString(font, label, bx + (bw - font.width(label)) / 2, by + 4, ACCENT, false);
            hits.add(new Hit(bx, by, bw, 16, () -> openOld(page.screen()), null, null));
        };
    }

    private void openOld(String which) {
        remember(state);
        com.vnap.client.McClient.setScreen(which.equals("settings") ? new com.vnap.client.SettingsScreen(this) : new com.vnap.client.ReactionsScreen(this));
    }

    private @Nullable Runnable linkFor(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        GuideBook.Entry target = book.byItem.get(id);
        if (target == null || (state.kind() == Kind.ENTRY && target.id().equals(state.id()))) return null;
        return () -> openEntry(target);
    }

    private static ItemStack cycle(List<ItemStack> options) {
        if (options.isEmpty()) return ItemStack.EMPTY;
        return options.get((int) ((System.currentTimeMillis() / 1200L) % options.size()));
    }

    /** A crafting recipe as the page draws it: what it makes, what lies in each cell (row by row), the grid's width. */
    private record Grid(ItemStack result, List<List<ItemStack>> cells, int columns) {
    }

    //? if >=1.21.2 {
    /*// From 1.21.2 a client has no recipes but those the server sends (VillagerLife asks it to send crafting).
    private static net.minecraft.world.item.crafting.RecipeMap recipes = net.minecraft.world.item.crafting.RecipeMap.EMPTY;

    public static void onRecipes(net.neoforged.neoforge.client.event.RecipesReceivedEvent event) {
        recipes = event.getRecipeMap();
    }

    private static @Nullable Grid grid(ResourceLocation id, net.minecraft.core.HolderLookup.Provider registries) {
        var holder = recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, id));
        if (holder == null) return null;
        List<List<ItemStack>> cells = new java.util.ArrayList<>();
        var nothing = net.minecraft.world.item.crafting.CraftingInput.EMPTY;
        if (holder.value() instanceof ShapedRecipe shaped) {
            for (java.util.Optional<Ingredient> ingredient : shaped.getIngredients()) cells.add(ingredient.map(GuideScreen::stacks).orElse(List.of()));
            return new Grid(com.vnap.Mc.assemble(shaped, nothing, registries), cells, shaped.getWidth());
        }
        if (holder.value() instanceof net.minecraft.world.item.crafting.ShapelessRecipe shapeless) {
            for (Ingredient ingredient : shapeless.placementInfo().ingredients()) cells.add(stacks(ingredient));
            return new Grid(com.vnap.Mc.assemble(shapeless, nothing, registries), cells, Math.min(3, Math.max(1, (int) Math.ceil(Math.sqrt(cells.size())))));
        }
        return null;
    }

    private static List<ItemStack> stacks(Ingredient ingredient) {
        return ingredient.getValues().stream().map(ItemStack::new).toList();
    }
    *///?} else {
    private @Nullable Grid grid(ResourceLocation id, net.minecraft.core.HolderLookup.Provider registries) {
        Recipe<?> recipe = minecraft == null || minecraft.level == null ? null
                : minecraft.level.getRecipeManager().byKey(id).map(holder -> (Recipe<?>) holder.value()).orElse(null);
        if (recipe == null) return null;
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        List<List<ItemStack>> cells = ingredients.stream().map(ingredient -> List.of(ingredient.getItems())).toList();
        int columns = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : Math.min(3, Math.max(1, (int) Math.ceil(Math.sqrt(ingredients.size()))));
        return new Grid(recipe.getResultItem(registries), cells, columns);
    }
    //?}

    static ItemStack stack(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    // ---------------------------------------------------------------------------------- text

    /** A wrapped line; {@code start} marks the first line of a paragraph, a list item or a blank line. */
    private record Line(FormattedCharSequence text, int indent, boolean gap, boolean start, boolean bullet) {
        Line(FormattedCharSequence text, int indent, boolean gap, boolean start) {
            this(text, indent, gap, start, false);
        }

        int height() {
            return gap ? 4 : 10;
        }
    }

    /**
     * The guide's light markup: a blank line starts a paragraph, "- " a list item, **bold**, [[term]] in the accent
     * colour, //italic//.
     */
    private List<Line> wrap(String text, int width) {
        List<Line> out = new ArrayList<>();
        if (text.isEmpty()) return out;
        for (String raw : text.split("\n", -1)) {
            String paragraph = raw.stripTrailing();
            if (paragraph.isBlank()) {
                out.add(new Line(FormattedCharSequence.EMPTY, 0, true, true));
                continue;
            }
            boolean bullet = paragraph.startsWith("- ");
            Component styled = markup(bullet ? paragraph.substring(2) : paragraph);
            int indent = bullet ? 8 : 0;
            List<FormattedCharSequence> lines = font.split(styled, width - indent);
            for (int i = 0; i < lines.size(); i++) {
                out.add(new Line(lines.get(i), indent, false, i == 0, bullet && i == 0));
            }
        }
        return out;
    }

    private static Component markup(String text) {
        MutableComponent out = Component.empty();
        boolean bold = false;
        boolean accent = false;
        boolean italic = false;
        StringBuilder run = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            String two = i + 1 < text.length() ? text.substring(i, i + 2) : "";
            boolean toggle = two.equals("**") || two.equals("[[") || two.equals("]]") || two.equals("//");
            if (!toggle) {
                run.append(text.charAt(i));
                i++;
                continue;
            }
            out.append(Component.literal(run.toString()).withStyle(style(bold, accent, italic)));
            run.setLength(0);
            switch (two) {
                case "**" -> bold = !bold;
                case "[[" -> accent = true;
                case "]]" -> accent = false;
                default -> italic = !italic;
            }
            i += 2;
        }
        out.append(Component.literal(run.toString()).withStyle(style(bold, accent, italic)));
        return out;
    }

    private static Style style(boolean bold, boolean accent, boolean italic) {
        Style style = Style.EMPTY.withBold(bold).withItalic(italic);
        return accent ? style.withColor(ACCENT) : style;
    }

    private int drawText(GuiGraphics graphics, List<Line> lines, int x, int y, int height) {
        int used = 0;
        for (Line line : lines) {
            if (used + line.height() > height + 1) break;
            if (line.bullet()) McClient.blit(graphics, ATLAS, x - 1, y + used, 478, 40, 7, 7, ATLAS_W, ATLAS_H);
            if (!line.gap()) graphics.drawString(font, line.text(), x + line.indent(), y + used, INK, false);
            used += line.height();
        }
        return y + used;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
