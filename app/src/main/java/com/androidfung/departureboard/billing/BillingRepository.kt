package com.androidfung.departureboard.billing

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.androidfung.departureboard.data.datastore.dataStore
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interface defining subscription, billing, and quota capabilities.
 */
interface BillingDataSource {
    val isProFlow: Flow<Boolean>
    val availableProducts: StateFlow<List<ProductDetails>>
    val billingError: StateFlow<String?>
    fun queryAvailableProducts()
    fun refreshPurchases()
    fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails, basePlanId: String? = null): Boolean
    suspend fun tryConsumeAiQuery(): Boolean
    suspend fun getRemainingAiQueries(): Int
    fun setDebugPro(enabled: Boolean)
}

/**
 * Repository and manager interface for Google Play Billing (PBL v9).
 * Manages subscription status, product queries, purchases, and quota checks.
 */
class BillingRepository(
    private val context: Context
) : BillingDataSource, PurchasesUpdatedListener {

    companion object {
        private val KEY_IS_PRO = booleanPreferencesKey("user_is_pro")
        private val KEY_AI_QUERY_DATE = stringPreferencesKey("ai_query_date")
        private val KEY_AI_QUERY_COUNT = intPreferencesKey("ai_query_count")

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: BillingRepository? = null

        fun getInstance(context: Context): BillingRepository {
            return instance ?: synchronized(this) {
                instance ?: BillingRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Flow of Pro subscription status persisted in DataStore
    override val isProFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_PRO] ?: false
    }

    private val _availableProducts = MutableStateFlow<List<ProductDetails>>(emptyList())
    override val availableProducts: StateFlow<List<ProductDetails>> = _availableProducts.asStateFlow()

    private val _billingError = MutableStateFlow<String?>(null)
    override val billingError: StateFlow<String?> = _billingError.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    init {
        startBillingConnection()
    }

    private fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAvailableProducts()
                    refreshPurchases()
                } else {
                    android.util.Log.w("BillingRepository", "Setup failed: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Play Billing 7+ automatically manages reconnections
                android.util.Log.d("BillingRepository", "Billing service disconnected")
            }
        })
    }

    override fun queryAvailableProducts() {
        if (!billingClient.isReady) return

        val subscriptionProductList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SubscriptionTier.PRODUCT_SUBSCRIPTION_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val querySubsParams = QueryProductDetailsParams.newBuilder()
            .setProductList(subscriptionProductList)
            .build()

        billingClient.queryProductDetailsAsync(querySubsParams) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _availableProducts.value = detailsResult.productDetailsList
            }
        }
    }

    /**
     * Refreshes active subscription purchases from Google Play.
     */
    override fun refreshPurchases() {
        if (!billingClient.isReady) return

        val subsParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(subsParams) { result, purchases ->
            var hasActivePro = false
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                for (p in purchases) {
                    if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        hasActivePro = true
                        acknowledgePurchaseIfNeeded(p)
                    }
                }
            }
            updateProStatusLocally(hasActivePro)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    acknowledgePurchaseIfNeeded(purchase)
                    updateProStatusLocally(true)
                }
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // User cancelled flow
        } else {
            _billingError.value = billingResult.debugMessage
        }
    }

    private fun acknowledgePurchaseIfNeeded(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    android.util.Log.d("BillingRepository", "Purchase acknowledged successfully")
                }
            }
        }
    }

    private fun updateProStatusLocally(isPro: Boolean) {
        scope.launch {
            context.dataStore.edit { preferences ->
                preferences[KEY_IS_PRO] = isPro
            }
        }
    }

    /**
     * Launches Google Play Billing purchase sheet for a selected subscription product and base plan.
     */
    override fun launchPurchaseFlow(activity: Activity, productDetails: ProductDetails, basePlanId: String?): Boolean {
        if (!billingClient.isReady) {
            startBillingConnection()
            return false
        }

        val offer = if (basePlanId != null) {
            productDetails.subscriptionOfferDetails?.firstOrNull { it.basePlanId == basePlanId }
                ?: productDetails.subscriptionOfferDetails?.firstOrNull()
        } else {
            productDetails.subscriptionOfferDetails?.firstOrNull()
        }

        val offerToken = offer?.offerToken ?: return false
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val response = billingClient.launchBillingFlow(activity, flowParams)
        if (response.responseCode != BillingClient.BillingResponseCode.OK) {
            android.util.Log.w("BillingRepository", "launchBillingFlow returned: code=${response.responseCode}, msg=${response.debugMessage}")
        }
        return response.responseCode == BillingClient.BillingResponseCode.OK
    }

    // =========================================================================
    // Quota Helpers: AI Queries
    // =========================================================================

    /**
     * Checks if user has remaining AI queries for today and increments count if allowed.
     * Returns true if query is allowed, false if free quota is exceeded.
     */
    override suspend fun tryConsumeAiQuery(): Boolean {
        val isPro = isProFlow.first()
        if (isPro) return true // Unlimited for Pro

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
        var allowed = false

        context.dataStore.edit { preferences ->
            val storedDate = preferences[KEY_AI_QUERY_DATE] ?: ""
            val count = if (storedDate == today) {
                preferences[KEY_AI_QUERY_COUNT] ?: 0
            } else {
                0
            }

            if (count < SubscriptionTier.FREE_MAX_DAILY_AI_QUERIES) {
                preferences[KEY_AI_QUERY_DATE] = today
                preferences[KEY_AI_QUERY_COUNT] = count + 1
                allowed = true
            } else {
                allowed = false
            }
        }
        return allowed
    }

    /**
     * Returns remaining daily AI queries for the user.
     */
    override suspend fun getRemainingAiQueries(): Int {
        val isPro = isProFlow.first()
        if (isPro) return SubscriptionTier.PRO_MAX_DAILY_AI_QUERIES

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
        val preferences = context.dataStore.data.first()
        val storedDate = preferences[KEY_AI_QUERY_DATE] ?: ""
        val count = if (storedDate == today) preferences[KEY_AI_QUERY_COUNT] ?: 0 else 0
        return (SubscriptionTier.FREE_MAX_DAILY_AI_QUERIES - count).coerceAtLeast(0)
    }

    /**
     * For manual developer testing / previews.
     */
    override fun setDebugPro(enabled: Boolean) {
        updateProStatusLocally(enabled)
    }
}
