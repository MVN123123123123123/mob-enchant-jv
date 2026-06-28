package com.mobenchant.mvn123123123123123.client.mixin;

import com.mobenchant.mvn123123123123123.client.BossGuardState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SkeletonRenderState.class)
public class SkeletonRenderStateMixin implements BossGuardState {

    @Unique
    private boolean isBossGuard;

    @Override
    public boolean isBossGuard() {
        return this.isBossGuard;
    }

    @Override
    public void setBossGuard(boolean isBossGuard) {
        this.isBossGuard = isBossGuard;
    }
}
