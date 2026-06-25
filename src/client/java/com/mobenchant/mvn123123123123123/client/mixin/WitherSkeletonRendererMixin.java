package com.mobenchant.mvn123123123123123.client.mixin;

import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WitherSkeletonRenderer.class)
public abstract class WitherSkeletonRendererMixin {

    @Shadow protected abstract boolean addLayer(RenderLayer<?, ?> layer);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addElytraLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
        this.addLayer(new WingsLayer<>((WitherSkeletonRenderer) (Object) this, context.getModelSet(), context.getEquipmentRenderer()));
    }
}
