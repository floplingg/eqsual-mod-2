package com.eqsual.cosmetics.client;

import com.eqsual.cosmetics.cosmetic.CosmeticDef;
import com.eqsual.cosmetics.cosmetic.CosmeticRegistry;
import com.eqsual.cosmetics.network.CosmeticPayloads.SetCosmetics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Client-side cosmetic state.
 * <ul>
 *   <li>Your own loadout always comes from the local config, so it renders even on servers without the mod
 *       (client-side fallback).</li>
 *   <li>Other players' loadouts arrive from the server (when it runs the mod) and live in {@link #REMOTE}.</li>
 * </ul>
 */
public final class ClientCosmetics {
    /** Fake entity id used for the menu's 3D preview so the render layer can show "try-on" cosmetics. */
    public static final int PREVIEW_ENTITY_ID = Integer.MIN_VALUE + 7;

    private static final Map<UUID, List<CosmeticDef>> REMOTE = new ConcurrentHashMap<>();
    private static CosmeticConfig config;
    private static volatile List<CosmeticDef> previewLoadout = List.of();
    private static List<CosmeticDef> equippedDefs = List.of();

    private ClientCosmetics() { }

    public static void init() {
        config = CosmeticConfig.load();
        rebuildEquipped();
    }

    public static CosmeticConfig config() { return config; }

    // ---------------------------------------------------------------- own loadout

    public static List<CosmeticDef> equipped() { return equippedDefs; }

    public static boolean isEquipped(CosmeticDef def) { return config.equipped.contains(def.id()); }

    public static void equip(CosmeticDef def) {
        List<String> ids = new ArrayList<>(config.equipped);
        ids.removeIf(id -> {
            CosmeticDef other = CosmeticRegistry.get(id);
            return other == null || other.slot() == def.slot();
        });
        ids.add(def.id());
        setEquipped(ids);
    }

    public static void unequip(CosmeticDef def) {
        List<String> ids = new ArrayList<>(config.equipped);
        ids.remove(def.id());
        setEquipped(ids);
    }

    public static void unequipAll() { setEquipped(List.of()); }

    private static void setEquipped(List<String> ids) {
        config.equipped = CosmeticRegistry.sanitize(ids);
        config.save();
        rebuildEquipped();
        sendToServer();
    }

    private static void rebuildEquipped() {
        List<CosmeticDef> list = new ArrayList<>();
        for (String id : config.equipped) {
            CosmeticDef d = CosmeticRegistry.get(id);
            if (d != null) list.add(d);
        }
        equippedDefs = Collections.unmodifiableList(list);
    }

    // ---------------------------------------------------------------- favourites

    public static boolean isFavorite(CosmeticDef def) { return config.favorites.contains(def.id()); }

    public static void toggleFavorite(CosmeticDef def) {
        if (!config.favorites.remove(def.id())) config.favorites.add(def.id());
        config.save();
    }

    // ---------------------------------------------------------------- menu preview

    public static void setPreviewLoadout(List<CosmeticDef> loadout) { previewLoadout = List.copyOf(loadout); }

    // ---------------------------------------------------------------- networking

    /** True when the current server runs Eqsual Cosmetics and will relay our cosmetics to others. */
    public static boolean serverSupported() {
        try {
            return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(SetCosmetics.TYPE);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static void sendToServer() {
        if (serverSupported()) ClientPlayNetworking.send(new SetCosmetics(List.copyOf(config.equipped)));
    }

    public static void onRemoteUpdate(UUID player, List<String> ids) {
        List<CosmeticDef> defs = new ArrayList<>();
        for (String id : CosmeticRegistry.sanitize(ids)) defs.add(CosmeticRegistry.get(id));
        if (defs.isEmpty()) REMOTE.remove(player); else REMOTE.put(player, List.copyOf(defs));
    }

    public static void clearRemote() { REMOTE.clear(); }

    // ---------------------------------------------------------------- lookups used while rendering

    /** Cosmetics to draw for a rendered player, identified by entity id. */
    public static List<CosmeticDef> forEntityId(int entityId) {
        if (entityId == PREVIEW_ENTITY_ID) return previewLoadout;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        Entity e = mc.level.getEntity(entityId);
        if (e == null) return List.of();
        return forPlayer(e.getUUID());
    }

    public static List<CosmeticDef> forPlayer(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(uuid)) return equippedDefs;
        if (!config.showOthers) return List.of();
        List<CosmeticDef> remote = REMOTE.get(uuid);
        return remote == null ? List.of() : remote;
    }
}
