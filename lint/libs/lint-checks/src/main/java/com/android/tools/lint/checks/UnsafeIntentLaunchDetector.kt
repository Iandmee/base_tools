/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_EXPORTED
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PERMISSION
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastAssignment
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.isReturningLambdaResult
import com.android.tools.lint.detector.api.isScopingThis
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedName
import org.jetbrains.uast.isNullLiteral
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Element
import java.util.EnumSet

class UnsafeIntentLaunchDetector : Detector(), SourceCodeScanner, XmlScanner {

    private val registerReceiverMethods = listOf(
        "registerReceiver",
        "registerReceiverAsUser",
        "registerReceiverForAllUsers",
    )

    override fun getApplicableMethodNames() = listOf(
        "getParcelableExtra",
        "getParcelable",
        "getIntent",
        "parseUri",
    ) + registerReceiverMethods

    override fun applicableSuperClasses() = listOf(
        "android.app.Activity",
        "android.content.BroadcastReceiver",
        "android.app.Service",
    )

    override fun getApplicableElements() = listOf(
        SdkConstants.TAG_ACTIVITY,
        SdkConstants.TAG_SERVICE,
        SdkConstants.TAG_RECEIVER,
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val exportedAttr = element.getAttributeNS(ANDROID_URI, ATTR_EXPORTED)
        if ("true" == exportedAttr || exportedAttr.isEmpty() && element.getElementsByTagName("intent-filter").length > 0) {
            val permission = element.getAttributeNS(ANDROID_URI, ATTR_PERMISSION)
            if (!isProbablyProtectedBySignaturePermission(permission)) {
                var componentName = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
                if (componentName.startsWith(".")) componentName =
                    context.project.`package` + componentName
                storeUnprotectedComponents(context, componentName)
            }
        }
    }

    // Any permission that is not declared by the system as normal permission is considered as a signature protected permission.
    // It could be hard to actually check if the permission is actually a signature permission.
    private fun isProbablyProtectedBySignaturePermission(permission: String?): Boolean {
        return !permission.isNullOrBlank() && !KNOWN_NORMAL_PERMISSIONS.contains(permission)
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        // This method handles unsafe intent passed in as parameter to certain methods by the platform.
        val evaluator = context.evaluator
        val methodNames = when {
            evaluator.extendsClass(declaration.javaPsi, ACTIVITY_CLASS, true) -> UNSAFE_INTENT_AS_PARAMETER_METHODS[ACTIVITY_CLASS]
            evaluator.extendsClass(declaration.javaPsi, SERVICE_CLASS, true) -> UNSAFE_INTENT_AS_PARAMETER_METHODS[SERVICE_CLASS]
            evaluator.extendsClass(declaration.javaPsi, BROADCAST_RECEIVER_CLASS, true)
            -> UNSAFE_INTENT_AS_PARAMETER_METHODS[BROADCAST_RECEIVER_CLASS]
            else -> return
        } ?: return
        for (methodName in methodNames) {
            for (psiMethod in declaration.javaPsi.findMethodsByName(methodName, false)) {
                val method = psiMethod.toUElementOfType<UMethod>()
                val intentParam = method?.javaPsi?.parameterList?.parameters?.firstOrNull {
                    it.type.canonicalText == INTENT_CLASS
                }?.toUElementOfType<UParameter>()
                val visitor = IntentLaunchChecker(
                    initial = setOf(intentParam ?: return),
                    context = context,
                    location = context.getLocation(intentParam.sourcePsi),
                )
                method.accept(visitor)
                if (visitor.launched) {
                    storeIncidentsToPartialResults(context, visitor)
                }
            }
        }
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        if (method.name in registerReceiverMethods && evaluator.isMemberInSubClassOf(method, CONTEXT_CLASS)) {
            // register receiver at runtime methods, figure out if it is registered as unprotected.
            processRuntimeReceiver(context, node, method)
        } else if (isUnParcellingIntentMethods(evaluator, method) or isParseUnsafeUri(evaluator, node, method)) {
            // methods that launch Intent. Figure out if the Intent is launched.
            val visitor = IntentLaunchChecker(
                initial = setOf(node),
                context = context,
                location = context.getLocation(node)
            )
            val containingMethod = node.getParentOfType(UMethod::class.java)
            containingMethod?.accept(visitor)
            if (visitor.launched) {
                if (visitor.unprotectedReceiver) {
                    // The registered-at-runtime receiver case, report the issue immediately.
                    val message = """
                        This intent could be coming from an untrusted source. It is later launched by \
                        an unprotected component. You could either make the component \
                        protected; or sanitize this intent using \
                        androidx.core.content.IntentSanitizer.
                    """.trimIndent()
                    context.report(Incident(ISSUE, visitor.location, message))
                } else {
                    storeIncidentsToPartialResults(context, visitor)
                }
            }
        }
    }

    private fun isParseUnsafeUri(evaluator: JavaEvaluator, call: UCallExpression, method: PsiMethod): Boolean {
        if (method.name == "parseUri" && evaluator.isMemberInClass(method, INTENT_CLASS)) {
            val intentArg = call.getArgumentForParameter(0)?.skipParenthesizedExprDown()
            val getUriStringCall = if (intentArg is USimpleNameReferenceExpression) {
                findLastAssignment(intentArg.resolve() as? PsiVariable ?: return false, call)
            } else intentArg

            val getUriStringMethod = (getUriStringCall?.findSelector() as? UCallExpression)?.resolve() ?: return false
            return isUnParcellingStringMethods(evaluator, getUriStringMethod)
        } else return false

    }

    private fun isUnParcellingIntentMethods(evaluator: JavaEvaluator, method: PsiMethod): Boolean {
        return (method.name == "getParcelableExtra") && evaluator.isMemberInSubClassOf(method, INTENT_CLASS)
                || method.name == "getParcelable" && evaluator.isMemberInSubClassOf(method, "android.os.Bundle")
                || method.name == "getIntent" && evaluator.isMemberInSubClassOf(method, CONTEXT_CLASS)
    }

    private fun isUnParcellingStringMethods(evaluator: JavaEvaluator, method: PsiMethod): Boolean {
        return (method.name == "getStringExtra") && evaluator.isMemberInSubClassOf(method, INTENT_CLASS)
                || method.name == "getString" && evaluator.isMemberInSubClassOf(method, "android.os.Bundle")
    }

    private fun processRuntimeReceiver(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        // The parameter positions vary across the various registerReceiver*() methods, so rather
        // than hardcode them we simply look them up based on the parameter name and type.
        val receiverArg =
            UastLintUtils.findArgument(node, method, BROADCAST_RECEIVER_CLASS) ?: return
        if (receiverArg.isNullLiteral()) return
        val filterArg = UastLintUtils.findArgument(node, method, "android.content.IntentFilter") ?: return
        val flagsArg = UastLintUtils.findArgument(node, method, TYPE_INT)

        val evaluator = ConstantEvaluator().allowFieldInitializers()
        val flags = evaluator.evaluate(flagsArg) as? Int
        if (flags != null && (flags and RECEIVER_NOT_EXPORTED) != 0) return

        val (isProtected, _) = BroadcastReceiverUtils.checkIsProtectedReceiverAndReturnUnprotectedActions(
            filterArg,
            node,
            evaluator
        )
        if (!isProtected) {
            val receiverVar = receiverArg.tryResolve() as? PsiVariable ?: return
            val receiverAssignment = findLastAssignment(receiverVar, node)?.skipParenthesizedExprDown() ?: return
            val receiverConstructor = receiverAssignment.findSelector() as? UCallExpression
            val unprotectedReceiverClassName = receiverConstructor?.classReference.getQualifiedName() ?: return
            storeUnprotectedComponents(context, unprotectedReceiverClassName)
        }
    }

    private fun storeUnprotectedComponents(
        context: Context,
        unprotectedComponentName: String
    ) {
        val lintMap = context.getPartialResults(ISSUE).map()
        val unprotectedComponents = lintMap.getMap(KEY_UNPROTECTED) ?: map().also { lintMap.put(KEY_UNPROTECTED, it) }
        // the value of the lintMap is not used. only the key is used later.
        unprotectedComponents.put(unprotectedComponentName, true)
    }

    private fun storeIncidentsToPartialResults(
        context: Context,
        visitor: IntentLaunchChecker
    ) {
        val lintMap = context.getPartialResults(ISSUE).map()
        val incidents = lintMap.getMap(KEY_INCIDENTS) ?: map().also { lintMap.put(KEY_INCIDENTS, it) }
        // key is not important. so the size of the map is used to make it unique.
        incidents.put(
            incidents.size.toString(),
            map().apply {
                put(KEY_LOCATION, visitor.location)
                put(KEY_SECONDARY_LOCATION, visitor.location.secondary ?: return)
                put(KEY_INCIDENT_CLASS, visitor.incidentClass ?: return)
            }
        )
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.isGlobalAnalysis()) {
            checkPartialResults(context, context.getPartialResults(ISSUE))
        }
    }

    override fun checkPartialResults(context: Context, partialResults: PartialResult) {
        val incidents = partialResults.map().getMap(KEY_INCIDENTS) ?: return
        val unprotectedComponents = partialResults.map().getMap(KEY_UNPROTECTED) ?: return
        for (key in incidents) {
            val incidentMap = incidents.getMap(key)
            val incidentComponent = incidentMap?.get(KEY_INCIDENT_CLASS) ?: continue
            if (unprotectedComponents.containsKey(incidentComponent)) {
                val location = incidentMap.getLocation(KEY_LOCATION) ?: continue
                location.secondary = incidentMap.getLocation(KEY_SECONDARY_LOCATION)
                val message = """
                    This intent could be coming from an untrusted source. It is later launched by \
                    an unprotected component $incidentComponent. You could either make the component \
                    $incidentComponent protected; or sanitize this intent using \
                    androidx.core.content.IntentSanitizer.
                """.trimIndent()
                context.report(Incident(ISSUE, location, message))
            }
        }
    }

    private inner class IntentLaunchChecker(
        initial: Collection<UElement>,
        var context: JavaContext,
        var location: Location,
        var incidentClass: String? = null,
        var launched: Boolean = false,
        var returned: Boolean = false,
        var unprotectedReceiver: Boolean = false,
        var resolveCallDepth: Int = 0
    ) : DataFlowAnalyzer(initial) {

        override fun returnsSelf(call: UCallExpression): Boolean {
            // intent = getIntent().getParcelableExtra() could have been considered chained builder without this override
            // and falsely identify the getIntent() calls as an issue.
            if (call.methodName in INTENT_METHODS_RETURNS_INTENT_BUT_NOT_SELF &&
                call.receiverType?.canonicalText == INTENT_CLASS
            ) {
                return false
            }

            if (isReturningLambdaResult(call)) {
                for (lambda in call.valueArguments) {
                    if (lambda !is ULambdaExpression) break
                    // call's arguments could either be empty (in case of run, with, apply) or the context (in case of let, also)
                    val tracked =
                        (if (lambda.valueParameters.isEmpty()) getThisExpression(lambda.body) else lambda.valueParameters[0]) ?: break
                    val returnsTracker = ReturnsTracker(context, tracked)
                    lambda.body.accept(returnsTracker)
                    if (returnsTracker.returned) return true
                }
            }

            return super.returnsSelf(call)
        }

        override fun argument(call: UCallExpression, reference: UElement) {
            if (incidentClass == null) {
                // This method could be called recursively, see else branch below. The top level method call would be in the incident class.
                incidentClass = call.getParentOfType(UClass::class.java)?.qualifiedName
                if (incidentClass == null) {
                    // must be an anonymous BroadcastReceiver. It has to be registered at runtime.
                    unprotectedReceiver = handleAnonymousBroadcastReceiver(call)
                }
            }
            if (isIntentLaunchedBySystem(context.evaluator, call)) {
                launched = true
                location.secondary = context.getLocation(call)
                location.secondary?.message = "The unsafe intent is launched here."
            } else {
                if (resolveCallDepth > MAX_CALL_DEPTH) return
                // escaped to another method call. check the method recursively.
                val containingMethod = call.resolve()?.toUElementOfType<UMethod>() ?: return
                val intentParameter = context.evaluator.computeArgumentMapping(call, containingMethod.javaPsi)[reference]
                val visitor = IntentLaunchChecker(
                    initial = setOf(intentParameter.toUElement() ?: return),
                    context = context,
                    location = location,
                    incidentClass = incidentClass,
                    resolveCallDepth = resolveCallDepth + 1
                )
                containingMethod.accept(visitor)
                if (visitor.launched) {
                    storeIncidentsToPartialResults(context, visitor)
                } else if (visitor.returned) {
                    // if the visited method returns the passed-in unsafe Intent, add this call to track it.
                    instances.add(call)
                }
            }
        }

        /**
         * Handles kotlin scoping function with "this" object reference, like run, with. If "this" is tracked, get into the lambda function
         * and keep track of "this".
         */
        override fun receiver(call: UCallExpression) {
            if (resolveCallDepth > MAX_CALL_DEPTH) return
            if (isScopingThis(call)) {
                for (lambda in call.valueArguments) {
                    if (lambda !is ULambdaExpression) break
                    val tracked = getThisExpression(lambda.body) ?: break
                    val visitor = IntentLaunchChecker(
                        initial = setOf(tracked),
                        context = context,
                        location = location,
                        resolveCallDepth = resolveCallDepth + 1
                    )
                    lambda.body.accept(visitor)
                    if (visitor.launched) {
                        storeIncidentsToPartialResults(context, visitor)
                    }
                }
            }
        }

        override fun returns(expression: UReturnExpression) {
            // indicates the passed-in unsafe intent is returned.
            returned = true
        }

        private fun isIntentLaunchedBySystem(evaluator: JavaEvaluator, call: UCallExpression): Boolean {
            return isIntentLaunchedByContextMethods(evaluator, call) ||
                isIntentLaunchedByActivityMethods(evaluator, call) ||
                isIntentLaunchedByBroadcastReceiver(evaluator, call) ||
                isIntentLaunchedByPendingIntentMethods(evaluator, call) ||
                isIntentLaunchedByFragmentMethods(evaluator, call)
        }

        private fun isIntentLaunchedByContextMethods(evaluator: JavaEvaluator, call: UCallExpression): Boolean {
            val method = call.resolve()
            return method?.containingClass?.qualifiedName == CONTEXT_CLASS ||
                method?.findSuperMethods(evaluator.findClass(CONTEXT_CLASS))?.isNotEmpty() ?: false
        }

        private fun isIntentLaunchedByActivityMethods(evaluator: JavaEvaluator, call: UCallExpression): Boolean {
            return call.methodName in ACTIVITY_INTENT_LAUNCH_METHODS &&
                evaluator.extendsClass(evaluator.getTypeClass(call.receiverType), ACTIVITY_CLASS, true)
        }

        private fun isIntentLaunchedByBroadcastReceiver(evaluator: JavaEvaluator, call: UCallExpression): Boolean {
            return call.methodName == "peekService" &&
                evaluator.extendsClass(evaluator.getTypeClass(call.receiverType), BROADCAST_RECEIVER_CLASS, true)
        }

        private fun isIntentLaunchedByFragmentMethods(evaluator: JavaEvaluator, call: UCallExpression): Boolean {
            val receiverClass = evaluator.getTypeClass(call.receiverType)
            return call.methodName in FRAGMENT_INTENT_LAUNCH_METHODS &&
                (
                    evaluator.extendsClass(receiverClass, FRAGMENT_CLASS, true) ||
                        evaluator.extendsClass(receiverClass, ANDROIDX_FRAGMENT_CLASS, true)
                    )
        }

        private fun isIntentLaunchedByPendingIntentMethods(evaluator: JavaEvaluator, call: UCallExpression): Boolean {
            return call.methodName in PENDING_INTENT_LAUNCH_METHODS &&
                evaluator.isMemberInClass(call.resolve(), PENDING_INTENT_CLASS)
        }

        private fun handleAnonymousBroadcastReceiver(call: UCallExpression): Boolean {
            // The call is an Intent launching call. First find the anonymous class which is the class that contains this call.
            val anonymousClass = call.getParentOfType(UClass::class.java, true)
            // The parent of the anonymous class is the constructor call of the anonymous receiver. Use a DataFlowAnalyzer to keep track
            // of it to see if it is registered as an unprotected receiver.
            val parent = anonymousClass?.uastParent ?: return false
            var result = false
            val visitor = object : DataFlowAnalyzer(setOf(parent)) {
                override fun argument(call: UCallExpression, reference: UElement) {
                    if (call.methodName in registerReceiverMethods) {
                        val method = call.resolve() ?: return
                        if (!context.evaluator.isMemberInSubClassOf(method, CONTEXT_CLASS)) return
                        val filterArg = UastLintUtils.findArgument(call, method, "android.content.IntentFilter") ?: return
                        val flagsArg = UastLintUtils.findArgument(call, method, TYPE_INT)

                        val evaluator = ConstantEvaluator().allowFieldInitializers()
                        val flags = evaluator.evaluate(flagsArg) as? Int
                        if (flags != null && (flags and RECEIVER_NOT_EXPORTED) != 0) return

                        val (isProtected, _) =
                            BroadcastReceiverUtils.checkIsProtectedReceiverAndReturnUnprotectedActions(
                                filterArg,
                                call,
                                evaluator
                            )
                        if (!isProtected) {
                            result = true
                        }
                    }
                }
            }
            // We will only handle the case the receiver instance did not escape the class where it is instantiated.
            parent.getParentOfType(UMethod::class.java)?.accept(visitor)
            return result
        }

        /**
         * Get the "this" instance expression from an expression - typically a block of code.
         */
        private fun getThisExpression(block: UExpression): UThisExpression? {
            var result: UThisExpression? = null
            block.accept(object : AbstractUastVisitor() {
                override fun visitThisExpression(node: UThisExpression): Boolean {
                    result = node
                    return super.visitThisExpression(node)
                }
            })
            return result
        }

        /**
         * check if the tracked is returned from the visited method. It will follow the tracked if it is passed down to another method.
         */
        inner class ReturnsTracker(
            val context: JavaContext,
            tracked: UElement,
            var returned: Boolean = false,
            var resolveCallDepth: Int = 0
        ) :
            DataFlowAnalyzer(setOf(tracked)) {
            override fun returns(expression: UReturnExpression) {
                returned = true
            }

            override fun argument(call: UCallExpression, reference: UElement) {
                if (resolveCallDepth > MAX_CALL_DEPTH) return
                val containingMethod = call.resolve()?.toUElementOfType<UMethod>() ?: return
                val tracked = context.evaluator.computeArgumentMapping(call, containingMethod.javaPsi)[reference].toUElement() ?: return
                val returnsTracker = ReturnsTracker(context, tracked, resolveCallDepth = resolveCallDepth + 1)
                call.resolve()?.toUElementOfType<UMethod>()?.accept(returnsTracker)
                returned = returnsTracker.returned
            }
        }
    }

    companion object {
        private val IMPLEMENTATION = Implementation(
            UnsafeIntentLaunchDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)
        )

        /**
         * Issue describing the problem and pointing to the detector
         * implementation.
         */
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UnsafeIntentLaunch",
            briefDescription = "Launched Unsafe Intent",
            explanation = """
                    Intent that potentially could come from an untrusted source should not be \
                    launched from an unprotected component without first being sanitized. See \
                    this support FAQ for details: https://support.google.com/faqs/answer/9267555
                    """,
            category = Category.SECURITY,
            priority = 6,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = IMPLEMENTATION
        )

        private const val RECEIVER_NOT_EXPORTED = 0x4
        private const val KEY_UNPROTECTED = "unprotected"
        private const val KEY_INCIDENTS = "incidents"
        private const val KEY_INCIDENT_CLASS = "incidentClass"
        private const val KEY_LOCATION = "location"
        private const val KEY_SECONDARY_LOCATION = "secondaryLocation"
        private const val CONTEXT_CLASS = "android.content.Context"
        private const val ACTIVITY_CLASS = "android.app.Activity"
        private const val SERVICE_CLASS = "android.app.Service"
        private const val BROADCAST_RECEIVER_CLASS = "android.content.BroadcastReceiver"
        private const val FRAGMENT_CLASS = "android.app.Fragment"
        private const val ANDROIDX_FRAGMENT_CLASS = "androidx.fragment.app.Fragment"
        private const val PENDING_INTENT_CLASS = "android.app.PendingIntent"
        private const val INTENT_CLASS = "android.content.Intent"
        private const val MAX_CALL_DEPTH = 3

        private val UNSAFE_INTENT_AS_PARAMETER_METHODS = mapOf(
            BROADCAST_RECEIVER_CLASS to arrayOf("onReceive"),
            ACTIVITY_CLASS to arrayOf("onNewIntent", "onActivityResult", "onActivityReenter"),
            SERVICE_CLASS to arrayOf("onBind", "onUnbind", "onRebind", "onTaskRemoved", "onStartCommand", "onStart")
        )

        private val ACTIVITY_INTENT_LAUNCH_METHODS = listOf(
            "createPendingResult",
            "navigateUpTo",
            "navigateUpToFromChild",
            "startActivityIfNeeded",
            "startActivityForResult",
            "startActivityFromChild",
            "startActivityFromFragment",
            "startIntentSender",
            "startIntentSenderFromChild",
            "startIntentSenderForResult",
            "startNextMatchingActivity",
            "setResult"
        )

        private val FRAGMENT_INTENT_LAUNCH_METHODS = listOf(
            "startActivity",
            "startActivityForResult",
            "startIntentSenderForResult"
        )

        private val PENDING_INTENT_LAUNCH_METHODS = listOf(
            "getActivity",
            "getBroadcast",
            "getService",
            "getForegroundService"
        )

        private val INTENT_METHODS_RETURNS_INTENT_BUT_NOT_SELF = arrayOf(
            "cloneFilter",
            "getOriginalIntent",
            "getSelector",
            "getParcelableExtra",
        )

        private val KNOWN_NORMAL_PERMISSIONS = listOf(
            "android.permission.READ_BASIC_PHONE_STATE",
            "android.permission.MANAGE_OWN_CALLS",
            "android.permission.CALL_COMPANION_APP",
            "android.permission.HIGH_SAMPLING_RATE_SENSORS",
            "android.permission.USE_FINGERPRINT",
            "android.permission.USE_BIOMETRIC",
            "android.permission.READ_PROFILE",
            "android.permission.WRITE_PROFILE",
            "android.permission.READ_SOCIAL_STREAM",
            "android.permission.WRITE_SOCIAL_STREAM",
            "android.permission.READ_USER_DICTIONARY",
            "android.permission.WRITE_USER_DICTIONARY",
            "android.permission.WRITE_SMS",
            "com.android.browser.permission.READ_HISTORY_BOOKMARKS",
            "com.android.browser.permission.WRITE_HISTORY_BOOKMARKS",
            "android.permission.AUTHENTICATE_ACCOUNTS",
            "android.permission.MANAGE_ACCOUNTS",
            "android.permission.USE_CREDENTIALS",
            "android.permission.SUBSCRIBED_FEEDS_READ",
            "android.permission.SUBSCRIBED_FEEDS_WRITE",
            "android.permission.FLASHLIGHT",
            "com.android.alarm.permission.SET_ALARM",
            "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.NFC",
            "android.permission.NFC_TRANSACTION_EVENT",
            "android.permission.NFC_PREFERRED_PAYMENT_INFO",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE",
            "android.permission.VIBRATE",
            "android.permission.WAKE_LOCK",
            "android.permission.TRANSMIT_IR",
            "android.permission.TURN_SCREEN_ON",
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.DISABLE_KEYGUARD",
            "android.permission.REQUEST_PASSWORD_COMPLEXITY",
            "android.permission.GET_TASKS",
            "android.permission.REORDER_TASKS",
            "android.permission.RESTART_PACKAGES",
            "android.permission.KILL_BACKGROUND_PROCESSES",
            "android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND",
            "android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND",
            "android.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND",
            "android.permission.REQUEST_COMPANION_PROFILE_WATCH",
            "android.permission.HIDE_OVERLAY_WINDOWS",
            "android.permission.SET_WALLPAPER",
            "android.permission.SET_WALLPAPER_HINTS",
            "android.permission.EXPAND_STATUS_BAR",
            "com.android.launcher.permission.INSTALL_SHORTCUT",
            "com.android.launcher.permission.UNINSTALL_SHORTCUT",
            "android.permission.READ_SYNC_SETTINGS",
            "android.permission.WRITE_SYNC_SETTINGS",
            "android.permission.READ_SYNC_STATS",
            "android.permission.PERSISTENT_ACTIVITY",
            "android.permission.GET_PACKAGE_SIZE",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.BROADCAST_STICKY",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.USE_EXACT_ALARM",
            "android.permission.REQUEST_DELETE_PACKAGES",
            "android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE",
            "android.permission.DELIVER_COMPANION_MESSAGES",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            "android.permission.ACCESS_NOTIFICATION_POLICY",
            "android.permission.READ_INSTALL_SESSIONS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.USE_FULL_SCREEN_INTENT",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.READ_NEARBY_STREAMING_POLICY",
            "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
        )
    }
}
