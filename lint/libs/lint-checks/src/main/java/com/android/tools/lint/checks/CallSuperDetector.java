/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;
import static com.android.tools.lint.detector.api.LintUtils.skipParentheses;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.UastScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.USuperExpression;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

/**
 * Makes sure that methods call super when overriding methods.
 */
public class CallSuperDetector extends Detector implements UastScanner {
    private static final String CALL_SUPER_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "CallSuper";
    private static final String ON_DETACHED_FROM_WINDOW = "onDetachedFromWindow";
    private static final String ON_VISIBILITY_CHANGED = "onVisibilityChanged";

    private static final Implementation IMPLEMENTATION = new Implementation(
            CallSuperDetector.class,
            Scope.JAVA_FILE_SCOPE);

    /** Missing call to super */
    public static final Issue ISSUE = Issue.create(
            "MissingSuperCall",
            "Missing Super Call",

            "Some methods, such as `View#onDetachedFromWindow`, require that you also " +
            "call the super implementation as part of your method.",

            Category.CORRECTNESS,
            9,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Constructs a new {@link CallSuperDetector} check */
    public CallSuperDetector() {
    }

    // ---- Implements UastScanner ----


    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NonNull UMethod method) {
                checkCallSuper(context, method);
            }
        };
    }

    private static void checkCallSuper(@NonNull JavaContext context,
            @NonNull UMethod method) {

        PsiMethod superMethod = getRequiredSuperMethod(context, method);
        if (superMethod != null) {
            if (!SuperCallVisitor.callsSuper(method, superMethod)) {
                String methodName = method.getName();
                String message = "Overriding method should call `super."
                        + methodName + "`";
                Location location = context.getNameLocation(method);
                context.report(ISSUE, method, location, message);
            }
        }
    }

    /**
     * Checks whether the given method overrides a method which requires the super method
     * to be invoked, and if so, returns it (otherwise returns null)
     */
    @Nullable
    private static PsiMethod getRequiredSuperMethod(@NonNull JavaContext context,
            @NonNull UMethod method) {

        JavaEvaluator evaluator = context.getEvaluator();
        PsiMethod directSuper = evaluator.getSuperMethod(method);
        if (directSuper == null) {
            return null;
        }

        String name = method.getName();
        if (ON_DETACHED_FROM_WINDOW.equals(name)) {
            // No longer annotated on the framework method since it's
            // now handled via onDetachedFromWindowInternal, but overriding
            // is still dangerous if supporting older versions so flag
            // this for now (should make annotation carry metadata like
            // compileSdkVersion >= N).
            if (!evaluator.isMemberInSubClassOf(method, CLASS_VIEW, false)) {
                return null;
            }
            return directSuper;
        } else if (ON_VISIBILITY_CHANGED.equals(name)) {
            // From Android Wear API; doesn't yet have an annotation
            // but we want to enforce this right away until the AAR
            // is updated to supply it once @CallSuper is available in
            // the support library
            if (!evaluator.isMemberInSubClassOf(method,
                    "android.support.wearable.watchface.WatchFaceService.Engine", false)) {
                return null;
            }
            return directSuper;
        }

        PsiAnnotation[] annotations = evaluator.getAllAnnotations(directSuper, true);
        for (PsiAnnotation annotation : annotations) {
            String signature = annotation.getQualifiedName();
            if (CALL_SUPER_ANNOTATION.equals(signature) || signature != null
                    && (signature.endsWith(".OverrideMustInvoke")
                            || signature.endsWith(".OverridingMethodsMustInvokeSuper"))) {
                return directSuper;
            }
        }

        return null;
    }

    /** Visits a method and determines whether the method calls its super method */
    private static class SuperCallVisitor extends AbstractUastVisitor {
        private final PsiMethod targetMethod;
        private boolean callsSuper;

        public static boolean callsSuper(@NonNull UMethod method,
                @NonNull PsiMethod superMethod) {
            SuperCallVisitor visitor = new SuperCallVisitor(superMethod);
            method.accept(visitor);
            return visitor.callsSuper;
        }

        private SuperCallVisitor(@NonNull PsiMethod method) {
            targetMethod = method;
        }

        @Override
        public boolean visitSuperExpression(USuperExpression node) {
            UElement parent = skipParentheses(node.getUastParent());
            if (parent instanceof UReferenceExpression) {
                PsiElement resolved = ((UReferenceExpression) parent).resolve();
                if (targetMethod.equals(resolved)) {
                    callsSuper = true;
                }
            }

            return super.visitSuperExpression(node);
        }
    }
}
