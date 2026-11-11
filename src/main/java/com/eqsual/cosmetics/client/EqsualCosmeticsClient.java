package com.eqsual.cosmetics.client;

import com.eqsual.cosmetics.EqsualCosmetics;
import com.eqsual.cosmetics.client.gui.CosmeticsScreen;
import com.eqsual.cosmetics.client.render.CosmeticLayer;
import com.eqsual.cosmetics.network.CosmeticPayloads.SyncCosmetics;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class EqsualCosmeticsClient implements ClientModInitializer {
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(EqsualCosmetics.MOD_ID, "main"));
    public static KeyMapping openMenuKey;

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        ClientCosmetics.init();

        // Options -> Controls -> Key Binds -> "Eqsual Cosmetics" (default: K, fully rebindable)
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.eqsual_cosmetics.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.player != null && client.screen == null) client.setScreen(new CosmeticsScreen());
            }
            CosmeticParticles.tick(client);
        });

        // Attach the cosmetic layer to player renderers (wide + slim models).
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof AvatarRenderer<?> avatarRenderer) {
                helper.register(new CosmeticLayer((RenderLayerParent) avatarRenderer));
            }
        });

        // Multiplayer sync.
        ClientPlayNetworking.registerGlobalReceiver(SyncCosmetics.TYPE,
                (payload, context) -> ClientCosmetics.onRemoteUpdate(payload.player(), payload.ids()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientCosmetics.clearRemote();
            ClientCosmetics.sendToServer(); // no-op on servers without the mod (local-only fallback)
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientCosmetics.clearRemote());
    }

    public static boolean isOpenKey(net.minecraft.client.input.KeyEvent event) {
        return openMenuKey != null && openMenuKey.matches(event);
    }

    static Minecraft mc() { return Minecraft.getInstance(); }
}
