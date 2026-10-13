package com.darkweblock.app

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing for the one-time Pro unlock.
 *
 * DORMANT UNTIL the managed product [PRODUCT_ID] exists in Play Console and the
 * app is installed from a track Play recognises. Until then the product query
 * returns nothing and [available] stays false; the dashboard keeps the debug
 * test-unlock. Entitlement is mirrored into [PlanStore] so the rest of the app
 * is unchanged.
 */
class BillingManager(
    private val context: Context,
    private val onChanged: () -> Unit,
) {
    var available: Boolean = false
        private set
    var price: String? = null
        private set

    private var productDetails: ProductDetails? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach(::handlePurchase)
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (client.isReady) {
            queryProduct(); queryOwned(); return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryOwned()
                }
            }

            override fun onBillingServiceDisconnected() {
                available = false
            }
        })
    }

    fun stop() {
        runCatching { client.endConnection() }
    }

    /** Launches the Play purchase sheet. No-op until the product is available. */
    fun purchase(activity: Activity) {
        val details = productDetails ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        client.queryProductDetailsAsync(params) { result, productList ->
            val details = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productList.firstOrNull()
            } else {
                null
            }
            productDetails = details
            price = details?.oneTimePurchaseOfferDetails?.formattedPrice
            available = details != null
            onChanged()
        }
    }

    private fun queryOwned() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::handlePurchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (PRODUCT_ID !in purchase.products) return

        PlanStore.setPro(context, true)
        onChanged()

        if (!purchase.isAcknowledged) {
            val ack = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(ack) { }
        }
    }

    companion object {
        /** Must match the managed product id created in Play Console. */
        const val PRODUCT_ID = "pro_unlock"
    }
}
