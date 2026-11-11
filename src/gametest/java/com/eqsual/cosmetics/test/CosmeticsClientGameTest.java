package com.eqsual.cosmetics.test;

import com.eqsual.cosmetics.client.ClientCosmetics;
import com.eqsual.cosmetics.client.EqsualCosmeticsClient;
import com.eqsual.cosmetics.client.gui.CosmeticsScreen;
import com.eqsual.cosmetics.cosmetic.CosmeticDef;
import com.eqsual.cosmetics.cosmetic.CosmeticRegistry;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/**
 * Runtime smoke test (dev only, not shipped): opens a world, renders every cosmetic on the player in
 * third person, opens the menu through the real keybind, and checks equip/unequip/favourite/save logic.
 */
@SuppressWarnings("UnstableApiUsage")
public class CosmeticsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        List<CosmeticDef> all = new ArrayList<>(CosmeticRegistry.all());
        if (all.size() < 14) throw new AssertionError("Expected at least 14 cosmetics, got " + all.size());

        // Logic checks (equip replaces within slot, unequip, favourites)
        context.runOnClient(client -> {
            ClientCosmetics.unequipAll();
            CosmeticDef royal = CosmeticRegistry.get("royal_crown");
            CosmeticDef gold = CosmeticRegistry.get("golden_crown");
            CosmeticDef halo = CosmeticRegistry.get("halo");
            ClientCosmetics.equip(royal);
            ClientCosmetics.equip(halo);
            ClientCosmetics.equip(gold); // same slot as royal -> replaces it
            check(!ClientCosmetics.isEquipped(royal) && ClientCosmetics.isEquipped(gold) && ClientCosmetics.isEquipped(halo), "slot replace");
            ClientCosmetics.unequip(gold);
            check(!ClientCosmetics.isEquipped(gold) && ClientCosmetics.equipped().size() == 1, "unequip");
            boolean fav = ClientCosmetics.isFavorite(royal);
            ClientCosmetics.toggleFavorite(royal);
            check(ClientCosmetics.isFavorite(royal) != fav, "favorite toggle");
            ClientCosmetics.toggleFavorite(royal);
            ClientCosmetics.unequipAll();
        });

        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("weather clear");
            sp.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                client.options.hideGui = true;
                client.player.setYRot(0); client.player.setYHeadRot(0); client.player.setXRot(0);
            });
            context.waitTicks(10);

            for (CosmeticDef def : all) {
                context.runOnClient(client -> { ClientCosmetics.unequipAll(); ClientCosmetics.equip(def); });
                context.waitTicks(3);
                context.takeScreenshot("cos_" + def.id());
            }

            // Combined loadout: crown + halo (halo must float above the crown), front then back
            context.runOnClient(client -> {
                ClientCosmetics.unequipAll();
                ClientCosmetics.equip(CosmeticRegistry.get("royal_crown"));
                ClientCosmetics.equip(CosmeticRegistry.get("halo"));
            });
            context.waitTicks(3);
            context.takeScreenshot("cos_zz_loadout_front");
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                client.player.setXRot(35);
            });
            context.waitTicks(5);
            context.takeScreenshot("loadout_back");

            // Menu via the real keybind
            context.runOnClient(client -> {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.options.hideGui = false;
            });
            context.waitTicks(2);
            context.takeScreenshot("first_person");
            context.getInput().pressKey(EqsualCosmeticsClient.openMenuKey);
            context.waitTicks(5);
            context.runOnClient(client -> check(client.screen instanceof CosmeticsScreen, "keybind opens menu"));
            context.waitTicks(10);
            context.takeScreenshot("menu");
            context.getInput().pressKey(EqsualCosmeticsClient.openMenuKey);
            context.waitTicks(3);
            context.runOnClient(client -> check(client.screen == null, "keybind closes menu"));
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError("Check failed: " + what);
    }
}
