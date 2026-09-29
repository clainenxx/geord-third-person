package com.example.betterthirdperson;

import net.minecraft.util.math.MathHelper;

/**
 * Holds the "free" camera rotation used while in third-person-back view.
 *
 * This is intentionally NOT the same as the player entity's yaw/pitch:
 * mouse movement updates this object instead of the player directly
 * (see {@link com.example.betterthirdperson.mixin.MouseMixin}), so you can
 * look around freely without turning your character. The player's body
 * only catches up to this rotation when you move or interact
 * (see {@link BetterThirdPersonClient#onTick}).
 */
public class CameraState {
    private static final CameraState INSTANCE = new CameraState();

    private float yaw;
    private float pitch;
    private boolean initialized = false;

    public static CameraState get() {
        return INSTANCE;
    }

    /** Apply a raw mouse delta, using the same scale vanilla's changeLookDirection uses. */
    public void rotate(double cursorDeltaX, double cursorDeltaY) {
        this.yaw += (float) (cursorDeltaX * 0.15);
        this.pitch = MathHelper.clamp(this.pitch + (float) (cursorDeltaY * 0.15), -90.0F, 90.0F);
    }

    /** Re-align the free camera with the player, e.g. when (re)entering third person back view. */
    public void syncFrom(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
