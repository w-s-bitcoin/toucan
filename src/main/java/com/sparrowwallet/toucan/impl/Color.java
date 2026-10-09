package com.sparrowwallet.toucan.impl;

import static com.sparrowwallet.toucan.impl.Utils.clamped;

public class Color {
    final double r;
    final double g;
    final double b;

    public Color(double r, double g, double b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    static Color fromUint8Values(int r, int g, int b) {
        // The C++ reference divides palette channels in double precision.
        return new Color((double) r / 255, (double) g / 255, (double) b / 255);
    }

    Color lighten(double t) {
        return this.lerpTo(Colors.WHITE, t);
    }

    Color darken(double t) {
        return this.lerpTo(Colors.BLACK, t);
    }

    double luminance() {
        // Match the reference's powf(..., 2) and sqrtf: round the inputs,
        // squared terms, sum, and result to float, preserving color ordering.
        float red = (float)(0.299 * this.r);
        float green = (float)(0.587 * this.g);
        float blue = (float)(0.114 * this.b);
        return (float)Math.sqrt(red * red + green * green + blue * blue);
    }

    Color burn(double t) {
        double f = Math.max(1.0f - t, 1.0e-7f);
        return new Color(
                Math.min(1.0f - (1.0f - this.r) / f, 1.0f),
                Math.min(1.0f - (1.0f - this.g) / f, 1.0f),
                Math.min(1.0f - (1.0f - this.b) / f, 1.0f)
                );
    }

    Color lerpTo(Color other, double t) {
        double f = clamped(t);
        double red = clamped(this.r * (1 - f) + other.r * f);
        double green = clamped(this.g * (1 - f) + other.g * f);
        double blue = clamped(this.b * (1 - f) + other.b * f);
        return new Color(red, green, blue);
    }
}
