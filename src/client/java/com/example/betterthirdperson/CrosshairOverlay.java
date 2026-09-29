package com.example.betterthirdperson;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Vanilla only draws the crosshair in first person - third person back view
 * (even normal vanilla third person) shows none at all. Since this mod's
 * free camera makes third person the "main" way to play, we draw a small
 * crosshair back in ourselves whenever the free camera is active.
 */
public class CrosshairOverlay {
    private static final int REACH = 4;      // arm length from center, in pixels
    private static final int THICKNESS = 1;  // arm thickness, in pixels
    private static final int COLOR = 0xFFFFFFFF; // white, ARGB

    public static void register() {
        HudRenderCallback.EVENT.register(CrosshairOverlay::render);
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null || client.options.hudHidden) {
            return;
        }
        if (!BetterThirdPersonClient.isActive(client)) {
            // First person / front-view third person: vanilla already draws (or intentionally
            // doesn't draw) its own crosshair, so we stay out of the way.
            return;
        }

        int centerX = client.getWindow().getScaledWidth() / 2;
        int centerY = client.getWindow().getScaledHeight() / 2;

        // Horizontal arm.
        context.fill(centerX - REACH, centerY, centerX + REACH + THICKNESS, centerY + THICKNESS, COLOR);
        // Vertical arm.
        context.fill(centerX, centerY - REACH, centerX + THICKNESS, centerY + REACH + THICKNESS, COLOR);
    }
}
