package com.example.betterthirdperson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Live-adjustable camera placement values, editable in-game via
 * {@link BetterThirdPersonConfigScreen} and persisted to
 * config/betterthirdperson.properties.
 */
public class Config {
    private static final Config INSTANCE = new Config();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("betterthirdperson.properties");

    public static final float DEFAULT_DISTANCE = 5.0f;
    public static final float DEFAULT_UP_OFFSET = 0.55f;
    public static final float DEFAULT_RIGHT_OFFSET = 0.6f;

    public static final float MIN_DISTANCE = 1.0f;
    public static final float MAX_DISTANCE = 10.0f;
    public static final float MIN_UP_OFFSET = -2.0f;
    public static final float MAX_UP_OFFSET = 3.0f;
    public static final float MIN_RIGHT_OFFSET = -3.0f;
    public static final float MAX_RIGHT_OFFSET = 3.0f;

    public static final float DEFAULT_EXTRA_REACH = 0.0f;
    public static final float MIN_EXTRA_REACH = 0.0f;
    public static final float MAX_EXTRA_REACH = 6.0f;

    /** How far back the camera sits, in blocks. */
    public volatile float distance = DEFAULT_DISTANCE;
    /** How far up the camera sits, in blocks. */
    public volatile float upOffset = DEFAULT_UP_OFFSET;
    /** How far to the right the camera is offset, in blocks. */
    public volatile float rightOffset = DEFAULT_RIGHT_OFFSET;
    /**
     * Extra blocks/units added on top of the vanilla attack and block-interaction
     * range while the free third-person camera is active (see {@link com.example.betterthirdperson.mixin.GameRendererMixin}).
     * Note: this only extends the CLIENT-side crosshair raycast (what you can target
     * and therefore what gets attacked/broken). It does not change the underlying
     * reach attribute, so a server with its own reach/anti-cheat checks can still
     * reject interactions past the vanilla range.
     */
    public volatile float extraReach = DEFAULT_EXTRA_REACH;

    private Config() {
    }

    public static Config get() {
        return INSTANCE;
    }

    public void load() {
        if (!Files.exists(FILE)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            props.load(in);
            this.distance = parse(props, "distance", this.distance);
            this.upOffset = parse(props, "upOffset", this.upOffset);
            this.rightOffset = parse(props, "rightOffset", this.rightOffset);
            this.extraReach = parse(props, "extraReach", this.extraReach);
        } catch (IOException e) {
            BetterThirdPersonClient.LOGGER.warn("Failed to load betterthirdperson config", e);
        }
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("distance", Float.toString(this.distance));
        props.setProperty("upOffset", Float.toString(this.upOffset));
        props.setProperty("rightOffset", Float.toString(this.rightOffset));
        props.setProperty("extraReach", Float.toString(this.extraReach));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                props.store(out, "Better Third Person camera settings");
            }
        } catch (IOException e) {
            BetterThirdPersonClient.LOGGER.warn("Failed to save betterthirdperson config", e);
        }
    }

    private static float parse(Properties props, String key, float fallback) {
        try {
            return Float.parseFloat(props.getProperty(key, Float.toString(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
