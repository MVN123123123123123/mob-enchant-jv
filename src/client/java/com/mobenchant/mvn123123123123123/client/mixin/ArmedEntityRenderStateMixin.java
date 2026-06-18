package com.mobenchant.mvn123123123123123.client.mixin;

import com.mobenchant.mvn123123123123123.MobEnchantData;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmedEntityRenderState.class)
public class ArmedEntityRenderStateMixin {

    @Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
    private static void hideWeaponIfVanishing(LivingEntity livingEntity, ArmedEntityRenderState state, ItemModelResolver resolver, float partialTicks, CallbackInfo ci) {
        if (livingEntity.isInvisible() && MobEnchantData.INSTANCE.getMobEnchant(livingEntity, "curse_of_vanishing") != null) {
            if (state.rightHandItemState != null) {
                state.rightHandItemState.clear();
            }
            if (state.leftHandItemState != null) {
                state.leftHandItemState.clear();
            }
        }
    }
}
