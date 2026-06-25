package com.mobenchant.mvn123123123123123.mixin;

import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnderMan.class)
public class EnderManMixin {

    @Inject(method = "teleport()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void cancelTeleport1(CallbackInfoReturnable<Boolean> cir) {
        if (((net.minecraft.world.entity.Entity) (Object) this).entityTags().contains("boss_guard")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "teleport(DDD)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void cancelTeleport2(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (((net.minecraft.world.entity.Entity) (Object) this).entityTags().contains("boss_guard")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "teleportTowards(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void cancelTeleport3(net.minecraft.world.entity.Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (((net.minecraft.world.entity.Entity) (Object) this).entityTags().contains("boss_guard")) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "randomTeleport", at = @At("HEAD"), cancellable = true, require = 0)
    private void cancelRandomTeleport(double x, double y, double z, boolean particleEffects, CallbackInfoReturnable<Boolean> cir) {
        if (((net.minecraft.world.entity.Entity) (Object) this).entityTags().contains("boss_guard")) {
            cir.setReturnValue(false);
        }
    }
}
