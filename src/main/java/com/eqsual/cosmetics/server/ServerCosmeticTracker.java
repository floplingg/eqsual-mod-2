package com.eqsual.cosmetics.server;

import com.eqsual.cosmetics.cosmetic.CosmeticRegistry;
import com.eqsual.cosmetics.network.CosmeticPayloads.SetCosmetics;
import com.eqsual.cosmetics.network.CosmeticPayloads.SyncCosmetics;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of cosmetic sync (works on dedicated servers and the integrated singleplayer/LAN server).
 * Keeps each online player's loadout and relays changes to every client that has the mod.
 * Players without the mod are simply skipped — they never receive unknown packets.
 */
public final class ServerCosmeticTracker {
    private static final Map<UUID, List<String>> EQUIPPED = new ConcurrentHashMap<>();
    private static final RateLimiter RATE = new RateLimiter(10);

    private ServerCosmeticTracker() { }

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(SetCosmetics.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            onSet(player.level().getServer(), player, payload.ids());
        });

        // Joining: send the newcomer everyone's current cosmetics.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer joined = handler.getPlayer();
            if (!ServerPlayNetworking.canSend(joined, SyncCosmetics.TYPE)) return;
            for (Map.Entry<UUID, List<String>> e : EQUIPPED.entrySet()) {
                if (!e.getKey().equals(joined.getUUID())) {
                    ServerPlayNetworking.send(joined, new SyncCosmetics(e.getKey(), e.getValue()));
                }
            }
        });

        // Leaving: forget them and tell everyone to drop their cosmetics.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            RATE.forget(uuid);
            if (EQUIPPED.remove(uuid) != null) broadcast(server, new SyncCosmetics(uuid, List.of()), uuid);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            EQUIPPED.clear();
            RATE.clear();
        });
    }

    private static void onSet(MinecraftServer server, ServerPlayer player, List<String> requested) {
        if (server == null) return;
        UUID uuid = player.getUUID();
        List<String> clean = List.copyOf(CosmeticRegistry.sanitize(requested));
        List<String> previous = EQUIPPED.get(uuid);
        if (clean.equals(previous == null ? List.of() : previous)) return;
        // Spam protection: identical loadouts are ignored above, and anything over the per-second
        // budget is dropped (the client always sends its full loadout, so the next change catches up).
        if (!RATE.tryAcquire(uuid)) return;
        if (clean.isEmpty()) EQUIPPED.remove(uuid); else EQUIPPED.put(uuid, clean);
        broadcast(server, new SyncCosmetics(uuid, clean), uuid);
    }

    private static void broadcast(MinecraftServer server, SyncCosmetics payload, UUID except) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(except)) continue;
            if (ServerPlayNetworking.canSend(p, SyncCosmetics.TYPE)) ServerPlayNetworking.send(p, payload);
        }
    }

    /** Allows {@code perSecond} updates per player per second. */
    private static final class RateLimiter {
        private final int perSecond;
        private final Map<UUID, long[]> windows = new ConcurrentHashMap<>();

        RateLimiter(int perSecond) { this.perSecond = perSecond; }

        boolean tryAcquire(UUID uuid) {
            long sec = System.currentTimeMillis() / 1000L;
            long[] w = windows.computeIfAbsent(uuid, k -> new long[]{sec, 0});
            if (w[0] != sec) { w[0] = sec; w[1] = 0; }
            return ++w[1] <= perSecond;
        }

        void forget(UUID uuid) { windows.remove(uuid); }

        void clear() { windows.clear(); }
    }
}
