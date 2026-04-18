package eu.on.screen

import android.app.Application
import com.google.android.gms.ads.MobileAds

class KidsDrawingApplication : Application() {
    lateinit var appOpenManager: AppOpenManager
        private set
    lateinit var interstitialAdManager: InterstitialAdManager
        private set

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        appOpenManager = AppOpenManager(this)
        interstitialAdManager = InterstitialAdManager(this)
    }
}
