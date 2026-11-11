package com.eqsual.cosmetics.cosmetic;

import com.google.gson.JsonArray;
import java.util.List;
import java.util.Locale;

/**
 * One cosmetic from the catalogue. Every cosmetic is free and always available —
 * there is intentionally no rarity, price, lock or exclusivity field.
 */
public record CosmeticDef(
        String id,
        String name,
        String description,
        List<String> categories,
        Slot slot,
        List<String> tags,
        int icon,
        ParticleSpec particles,
        JsonArray parts) {

    public enum Slot {
        HEAD("Head"), EARS("Ears"), HALO("Halo");

        public final String label;

        Slot(String label) { this.label = label; }

        public static Slot parse(String s) {
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "ears" -> EARS;
                case "halo" -> HALO;
                default -> HEAD;
            };
        }
    }

    /** Optional ambient particle effect. {@code y} is measured in blocks above the top of the head. */
    public record ParticleSpec(String type, float chance, float spread, float y, float vy) { }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (name.toLowerCase(Locale.ROOT).contains(q) || id.contains(q)) return true;
        for (String c : categories) if (c.toLowerCase(Locale.ROOT).contains(q)) return true;
        for (String t : tags) if (t.toLowerCase(Locale.ROOT).contains(q)) return true;
        return slot.label.toLowerCase(Locale.ROOT).contains(q);
    }
}
