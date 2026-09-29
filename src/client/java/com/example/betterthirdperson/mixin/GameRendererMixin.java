package com.example.betterthirdperson.mixin;

import com.example.betterthirdperson.BetterThirdPersonClient;
import com.example.betterthirdperson.CameraState;
import com.example.betterthirdperson.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's GameRenderer#findCrosshairTarget(Entity, ...) raycasts starting
 * at the PLAYER'S EYE (camera.getCameraPosVec / camera.raycast / camera.getRotationVec
 * all read the entity itself, never the visual {@link Camera}). That's fine
 * in vanilla because first person eye == visual camera position, but this
 * mod's free third-person camera sits offset up/right/back from the eye -
 * so a ray that starts at the eye, even if aimed the right direction, ends
 * up parallel-shifted from what's actually centered on screen. At close
 * range (a tree trunk right in front of you) that shift is enough to miss
 * entirely, which is exactly the bug this fixes.
 *
 * This mixin replaces the raycast outright: same algorithm vanilla uses
 * (block raycast, then an entity raycast along the same ray, whichever hits
 * closer), but starting from the actual {@link Camera}'s current position
 * and this mod's free look direction ({@link CameraState}) instead of the
 * player entity's eye/rotation.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private Camera camera;

    @Shadow
    private static HitResult ensureTargetInRange(HitResult hitResult, Vec3d cameraPos, double interactionRange) {
        throw new UnsupportedOperationException("shadowed");
    }

    @Inject(method = "findCrosshairTarget", at = @At("HEAD"), cancellable = true)
    private void betterThirdPerson$cameraRaycast(Entity cameraEntity, double blockInteractionRange,
                                                  double entityInteractionRange, float tickProgress,
                                                  CallbackInfoReturnable<HitResult> cir) {
        if (!(cameraEntity instanceof ClientPlayerEntity player) || this.client.world == null
                || !BetterThirdPersonClient.isActive(this.client)) {
            return;
        }

        CameraState state = CameraState.get();

        // The crosshair is always the truth: the ray starts at the visual camera and follows the
        // free camera's direction, even when that points at what is behind the character's back
        // or at the character's own face. Clicking then turns the body toward that direction
        // (see BetterThirdPersonClient), so the character ends up facing the target.
        Vec3d origin = this.camera.getPos();
        Vec3d direction = betterThirdPerson$rotationVec(state.getPitch(), state.getYaw());

        // Camera looking at the character's BACK (body faces roughly the same way as the camera):
        // everything between the camera and the character is behind the character and must not be
        // targetable. The ray keeps following the crosshair, but only starts once it reaches the
        // character's depth, so only what is in front of the character can be hit/used/broken.
        // Exception: when the player just turned around on purpose (click while looking at the
        // character's face, or clicking while backpedaling with S) targets behind are allowed.
        boolean facingSameWay = Math.abs(MathHelper.wrapDegrees(state.getYaw() - player.getYaw())) <= 90.0F;
        if (facingSameWay && !BetterThirdPersonClient.isBehindTargetingAllowed()) {
            double along = player.getEyePos().subtract(origin).dotProduct(direction);
            if (along > 0.0) {
                origin = origin.add(direction.multiply(along));
            }
        }

        // Extra reach (config-adjustable) is added on top of the vanilla ranges here so the
        // crosshair can pick out farther targets while the free camera is active. This only
        // affects what THIS client picks as its target - it doesn't touch the underlying reach
        // attribute, so a server enforcing its own reach checks can still reject the interaction.
        float bonus = Math.max(0.0F, Config.get().extraReach);
        double extendedBlockRange = blockInteractionRange + bonus;
        double extendedEntityRange = entityInteractionRange + bonus;

        double maxRange = Math.max(extendedBlockRange, extendedEntityRange);
        Vec3d end = origin.add(direction.multiply(maxRange));

        HitResult blockHit = this.client.world.raycast(new RaycastContext(
                origin, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, player));

        double blockDistSq = blockHit.getPos().squaredDistanceTo(origin);
        double effectiveRange = maxRange;
        double effectiveRangeSq = MathHelper.square(maxRange);
        if (blockHit.getType() != HitResult.Type.MISS) {
            effectiveRangeSq = blockDistSq;
            effectiveRange = Math.sqrt(blockDistSq);
        }

        Vec3d reach = origin.add(direction.x * effectiveRange, direction.y * effectiveRange, direction.z * effectiveRange);
        Box box = player.getBoundingBox().stretch(direction.multiply(effectiveRange)).expand(1.0, 1.0, 1.0);
        EntityHitResult entityHit = ProjectileUtil.raycast(player, origin, reach, box, EntityPredicates.CAN_HIT, effectiveRangeSq);

        HitResult result = (entityHit != null && entityHit.getPos().squaredDistanceTo(origin) < blockDistSq)
                ? ensureTargetInRange(entityHit, origin, extendedEntityRange)
                : ensureTargetInRange(blockHit, origin, extendedBlockRange);

        cir.setReturnValue(result);
    }

    /**
     * Same math as Entity#getRotationVector(pitch, yaw) - a normalized look direction from angles.
     *
     * NOTE: pitch is positive when looking DOWN in Minecraft's convention, so the vertical
     * component must be {@code -sin(pitch)} (looking down -> negative/downward Y). The previous
     * version of this method used {@code +sin(pitch)} here, which inverted the vertical aim of
     * every attack/interaction while the free camera was active: aiming down would raycast up,
     * and vice versa.
     */
    private static Vec3d betterThirdPerson$rotationVec(float pitch, float yaw) {
        float f = pitch * ((float) Math.PI / 180F);
        float g = -yaw * ((float) Math.PI / 180F) - (float) Math.PI;
        float h = MathHelper.sin(g);
        float i = MathHelper.cos(g);
        float j = -MathHelper.cos(f);
        float k = -MathHelper.sin(f);
        return new Vec3d((double) (h * j), (double) k, (double) (i * j));
    }
}
