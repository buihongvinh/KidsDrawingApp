package eu.on.screen

import android.content.Context
import androidx.preference.PreferenceManager

object VipManager {
    private const val PREF_IS_VIP = "pref_is_vip"
    private const val PRODUCT_ID_PLACEHOLDER = "replace_me.vip_remove_ads"

    fun isVip(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_IS_VIP, false)
    }

    fun setVip(context: Context, isVip: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_IS_VIP, isVip)
            .apply()
    }

    fun getVipProductId(context: Context): String {
        return context.getString(R.string.billing_vip_product_id).trim()
    }

    fun isVipProductConfigured(context: Context): Boolean {
        val productId = getVipProductId(context)
        return productId.isNotBlank() && productId != PRODUCT_ID_PLACEHOLDER
    }
}
