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

package com.android.tools.lint.checks;

import static com.android.tools.lint.client.api.JavaParser.TYPE_STRING;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ConstantEvaluator;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.UastScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.UastLintUtils;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jetbrains.uast.UBinaryExpression;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UClassInitializer;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UIfExpression;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UPolyadicExpression;
import org.jetbrains.uast.UQualifiedReferenceExpression;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.jetbrains.uast.UastUtils;

/**
 * Detector for finding inefficiencies and errors in logging calls.
 */
public class LogDetector extends Detector implements UastScanner {
    private static final Implementation IMPLEMENTATION = new Implementation(
          LogDetector.class, Scope.JAVA_FILE_SCOPE);


    /** Log call missing surrounding if */
    public static final Issue CONDITIONAL = Issue.create(
            "LogConditional",
            "Unconditional Logging Calls",
            "The BuildConfig class (available in Tools 17) provides a constant, \"DEBUG\", " +
            "which indicates whether the code is being built in release mode or in debug " +
            "mode. In release mode, you typically want to strip out all the logging calls. " +
            "Since the compiler will automatically remove all code which is inside a " +
            "\"if (false)\" check, surrounding your logging calls with a check for " +
            "BuildConfig.DEBUG is a good idea.\n" +
            "\n" +
            "If you **really** intend for the logging to be present in release mode, you can " +
            "suppress this warning with a @SuppressLint annotation for the intentional " +
            "logging calls.",

            Category.PERFORMANCE,
            5,
            Severity.WARNING,
            IMPLEMENTATION).setEnabledByDefault(false);

    /** Mismatched tags between isLogging and log calls within it */
    public static final Issue WRONG_TAG = Issue.create(
            "LogTagMismatch",
            "Mismatched Log Tags",
            "When guarding a `Log.v(tag, ...)` call with `Log.isLoggable(tag)`, the " +
            "tag passed to both calls should be the same. Similarly, the level passed " +
            "in to `Log.isLoggable` should typically match the type of `Log` call, e.g. " +
            "if checking level `Log.DEBUG`, the corresponding `Log` call should be `Log.d`, " +
            "not `Log.i`.",

            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            IMPLEMENTATION);

    /** Log tag is too long */
    public static final Issue LONG_TAG = Issue.create(
            "LongLogTag",
            "Too Long Log Tags",
            "Log tags are only allowed to be at most 23 tag characters long.",

            Category.CORRECTNESS,
            5,
            Severity.ERROR,
            IMPLEMENTATION);

    @SuppressWarnings("SpellCheckingInspection")
    private static final String IS_LOGGABLE = "isLoggable";
    public static final String LOG_CLS = "android.util.Log";
    private static final String PRINTLN = "println";

    @Nullable
    private static String getTagForMethod(@NonNull String method) {
        switch (method) {
            case "d": return "DEBUG";
            case "e": return "ERROR";
            case "i": return "INFO";
            case "v": return "VERBOSE";
            case "w": return "WARN";
            default: return null;
        }
    }

    // ---- Implements Detector.JavaScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        return Arrays.asList(
                "d",
                "e",
                "i",
                "v",
                "w",
                PRINTLN,
                IS_LOGGABLE);
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UCallExpression node,
            @NonNull PsiMethod method) {
        JavaEvaluator evaluator = context.getEvaluator();
        if (!evaluator.isMemberInClass(method, LOG_CLS)) {
            return;
        }

        String name = method.getName();
        boolean withinConditional = IS_LOGGABLE.equals(name) ||
                checkWithinConditional(context, node.getUastParent(), node);

        // See if it's surrounded by an if statement (and it's one of the non-error, spammy
        // log methods (info, verbose, etc))
        if (("i".equals(name) || "d".equals(name) || "v".equals(name) || PRINTLN.equals(name))
                && !withinConditional
                && performsWork(node)
                && context.isEnabled(CONDITIONAL)) {
            String message = String.format("The log call Log.%1$s(...) should be " +
                            "conditional: surround with `if (Log.isLoggable(...))` or " +
                            "`if (BuildConfig.DEBUG) { ... }`",
                    node.getMethodName());
            Location location = context.getLocation(node);
            context.report(CONDITIONAL, node, location, message);
        }

        // Check tag length
        if (context.isEnabled(LONG_TAG)) {
            int tagArgumentIndex = PRINTLN.equals(name) ? 1 : 0;
            PsiParameterList parameterList = method.getParameterList();
            List<UExpression> argumentList = node.getValueArguments();
            if (evaluator.parameterHasType(method, tagArgumentIndex, TYPE_STRING)
                    && parameterList.getParametersCount() == argumentList.size()) {
                UExpression argument = argumentList.get(tagArgumentIndex);
                String tag = ConstantEvaluator.evaluateString(context, argument, true);
                if (tag != null && tag.length() > 23) {
                    String message = String.format(
                            "The logging tag can be at most 23 characters, was %1$d (%2$s)",
                            tag.length(), tag);
                    context.report(LONG_TAG, node, context.getLocation(argument), message);
                }
            }
        }
    }

    /** Returns true if the given logging call performs "work" to compute the message */
    private static boolean performsWork(
            @NonNull UCallExpression node) {
        String referenceName = node.getMethodName();
        if (referenceName == null) {
            return false;
        }
        int messageArgumentIndex = PRINTLN.equals(referenceName) ? 2 : 1;
        List<UExpression> arguments = node.getValueArguments();
        if (arguments.size() > messageArgumentIndex) {
            UExpression argument = arguments.get(messageArgumentIndex);
            if (argument == null) {
                return false;
            }
            if (argument instanceof ULiteralExpression) {
                return false;
            }
            if (argument instanceof UPolyadicExpression) {
                String string = UastUtils.evaluateString(argument);
                //noinspection VariableNotUsedInsideIf
                if (string != null) { // does it resolve to a constant?
                    return false;
                }
            } else if (argument instanceof UBinaryExpression) {
                // Not currently a polyadic expr in UAST: repeat check done for polyadic
                String string = UastUtils.evaluateString(argument);
                //noinspection VariableNotUsedInsideIf
                if (string != null) { // does it resolve to a constant?
                    return false;
                }
            } else if (argument instanceof USimpleNameReferenceExpression) {
                // Just a simple local variable/field reference
                return false;
            } else if (argument instanceof UQualifiedReferenceExpression) {
                String string = UastUtils.evaluateString(argument);
                //noinspection VariableNotUsedInsideIf
                if (string != null) {
                    return false;
                }
                PsiElement resolved = ((UQualifiedReferenceExpression) argument).resolve();
                if (resolved instanceof PsiVariable) {
                    // Just a reference to a property/field, parameter or variable
                    return false;
                }
            }

            // Method invocations etc
            return true;
        }

        return false;
    }

    private static boolean checkWithinConditional(
            @NonNull JavaContext context,
            @Nullable UElement curr,
            @NonNull UCallExpression logCall) {
        while (curr != null) {
            if (curr instanceof UIfExpression) {

                UExpression condition = ((UIfExpression) curr).getCondition();
                if (condition instanceof UQualifiedReferenceExpression) {
                    condition = getLastInQualifiedChain((UQualifiedReferenceExpression) condition);
                }

                if (condition instanceof UCallExpression) {
                    UCallExpression call = (UCallExpression) condition;
                    if (IS_LOGGABLE.equals(call.getMethodName())) {
                        checkTagConsistent(context, logCall, call);
                    }
                }

                return true;
            } else if (curr instanceof UCallExpression
                    || curr instanceof UMethod
                    || curr instanceof UClassInitializer
                    || curr instanceof UField
                    || curr instanceof UClass) { // static block
                break;
            }
            curr = curr.getUastParent();
        }
        return false;
    }

    /** Checks that the tag passed to Log.s and Log.isLoggable match */
    private static void checkTagConsistent(JavaContext context, UCallExpression logCall,
            UCallExpression isLoggableCall) {
        List<UExpression> isLoggableArguments = isLoggableCall.getValueArguments();
        List<UExpression> logArguments = logCall.getValueArguments();
        if (isLoggableArguments.isEmpty() || logArguments.isEmpty()) {
            return;
        }
        UExpression isLoggableTag = isLoggableArguments.get(0);
        UExpression logTag = logArguments.get(0);

        String logCallName = logCall.getMethodName();
        if (logCallName == null) {
            return;
        }
        boolean isPrintln = PRINTLN.equals(logCallName);
        if (isPrintln && logArguments.size() > 1) {
            logTag = logArguments.get(1);
        }

        if (logTag != null) {
            if (!areLiteralsEqual(isLoggableTag, logTag) &&
                    !UastLintUtils.areIdentifiersEqual(isLoggableTag, logTag)) {
                PsiNamedElement resolved1 = UastUtils.tryResolveNamed(isLoggableTag);
                PsiNamedElement resolved2 = UastUtils.tryResolveNamed(logTag);
                if ((resolved1 == null || resolved2 == null || !resolved1.equals(resolved2))
                        && context.isEnabled(WRONG_TAG)) {
                    Location location = context.getLocation(logTag);
                    Location alternate = context.getLocation(isLoggableTag);
                    alternate.setMessage("Conflicting tag");
                    location.setSecondary(alternate);
                    String isLoggableDescription = resolved1 != null
                            ? resolved1.getName()
                            : isLoggableTag.asRenderString();
                    String logCallDescription = resolved2 != null
                            ? resolved2.getName()
                            : logTag.asRenderString();
                    String message = String.format(
                            "Mismatched tags: the `%1$s()` and `isLoggable()` calls typically " +
                                    "should pass the same tag: `%2$s` versus `%3$s`",
                            logCallName,
                            isLoggableDescription,
                            logCallDescription);
                    context.report(WRONG_TAG, isLoggableCall, location, message);
                }
            }
        }

        // Check log level versus the actual log call type (e.g. flag
        //    if (Log.isLoggable(TAG, Log.DEBUG) Log.info(TAG, "something")

        if (logCallName.length() != 1 || isLoggableArguments.size() < 2) { // e.g. println
            return;
        }
        UExpression isLoggableLevel = isLoggableArguments.get(1);
        if (isLoggableLevel == null) {
            return;
        }
        PsiNamedElement resolved = UastUtils.tryResolveNamed(isLoggableLevel);
        if (resolved == null) {
            return;
        }
        if (resolved instanceof PsiVariable) {
            PsiClass containingClass = UastUtils.getContainingClass(resolved);
            if (containingClass == null
                    || !"android.util.Log".equals(containingClass.getQualifiedName())
                    || resolved.getName() == null
                    || resolved.getName().equals(getTagForMethod(logCallName))) {
                return;
            }

            String expectedCall = resolved.getName().substring(0, 1)
                    .toLowerCase(Locale.getDefault());

            String message = String.format(
                    "Mismatched logging levels: when checking `isLoggable` level `%1$s`, the " +
                            "corresponding log call should be `Log.%2$s`, not `Log.%3$s`",
                    resolved.getName(), expectedCall, logCallName);
            Location location = context.getCallLocation(logCall, false, false);
            Location alternate = context.getLocation(isLoggableLevel);
            alternate.setMessage("Conflicting tag");
            location.setSecondary(alternate);
            context.report(WRONG_TAG, isLoggableCall, location, message);
        }
    }

    @NonNull
    private static UExpression getLastInQualifiedChain(@NonNull UQualifiedReferenceExpression node) {
        UExpression last = node.getSelector();
        while (last instanceof UQualifiedReferenceExpression) {
            last = ((UQualifiedReferenceExpression) last).getSelector();
        }
        return last;
    }

    private static boolean areLiteralsEqual(UExpression first, UExpression second) {
        if (!(first instanceof ULiteralExpression)) {
            return false;
        }

        if (!(second instanceof ULiteralExpression)) {
            return false;
        }

        Object firstValue = ((ULiteralExpression) first).getValue();
        Object secondValue = ((ULiteralExpression) second).getValue();

        if (firstValue == null) {
            return secondValue == null;
        }

        return firstValue.equals(secondValue);
    }
}
