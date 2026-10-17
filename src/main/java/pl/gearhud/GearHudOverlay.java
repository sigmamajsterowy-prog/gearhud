package pl.gearhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GearHudOverlay {
    private static final int W = 132, H = 50, GAP = 4, MARGIN = 6;
    private static final long ANIM = 320;
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final class Entry {
        UUID id;
        boolean visible;
        long appear, leave;
        String name = "";
        float dist, health, maxHealth;
        final ItemStack[] armor = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        ItemStack weapon = ItemStack.EMPTY;
        boolean weaponIsSword;
        SkinTextures skin;
    }

    private static final Map<UUID, Entry> ENTRIES = new LinkedHashMap<>();

    private GearHudOverlay() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;
        if (mc.player == null || world == null || mc.options.hudHidden || !GearHudClient.enabled) {
            ENTRIES.clear();
            return;
        }

        final long now = System.currentTimeMillis();
        boolean allowed = !GearHudClient.netherOnly || world.getRegistryKey() == World.NETHER;
        Set<UUID> seen = new HashSet<>();

        if (allowed) {
            List<AbstractClientPlayerEntity> near = new ArrayList<>();
            for (AbstractClientPlayerEntity p : world.getPlayers()) {
                if (p == mc.player || p.isSpectator()) continue;
                if (mc.player.distanceTo(p) <= GearHudClient.range) near.add(p);
            }
            near.sort(Comparator.comparingDouble((AbstractClientPlayerEntity p) -> mc.player.distanceTo(p)));

            int limit = Math.min(near.size(), GearHudClient.maxCards);
            for (int i = 0; i < limit; i++) {
                AbstractClientPlayerEntity p = near.get(i);
                Entry e = ENTRIES.computeIfAbsent(p.getUuid(), k -> {
                    Entry n = new Entry();
                    n.id = k;
                    n.visible = true;
                    n.appear = now;
                    return n;
                });
                if (!e.visible) { // gracz wrocil w trakcie animacji znikania
                    float prog = progress(e, now);
                    e.appear = now - (long) (prog * ANIM);
                    e.visible = true;
                }
                seen.add(e.id);

                e.name = p.getName().getString();
                e.dist = mc.player.distanceTo(p);
                e.health = p.getHealth();
                e.maxHealth = Math.max(1f, p.getMaxHealth());
                for (int s = 0; s < 4; s++) e.armor[s] = p.getEquippedStack(ARMOR[s]).copy();
                ItemStack main = p.getMainHandStack();
                ItemStack off = p.getOffHandStack();
                if (main.isIn(ItemTags.SWORDS)) { e.weapon = main.copy(); e.weaponIsSword = true; }
                else if (off.isIn(ItemTags.SWORDS)) { e.weapon = off.copy(); e.weaponIsSword = true; }
                else { e.weapon = main.copy(); e.weaponIsSword = false; }
                e.skin = p.getSkinTextures();
            }
        }

        for (Entry e : ENTRIES.values()) {
            if (e.visible && !seen.contains(e.id)) {
                e.visible = false;
                e.leave = now;
            }
        }
        ENTRIES.values().removeIf(e -> !e.visible && progress(e, now) <= 0f);
        if (ENTRIES.isEmpty()) return;

        List<Entry> list = new ArrayList<>(ENTRIES.values());
        list.sort(Comparator.<Entry>comparingInt(e -> e.visible ? 0 : 1).thenComparingDouble(e -> e.dist));

        int accent = world.getRegistryKey() == World.NETHER ? 0xFFFF6A3D
                : world.getRegistryKey() == World.END ? 0xFFC084FC
                : 0xFF4ADE80;

        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            float t = ease(progress(e, now));
            int x = sw - MARGIN - W + Math.round((1f - t) * (W + MARGIN + 10));
            int y = sh - MARGIN - (i + 1) * (H + GAP) + GAP;
            drawCard(ctx, tr, e, x, y, accent);
        }
    }

    private static float progress(Entry e, long now) {
        float p = e.visible ? (now - e.appear) / (float) ANIM : 1f - (now - e.leave) / (float) ANIM;
        return Math.max(0f, Math.min(1f, p));
    }

    private static float ease(float p) {
        float inv = 1f - p;
        return 1f - inv * inv * inv;
    }

    private static void drawCard(DrawContext ctx, TextRenderer tr, Entry e, int x, int y, int accent) {
        int top = 0xE01B1B2B, bottom = 0xE00D0D16;
        // tlo z zaokraglonymi rogami
        ctx.fillGradient(x + 1, y, x + W - 1, y + H, top, bottom);
        ctx.fill(x, y + 1, x + 1, y + H - 1, top);
        ctx.fill(x + W - 1, y + 1, x + W, y + H - 1, bottom);
        // akcent
        ctx.fill(x, y + 2, x + 2, y + H - 2, accent);
        ctx.fill(x + 3, y, x + W - 3, y + 1, (accent & 0x00FFFFFF) | 0x90000000);

        // glowa
        ctx.fill(x + 5, y + 5, x + 23, y + 23, (accent & 0x00FFFFFF) | 0xA0000000);
        if (e.skin != null) PlayerSkinDrawer.draw(ctx, e.skin, x + 6, y + 6, 16);

        // nick + dystans
        String dist = Math.round(e.dist) + "m";
        int distW = tr.getWidth(dist);
        String name = tr.trimToWidth(e.name, W - 26 - 6 - distW - 6);
        ctx.drawText(tr, name, x + 26, y + 6, 0xFFFFFFFF, true);
        ctx.drawText(tr, dist, x + W - 6 - distW, y + 6, 0xFF9CA3AF, true);

        // zycie
        float frac = Math.max(0f, Math.min(1f, e.health / e.maxHealth));
        ctx.drawText(tr, "\u2665 " + (int) Math.ceil(e.health), x + 26, y + 17, 0xFFFF5555, true);
        int bx = x + 60, by = y + 19, bw = W - 60 - 6;
        ctx.fill(bx, by, bx + bw, by + 4, 0xA0000000);
        int r = (int) (255 * (1f - frac));
        int g = (int) (200 * frac + 40);
        ctx.fill(bx, by, bx + Math.round(bw * frac), by + 4, 0xFF000000 | (r << 16) | (g << 8) | 0x3C);

        // set + miecz
        int sy = y + 28;
        for (int i = 0; i < 4; i++) {
            drawSlot(ctx, e.armor[i], x + 6 + i * 20, sy, false, accent);
        }
        ctx.fill(x + 96, sy, x + 97, sy + 18, 0x40FFFFFF);
        drawSlot(ctx, e.weapon, x + 108, sy, e.weaponIsSword, accent);
    }

    private static void drawSlot(DrawContext ctx, ItemStack stack, int sx, int sy, boolean highlight, int accent) {
        ctx.fill(sx, sy, sx + 18, sy + 18, stack.isEmpty() ? 0x40000000 : 0x90000000);
        if (highlight) {
            ctx.fill(sx, sy, sx + 18, sy + 1, accent);
            ctx.fill(sx, sy + 17, sx + 18, sy + 18, accent);
            ctx.fill(sx, sy, sx + 1, sy + 18, accent);
            ctx.fill(sx + 17, sy, sx + 18, sy + 18, accent);
        }
        if (stack.isEmpty()) return;

        ctx.drawItem(stack, sx + 1, sy + 1);

        if (stack.isItemBarVisible()) {
            int ix = sx + 1, iy = sy + 1;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0f, 0f, 200f);
            ctx.fill(ix + 2, iy + 13, ix + 15, iy + 15, 0xFF000000);
            ctx.fill(ix + 2, iy + 13, ix + 2 + stack.getItemBarStep(), iy + 14, 0xFF000000 | stack.getItemBarColor());
            ctx.getMatrices().pop();
        }
    }
}
