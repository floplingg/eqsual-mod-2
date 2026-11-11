package com.eqsual.cosmetics.client;

import com.eqsual.cosmetics.cosmetic.CosmeticDef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/** Optional ambient particles above players wearing particle cosmetics (client-side only, cheap). */
public final class CosmeticParticles {
    private static final Map<String, ParticleOptions> TYPES = new HashMap<>();
    private static final double MAX_DIST_SQ = 40 * 40;

    private CosmeticParticles() { }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (!ClientCosmetics.config().particles) return;
        RandomSource rnd = mc.level.getRandom();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Player player : mc.level.players()) {
            if (player.isInvisible() || player.isSpectator()) continue;
            boolean self = player == mc.player;
            if (self && firstPerson) continue; // keep your own view clear
            if (!self && player.distanceToSqr(mc.player) > MAX_DIST_SQ) continue;
            List<CosmeticDef> loadout = ClientCosmetics.forPlayer(player.getUUID());
            for (CosmeticDef def : loadout) {
                CosmeticDef.ParticleSpec spec = def.particles();
                if (spec == null || rnd.nextFloat() >= spec.chance()) continue;
                ParticleOptions type = resolve(spec.type());
                if (type == null) continue;
                double x = player.getX() + (rnd.nextDouble() - 0.5) * 2 * spec.spread();
                double z = player.getZ() + (rnd.nextDouble() - 0.5) * 2 * spec.spread();
                double y = player.getY() + player.getBbHeight() + spec.y() - 0.15 + rnd.nextDouble() * 0.1;
                mc.level.addParticle(type, x, y, z, 0.0, spec.vy(), 0.0);
            }
        }
    }

    private static ParticleOptions resolve(String name) {
        return TYPES.computeIfAbsent(name, n -> {
            Identifier id = Identifier.tryParse(n.contains(":") ? n : "minecraft:" + n);
            if (id == null) return null;
            ParticleType<?> t = BuiltInRegistries.PARTICLE_TYPE.getValue(id);
            return t instanceof SimpleParticleType simple ? simple : null;
        });
    }
}
