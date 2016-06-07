/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.pixelprobe;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;

/**
 * The shape information for an image's shape layer.
 * Contains information about the data (path) as well as styling.
 */
public final class ShapeInfo {
    private final Style style;
    private final Path2D path;

    private final Paint fillPaint;
    private final float fillOpacity;

    private final Stroke stroke;
    private final Paint strokePaint;
    private final float strokeOpacity;
    private final BlendMode strokeBlendMode;
    private final Alignment strokeAlignment;

    /**
     * The shape style defines how the shape's path should be rendered.
     */
    public enum Style {
        /**
         * Fill the shape according to its winding rule.
         */
        FILL,
        /**
         * Apply a stroke on the shape but don't fill it.
         */
        STROKE,
        /**
         * Fill the shape and apply a stroke.
         */
        FILL_AND_STROKE,
        /**
         * Do not render the shape.
         */
        NONE;

        /**
         * Returns a style for the specified fill and stroke configuration.
         */
        public static Style from(boolean fillEnabled, boolean strokeEnabled) {
            if (fillEnabled) {
                return strokeEnabled ? FILL_AND_STROKE : FILL;
            } else if (strokeEnabled) {
                return STROKE;
            }
            return NONE;
        }
    }

    /**
     * Defines how the stroke should be aligned over the shape's path.
     */
    public enum Alignment {
        /**
         * The stroke is drawn inside the shape defined by the path.
         */
        INSIDE,
        /**
         * The stroke is drawn centered around the path of the shape.
         */
        CENTER,
        /**
         * The stroke is drawn outside the shape defined by the path.
         */
        OUTSIDE
    }

    ShapeInfo(Builder builder) {
        style = builder.style;
        path = builder.path;

        fillPaint = builder.fillPaint;
        fillOpacity = builder.fillOpacity;

        stroke = builder.stroke;
        strokePaint = builder.strokePaint;
        strokeOpacity = builder.strokeOpacity;
        strokeBlendMode = builder.strokeBlendMode;
        strokeAlignment = builder.strokeAlignment;
    }

    /**
     * Returns the style describing how this shape's path should be rendered.
     */
    public Style getStyle() {
        return style;
    }

    /**
     * Returns the path representing this shape.
     */
    public Path2D getPath() {
        return path;
    }

    /**
     * Returns the paint that should be used to fill this shape.
     *
     * @see #getStyle()
     * @see #getFillOpacity()
     */
    public Paint getFillPaint() {
        return fillPaint;
    }

    /**
     * Returns the opacity of the fill paint for this shape as a value
     * between 0.0 and 1.0.
     *
     * @see #getStyle()
     * @see #getFillPaint()
     */
    public float getFillOpacity() {
        return fillOpacity;
    }

    /**
     * Returns the stroke properties for this shape.
     *
     * @see #getStyle()
     * @see #getStrokePaint()
     * @see #getStrokeOpacity()
     */
    public Stroke getStroke() {
        return stroke;
    }

    /**
     * Returns the paint that should be used to stroke this shape.
     *
     * @see #getStyle()
     * @see #getStroke()
     * @see #getStrokeOpacity()
     */
    public Paint getStrokePaint() {
        return strokePaint;
    }

    /**
     * Returns the opacity of the fill paint for this shape as a value
     * between 0.0 and 1.0.
     *
     * @see #getStyle()
     * @see #getStroke()
     * @see #getStrokePaint()
     */
    public float getStrokeOpacity() {
        return strokeOpacity;
    }

    /**
     * Returns the stroke's blending mode.
     */
    public BlendMode getStrokeBlendMode() {
        return strokeBlendMode;
    }

    /**
     * Returns the alignment of the stroke over this shape's path.
     */
    public Alignment getStrokeAlignment() {
        return strokeAlignment;
    }

    @SuppressWarnings("UseJBColor")
    public static final class Builder {
        private Style style = Style.FILL;
        private Path2D path = new GeneralPath();

        private Paint fillPaint = Color.BLACK;
        private float fillOpacity = 1.0f;

        private Stroke stroke = new BasicStroke(0.0f);
        private Paint strokePaint = Color.BLACK;
        private float strokeOpacity = 1.0f;
        private BlendMode strokeBlendMode = BlendMode.NORMAL;
        private Alignment strokeAlignment = Alignment.CENTER;

        public Builder style(Style style) {
            this.style = style;
            return this;
        }

        public Builder path(Path2D path) {
            this.path = path;
            return this;
        }

        public Builder fillPaint(Paint paint) {
            fillPaint = paint;
            return this;
        }

        public Builder fillOpacity(float opacity) {
            fillOpacity = opacity;
            return this;
        }

        public Builder stroke(Stroke stroke) {
            this.stroke = stroke;
            return this;
        }

        public Builder strokePaint(Paint paint) {
            strokePaint = paint;
            return this;
        }

        public Builder strokeOpacity(float opacity) {
            strokeOpacity = opacity;
            return this;
        }

        public Builder strokeBlendMode(BlendMode blendMode) {
            strokeBlendMode = blendMode;
            return this;
        }

        public Builder strokeAlignment(Alignment alignment) {
            strokeAlignment = alignment;
            return this;
        }

        public ShapeInfo build() {
            return new ShapeInfo(this);
        }
    }
}
