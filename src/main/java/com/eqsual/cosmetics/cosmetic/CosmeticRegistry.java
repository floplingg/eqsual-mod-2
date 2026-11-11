package com.eqsual.cosmetics.cosmetic;

import com.eqsual.cosmetics.EqsualCosmetics;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cosmetic catalogue, loaded from {@code /eqsual_cosmetics/cosmetics.json} inside the mod jar.
 * Shared by client (rendering/menu) and server (validating what players equip).
 */
public final class CosmeticRegistry {
    public static final String RESOURCE = "/eqsual_cosmetics/cosmetics.json";

    private static final Map<String, CosmeticDef> BY_ID = new LinkedHashMap<>();
    private static final List<String> CATEGORIES = new ArrayList<>();
    private static int atlasSize = 128;

    private CosmeticRegistry() { }

    public static void load() {
        if (!BY_ID.isEmpty()) return;
        try (InputStream in = CosmeticRegistry.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing " + RESOURCE);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            atlasSize = root.has("atlasSize") ? root.get("atlasSize").getAsInt() : 128;
            for (JsonElement e : root.getAsJsonArray("categories")) CATEGORIES.add(e.getAsString());
            for (JsonElement e : root.getAsJsonArray("cosmetics")) {
                JsonObject o = e.getAsJsonObject();
                CosmeticDef.ParticleSpec particles = null;
                if (o.has("particles") && o.get("particles").isJsonObject()) {
                    JsonObject p = o.getAsJsonObject("particles");
                    particles = new CosmeticDef.ParticleSpec(
                            p.get("type").getAsString(),
                            p.get("chance").getAsFloat(),
                            p.get("spread").getAsFloat(),
                            p.get("y").getAsFloat(),
                            p.get("vy").getAsFloat());
                }
                CosmeticDef def = new CosmeticDef(
                        o.get("id").getAsString(),
                        o.get("name").getAsString(),
                        o.has("description") ? o.get("description").getAsString() : "",
                        strings(o.getAsJsonArray("categories")),
                        CosmeticDef.Slot.parse(o.get("slot").getAsString()),
                        strings(o.getAsJsonArray("tags")),
                        o.has("icon") ? o.get("icon").getAsInt() : 0,
                        particles,
                        o.getAsJsonArray("parts"));
                BY_ID.put(def.id(), def);
            }
        } catch (Exception ex) {
            EqsualCosmetics.LOG.error("Failed to load cosmetic catalogue", ex);
        }
    }

    private static List<String> strings(JsonArray arr) {
        List<String> out = new ArrayList<>();
        if (arr != null) for (JsonElement e : arr) out.add(e.getAsString());
        return Collections.unmodifiableList(out);
    }

    public static CosmeticDef get(String id) { return BY_ID.get(id); }

    public static Collection<CosmeticDef> all() { return Collections.unmodifiableCollection(BY_ID.values()); }

    public static List<String> categories() { return Collections.unmodifiableList(CATEGORIES); }

    public static int atlasSize() { return atlasSize; }

    /**
     * Cleans a requested loadout: drops unknown ids and duplicates, keeps at most one cosmetic per slot
     * (the last one listed wins), and returns them in slot order.
     */
    public static List<String> sanitize(List<String> ids) {
        Map<CosmeticDef.Slot, String> bySlot = new EnumMap<>(CosmeticDef.Slot.class);
        if (ids != null) {
            for (String id : ids) {
                CosmeticDef def = id == null ? null : BY_ID.get(id);
                if (def != null) bySlot.put(def.slot(), def.id());
            }
        }
        return new ArrayList<>(bySlot.values());
    }
}
