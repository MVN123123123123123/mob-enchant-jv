package com.mobenchant.mvn123123123123123.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WingsLayer.class)
public class WingsLayerMixin {

    @Inject(method = "submit", at = @At("HEAD"))
    private void preSubmit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i,
            HumanoidRenderState humanoidRenderState, float f, float g, CallbackInfo ci) {
        if (humanoidRenderState instanceof EndermanRenderState) {
            poseStack.pushPose();
            // Enderman body is significantly higher than standard humanoid mobs.
            // Translating by roughly 18 pixels (1.15F) upwards to align with the body
            // pivot.
            poseStack.translate(0.0F, -0.85F, 0.0F);
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void postSubmit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i,
            HumanoidRenderState humanoidRenderState, float f, float g, CallbackInfo ci) {
        if (humanoidRenderState instanceof EndermanRenderState) {
            poseStack.popPose();
        }
    }
}
