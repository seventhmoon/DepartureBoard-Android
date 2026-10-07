package com.androidfung.departureboard.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.billingclient.api.ProductDetails
import com.androidfung.departureboard.billing.BillingDataSource
import com.androidfung.departureboard.billing.SubscriptionTier

/**
 * Paywall bottom sheet showcasing Prompt Departure Pro features and subscription plans.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallBottomSheet(
    onDismissRequest: () -> Unit,
    billingRepository: BillingDataSource,
    modifier: Modifier = Modifier,
    reasonMessage: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val products by billingRepository.availableProducts.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        billingRepository.queryAvailableProducts()
    }

    var selectedPlanType by remember { mutableStateOf(SubscriptionTier.BASE_PLAN_ANNUAL) }

    val subProduct = products.firstOrNull { it.productId == SubscriptionTier.PRODUCT_SUBSCRIPTION_ID }
        ?: products.firstOrNull()

    val annualOffer = subProduct?.subscriptionOfferDetails?.firstOrNull {
        it.basePlanId == SubscriptionTier.BASE_PLAN_ANNUAL || it.basePlanId.contains("annual", ignoreCase = true)
    } ?: subProduct?.subscriptionOfferDetails?.firstOrNull()

    val monthlyOffer = subProduct?.subscriptionOfferDetails?.firstOrNull {
        it.basePlanId == SubscriptionTier.BASE_PLAN_MONTHLY || it.basePlanId.contains("monthly", ignoreCase = true)
    }

    val (annualPlanPricing, monthlyPlanPricing) = remember(annualOffer, monthlyOffer) {
        calculateSubscriptionPricing(annualOffer, monthlyOffer)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Pro Crown Icon + Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(36.dp))

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFFFD700),
                                    Color(0xFFFF9800)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Upgrade to Pro",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!reasonMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = reasonMessage,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Supercharge your London daily commute",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Pro Features List
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProFeatureRow(
                        title = "Up to ${SubscriptionTier.PRO_MAX_STATIONS} Saved Stations",
                        subtitle = "Free tier is limited to ${SubscriptionTier.FREE_MAX_STATIONS} stations"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ProFeatureRow(
                        title = "Up to ${SubscriptionTier.PRO_MAX_WIDGETS} Home Screen Widgets",
                        subtitle = "Track all your commute connections at a glance"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ProFeatureRow(
                        title = "Unlimited AI Transit Assistant",
                        subtitle = "Natural language queries & live status updates"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Subscription Options Selector
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Annual Plan (Featured)
                PlanOptionCard(
                    title = "Annual Plan",
                    subtitle = annualPlanPricing.subtitle,
                    price = annualPlanPricing.formattedPrice,
                    tag = annualPlanPricing.savingsTag,
                    isSelected = selectedPlanType == SubscriptionTier.BASE_PLAN_ANNUAL,
                    onClick = { selectedPlanType = SubscriptionTier.BASE_PLAN_ANNUAL }
                )

                // Monthly Plan
                PlanOptionCard(
                    title = "Monthly Plan",
                    subtitle = monthlyPlanPricing.subtitle,
                    price = monthlyPlanPricing.formattedPrice,
                    isSelected = selectedPlanType == SubscriptionTier.BASE_PLAN_MONTHLY,
                    onClick = { selectedPlanType = SubscriptionTier.BASE_PLAN_MONTHLY }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // CTA Button
            Button(
                onClick = {
                    val act = activity ?: context.findActivity()
                    val targetBasePlan = if (selectedPlanType == SubscriptionTier.BASE_PLAN_ANNUAL) {
                        SubscriptionTier.BASE_PLAN_ANNUAL
                    } else {
                        SubscriptionTier.BASE_PLAN_MONTHLY
                    }

                    if (subProduct != null && act != null) {
                        val launched = billingRepository.launchPurchaseFlow(act, subProduct, targetBasePlan)
                        if (!launched) {
                            Toast.makeText(context, "Unable to start Google Play purchase flow. Check Play Store connection.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // Product is not yet live/configured in Google Play Console (local dev/testing mode).
                        // Unlock Pro locally and confirm to developer.
                        billingRepository.setDebugPro(true)
                        Toast.makeText(context, "Pro activated! (Google Play products not yet live in Console)", Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Continue with ${if (selectedPlanType == SubscriptionTier.BASE_PLAN_ANNUAL) "Annual" else "Monthly"}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Restore Purchases
            TextButton(
                onClick = {
                    billingRepository.refreshPurchases()
                }
            ) {
                Text(
                    text = "Restore Purchases",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProFeatureRow(
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanOptionCard(
    title: String,
    subtitle: String,
    price: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String? = null
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (tag != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF9800))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.Black,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = price,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private data class PlanPricingInfo(
    val formattedPrice: String,
    val subtitle: String,
    val savingsTag: String? = null
)

/**
 * Calculates dynamic plan pricing and savings percentage directly from Google Play
 * [ProductDetails.SubscriptionOfferDetails], with graceful localized fallbacks for offline / local debug testing.
 */
private fun calculateSubscriptionPricing(
    annualOffer: ProductDetails.SubscriptionOfferDetails?,
    monthlyOffer: ProductDetails.SubscriptionOfferDetails?
): Pair<PlanPricingInfo, PlanPricingInfo> {
    val annualPhase = annualOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()
    val monthlyPhase = monthlyOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()

    // 1. Live Google Play formatted prices (e.g. "£9.99", "$11.99", "€10.99")
    val annualPrice = annualPhase?.formattedPrice ?: "£9.99 / year"
    val monthlyPrice = monthlyPhase?.formattedPrice ?: "£1.49 / month"

    // 2. Dynamic savings percentage calculated from Google Play pricing in micros
    val savingsTag: String? = if (annualPhase != null && monthlyPhase != null && monthlyPhase.priceAmountMicros > 0) {
        val annualMicros = annualPhase.priceAmountMicros
        val monthlyYearTotal = monthlyPhase.priceAmountMicros * 12
        val savings = ((1.0 - (annualMicros.toDouble() / monthlyYearTotal)) * 100).toInt()
        if (savings > 0) "SAVE $savings%" else null
    } else {
        "SAVE 44%"
    }

    // 3. Dynamic per-month equivalent breakdown based on currency code
    val annualSubtitle = if (annualPhase != null && annualPhase.priceAmountMicros > 0) {
        val monthlyEquiv = (annualPhase.priceAmountMicros / 12) / 1_000_000.0
        val symbol = try {
            java.util.Currency.getInstance(annualPhase.priceCurrencyCode).symbol
        } catch (_: Exception) {
            "£"
        }
        "1 Year of Pro Access (${symbol}${String.format(java.util.Locale.UK, "%.2f", monthlyEquiv)}/mo)"
    } else {
        "1 Year of Pro Access (£0.83/mo)"
    }

    val monthlySubtitle = "Billed monthly, cancel anytime"

    return Pair(
        PlanPricingInfo(annualPrice, annualSubtitle, savingsTag),
        PlanPricingInfo(monthlyPrice, monthlySubtitle, null)
    )
}

/**
 * Traverses context wrappers to find the hosting Activity for Google Play Billing flows.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
