package com.example.chat.ui.settings.pro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.example.chat.ui.icons.ChatyPackAwareIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chat.ui.kit.ChatyAppBar
import com.example.chat.ui.theme.AppColors
import com.example.chat.ui.theme.LocalAppColors

/** Port of lib/features/settings/pro_pricing_screen.dart.
 *
 *  The screen is marketing UI only in the Dart source: "Get Started" pops
 *  the route and shows the activation snackbar on the previous screen
 *  ('Chaty Pro Plus Plan Activated! 🎉', bold onAccent text on accentColor).
 *  No purchase, billing, or backend call exists in the source, and none is
 *  added here. [onGetStarted] = pop + host-side snackbar. */
@Composable
fun ProPricingScreen(
    onBack: () -> Unit,
    onGetStarted: () -> Unit,
) {
    val colors = LocalAppColors.current
    var isYearly by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        ChatyAppBar(title = "Pricing Plan", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Title and subtitle.
            Text(
                // SUB-R1-059: manual line break dropped so the title wraps naturally.
                // "Easily Build Better Chats\nwith Chaty Pro",
                "Easily Build Better Chats with Chaty Pro",
                color = colors.foreground,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                lineHeight = (24 * 1.25).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Turn ideas into conversations instantly with AI",
                color = colors.foregroundSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.1.sp,
                lineHeight = (12 * 1.3).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            // Segmented switch (Monthly | Yearly [Save 50%]).
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    modifier = Modifier
                        .background(colors.surfaceSecondary, RoundedCornerShape(999.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(999.dp))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BillingSegment(
                        label = "Monthly",
                        selected = !isYearly,
                        horizontalPadding = 22,
                        colors = colors,
                    ) { isYearly = false }
                    BillingSegment(
                        label = "Yearly",
                        selected = isYearly,
                        horizontalPadding = 18,
                        colors = colors,
                        badge = {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Save 50%",
                                // SUB-R1-041: promo badge uses theme accent (was error red; approval relayed by Main at 10:29 AM, not seen directly).
                                // color = colors.onError,
                                color = colors.onPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    // .background(colors.error, RoundedCornerShape(8.dp))
                                    .background(colors.primary, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        },
                    ) { isYearly = true }
                }
            }
            Spacer(Modifier.height(24.dp))

            // Plus plan card (ChatyCard: surface, 1dp border, radius card=18).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(18.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                    .padding(20.dp),
            ) {
                Text(
                    "Plus",
                    color = colors.foreground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (isYearly) "$10.25" else "$20.50",
                        color = colors.foreground,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isYearly) "Per Month billed annually" else "Per Month billed monthly",
                        color = colors.foregroundSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.1.sp,
                        lineHeight = (12 * 1.3).sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                // SUB-R1-015: state the amount actually charged.
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isYearly) "$123.00 charged per year" else "$20.50 charged per month",
                    color = colors.foreground,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(20.dp))

                // ChatyPrimaryButton: height 48, radius lg=16, primary/white,
                // full width, zero elevation.
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        // Prior fixed foreground: contentColor = Color.White,
                        contentColor = colors.onPrimary,
                    ),
                ) {
                    Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                // SUB-R1-014: renewal and cancellation terms (text only; no billing is wired).
                Spacer(Modifier.height(10.dp))
                Text(
                    if (isYearly) "Renews every year until cancelled. Cancel any time in your store subscription settings."
                    else "Renews every month until cancelled. Cancel any time in your store subscription settings.",
                    color = colors.foregroundSecondary,
                    fontSize = 11.sp,
                    lineHeight = (11 * 1.3).sp,
                )
                Spacer(Modifier.height(20.dp))

                Text(
                    "Included Features",
                    color = colors.foregroundSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.1.sp,
                    lineHeight = (12 * 1.3).sp,
                )
                Spacer(Modifier.height(12.dp))

                CheckItem(colors, "Smart AI messaging assistance")
                CheckItem(colors, "Access to Chaty Pro Theme Library")
                CheckItem(colors, "Early access to new features")
                // SUB-R4-015: unsubstantiated security claim, commented out rather than deleted.
                // CheckItem(colors, "Next-generation privacy & encryption tools")
                CheckItem(colors, "Unlimited task and backup synchronization")
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BillingSegment(
    label: String,
    selected: Boolean,
    horizontalPadding: Int,
    colors: AppColors,
    badge: (@Composable () -> Unit)? = null,
    onTap: () -> Unit,
) {
    val background by animateColorAsState(
        if (selected) colors.surfaceElevated else Color.Transparent,
        animationSpec = tween(200),
        label = "segmentBg",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(background, RoundedCornerShape(999.dp))
            .heightIn(min = 48.dp)
            .clickable(onClick = onTap)
            .padding(horizontal = horizontalPadding.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (selected) colors.foreground else colors.foregroundSecondary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
        )
        badge?.invoke()
    }
}

/** Dart _buildCheckItem. */
@Composable
private fun CheckItem(colors: AppColors, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp).semantics(mergeDescendants = true) { },
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(colors.success.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            com.example.chat.ui.icons.ChatyIcon(com.example.chat.ui.icons.ChatyIconId.check,
                semanticLabel = null,
                color = colors.success,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            color = colors.foreground,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
