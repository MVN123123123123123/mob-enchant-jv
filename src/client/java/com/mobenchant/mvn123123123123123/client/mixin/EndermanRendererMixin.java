package com.mobenchant.mvn123123123123123.client.mixin;

import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndermanRenderer.class)
public abstract class EndermanRendererMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addElytraLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
        ((LivingEntityRendererAccessor) this).invokeAddLayer(new WingsLayer<>((EndermanRenderer) (Object) this, context.getModelSet(), context.getEquipmentRenderer()));
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/EnderMan;Lnet/minecraft/client/renderer/entity/state/EndermanRenderState;F)V", at = @At("RETURN"))
    public void extractBossGuardState(net.minecraft.world.entity.monster.EnderMan entity, net.minecraft.client.renderer.entity.state.EndermanRenderState state, float f, CallbackInfo ci) {
        if (entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA)) {
            ((com.mobenchant.mvn123123123123123.client.BossGuardState) state).setBossGuard(true);
        }
    }
}
