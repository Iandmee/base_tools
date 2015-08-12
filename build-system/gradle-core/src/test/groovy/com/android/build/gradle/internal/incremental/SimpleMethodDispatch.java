/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.incremental;

/**
 * Created by jedo on 7/23/15.
 */
public class SimpleMethodDispatch extends BaseClass {

    private int field = 183*4;
    public int otherField = 40;

    public long getIntValue(int value) {
        System.out.println("out !");
        return calculateIntValue(value, otherField);
    }

    private long calculateIntValue(Integer value, int otherValue) {
        int newValue = value + otherValue;
        System.out.println("hello " + newValue);
        return field / (newValue);
    }

    public String getStringValue() {
        return protectedField + publicField + packagePrivateField;
    }

    public String invokeAllParent() {
        return super.packagePrivateMethod() + super.protectedMethod() + super.publicMethod();
    }
}
