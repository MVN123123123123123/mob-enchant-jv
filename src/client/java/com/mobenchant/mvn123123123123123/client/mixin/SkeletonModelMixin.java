package com.mobenchant.mvn123123123123123.client.mixin;

import com.mobenchant.mvn123123123123123.client.BossGuardState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkeletonModel.class)
public abstract class SkeletonModelMixin<T extends SkeletonRenderState> extends HumanoidModel<T> {

    public SkeletonModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/SkeletonRenderState;)V", at = @At("RETURN"))
    private void setupBossGuardFlyAnim(T state, CallbackInfo ci) {
        if (((BossGuardState) state).isBossGuard()) {
            if (state.isFallFlying) {
                this.rightArm.xRot = 0.0F;
                this.leftArm.xRot = 0.0F;
                this.rightArm.yRot = 0.0F;
                this.leftArm.yRot = 0.0F;
                this.rightArm.zRot = 0.0F;
                this.leftArm.zRot = 0.0F;

                this.rightLeg.xRot = 0.0F;
                this.leftLeg.xRot = 0.0F;
                this.rightLeg.yRot = 0.0F;
                this.leftLeg.yRot = 0.0F;
                this.rightLeg.zRot = 0.0F;
                this.leftLeg.zRot = 0.0F;
            }
        }
    }
}
