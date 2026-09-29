package com.example.betterthirdperson;

import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class BetterThirdPersonConfigScreen extends Screen {
    private final Screen parent;

    public BetterThirdPersonConfigScreen(Screen parent) {
        super(Text.literal("Better Third Person"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Config config = Config.get();
        int centerX = this.width / 2;
        int y = this.height / 2 - 60;

        this.addDrawableChild(new OffsetSlider(centerX - 100, y, 200, 20,
                "Jarak kamera", Config.MIN_DISTANCE, Config.MAX_DISTANCE, config.distance,
                v -> config.distance = v));
        y += 24;

        this.addDrawableChild(new OffsetSlider(centerX - 100, y, 200, 20,
                "Ketinggian kamera", Config.MIN_UP_OFFSET, Config.MAX_UP_OFFSET, config.upOffset,
                v -> config.upOffset = v));
        y += 24;

        this.addDrawableChild(new OffsetSlider(centerX - 100, y, 200, 20,
                "Geser ke samping", Config.MIN_RIGHT_OFFSET, Config.MAX_RIGHT_OFFSET, config.rightOffset,
                v -> config.rightOffset = v));
        y += 24;

        this.addDrawableChild(new OffsetSlider(centerX - 100, y, 200, 20,
                "Tambahan jarak pukul", Config.MIN_EXTRA_REACH, Config.MAX_EXTRA_REACH, config.extraReach,
                v -> config.extraReach = v));
        y += 30;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset ke default"), button -> {
            config.distance = Config.DEFAULT_DISTANCE;
            config.upOffset = Config.DEFAULT_UP_OFFSET;
            config.rightOffset = Config.DEFAULT_RIGHT_OFFSET;
            config.extraReach = Config.DEFAULT_EXTRA_REACH;
            this.clearAndInit();
        }).dimensions(centerX - 100, y, 200, 20).build());
        y += 24;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Selesai"), button -> {
            config.save();
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
        }).dimensions(centerX - 100, y, 200, 20).build());
    }

    @Override
    public void close() {
        Config.get().save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** A slider mapped to a float range, showing its current value live in the label. */
    private static class OffsetSlider extends SliderWidget {
        private final double min;
        private final double max;
        private final String label;
        private final Consumer<Float> onChange;

        OffsetSlider(int x, int y, int width, int height, String label, double min, double max,
                     double current, Consumer<Float> onChange) {
            super(x, y, width, height, Text.literal(label + ": " + format(current)),
                    clampNormalized((current - min) / (max - min)));
            this.min = min;
            this.max = max;
            this.label = label;
            this.onChange = onChange;
        }

        private static double clampNormalized(double v) {
            return Math.max(0.0, Math.min(1.0, v));
        }

        private static String format(double v) {
            return String.format(Locale.ROOT, "%.2f", v);
        }

        private double currentValue() {
            return this.min + (this.max - this.min) * this.value;
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal(this.label + ": " + format(currentValue())));
        }

        @Override
        protected void applyValue() {
            this.onChange.accept((float) currentValue());
        }
    }
}
