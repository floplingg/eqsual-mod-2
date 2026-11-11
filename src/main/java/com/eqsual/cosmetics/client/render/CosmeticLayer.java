package com.eqsual.cosmetics.client.render;

import com.eqsual.cosmetics.client.ClientCosmetics;
import com.eqsual.cosmetics.client.CosmeticConfig;
import com.eqsual.cosmetics.cosmetic.CosmeticDef;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Player render layer that attaches cosmetics to the head bone, so they follow head yaw/pitch,
 * sneaking, swimming, sleeping, etc. Used for every player in third person, F5, other players
 * and the menu preview. (In first person your own head isn't drawn, so neither are your cosmetics —
 * they never block your view.)
 */
public class CosmeticLayer<M extends EntityModel<AvatarRenderState> & HeadedModel> extends RenderLayer<AvatarRenderState, M> {

    public CosmeticLayer(RenderLayerParent<AvatarRenderState, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        List<CosmeticDef> loadout = ClientCosmetics.forEntityId(state.id);
        if (loadout.isEmpty()) return;
        CosmeticConfig cfg = ClientCosmetics.config();
        if (cfg.hideUnderHelmet && state.id != ClientCosmetics.PREVIEW_ENTITY_ID && !state.headEquipment.isEmpty()) return;

        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        poseStack.pushPose();
        M model = this.getParentModel();
        model.root().translateAndRotate(poseStack);
        model.translateToHead(poseStack);
        // head bone space (blocks, Y down, face towards -Z)  ->  cosmetic space (pixels, Y up, face towards +Z)
        poseStack.translate(0.0F, -0.5F, 0.0F);
        poseStack.scale(1.0F / 16.0F, -1.0F / 16.0F, -1.0F / 16.0F);
        for (CosmeticDef def : loadout) {
            CosmeticModel.get(def).submit(poseStack, collector, light, overlay, state.ageInTicks, cfg.animations, cfg.glow);
        }
        poseStack.popPose();
    }
}
