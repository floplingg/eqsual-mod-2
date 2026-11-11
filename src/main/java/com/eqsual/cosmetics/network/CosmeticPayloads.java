package com.eqsual.cosmetics.network;

import com.eqsual.cosmetics.EqsualCosmetics;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Network payloads. Payload sizes are tiny: a UUID plus up to a handful of short ids. */
public final class CosmeticPayloads {
    public static final int MAX_IDS = 8;
    public static final int MAX_ID_LENGTH = 64;

    private CosmeticPayloads() { }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(SetCosmetics.TYPE, SetCosmetics.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncCosmetics.TYPE, SyncCosmetics.CODEC);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(EqsualCosmetics.MOD_ID, path);
    }

    private static void writeIds(FriendlyByteBuf buf, List<String> ids) {
        int n = Math.min(ids.size(), MAX_IDS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeUtf(ids.get(i), MAX_ID_LENGTH);
    }

    private static List<String> readIds(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_IDS) throw new IllegalArgumentException("Too many cosmetic ids: " + n);
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(buf.readUtf(MAX_ID_LENGTH));
        return ids;
    }

    /** Client -> server: "these are the cosmetics I have equipped now". */
    public record SetCosmetics(List<String> ids) implements CustomPacketPayload {
        public static final Type<SetCosmetics> TYPE = new Type<>(id("set_cosmetics"));
        public static final StreamCodec<FriendlyByteBuf, SetCosmetics> CODEC =
                CustomPacketPayload.codec(SetCosmetics::write, SetCosmetics::read);

        private void write(FriendlyByteBuf buf) { writeIds(buf, ids); }

        private static SetCosmetics read(FriendlyByteBuf buf) { return new SetCosmetics(readIds(buf)); }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> client: player {@code player} now wears {@code ids} (empty = nothing / player left). */
    public record SyncCosmetics(UUID player, List<String> ids) implements CustomPacketPayload {
        public static final Type<SyncCosmetics> TYPE = new Type<>(id("sync_cosmetics"));
        public static final StreamCodec<FriendlyByteBuf, SyncCosmetics> CODEC =
                CustomPacketPayload.codec(SyncCosmetics::write, SyncCosmetics::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeUUID(player);
            writeIds(buf, ids);
        }

        private static SyncCosmetics read(FriendlyByteBuf buf) {
            UUID uuid = buf.readUUID();
            return new SyncCosmetics(uuid, readIds(buf));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
