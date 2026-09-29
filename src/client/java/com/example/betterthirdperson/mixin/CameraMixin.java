package com.example.betterthirdperson.mixin;

import com.example.betterthirdperson.BetterThirdPersonClient;
import com.example.betterthirdperson.CameraState;
import com.example.betterthirdperson.Config;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla third-person-back camera placement with a free-look
 * version: the rotation comes from {@link CameraState} (controlled by
 * {@link MouseMixin}) instead of the focused entity's own yaw/pitch, and the
 * final offset is pushed further back, higher, and to the right.
 *
 * Front-facing third person (F5 twice) and first person are left completely
 * untouched - the injected code bails out immediately for those cases and
 * lets vanilla's own {@code update} body run.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private boolean ready;
    @Shadow private BlockView area;
    @Shadow private Entity focusedEntity;
    @Shadow private boolean thirdPerson;
    @Shadow private float cameraY;
    @Shadow private float lastCameraY;
    @Shadow private float lastTickProgress;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void moveBy(float f, float g, float h);

    @Shadow
    private float clipToSpace(float f) {
        throw new UnsupportedOperationException("shadowed");
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void betterThirdPerson$freeCamera(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                               boolean inverseView, float tickProgress, CallbackInfo ci) {
        if (!BetterThirdPersonClient.isActive(thirdPerson, inverseView)) {
            // First person, or the front-facing third person view: let vanilla handle it as normal.
            return;
        }

        this.ready = true;
        this.area = area;
        this.focusedEntity = focusedEntity;
        this.thirdPerson = thirdPerson;
        this.lastTickProgress = tickProgress;

        double x = MathHelper.lerp((double) tickProgress, focusedEntity.lastX, focusedEntity.getX());
        double y = MathHelper.lerp((double) tickProgress, focusedEntity.lastY, focusedEntity.getY())
                + (double) MathHelper.lerp(tickProgress, this.lastCameraY, this.cameraY);
        double z = MathHelper.lerp((double) tickProgress, focusedEntity.lastZ, focusedEntity.getZ());
        this.setPos(x, y, z);

        CameraState state = CameraState.get();
        this.setRotation(state.getYaw(), state.getPitch());

        Config config = Config.get();
        float scale = (focusedEntity instanceof LivingEntity living) ? living.getScale() : 1.0F;
        float distance = config.distance * scale;
        float clipped = this.clipToSpace(distance);

        // f = pulled backwards, g = pushed up, h = pushed right (see Camera#moveBy).
        this.moveBy(-clipped, config.upOffset, config.rightOffset);

        ci.cancel();
    }
}
