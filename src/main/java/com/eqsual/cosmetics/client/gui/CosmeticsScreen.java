package com.eqsual.cosmetics.client.gui;

import com.eqsual.cosmetics.EqsualCosmetics;
import com.eqsual.cosmetics.client.ClientCosmetics;
import com.eqsual.cosmetics.client.CosmeticConfig;
import com.eqsual.cosmetics.client.EqsualCosmeticsClient;
import com.eqsual.cosmetics.cosmetic.CosmeticDef;
import com.eqsual.cosmetics.cosmetic.CosmeticRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The cosmetics menu: rotating 3D preview, searchable/filterable grid, favourites, equip/unequip,
 * equipped list and visual toggles. Every cosmetic here is free and always available.
 */
public class CosmeticsScreen extends Screen {
    private static final Identifier ICONS = Identifier.fromNamespaceAndPath(EqsualCosmetics.MOD_ID, "textures/gui/icons.png");

    // palette
    private static final int BG = 0xF0101116;
    private static final int PANEL = 0xFF181920;
    private static final int PANEL_2 = 0xFF1F2029;
    private static final int LINE = 0xFF2C2E3A;
    private static final int TEXT = 0xFFE9E9F0;
    private static final int MUTED = 0xFF8C8FA3;
    private static final int ACCENT = 0xFFE23B4A;   // red, matching the red & black crown
    private static final int GOLD = 0xFFF2C14E;
    private static final int GREEN = 0xFF4CD18A;

    private static final String FILTER_ALL = "All";
    private static final String FILTER_FAV = "★ Favorites";
    private static final String FILTER_EQUIPPED = "Equipped";

    private static final int CARD_W = 58, CARD_H = 56, GAP = 4;

    private static String lastFilter = FILTER_ALL;
    private static String lastSearch = "";
    private static float lastSpin = 200f;

    private int px, py, pw, ph;              // outer panel
    private int prevX, prevY, prevW, prevH;  // preview box
    private int gridX, gridY, gridW, gridH;  // grid viewport
    private int infoY;                        // details bar top
    private int cols = 1;

    private EditBox search;
    private Button equipButton, favButton;
    private final List<Chip> chips = new ArrayList<>();
    private final List<CosmeticDef> visible = new ArrayList<>();
    private String filter = lastFilter;
    private CosmeticDef selected;
    private double scroll;
    private float spin = lastSpin, tilt = 0.3f;
    private boolean draggingPreview, draggingScrollbar;
    private long lastInteraction;

    public CosmeticsScreen() {
        super(Component.translatable("screen.eqsual_cosmetics.title"));
    }

    private static CosmeticConfig cfg() { return ClientCosmetics.config(); }

    // ------------------------------------------------------------------------------------------------ layout

    @Override
    protected void init() {
        pw = Math.min(this.width - 16, 480);
        ph = Math.min(this.height - 16, 300);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        int leftW = Math.max(110, Math.min(140, pw / 4 + 10));
        prevX = px + 8;
        prevY = py + 26;
        prevW = leftW;
        prevH = ph - 26 - 8 - 62;

        int rx = prevX + prevW + 8;
        int rw = px + pw - 8 - rx;

        // header toggles
        int tx = px + pw - 8;
        tx = addToggle(tx, "Others", () -> cfg().showOthers, v -> cfg().showOthers = v,
                "Show cosmetics worn by other players");
        tx = addToggle(tx, "FX", () -> cfg().particles, v -> cfg().particles = v, "Particles");
        tx = addToggle(tx, "Anim", () -> cfg().animations, v -> cfg().animations = v, "Animations");
        addToggle(tx, "Glow", () -> cfg().glow, v -> cfg().glow = v, "Glowing parts");

        // search box
        search = new EditBox(this.font, rx + 1, py + 26, rw - 2, 16, Component.translatable("screen.eqsual_cosmetics.search"));
        search.setHint(Component.literal("Search cosmetics…").withColor(MUTED));
        search.setMaxLength(48);
        search.setValue(lastSearch);
        search.setResponder(s -> { lastSearch = s; scroll = 0; refresh(); });
        addRenderableWidget(search);

        // filter chips (wrap onto several lines if needed)
        chips.clear();
        List<String> filters = new ArrayList<>(List.of(FILTER_ALL, FILTER_FAV, FILTER_EQUIPPED));
        filters.addAll(CosmeticRegistry.categories());
        int cx = rx, cy = py + 46;
        for (String f : filters) {
            int w = this.font.width(f) + 10;
            if (cx + w > rx + rw) { cx = rx; cy += 14; }
            chips.add(new Chip(f, cx, cy, w, 12));
            cx += w + 3;
        }

        infoY = py + ph - 8 - 40;
        gridX = rx;
        gridY = cy + 16;
        gridW = rw;
        gridH = infoY - 4 - gridY;
        cols = Math.max(1, (gridW - 6 + GAP) / (CARD_W + GAP));

        // details bar buttons
        int by = infoY + 20;
        favButton = addRenderableWidget(Button.builder(Component.literal("☆"), b -> {
            if (selected != null) ClientCosmetics.toggleFavorite(selected);
            refresh();
        }).bounds(px + pw - 8 - 20, by, 20, 16).build());
        equipButton = addRenderableWidget(Button.builder(Component.literal("Equip"), b -> {
            if (selected == null) return;
            if (ClientCosmetics.isEquipped(selected)) ClientCosmetics.unequip(selected);
            else ClientCosmetics.equip(selected);
            refresh();
        }).bounds(px + pw - 8 - 20 - 4 - 70, by, 70, 16).build());

        // left column buttons
        addRenderableWidget(Button.builder(Component.literal("Unequip all"), b -> {
            ClientCosmetics.unequipAll();
            refresh();
        }).bounds(prevX, py + ph - 8 - 16, prevW, 16).build());

        if (selected == null && !ClientCosmetics.equipped().isEmpty()) selected = ClientCosmetics.equipped().get(0);
        refresh();
    }

    private int addToggle(int right, String label, BooleanSupplier get, Consumer<Boolean> set, String tip) {
        int w = Math.max(34, this.font.width(label + " ●") + 10);
        int x = right - w;
        Button b = Button.builder(toggleText(label, get.getAsBoolean()), btn -> {
            set.accept(!get.getAsBoolean());
            cfg().save();
            btn.setMessage(toggleText(label, get.getAsBoolean()));
        }).bounds(x, py + 5, w, 16).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tip))).build();
        addRenderableWidget(b);
        return x - 3;
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + " ").append(Component.literal("●").withColor(on ? GREEN : 0xFF6B6E80));
    }

    private void refresh() {
        visible.clear();
        String q = search == null ? "" : search.getValue();
        List<CosmeticDef> favs = new ArrayList<>(), rest = new ArrayList<>();
        for (CosmeticDef d : CosmeticRegistry.all()) {
            if (!d.matches(q)) continue;
            boolean ok = switch (filter) {
                case FILTER_ALL -> true;
                case FILTER_FAV -> ClientCosmetics.isFavorite(d);
                case FILTER_EQUIPPED -> ClientCosmetics.isEquipped(d);
                default -> d.categories().contains(filter);
            };
            if (!ok) continue;
            (ClientCosmetics.isFavorite(d) ? favs : rest).add(d);
        }
        visible.addAll(favs);   // favourites float to the top
        visible.addAll(rest);
        clampScroll();
        updateButtons();
    }

    private void updateButtons() {
        if (equipButton == null) return;
        equipButton.active = selected != null;
        favButton.active = selected != null;
        boolean eq = selected != null && ClientCosmetics.isEquipped(selected);
        equipButton.setMessage(Component.literal(eq ? "Unequip" : "Equip"));
        boolean fav = selected != null && ClientCosmetics.isFavorite(selected);
        favButton.setMessage(Component.literal(fav ? "★" : "☆").withColor(fav ? GOLD : TEXT));
    }

    private int contentHeight() {
        int rows = (visible.size() + cols - 1) / cols;
        return rows * (CARD_H + GAP);
    }

    private void clampScroll() {
        double max = Math.max(0, contentHeight() - gridH);
        scroll = Mth.clamp(scroll, 0, max);
    }

    // ------------------------------------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (cfg().autoRotate && !draggingPreview && System.currentTimeMillis() - lastInteraction > 2500) {
            spin = (spin + partialDelta() * 1.2f) % 360f;
        }

        // frame
        g.fill(px, py, px + pw, py + ph, BG);
        outline(g, px, py, pw, ph, LINE);
        g.fill(px, py, px + pw, py + 1, ACCENT);
        g.drawString(this.font, Component.literal("♛ ").withColor(ACCENT).append(Component.literal("Eqsual Cosmetics").withColor(TEXT)), px + 8, py + 9, TEXT, true);
        String sub = "All free · " + CosmeticRegistry.all().size() + " cosmetics";
        g.drawString(this.font, sub, px + 8 + this.font.width("♛ Eqsual Cosmetics") + 8, py + 9, MUTED, false);

        renderPreview(g, mouseX, mouseY, partial);
        renderEquipped(g, mouseX, mouseY);
        renderChips(g, mouseX, mouseY);
        renderGrid(g, mouseX, mouseY);
        renderDetails(g);

        super.render(g, mouseX, mouseY, partial);
    }

    private long lastFrame = System.nanoTime();

    private float partialDelta() {
        long now = System.nanoTime();
        float dt = (now - lastFrame) / 50_000_000f; // in ticks
        lastFrame = now;
        return Math.min(dt, 4f);
    }

    private void renderPreview(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fillGradient(prevX, prevY, prevX + prevW, prevY + prevH, 0xFF262838, 0xFF14151C);
        outline(g, prevX, prevY, prevW, prevH, LINE);
        // floor glow
        int fy = prevY + prevH - 22;
        g.fill(prevX + prevW / 2 - 30, fy, prevX + prevW / 2 + 30, fy + 2, 0x40E23B4A);

        LocalPlayer player = this.minecraft.player;
        if (player != null) {
            List<CosmeticDef> loadout = new ArrayList<>(ClientCosmetics.equipped());
            if (selected != null && !ClientCosmetics.isEquipped(selected)) {   // try-on preview
                loadout.removeIf(d -> d.slot() == selected.slot());
                loadout.add(selected);
            }
            ClientCosmetics.setPreviewLoadout(loadout);
            renderPlayer(g, player, prevX + 1, prevY + 1, prevX + prevW - 1, prevY + prevH - 12, partial);
        }
        String hint = "Drag to rotate";
        g.drawCenteredString(this.font, hint, prevX + prevW / 2, prevY + prevH - 10, 0xFF5D6074);
    }

    private void renderPlayer(GuiGraphics g, LocalPlayer player, int x1, int y1, int x2, int y2, float partial) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LocalPlayer, ?> renderer = dispatcher.getRenderer(player);
        EntityRenderState state = renderer.createRenderState(player, partial);
        state.lightCoords = 15728880;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0F + spin;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1.0F;
        }
        if (state instanceof AvatarRenderState avatar) {
            avatar.id = ClientCosmetics.PREVIEW_ENTITY_ID;
            avatar.ageInTicks = player.tickCount + partial;
        }
        int size = (int) ((y2 - y1) / 2.75f);
        Quaternionf camera = new Quaternionf().rotateX(tilt);
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
        Vector3f offset = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
        g.submitEntityRenderState(state, size, offset, rotation, camera, x1, y1, x2, y2);
    }

    private void renderEquipped(GuiGraphics g, int mouseX, int mouseY) {
        int y = prevY + prevH + 6;
        g.drawString(this.font, "EQUIPPED", prevX, y, MUTED, false);
        y += 11;
        List<CosmeticDef> eq = ClientCosmetics.equipped();
        if (eq.isEmpty()) {
            g.drawString(this.font, "Nothing yet", prevX, y + 1, 0xFF5D6074, false);
            return;
        }
        for (CosmeticDef d : eq) {
            boolean hover = mouseX >= prevX && mouseX < prevX + prevW && mouseY >= y && mouseY < y + 11;
            g.fill(prevX, y, prevX + prevW, y + 11, hover ? PANEL_2 : PANEL);
            g.fill(prevX, y, prevX + 2, y + 11, GREEN);
            String name = this.font.plainSubstrByWidth(d.name(), prevW - 22);
            g.drawString(this.font, name, prevX + 5, y + 2, TEXT, false);
            g.drawString(this.font, "×", prevX + prevW - 8, y + 2, hover ? ACCENT : MUTED, false);
            y += 12;
        }
    }

    private void renderChips(GuiGraphics g, int mouseX, int mouseY) {
        for (Chip c : chips) {
            boolean active = c.label.equals(filter);
            boolean hover = c.contains(mouseX, mouseY);
            g.fill(c.x, c.y, c.x + c.w, c.y + c.h, active ? ACCENT : (hover ? PANEL_2 : PANEL));
            if (!active) outline(g, c.x, c.y, c.w, c.h, LINE);
            g.drawCenteredString(this.font, c.label, c.x + c.w / 2, c.y + 2, active ? 0xFFFFFFFF : (hover ? TEXT : MUTED));
        }
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(gridX, gridY, gridX + gridW, gridY + gridH, 0x60000000);
        if (visible.isEmpty()) {
            String msg = filter.equals(FILTER_FAV) ? "No favorites yet — click ☆ on a cosmetic" : "No cosmetics match";
            g.drawCenteredString(this.font, msg, gridX + gridW / 2, gridY + gridH / 2 - 4, MUTED);
            return;
        }
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        int totalW = cols * (CARD_W + GAP) - GAP;
        int ox = gridX + (gridW - 6 - totalW) / 2;
        for (int i = 0; i < visible.size(); i++) {
            CosmeticDef d = visible.get(i);
            int cx = ox + (i % cols) * (CARD_W + GAP);
            int cy = gridY + 3 + (i / cols) * (CARD_H + GAP) - (int) scroll;
            if (cy + CARD_H < gridY || cy > gridY + gridH) continue;
            boolean hover = inGrid(mouseX, mouseY) && mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H;
            boolean sel = d == selected;
            boolean eq = ClientCosmetics.isEquipped(d);
            g.fill(cx, cy, cx + CARD_W, cy + CARD_H, hover ? PANEL_2 : PANEL);
            outline(g, cx, cy, CARD_W, CARD_H, sel ? ACCENT : (eq ? GREEN : LINE));
            if (sel) outline(g, cx + 1, cy + 1, CARD_W - 2, CARD_H - 2, 0x80E23B4A);
            int icon = d.icon();
            g.blit(RenderPipelines.GUI_TEXTURED, ICONS, cx + (CARD_W - 32) / 2, cy + 5, (icon % 8) * 32f, (icon / 8) * 32f, 32, 32, 256, 256);
            String name = this.font.plainSubstrByWidth(d.name(), CARD_W - 4);
            g.drawCenteredString(this.font, name, cx + CARD_W / 2, cy + CARD_H - 13, eq ? GREEN : TEXT);
            boolean fav = ClientCosmetics.isFavorite(d);
            boolean starHover = hover && mouseX >= cx + CARD_W - 12 && mouseY < cy + 12;
            if (fav || hover) g.drawString(this.font, fav ? "★" : "☆", cx + CARD_W - 9, cy + 3, fav ? GOLD : (starHover ? GOLD : MUTED), false);
            if (eq) g.drawString(this.font, "✔", cx + 3, cy + 3, GREEN, false);
        }
        g.disableScissor();

        // scrollbar
        int ch = contentHeight();
        if (ch > gridH) {
            int trackX = gridX + gridW - 4;
            g.fill(trackX, gridY + 2, trackX + 2, gridY + gridH - 2, 0x40FFFFFF);
            int thumbH = Math.max(14, (int) ((gridH - 4) * (gridH / (float) ch)));
            int thumbY = gridY + 2 + (int) ((gridH - 4 - thumbH) * (scroll / (ch - gridH)));
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, draggingScrollbar ? ACCENT : 0xFFB0B3C6);
        }
    }

    private void renderDetails(GuiGraphics g) {
        int x = gridX, w = gridW;
        g.fill(x, infoY, x + w, infoY + 40, PANEL);
        outline(g, x, infoY, w, 40, LINE);
        if (selected == null) {
            g.drawString(this.font, "Select a cosmetic to preview it", x + 6, infoY + 6, MUTED, false);
            g.drawString(this.font, "Double-click a card to equip instantly", x + 6, infoY + 18, 0xFF5D6074, false);
            return;
        }
        int textW = w - 110;
        boolean eq = ClientCosmetics.isEquipped(selected);
        g.drawString(this.font, Component.literal(selected.name()).withColor(TEXT)
                .append(Component.literal(eq ? "  ✔ Equipped" : "  · Previewing").withColor(eq ? GREEN : MUTED)), x + 6, infoY + 5, TEXT, false);
        String meta = selected.slot().label + " · " + String.join(", ", selected.categories()) + " · Free";
        g.drawString(this.font, this.font.plainSubstrByWidth(meta, textW), x + 6, infoY + 16, GOLD, false);
        g.drawString(this.font, this.font.plainSubstrByWidth(selected.description(), textW), x + 6, infoY + 27, MUTED, false);
        String sync = ClientCosmetics.serverSupported() ? "● Synced" : "● Local only";
        g.drawString(this.font, sync, x + w - 6 - this.font.width(sync), infoY + 5,
                ClientCosmetics.serverSupported() ? GREEN : 0xFFB0B3C6, false);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // ------------------------------------------------------------------------------------------------ input

    private boolean inGrid(double mx, double my) {
        return mx >= gridX && mx < gridX + gridW && my >= gridY && my < gridY + gridH;
    }

    private boolean inPreview(double mx, double my) {
        return mx >= prevX && mx < prevX + prevW && my >= prevY && my < prevY + prevH;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        if (event.button() != 0) return false;

        for (Chip c : chips) {
            if (c.contains(mx, my)) {
                filter = lastFilter = c.label;
                scroll = 0;
                refresh();
                click();
                return true;
            }
        }

        // equipped list: click to unequip
        int y = prevY + prevH + 17;
        for (CosmeticDef d : ClientCosmetics.equipped()) {
            if (mx >= prevX && mx < prevX + prevW && my >= y && my < y + 11) {
                ClientCosmetics.unequip(d);
                selected = d;
                refresh();
                click();
                return true;
            }
            y += 12;
        }

        if (inPreview(mx, my)) {
            draggingPreview = true;
            lastInteraction = System.currentTimeMillis();
            return true;
        }

        if (inGrid(mx, my)) {
            if (contentHeight() > gridH && mx >= gridX + gridW - 7) {
                draggingScrollbar = true;
                dragScrollTo(my);
                return true;
            }
            CosmeticDef d = cardAt(mx, my);
            if (d != null) {
                int i = visible.indexOf(d);
                int totalW = cols * (CARD_W + GAP) - GAP;
                int cx = gridX + (gridW - 6 - totalW) / 2 + (i % cols) * (CARD_W + GAP);
                int cy = gridY + 3 + (i / cols) * (CARD_H + GAP) - (int) scroll;
                if (mx >= cx + CARD_W - 12 && my < cy + 12) {
                    ClientCosmetics.toggleFavorite(d);
                } else if (doubleClick && d == selected) {
                    if (ClientCosmetics.isEquipped(d)) ClientCosmetics.unequip(d); else ClientCosmetics.equip(d);
                } else {
                    selected = d;
                }
                refresh();
                click();
                return true;
            }
        }
        return false;
    }

    private CosmeticDef cardAt(double mx, double my) {
        int totalW = cols * (CARD_W + GAP) - GAP;
        int ox = gridX + (gridW - 6 - totalW) / 2;
        double lx = mx - ox, ly = my - (gridY + 3) + scroll;
        if (lx < 0 || ly < 0) return null;
        int col = (int) (lx / (CARD_W + GAP)), row = (int) (ly / (CARD_H + GAP));
        if (col >= cols || lx - col * (CARD_W + GAP) >= CARD_W || ly - row * (CARD_H + GAP) >= CARD_H) return null;
        int i = row * cols + col;
        return i >= 0 && i < visible.size() ? visible.get(i) : null;
    }

    private void dragScrollTo(double my) {
        int ch = contentHeight();
        double t = (my - gridY) / (double) gridH;
        scroll = t * (ch - gridH);
        clampScroll();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingPreview) {
            spin = (spin - (float) dx * 2.2f) % 360f;
            tilt = Mth.clamp(tilt + (float) dy * 0.012f, -0.6f, 0.9f);
            lastInteraction = System.currentTimeMillis();
            return true;
        }
        if (draggingScrollbar) {
            dragScrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingPreview = false;
        draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (inGrid(mx, my)) {
            scroll -= sy * 18;
            clampScroll();
            return true;
        }
        if (inPreview(mx, my)) {
            spin = (spin + (float) sy * 15f) % 360f;
            lastInteraction = System.currentTimeMillis();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Pressing the menu key again closes the menu (unless you're typing in the search box).
        if (!search.isFocused() && EqsualCosmeticsClient.isOpenKey(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void click() {
        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public void removed() {
        lastSpin = spin;
        ClientCosmetics.setPreviewLoadout(List.of());
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Chip(String label, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
}
