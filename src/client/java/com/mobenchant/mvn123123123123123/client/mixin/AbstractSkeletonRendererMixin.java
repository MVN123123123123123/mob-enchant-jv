package com.mobenchant.mvn123123123123123.client.mixin;

import net.minecraft.client.renderer.entity.AbstractSkeletonRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSkeletonRenderer.class)
public abstract class AbstractSkeletonRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/skeleton/AbstractSkeleton;Lnet/minecraft/client/renderer/entity/state/SkeletonRenderState;F)V", at = @At("RETURN"))
    public void extractBossGuardState(net.minecraft.world.entity.monster.skeleton.AbstractSkeleton entity, net.minecraft.client.renderer.entity.state.SkeletonRenderState state, float f, CallbackInfo ci) {
        if (entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA)) {
            ((com.mobenchant.mvn123123123123123.client.BossGuardState) state).setBossGuard(true);
        }
    }
}
