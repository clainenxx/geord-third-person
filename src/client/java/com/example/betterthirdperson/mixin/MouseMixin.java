package com.example.betterthirdperson.mixin;

import com.example.betterthirdperson.BetterThirdPersonClient;
import com.example.betterthirdperson.CameraState;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts the call vanilla makes to turn the player's body every time the
 * mouse moves. The real rotation call lives in {@code Mouse#updateMouse(double)}
 * (NOT the cursor-position callback, which only accumulates raw pixel deltas):
 *
 * <pre>
 *   this.client.player.changeLookDirection(i, j * (double) k);
 * </pre>
 *
 * so this mixin targets {@code updateMouse} and redirects that specific call.
 * While the free third-person camera is active, the rotation goes into
 * {@link CameraState} instead of the player entity, so looking around
 * doesn't spin your character - only the camera turns. The owner type used
 * below (ClientPlayerEntity) matches the static type of {@code this.client.player}
 * at that call site.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Redirect(
            method = "updateMouse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V")
    )
    private void betterThirdPerson$redirectLook(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (BetterThirdPersonClient.isActive(client)) {
            CameraState.get().rotate(cursorDeltaX, cursorDeltaY);
        } else {
            player.changeLookDirection(cursorDeltaX, cursorDeltaY);
        }
    }
}
