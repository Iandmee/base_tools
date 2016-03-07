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

package com.android.tools.lint.psi;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiStatement;

import org.eclipse.jdt.internal.compiler.ast.Statement;

class EcjPsiIfStatement extends EcjPsiStatement implements PsiIfStatement {
    private PsiExpression mCondition;
    private PsiStatement mThenBranch;
    private PsiStatement mElseBranch;

    EcjPsiIfStatement(@NonNull EcjPsiManager manager,
            @Nullable Statement statement) {
        super(manager, statement);
    }

    void setElse(@NonNull PsiStatement elseBranch) {
        mElseBranch = elseBranch;
    }

    void setThen(@NonNull PsiStatement thenBranch) {
        mThenBranch = thenBranch;
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitIfStatement(this);
        } else {
            visitor.visitElement(this);
        }
    }

    void setCondition(@NonNull PsiExpression condition) {
        mCondition = condition;
    }

    @Nullable
    @Override
    public PsiExpression getCondition() {
        return mCondition;
    }

    @Nullable
    @Override
    public PsiStatement getThenBranch() {
        return mThenBranch;
    }

    @Nullable
    @Override
    public PsiStatement getElseBranch() {
        return mElseBranch;
    }

    @Nullable
    @Override
    public PsiKeyword getElseElement() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiJavaToken getLParenth() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiJavaToken getRParenth() {
        throw new UnimplementedLintPsiApiException();
    }
}
