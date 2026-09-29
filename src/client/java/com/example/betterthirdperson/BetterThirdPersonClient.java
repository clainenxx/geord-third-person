package com.example.betterthirdperson;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Better Third Person
 *
 * A "Leawind's Third Person"-style free camera for vanilla third person
 * back view (F5, not the front-facing F5x2 view):
 *
 *  - The camera sits further back, a bit higher, and offset to the right,
 *    so the player's body shows on the left side of the screen instead of
 *    dead center (see {@link com.example.betterthirdperson.mixin.CameraMixin}).
 *  - Moving the mouse while standing still only turns the camera
 *    (see {@link com.example.betterthirdperson.mixin.MouseMixin} and
 *    {@link CameraState}) - the player's body stays put, like free-look.
 *    The crosshair still follows the free camera.
 *  - As soon as you move, attack, or use an item, the body (and thus your
 *    attacks/interactions) turns to face wherever the free camera is
 *    pointing - even if the camera is looking at the character's face.
 *  - Holding S turns the body toward the camera and walks "forward"; clicking while
 *    holding S turns the body to the crosshair and backpedals until 2 seconds pass
 *    without a click.
 */
public class BetterThirdPersonClient implements ClientModInitializer {
    public static final String MOD_ID = "betterthirdperson";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** How fast the body catches up to the camera while walking (0-1 per tick). Higher = snappier. */
    private static final float BODY_SYNC_SPEED = 0.25f;

    /** 2 seconds (40 ticks) without any click while backpedaling -> the body turns back around. */
    private static final int BACK_FACE_TICKS = 40;

    /**
     * Counts down while S (back only) is held. > 0 means "backpedal mode": the body faces the
     * crosshair direction (camera yaw) and really walks backwards (moonwalk). It is refilled to
     * {@link #BACK_FACE_TICKS} by every attack/use/mining action. At 0 the body turns 180 degrees
     * away from the camera again and walks "forward" toward the camera.
     */
    private static int backFaceTicks = 0;

    /**
     * > 0 while the player deliberately turned around to hit something BEHIND the body (a click
     * while the camera was looking at the character's face, or a click while backpedaling). During
     * this time the crosshair may target things behind the character. It is refreshed by every
     * action and expires {@link #BACK_FACE_TICKS} ticks after the last one.
     */
    private static int behindLatchTicks = 0;

    /**
     * True when the crosshair is allowed to pick targets behind the character's back. When the
     * body faces the same way as the camera (camera looking at the character's back) and this is
     * false, anything between the camera and the character is ignored (see GameRendererMixin).
     */
    public static boolean isBehindTargetingAllowed() {
        return backFaceTicks > 0 || behindLatchTicks > 0;
    }

    /** Called right before the body is snapped to the camera because of a click. */
    private static void latchIfTurningAround(ClientPlayerEntity player, CameraState state) {
        if (Math.abs(MathHelper.wrapDegrees(state.getYaw() - player.getYaw())) > 90.0F) {
            behindLatchTicks = BACK_FACE_TICKS;
        }
    }

    /**
     * True while S is held without W and the free camera is active. Whatever way the body is
     * currently facing (turned around to face the camera, or facing away from it while
     * backpedaling after a click), {@link com.example.betterthirdperson.mixin.LivingEntityMovementMixin}
     * rotates the movement input so the character always travels toward the camera.
     */
    private static volatile boolean walkingBackwardTurnAround = false;

    public static boolean isWalkingBackwardTurnAround() {
        return walkingBackwardTurnAround;
    }

    /** True while any of forward/back/left/right is held. */
    public static boolean isMovementInputHeld(MinecraftClient client) {
        return client.options.forwardKey.isPressed()
                || client.options.backKey.isPressed()
                || client.options.leftKey.isPressed()
                || client.options.rightKey.isPressed();
    }

    /** Opens the in-game slider screen for distance/height/side offset. Unbound by default. */
    public static KeyBinding openConfigKey;

    @Override
    public void onInitializeClient() {
        Config.get().load();

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.betterthirdperson.open_config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.betterthirdperson"
        ));

        ClientTickEvents.START_CLIENT_TICK.register(BetterThirdPersonClient::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(BetterThirdPersonClient::onTick);
        ClientTickEvents.END_CLIENT_TICK.register(BetterThirdPersonClient::onOpenConfigKey);
        CrosshairOverlay.register();
        LOGGER.info("Better Third Person initialized: free camera active in third person back view.");
    }

    private static void onOpenConfigKey(MinecraftClient client) {
        while (openConfigKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new BetterThirdPersonConfigScreen(null));
            }
        }
    }

    /** True while the free-camera behaviour should apply: third person BACK view only (not the front-facing one). */
    public static boolean isActive(MinecraftClient client) {
        return client.player != null
                && client.options.getPerspective() == Perspective.THIRD_PERSON_BACK;
    }

    /** Same check, from inside the Camera mixin, which only knows thirdPerson/inverseView flags. */
    public static boolean isActive(boolean thirdPerson, boolean inverseView) {
        return thirdPerson && !inverseView;
    }

    /**
     * Runs BEFORE vanilla handles this tick's clicks. If attack/use is pressed, the body is
     * turned to the camera direction immediately, so the very first hit/placement already goes
     * out with the right facing (important for stairs etc.) even if the body was turned around.
     */
    private static void onStartTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.currentScreen != null || !isActive(client)) {
            return;
        }
        CameraState state = CameraState.get();
        if (!state.isInitialized()) {
            return;
        }
        if (client.options.attackKey.isPressed() || client.options.useKey.isPressed()) {
            latchIfTurningAround(player, state);
            player.setYaw(state.getYaw());
            player.setBodyYaw(state.getYaw());
            player.setPitch(state.getPitch());
        }
    }

    private static void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        CameraState state = CameraState.get();

        if (!isActive(client)) {
            // Not in free third person right now (first person, front view, etc). Keep the stored
            // camera angle glued to the player so re-entering third person back doesn't snap the
            // view to a stale direction from last time.
            state.syncFrom(player.getYaw(), player.getPitch());
            walkingBackwardTurnAround = false;
            backFaceTicks = 0;
            behindLatchTicks = 0;
            return;
        }

        if (!state.isInitialized()) {
            state.syncFrom(player.getYaw(), player.getPitch());
        }

        boolean forward = client.options.forwardKey.isPressed();
        boolean back = client.options.backKey.isPressed();
        boolean left = client.options.leftKey.isPressed();
        boolean right = client.options.rightKey.isPressed();
        boolean moving = forward || back || left || right;
        // Broadened on purpose: relying only on attackKey/useKey.isPressed() (this exact tick) or
        // handSwinging misses ticks in between rapid clicks (mining a block by clicking repeatedly
        // rather than holding down), where all three can briefly be false at once. On those ticks
        // the body used to fall through to the slow "moving" lerp (if also walking) or freeze in
        // place, which looked like the turn-to-target wasn't instant. isBreakingBlock() and
        // attackCooldown keep "acting" true for the whole swing/mining sequence, not just the
        // exact tick the key state was polled.
        boolean acting = client.options.attackKey.isPressed()
                || client.options.useKey.isPressed()
                || player.isUsingItem()
                || player.handSwinging
                || client.attackCooldown > 0
                || (client.interactionManager != null && client.interactionManager.isBreakingBlock());

        // S only (no W): the body normally turns 180 degrees to face the direction of travel
        // (toward the camera) and walks "forward". As soon as the player hits/places/breaks/uses
        // something, the body turns back to the crosshair direction and walks truly backwards
        // (moonwalk) for as long as clicks keep coming; after 2 seconds without any action it
        // turns around again. LivingEntityMovementMixin keeps the world travel direction
        // identical in both modes.
        boolean backOnly = back && !forward;
        walkingBackwardTurnAround = backOnly;
        if (backOnly) {
            if (acting) {
                backFaceTicks = BACK_FACE_TICKS;
            } else if (backFaceTicks > 0) {
                backFaceTicks--;
            }
        } else {
            backFaceTicks = 0;
        }
        boolean backpedaling = backOnly && backFaceTicks > 0;

        if (acting) {
            if (behindLatchTicks > 0) {
                behindLatchTicks = BACK_FACE_TICKS;
            }
        } else if (behindLatchTicks > 0) {
            behindLatchTicks--;
        }

        if (moving) {
            float targetYaw = (backOnly && !backpedaling) ? state.getYaw() + 180.0F : state.getYaw();
            boolean snap = acting || backpedaling;
            // Turning around (body currently more than 90 degrees away from where it should face,
            // e.g. camera looking at the character's face and W is pressed): flip instantly instead
            // of easing through a sideways rotation. Only S-only turn-around keeps the smooth turn.
            if (!backOnly
                    && Math.abs(MathHelper.wrapDegrees(targetYaw - player.getYaw())) > 90.0F) {
                snap = true;
            }
            if (acting) {
                latchIfTurningAround(player, state);
            }
            float newYaw = snap
                    ? targetYaw
                    : lerpAngle(BODY_SYNC_SPEED, player.getYaw(), targetYaw);
            player.setYaw(newYaw);
            player.setBodyYaw(newYaw);
            player.setPitch(state.getPitch());
        } else if (acting) {
            // Standing still and clicking: always turn to face the crosshair direction, even if
            // the free camera is looking at the character's face. The body stays there afterwards
            // (S is not held, so there is nothing that turns it back).
            latchIfTurningAround(player, state);
            player.setYaw(state.getYaw());
            player.setBodyYaw(state.getYaw());
            player.setPitch(state.getPitch());
        }
        // else: standing still and not interacting -> leave the body exactly where it is (free-look).
    }

    private static float lerpAngle(float delta, float start, float end) {
        float diff = MathHelper.wrapDegrees(end - start);
        return start + delta * diff;
    }
}
