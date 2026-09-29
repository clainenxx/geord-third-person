package com.example.betterthirdperson.mixin;

import com.example.betterthirdperson.BetterThirdPersonClient;
import com.example.betterthirdperson.CameraState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * While S (back only) is held with the free camera active, the body may face either toward the
 * camera (turn-around mode) or along the camera direction (backpedal mode after a click, see
 * {@link BetterThirdPersonClient#onTick}). Movement is computed relative to the entity's own
 * yaw, so the movement input is rotated by (cameraYaw - bodyYaw) here. The character therefore
 * always travels the world direction "toward the camera", regardless of which way the body is
 * currently facing (or mid-turn).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMovementMixin {

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d betterThirdPerson$alignBackwardWalk(Vec3d movementInput) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ClientPlayerEntity
                && BetterThirdPersonClient.isActive(MinecraftClient.getInstance())
                && BetterThirdPersonClient.isWalkingBackwardTurnAround()) {
            // movementInput is (sideways, upward, forward). Rotate it from the camera's frame
            // into the body's frame (same convention as Entity#movementInputToVelocity).
            float d = (CameraState.get().getYaw() - self.getYaw()) * ((float) Math.PI / 180F);
            float sin = MathHelper.sin(d);
            float cos = MathHelper.cos(d);
            return new Vec3d(
                    movementInput.x * cos - movementInput.z * sin,
                    movementInput.y,
                    movementInput.z * cos + movementInput.x * sin);
        }
        return movementInput;
    }
}
