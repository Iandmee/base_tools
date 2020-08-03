/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.ide.common.gradle.model.impl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.SyncIssue;
import com.android.ide.common.gradle.model.IdeSyncIssue;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** Creates a deep copy of a {@link SyncIssue}. */
public final class IdeSyncIssueImpl implements IdeSyncIssue, Serializable {
    // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
    private static final long serialVersionUID = 3L;

    @NonNull private final String myMessage;
    @Nullable private final String myData;
    @Nullable private final List<String> myMultiLineMessage;
    private final int mySeverity;
    private final int myType;
    private final int myHashCode;

    // Used for serialization by the IDE.
    @SuppressWarnings("unused")
    public IdeSyncIssueImpl() {
        myMessage = "";
        myData = "";
        myMultiLineMessage = null;
        mySeverity = 0;
        myType = 0;

        myHashCode = 0;
    }

    public IdeSyncIssueImpl(
            @NotNull String message,
            @Nullable List<String> multiLineMessage,
            @Nullable String data,
            int severity,
            int type) {
        myMessage = message;
        myMultiLineMessage = multiLineMessage;
        myData = data;
        mySeverity = severity;
        myType = type;

        myHashCode = calculateHashCode();
    }

    @Override
    @NonNull
    public String getMessage() {
        return myMessage;
    }

    @Override
    @Nullable
    public String getData() {
        return myData;
    }

    @Override
    public int getSeverity() {
        return mySeverity;
    }

    @Override
    public int getType() {
        return myType;
    }

    @Override
    @Nullable
    public List<String> getMultiLineMessage() {
        return myMultiLineMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IdeSyncIssueImpl)) {
            return false;
        }
        IdeSyncIssueImpl issue = (IdeSyncIssueImpl) o;
        return mySeverity == issue.mySeverity
                && myType == issue.myType
                && Objects.equals(myMessage, issue.myMessage)
                && Objects.equals(myMultiLineMessage, issue.myMultiLineMessage)
                && Objects.equals(myData, issue.myData);
    }

    @Override
    public int hashCode() {
        return myHashCode;
    }

    private int calculateHashCode() {
        return Objects.hash(myMessage, myMultiLineMessage, myData, mySeverity, myType);
    }

    @Override
    public String toString() {
        return "IdeSyncIssue{"
                + "myMessage='"
                + myMessage
                + '\''
                + ", myData='"
                + myData
                + '\''
                + ", mySeverity="
                + mySeverity
                + ", myType="
                + myType
                + "}";
    }

    public static IdeSyncIssueImpl createFrom(@NonNull SyncIssue issue) {
        return new IdeSyncIssueImpl(
                issue.getMessage(),
                IdeModel.copyNewProperty(issue::getMultiLineMessage, null),
                issue.getData(),
                issue.getSeverity(),
                issue.getType());
    }
}
