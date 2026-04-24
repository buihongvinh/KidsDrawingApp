package eu.on.screen

import android.app.Activity
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

class BillingManager(
    private val activity: Activity,
    private val onProductDetailsChanged: (ProductDetails?) -> Unit,
    private val onVipStateChanged: (Boolean) -> Unit,
    private val onMessage: (String) -> Unit
) : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private var vipProductDetails: ProductDetails? = null
    private var vipOfferDetails: ProductDetails.SubscriptionOfferDetails? = null

    fun start() {
        onVipStateChanged(VipManager.isVip(activity))

        if (!VipManager.isVipProductConfigured(activity)) {
            onProductDetailsChanged(null)
            return
        }

        if (billingClient?.isReady == true) {
            queryVipProduct()
            queryOwnedPurchases()
            return
        }

        billingClient = BillingClient.newBuilder(activity)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .setListener(this)
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryVipProduct()
                    queryOwnedPurchases()
                } else {
                    onMessage("Google Play Billing is unavailable right now.")
                }
            }

            override fun onBillingServiceDisconnected() {
                vipProductDetails = null
                onProductDetailsChanged(null)
            }
        })
    }

    fun launchVipPurchase() {
        if (!VipManager.isVipProductConfigured(activity)) {
            onMessage("Set your VIP product ID before testing purchases.")
            return
        }

        val productDetails = vipProductDetails
        if (productDetails == null) {
            onMessage("VIP product is still loading from Google Play.")
            start()
            return
        }

        val offerDetails = vipOfferDetails
        if (offerDetails == null) {
            onMessage("No active subscription offer was returned from Google Play.")
            start()
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerDetails.offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = billingClient?.launchBillingFlow(activity, billingFlowParams)
        if (result == null) {
            onMessage("Google Play Billing is not connected yet.")
            return
        }

        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryOwnedPurchases()
            }
            else -> {
                onMessage("Could not start purchase flow.")
            }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                handlePurchases(purchases.orEmpty(), markVipAsFalseWhenMissing = true)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                onMessage("Purchase canceled.")
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryOwnedPurchases()
            }
            else -> {
                onMessage("Purchase failed. Please try again.")
            }
        }
    }

    private fun queryVipProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(VipManager.getVipProductId(activity))
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                vipProductDetails = null
                onProductDetailsChanged(null)
                onMessage("Could not load VIP product details.")
                return@queryProductDetailsAsync
            }

            vipProductDetails = productDetailsList.firstOrNull()
            vipOfferDetails = vipProductDetails?.subscriptionOfferDetails
                ?.firstOrNull { it.offerId == null }
                ?: vipProductDetails?.subscriptionOfferDetails?.firstOrNull()
            onProductDetailsChanged(vipProductDetails)
        }
    }

    private fun queryOwnedPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                return@queryPurchasesAsync
            }

            handlePurchases(purchases.orEmpty(), markVipAsFalseWhenMissing = true)
        }
    }

    private fun handlePurchases(
        purchases: List<Purchase>,
        markVipAsFalseWhenMissing: Boolean
    ) {
        val vipProductId = VipManager.getVipProductId(activity)
        val vipPurchase = purchases.firstOrNull { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.contains(vipProductId)
        }

        if (vipPurchase == null) {
            if (markVipAsFalseWhenMissing) {
                updateVipState(false)
            }
            return
        }

        if (vipPurchase.isAcknowledged) {
            updateVipState(true)
            return
        }

        val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(vipPurchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(acknowledgeParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                updateVipState(true)
                onMessage("VIP activated. Ads are now disabled.")
            } else {
                onMessage("Purchase completed but acknowledgement failed.")
            }
        }
    }

    private fun updateVipState(isVip: Boolean) {
        VipManager.setVip(activity, isVip)
        onVipStateChanged(isVip)
    }
}
