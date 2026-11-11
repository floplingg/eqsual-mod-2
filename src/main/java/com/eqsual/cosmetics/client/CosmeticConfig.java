package com.eqsual.cosmetics.client;

import com.eqsual.cosmetics.EqsualCosmetics;
import com.eqsual.cosmetics.cosmetic.CosmeticRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Persistent client settings: equipped cosmetics, favourites and visual toggles. Saved to config/eqsual_cosmetics.json. */
public final class CosmeticConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("eqsual_cosmetics.json");

    public List<String> equipped = new ArrayList<>();
    public List<String> favorites = new ArrayList<>();
    public boolean glow = true;
    public boolean animations = true;
    public boolean particles = true;
    public boolean showOthers = true;
    public boolean hideUnderHelmet = false;
    public boolean autoRotate = true;

    public static CosmeticConfig load() {
        CosmeticConfig cfg = null;
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, CosmeticConfig.class);
            } catch (Exception e) {
                EqsualCosmetics.LOG.warn("Could not read {}, using defaults", FILE, e);
            }
        }
        if (cfg == null) cfg = new CosmeticConfig();
        if (cfg.equipped == null) cfg.equipped = new ArrayList<>();
        if (cfg.favorites == null) cfg.favorites = new ArrayList<>();
        // Drop ids that no longer exist and enforce one cosmetic per slot.
        cfg.equipped = CosmeticRegistry.sanitize(cfg.equipped);
        cfg.favorites = new ArrayList<>(new LinkedHashSet<>(cfg.favorites));
        cfg.favorites.removeIf(id -> CosmeticRegistry.get(id) == null);
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try {
                Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
            } catch (Exception e2) {
                EqsualCosmetics.LOG.error("Could not save {}", FILE, e2);
            }
        }
    }
}
