package com.android.tools.agent.layoutinspector.property;

import android.view.Gravity;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntFunction;

public class GravityIntMapping implements IntFunction<Set<String>> {
    private IntFlagMapping gravityIntFlagMapping = new IntFlagMapping();

    public GravityIntMapping() {
        gravityIntFlagMapping.add(Gravity.FILL, Gravity.FILL, "fill");

        gravityIntFlagMapping.add(Gravity.FILL_VERTICAL, Gravity.FILL_VERTICAL, "fill_vertical");
        gravityIntFlagMapping.add(Gravity.FILL_VERTICAL, Gravity.TOP, "top");
        gravityIntFlagMapping.add(Gravity.FILL_VERTICAL, Gravity.BOTTOM, "bottom");

        gravityIntFlagMapping.add(
                Gravity.FILL_HORIZONTAL, Gravity.FILL_HORIZONTAL, "fill_horizontal");
        gravityIntFlagMapping.add(Gravity.FILL_HORIZONTAL, Gravity.LEFT, "left");
        gravityIntFlagMapping.add(Gravity.FILL_HORIZONTAL, Gravity.RIGHT, "right");

        gravityIntFlagMapping.add(Gravity.FILL, Gravity.CENTER, "center");
        gravityIntFlagMapping.add(
                Gravity.FILL_VERTICAL, Gravity.CENTER_VERTICAL, "center_vertical");
        gravityIntFlagMapping.add(
                Gravity.FILL_HORIZONTAL, Gravity.CENTER_HORIZONTAL, "center_horizontal");

        gravityIntFlagMapping.add(Gravity.CLIP_VERTICAL, Gravity.CLIP_VERTICAL, "clip_vertical");
        gravityIntFlagMapping.add(
                Gravity.CLIP_HORIZONTAL, Gravity.CLIP_HORIZONTAL, "clip_horizontal");
    }

    @Override
    public Set<String> apply(int value) {
        Set<String> values = gravityIntFlagMapping.of(value);
        if ((value & Gravity.RELATIVE_LAYOUT_DIRECTION) != 0) {
            values = new HashSet(values);
            if (values.remove("left")) {
                values.add("start");
            }
            if (values.remove("right")) {
                values.add("end");
            }
            values = Collections.unmodifiableSet(values);
        }
        return values;
    }
}
