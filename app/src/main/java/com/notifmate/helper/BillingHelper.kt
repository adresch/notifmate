package com.notifmate.helper

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*

class BillingHelper(private val context: Context, private val onPurchaseUpdated: (Boolean) -> Unit) {

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases -> handlePurchases(purchases) }
        .enablePendingPurchases()
        .build()

    private val PRODUCT_ID = "music_unlock" // Replace with your actual product ID

    fun startConnection(onConnected: () -> Unit) {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("BillingHelper", "Billing service connected successfully")
                    onConnected()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.e("BillingHelper", "Billing service disconnected")
            }
        })
    }

    fun queryPurchases(onPurchaseStatusChecked: (Boolean) -> Unit) {
        billingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasPurchased = purchases.any { it.skus.contains(PRODUCT_ID) }
                onPurchaseStatusChecked(hasPurchased)
            } else {
                onPurchaseStatusChecked(false) // Assume no purchase if query fails
            }
        }
    }

    fun launchPurchase(activity: Activity) {
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(listOf(PRODUCT_ID))
            .setType(BillingClient.SkuType.INAPP)
            .build()

        billingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && !skuDetailsList.isNullOrEmpty()) {
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setSkuDetails(skuDetailsList[0])
                    .build()
                billingClient.launchBillingFlow(activity, billingFlowParams)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>?) {
        purchases?.forEach { purchase ->
            if (purchase.skus.contains(PRODUCT_ID) && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        Log.d("BillingHelper", "Purchase acknowledged")
                        onPurchaseUpdated(true)
                    }
                }
            }
        }
    }
}
