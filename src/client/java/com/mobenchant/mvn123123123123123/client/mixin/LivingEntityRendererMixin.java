package com.mobenchant.mvn123123123123123.client.mixin;

import com.mobenchant.mvn123123123123123.client.BossGuardState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<S extends LivingEntityRenderState> {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("RETURN"))
    private void applyBossGuardFlightRotation(S state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
        if (state instanceof BossGuardState bossGuardState && bossGuardState.isBossGuard()) {
            if (state instanceof HumanoidRenderState humanoidState && humanoidState.isFallFlying) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - state.xRot));
            }
        }
    }
}
