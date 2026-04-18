package eu.on.screen

import android.content.pm.ApplicationInfo

object AdConfig {
    const val APP_ID = "ca-app-pub-9807820378544612~1021512326"

    private const val PROD_BANNER = "ca-app-pub-6561796537855097/4180018891"
    private const val PROD_NATIVE = "ca-app-pub-6561796537855097/9374081300"
    private const val PROD_INTERSTITIAL = "/21849154601,23155531379/Ad.Plus-APP-Interstitial"
    private const val PROD_APP_OPEN = "ca-app-pub-6561796537855097/2676732514"
    private const val PROD_REWARDED = "/21849154601,23155531379/Ad.Plus-APP-Rewarded"

    // Google demo ad units for Android testing:
    // https://developers.google.com/admob/android/test-ads
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"

    private var debugAdsEnabled = false

    fun init(applicationInfo: ApplicationInfo) {
        debugAdsEnabled = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    val useTestAds: Boolean
        get() = debugAdsEnabled

    val bannerUnitId: String
        get() = if (useTestAds) TEST_BANNER else PROD_BANNER

    val nativeUnitId: String
        get() = if (useTestAds) TEST_NATIVE else PROD_NATIVE

    val interstitialUnitId: String
        get() = if (useTestAds) TEST_INTERSTITIAL else PROD_INTERSTITIAL

    val appOpenUnitId: String
        get() = if (useTestAds) TEST_APP_OPEN else PROD_APP_OPEN

    val rewardedUnitId: String
        get() = if (useTestAds) TEST_REWARDED else PROD_REWARDED
}
