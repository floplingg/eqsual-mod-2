package com.eqsual.cosmetics;

import com.eqsual.cosmetics.cosmetic.CosmeticRegistry;
import com.eqsual.cosmetics.network.CosmeticPayloads;
import com.eqsual.cosmetics.server.ServerCosmeticTracker;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint (runs on both the client and dedicated/integrated servers).
 * Loads the cosmetic catalogue, registers network payloads and the server-side sync tracker.
 */
public class EqsualCosmetics implements ModInitializer {
    public static final String MOD_ID = "eqsual_cosmetics";
    public static final Logger LOG = LoggerFactory.getLogger("Eqsual Cosmetics");

    @Override
    public void onInitialize() {
        CosmeticRegistry.load();
        CosmeticPayloads.register();
        ServerCosmeticTracker.init();
        LOG.info("Eqsual Cosmetics loaded {} cosmetics (all free)", CosmeticRegistry.all().size());
    }
}
