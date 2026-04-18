package eu.on.screen

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds

class KidsDrawingApplication : Application() {
    lateinit var appOpenManager: AppOpenManager
        private set
    lateinit var interstitialAdManager: InterstitialAdManager
        private set

    override fun onCreate() {
        super.onCreate()
        AdConfig.init(applicationInfo)
        MobileAds.initialize(this) {}
        Log.d("Ads", "Initializing ads. useTestAds=${AdConfig.useTestAds}")
        appOpenManager = AppOpenManager(this)
        interstitialAdManager = InterstitialAdManager(this)
    }
}
