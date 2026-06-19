package com.mobenchant.mvn123123123123123.client.mixin;

import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EyesLayer.class)
public class EyesLayerMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void hideEyesIfInvisible(com.mojang.blaze3d.vertex.PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int i, EntityRenderState entityRenderState, float f, float g, CallbackInfo ci) {
        if (entityRenderState.isInvisible) {
            ci.cancel();
        }
    }
}
