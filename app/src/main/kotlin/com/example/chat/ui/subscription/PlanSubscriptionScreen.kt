package com.example.chat.ui.subscription

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.SnackbarDuration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.R
import com.example.chat.core.theme.ThemeConfig
import com.example.chat.core.theme.argbWithAlpha
import com.example.chat.ui.auth.AuthBackButton
import com.example.chat.ui.theme.LocalThemeConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/** Success green used by plan_subscription_screen.dart (SnackBar background,
 *  feature check icons, annual-save badge). */
private val SuccessGreen = Color(0xFF10B981)

/** SUB-R1-040 / R1-044: darker green (white text ~5.9:1) for the snackbar and check icons. */
// private val SuccessGreenAccessible = Color(0xFF047857) // Use the active theme role at the call site.

private const val PLAN_TAG = "PlanSubscription"

/** SUB-R1-003: defensive price parse (Number or numeric String). Null = unusable. */
private fun planPrice(plan: Map<String, Any?>, key: String): Double? {
    val v = plan[key] ?: return null
    return (v as? Number)?.toDouble() ?: v.toString().trim().toDoubleOrNull()
}

/** SUB-R1-012: single predicate for "free". */
private fun isFreePlanData(plan: Map<String, Any?>): Boolean =
    plan["tier"] == "free" || planPrice(plan, "monthly_price") == 0.0

/** SUB-R1-001: 499.0 -> "499", 499.5 -> "499.50". */
private fun formatPlanAmount(amount: Double): String =
    if (amount == Math.rint(amount)) amount.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", amount)

/** SUB-R1-002: INR keeps the existing rupee sign; other ISO codes use the
 *  platform symbol, falling back to the ISO code itself. */
private fun planCurrencySymbol(currency: String): String {
    val code = currency.trim().uppercase()
    if (code == "INR") return "\u20B9"
    return try {
        java.util.Currency.getInstance(code).getSymbol(java.util.Locale.getDefault())
    } catch (e: Exception) {
        code
    }
}

/** SUB-R1-004: whole-percent annual saving from loaded data, null when unknown. */
private fun annualSavingPercent(plans: List<Map<String, Any?>>): Int? {
    val percents = plans.mapNotNull { plan ->
        val m = planPrice(plan, "monthly_price")
        val a = planPrice(plan, "annual_price")
        if (m == null || a == null || m <= 0.0 || a <= 0.0) null
        else Math.round((1.0 - a / (m * 12.0)) * 100.0).toInt()
    }.filter { it > 0 }
    return percents.minOrNull()
}

/** SUB-R1-016: canned copy; raw detail goes to the log only. */
private fun planErrorCopy(e: Exception): String {
    android.util.Log.w(PLAN_TAG, "plan request failed: ${e.javaClass.simpleName}")
    return when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException ->
            "You appear to be offline. Check your connection and try again."
        is java.net.SocketTimeoutException, is kotlinx.coroutines.TimeoutCancellationException ->
            "The server took too long to respond. Please try again."
        else -> "Something went wrong. Please try again."
    }
}

/** SUB-R1-020: drop plans with a negative price or no id, de-dupe ids. */
private fun validatePlans(raw: List<Map<String, Any?>>): List<Map<String, Any?>> {
    val seen = HashSet<String>()
    return raw.filter { plan ->
        val id = plan["id"]?.toString()
        val bad = id.isNullOrBlank() ||
            (planPrice(plan, "monthly_price") ?: 0.0) < 0.0 ||
            (planPrice(plan, "annual_price") ?: 0.0) < 0.0 ||
            !seen.add(id)
        if (bad) android.util.Log.w(PLAN_TAG, "dropping unusable plan row")
        !bad
    }
}

/** Port of lib/features/subscription/plan_subscription_screen.dart.
 *
 *  Shown after organization registration (isNewRegistration = true, no back
 *  button, optional free-plan shortcut) and from chats-home / account
 *  settings when the workspace needs a plan (isNewRegistration = false).
 *
 *  Backend calls are injected; AppRoot wires ApiBackendService.getAvailablePlans
 *  / subscribeToPlan. [onSubscribed] replaces the Dart
 *  pushAndRemoveUntil(MainNavigationShell) — the host routes home. The Dart
 *  screen shows the success SnackBar and navigates in the same frame; the
 *  SnackBar is launched here for parity but the route change owns the exit. */
@Composable
fun PlanSubscriptionScreen(
    organizationId: String,
    organizationName: String,
    isNewRegistration: Boolean,
    onBack: () -> Unit,
    onSubscribed: (planName: String) -> Unit,
    loadPlans: suspend (organizationId: String) -> List<Map<String, Any?>>,
    subscribe: suspend (organizationId: String, planId: String, durationMonths: Int) -> Unit,
) {
    val theme = LocalThemeConfig.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // SUB-R3-047: below 360dp use shorter tab labels and 16dp side padding.
    val narrow = LocalConfiguration.current.screenWidthDp < 360

    var isLoadingPlans by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isAnnual by rememberSaveable { mutableStateOf(false) }
    var selectedPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var plans by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }

    // SUB-R2-021: only one load in flight; a new load cancels the previous one.
    val loadJobHolder = remember { arrayOfNulls<Job>(1) }

    fun friendlyError(e: Exception): String = planErrorCopy(e)

    fun loadPlansNow() {
        // Retire an old workspace request even when the new workspace is blank.
        loadJobHolder[0]?.cancel()
        if (organizationId.isBlank()) {
            // SUB-R1-010: fail fast instead of calling the server with no workspace.
            plans = emptyList()
            selectedPlanId = null
            errorMessage = "Workspace is not ready yet. Please go back and try again."
            isLoadingPlans = false
            return
        }
        isLoadingPlans = true
        errorMessage = null
        // loadJobHolder[0]?.cancel() // Moved above the blank-workspace return.
        loadJobHolder[0] = scope.launch {
            try {
                val loaded = validatePlans(kotlinx.coroutines.withTimeout(15_000) { loadPlans(organizationId) })
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                // SUB-R3-001: never show a blended-currency list.
                if (loaded.mapNotNull { it["currency"]?.toString()?.trim()?.uppercase() }.toSet().size > 1) {
                    android.util.Log.w(PLAN_TAG, "plans carry mixed currencies")
                    plans = emptyList()
                    selectedPlanId = null
                    errorMessage = "Plans are not available right now. Please try again later."
                    isLoadingPlans = false
                    return@launch
                }
                plans = loaded
                val previousChoice = selectedPlanId
                val keepChoice = previousChoice != null &&
                    loaded.any { it["id"]?.toString() == previousChoice }
                selectedPlanId = if (loaded.isEmpty()) {
                    null
                } else if (keepChoice) {
                    // SUB-R2-003: keep the earlier choice when it is still offered.
                    previousChoice
                } else {
                    // Dart: default to the first highlighted or free-tier plan.
                    // SUB-R2-005: explicit rule - highlighted, then cheapest paid, then free.
                    // val defaultPlan = loaded.firstOrNull {
                    //     it["highlighted"] == true || it["tier"] == "free"
                    // } ?: loaded.first()
                    val defaultPlan = loaded.firstOrNull { it["highlighted"] == true }
                        ?: loaded
                            .filter { (planPrice(it, "monthly_price") ?: 0.0) > 0.0 }
                            .minByOrNull { planPrice(it, "monthly_price") ?: Double.MAX_VALUE }
                        ?: loaded.firstOrNull { isFreePlanData(it) }
                        ?: loaded.first()
                    defaultPlan["id"]?.toString()
                }
                isLoadingPlans = false
            } catch (e: Exception) {
                // Preserve parent cancellation. An inner timeout still gets the existing error copy.
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                errorMessage = friendlyError(e)
                plans = emptyList()
                isLoadingPlans = false
            }
        }
    }

    // SUB-R2-006: free is judged on the displayed billing cycle.
    // fun isFreePlan(plan: Map<String, Any?>): Boolean = isFreePlanData(plan)
    fun isFreePlan(plan: Map<String, Any?>): Boolean =
        plan["tier"] == "free" ||
            (planPrice(plan, if (isAnnual) "annual_price" else "monthly_price") ?: 1.0) <= 0.0

    fun anyFreePlan(): Boolean = plans.any(::isFreePlanData)

    fun buttonText(): String {
        if (plans.isEmpty()) return "No Plans Available"
        val selected = plans.firstOrNull { it["id"]?.toString() == selectedPlanId } ?: emptyMap()
        // SUB-R1-049: name the plan on the CTA. Old generic copy kept below.
        // return if (isFreePlan(selected)) "Activate Free Plan" else "Subscribe & Activate Workspace"
        // SUB-R5-010: a lone plan reads "Activate {name}".
        if (plans.size == 1) return "Activate ${selected["name"] ?: "plan"}"
        return if (isFreePlan(selected)) "Activate Free Plan"
        else "Subscribe to ${selected["name"] ?: "plan"}"
    }

    // SUB-R1-011: one pipeline for the CTA and the free-plan shortcut.
    fun subscribePlan(planId: String?) {
        if (planId == null || isSubmitting) return
        val chosen = plans.firstOrNull { it["id"]?.toString() == planId }
        if (chosen == null) {
            // SUB-R2-004: the selected plan is no longer offered.
            errorMessage = "That plan is no longer available. Please choose another."
            return
        }
        // SUB-R1-005: a free plan never uses the annual duration.
        val months = if (isFreePlan(chosen)) 1 else if (isAnnual) 12 else 1
        isSubmitting = true
        errorMessage = null
        scope.launch {
            try {
                subscribe(organizationId, planId, months)
                val planName = (plans.firstOrNull { it["id"]?.toString() == planId }
                    ?.get("name") ?: "Plan").toString()
                launch {
                    snackbarHostState.showSnackbar(
                        duration = SnackbarDuration.Short,
                        message = "Subscribed to $planName! Workspace \"${organizationName.take(24)}${if (organizationName.length > 24) "\u2026" else ""}\" is now active.",
                    )
                }
                onSubscribed(planName)
            } catch (e: Exception) {
                // Preserve parent cancellation. An inner timeout still gets the existing error copy.
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                errorMessage = friendlyError(e)
                isSubmitting = false
            }
        }
    }

    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var showFreeConfirm by rememberSaveable { mutableStateOf(false) }

    // SUB-R1-009: paid plans get a price recap before the commit tap.
    fun handleSubscribe() {
        val chosen = plans.firstOrNull { it["id"]?.toString() == selectedPlanId }
        if (chosen != null && !isFreePlan(chosen)) showConfirm = true else subscribePlan(selectedPlanId)
    }

    LaunchedEffect(organizationId) { loadPlansNow() }

    // SUB-R1-028 / R3-022: recompute the CTA label only when its inputs change.
    // val ctaText = remember(plans, selectedPlanId) { buttonText() }
    val ctaText = remember(plans, selectedPlanId, isAnnual) { buttonText() }

    if (showFreeConfirm) {
        // SUB-R2-012: the free shortcut gets its own confirmation too.
        val free = plans.firstOrNull(::isFreePlanData)
        if (free == null) {
            showFreeConfirm = false
        } else {
            AlertDialog(
                onDismissRequest = { showFreeConfirm = false },
                title = { Text(stringResource(R.string.sub_free_confirm_title)) },
                text = { Text("${free["name"] ?: "Free"} for \"$organizationName\".") },
                confirmButton = {
                    TextButton(onClick = {
                        showFreeConfirm = false
                        val planId = free["id"]?.toString()
                        selectedPlanId = planId
                        subscribePlan(planId)
                    }) { Text(stringResource(R.string.sub_free_confirm_action)) }
                },
                dismissButton = { TextButton(onClick = { showFreeConfirm = false }) { Text(stringResource(R.string.sub_cancel)) } },
            )
        }
    }

    if (showConfirm) {
        val chosen = plans.firstOrNull { it["id"]?.toString() == selectedPlanId }
        if (chosen == null) {
            showConfirm = false
        } else {
            val amount = planPrice(chosen, if (isAnnual) "annual_price" else "monthly_price") ?: 0.0
            val symbol = planCurrencySymbol(chosen["currency"]?.toString() ?: "INR")
            AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text(stringResource(R.string.sub_confirm_title)) },
                text = {
                    Text(
                        "${chosen["name"] ?: "Plan"}: $symbol${formatPlanAmount(amount)}" +
                            "${if (isAnnual) " per year" else " per month"} for \"$organizationName\".",
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showConfirm = false; subscribePlan(selectedPlanId) }) {
                        Text(stringResource(R.string.sub_confirm_action))
                    }
                },
                dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.sub_cancel)) } },
            )
        }
    }

    Scaffold(
        containerColor = Color(theme.backgroundColor),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    // SUB-R1-040: was SuccessGreen (about 2.4:1 with white text).
                    // SUB-R3-033: tinted from theme tokens (accent + onAccent keep a theme-safe pair).
                    // containerColor = SuccessGreenAccessible,
                    // contentColor = Color.White,
                    containerColor = Color(theme.accentColor),
                    contentColor = Color(theme.onAccentColor),
                )
            }
        },
        bottomBar = {
            if (!isLoadingPlans) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = if (narrow) 16.dp else 24.dp).padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HorizontalDivider(color = Color(argbWithAlpha(theme.dividerColor, 0.2)), thickness = 1.dp)
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                Button(
                    onClick = { handleSubscribe() },
                    enabled = !isSubmitting && selectedPlanId != null && plans.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("subscribe_cta"),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(theme.accentColor),
                        contentColor = Color(theme.onAccentColor),
                        disabledContainerColor = Color(argbWithAlpha(theme.accentColor, 0.6)),
                        disabledContentColor = Color(theme.onAccentColor),
                    ),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.2.dp,
                            color = Color(theme.onAccentColor),
                        )
                    } else {
                        Text(
                            ctaText,
                            color = Color(theme.onAccentColor),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))

//Icon(
//                            Icons.AutoMirrored.Rounded.ArrowForward,
//                            contentDescription = null,
//                            tint = Color(theme.onAccentColor),
//                            modifier = Modifier.size(18.dp),
//                        )
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.arrowRight, Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color(theme.onAccentColor),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (plans.isNotEmpty() && selectedPlanId == null) {
                    // SUB-R2-031: explain why the CTA is disabled.
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sub_select_hint), color = Color(theme.secondaryTextColor), fontSize = 13.sp)
                }

                if (isNewRegistration && anyFreePlan()) {
                    val freePlan = plans.firstOrNull(::isFreePlanData) ?: plans.first()
                    Spacer(Modifier.height(16.dp))
                    // SUB-R1-055: was a bare TextButton; now an outlined 48dp secondary action.
                    OutlinedButton(
                        onClick = {
                            // SUB-R2-012: confirm first (was an immediate subscribe).
                            showFreeConfirm = true
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("free_plan_shortcut"),
                    ) {
                        Text(
                            // "Start with Free Plan (${(freePlan["name"] ?: "Free")})",
                            "Continue with Free (${(freePlan["name"] ?: "Free")})",
                            color = Color(theme.secondaryTextColor),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                    }
                }
            }
        },
    ) { padding ->
        if (isLoadingPlans) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Color(theme.accentColor), strokeWidth = 2.5.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.sub_loading_plans),
                    color = Color(theme.secondaryTextColor),
                    fontSize = 14.sp,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .semantics { paneTitle = "Subscription plans" }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (narrow) 16.dp else 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Dart AppBar: back arrow only outside the registration flow.
            if (!isNewRegistration) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    // SUB-R1-008: no leaving mid-subscribe.
                    if (!isSubmitting) AuthBackButton(onClick = onBack)
                }
            }

            Column(
                modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header badge.
                Row(
                    modifier = Modifier
                        .background(
                            Color(argbWithAlpha(theme.accentColor, 0.12)),
                            RoundedCornerShape(20.dp),
                        )
                        .border(
                            1.dp,
                            Color(argbWithAlpha(theme.accentColor, 0.3)),
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {

//Icon(
//                        Icons.Rounded.WorkspacePremium,
//                        contentDescription = null,
//                        tint = Color(theme.accentColor),
//                        modifier = Modifier.size(16.dp),
//                    )
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.premium, Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(theme.accentColor),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        // SUB-R2-042: no Step 1 exists on this screen, so the step label is retired.
                        // if (isNewRegistration) "Step 2: Activate Workspace" else "Organization Subscription",
                        if (isNewRegistration) "Activate Workspace" else "Organization Subscription",
                        color = Color(theme.accentColor),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text(
                    if (isNewRegistration) "Choose a Workspace Plan" else "Plans for $organizationName",
                    color = Color(theme.primaryTextColor),
                    // SUB-R3-044: was 26sp.
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    // SUB-R1-058: no manage-plan surface exists, so the promise is removed.
                    // "Select the subscription plan that best fits \"$organizationName\". You can change or upgrade at any time.",
                    "Select the subscription plan that best fits \"$organizationName\".",
                    color = Color(theme.secondaryTextColor),
                    // SUB-R3-044: was 14sp, unbounded lines.
                    fontSize = 13.sp,
                    lineHeight = (13 * 1.4).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))

                BillingCycleSwitcher(theme, isAnnual, annualSavingPercent(plans), narrow) { isAnnual = it }
                Spacer(Modifier.height(24.dp))

                // SUB-R1-053: with no plans the error is shown inside the single empty/error card below.
                errorMessage?.takeIf { plans.isNotEmpty() }?.let { msg ->
                    Row(
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                            .background(Color(argbWithAlpha(theme.dangerColor, 0.1)), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(argbWithAlpha(theme.dangerColor, 0.3)), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.errorOutline,
                            semanticLabel = null,
                            color = Color(theme.dangerColor),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Error: $msg",
                            color = Color(theme.dangerColor),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        // SUB-R1-022: the banner can be dismissed.
                        TextButton(onClick = { errorMessage = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.sub_dismiss), color = Color(theme.dangerColor), fontSize = 13.sp)
                        }
                    }
                }

                if (plans.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                            .background(Color(argbWithAlpha(theme.surfaceColor, 0.5)), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(argbWithAlpha(theme.dividerColor, 0.25)), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {

//Icon(
//                            Icons.Rounded.LayersClear,
//                            contentDescription = null,
//                            tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.7)),
//                            modifier = Modifier.size(44.dp),
//                        )
                        com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.layersOff, Icons.Rounded.LayersClear,
                            contentDescription = null,
                            tint = Color(argbWithAlpha(theme.secondaryTextColor, 0.7)),
                            modifier = Modifier.size(44.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (errorMessage != null) stringResource(R.string.sub_load_failed_title) else stringResource(R.string.sub_empty_title),
                            color = Color(theme.primaryTextColor),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            errorMessage ?: "Plans are not available right now. Check your connection and try again.",
                            color = Color(theme.secondaryTextColor),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = { loadPlansNow() }, enabled = !isLoadingPlans) {

//Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.refresh, Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.sub_retry_plans))
                        }
                    }
                } else {
                    plans.forEach { plan ->
                        PlanCard(
                            theme = theme,
                            plan = plan,
                            isAnnual = isAnnual,
                            isSelected = selectedPlanId == plan["id"]?.toString(),
                            showRadio = plans.size > 1,
                            onSelect = { plan["id"]?.toString()?.let { selectedPlanId = it } },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // SUB-R3-025: the CTA, hint and free shortcut moved to the Scaffold bottomBar below.
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BillingCycleSwitcher(theme: ThemeConfig, isAnnual: Boolean, savingPercent: Int?, narrow: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .background(Color(argbWithAlpha(theme.surfaceColor, 0.6)), RoundedCornerShape(14.dp))
            .border(1.dp, Color(argbWithAlpha(theme.dividerColor, 0.25)), RoundedCornerShape(14.dp))
            .padding(4.dp),
    ) {
        BillingTab(theme, if (narrow) "Monthly" else stringResource(R.string.sub_monthly_billing), !isAnnual, null) { onChange(false) }
        BillingTab(theme, if (narrow) "Annual" else stringResource(R.string.sub_annual_billing), isAnnual, savingPercent?.let { "Save $it%" }) { onChange(true) }
    }
}

@Composable
private fun BillingTab(
    theme: ThemeConfig,
    label: String,
    isSelected: Boolean,
    badge: String?,
    onTap: () -> Unit,
) {
    val background by animateColorAsState(
        if (isSelected) Color(theme.accentColor) else Color.Transparent,
        animationSpec = tween(200),
        label = "billingTabBg",
    )
    Row(
        modifier = Modifier
            .background(background, RoundedCornerShape(10.dp))
            // Selectable tab with a 48dp minimum touch height.
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onTap)
            .padding(vertical = 8.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (isSelected) Color(theme.onAccentColor) else Color(theme.secondaryTextColor),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                badge,
                color = if (isSelected) Color(theme.onAccentColor) else SuccessGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .background(
                        if (isSelected) Color(argbWithAlpha(0xFFFFFFFF, 0.25))
                        else Color(argbWithAlpha(0xFF10B981, 0.15)),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PlanCard(
    theme: ThemeConfig,
    plan: Map<String, Any?>,
    isAnnual: Boolean,
    isSelected: Boolean,
    showRadio: Boolean = true,
    onSelect: () -> Unit,
) {
    val name = plan["name"]?.toString() ?: "Plan"
    val isHighlighted = plan["highlighted"] == true
    val monthlyPrice = planPrice(plan, "monthly_price") ?: 0.0
    val annualPrice = planPrice(plan, "annual_price") ?: 0.0
    val currency = plan["currency"]?.toString() ?: "INR"
    val currencySymbol = planCurrencySymbol(currency)
    val price = if (isAnnual) annualPrice else monthlyPrice
    val period = if (isAnnual) "/year" else "/month"
    val description = plan["description"]?.toString()
    val productName = plan["product_name"]?.toString()

    // SUB-R4-026: parse features once per plan instead of on every recomposition.
    val features = remember(plan) {
        val out = mutableListOf<String>()
        // SUB-R3-003: accept a list, or a newline/comma separated string.
        val rawFeatures: List<*> = when (val raw = plan["features"]) {
            is List<*> -> raw
            is String -> raw.split('\n', ',')
            else -> emptyList<Any?>()
        }
        rawFeatures.forEach { f ->
            val text = f?.toString()?.trim()
            if (!text.isNullOrEmpty()) out.add(text)
        }
        out
    }

    val cardBackground by animateColorAsState(
        if (isSelected) Color(argbWithAlpha(theme.accentColor, 0.08))
        else Color(argbWithAlpha(theme.surfaceColor, 0.6)),
        animationSpec = tween(200),
        label = "planCardBg",
    )
    // SUB-R3-034: keyboard / switch-access focus gets the same 2dp accent outline.
    var focused by remember { mutableStateOf(false) }
    val borderColor = when {
        isSelected || focused -> Color(theme.accentColor)
        isHighlighted -> Color(argbWithAlpha(theme.accentColor, 0.4))
        else -> Color(argbWithAlpha(theme.dividerColor, 0.25))
    }

    Column(
        modifier = Modifier
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .then(
                if (isSelected) Modifier.shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = Color(argbWithAlpha(theme.accentColor, 0.15)),
                    spotColor = Color(argbWithAlpha(theme.accentColor, 0.15)),
                ) else Modifier,
            )
            .background(cardBackground, RoundedCornerShape(18.dp))
            // SUB-R1-051: constant 2dp border so selecting a card does not shift layout.
            // .border(if (isSelected) 2.dp else 1.2.dp, borderColor, RoundedCornerShape(18.dp))
            .border(2.dp, borderColor, RoundedCornerShape(18.dp))
            .onFocusChanged { focused = it.hasFocus }
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // SUB-R4-041: radio leads the title row; SUB-R5-010: hidden for a single plan.
            if (showRadio) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(
                            2.dp,
                            if (isSelected) Color(theme.accentColor)
                            else Color(argbWithAlpha(theme.secondaryTextColor, 0.4)),
                            CircleShape,
                        )
                        .background(if (isSelected) Color(theme.accentColor) else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.check,
                            semanticLabel = null,
                            color = Color(theme.onAccentColor),
                            // SUB-R2-037: was 14dp.
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    color = Color(theme.primaryTextColor),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    // SUB-R1-052: long names ellipsize so the chips keep their place.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!productName.isNullOrEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        productName.uppercase(),
                        color = Color(theme.accentColor),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier
                            .background(Color(argbWithAlpha(theme.accentColor, 0.12)), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                    )
                }
                if (isHighlighted) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.sub_recommended),
                        color = Color(theme.onAccentColor),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier
                            .background(Color(theme.accentColor), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            // SUB-R4-037: read amount and period as one phrase.
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Text(
                if (price <= 0) "Free" else "$currencySymbol${formatPlanAmount(price)}",
                color = Color(theme.primaryTextColor),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )
            if (price > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    period,
                    color = Color(theme.secondaryTextColor),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    // SUB-R1-047: period may wrap at large font scale instead of pushing the row off-screen.
                    modifier = Modifier.weight(1f, fill = false).padding(bottom = 3.dp),
                )
            }
        }
        if (isAnnual && price > 0) {
            // SUB-R2-041: monthly x 12 reference price, struck through.
            if (monthlyPrice > 0 && monthlyPrice * 12.0 > price) {
                Text(
                    "$currencySymbol${formatPlanAmount(monthlyPrice * 12.0)}",
                    color = Color(theme.secondaryTextColor),
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
            // SUB-R1-050: per-month equivalent under the annual total.
            Text(
                "about $currencySymbol${formatPlanAmount(price / 12.0)}/month",
                color = Color(theme.secondaryTextColor),
                fontSize = 12.sp,
            )
        }
        if (!description.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(description, color = Color(theme.secondaryTextColor), fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (features.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color(argbWithAlpha(theme.dividerColor, 0.2)), thickness = 1.dp)
            Spacer(Modifier.height(12.dp))
            features.forEach { feature ->
                Row(
                    Modifier
                        // SUB-R3-045: row spacing 10dp (was 8dp).
                        .padding(bottom = 10.dp)
                        // SUB-R1-044: say what the icon means.
                        .semantics(mergeDescendants = true) { contentDescription = "Included: $feature" },
                ) {

//Icon(
//                        Icons.Rounded.CheckCircle,
//                        contentDescription = null,
//                        // SUB-R1-044: darker tint for 3:1 non-text contrast.
//                        // tint = SuccessGreenAccessible,
//                        tint = Color(theme.successColor),
//                        // SUB-R3-045: was 16dp.
//                        modifier = Modifier.size(18.dp),
//                    )
                    com.example.chat.ui.icons.ChatyPurposeIcon(com.example.chat.ui.icons.ChatyIconId.checkCircle, Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        // SUB-R1-044: darker tint for 3:1 non-text contrast.
                        // tint = SuccessGreenAccessible,
                        tint = Color(theme.successColor),
                        // SUB-R3-045: was 16dp.
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        feature,
                        color = Color(argbWithAlpha(theme.primaryTextColor, 0.85)),
                        // SUB-R3-045: was 13sp.
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
