package eu.on.screen

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.MobileAds
import java.util.*


@RequiresApi(Build.VERSION_CODES.P)
class AppOpenManager(context: Context) :
    Application.ActivityLifecycleCallbacks {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd: Boolean = false
    private var loadTime: Long = 0
    private val appContext = context.applicationContext as Application

    companion object {
        private var isRegistered = false
    }

    init {
        // Correctly register on the Application object only once
        if (!isRegistered) {
            appContext.registerActivityLifecycleCallbacks(this)
            isRegistered = true
            MobileAds.initialize(appContext) {}
        }
    }

    fun fetchAd() {
        if (isAdAvailable() || isLoadingAd) {
            return
        }

        isLoadingAd = true
        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            appContext,
            "/21849154601,23155531379/Ad.Plus-APP-APPOpen",
            adRequest,
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadTime = Date().time
                    isLoadingAd = false
                }

                override fun onAdFailedToLoad(loadAdError: com.google.android.gms.ads.LoadAdError) {
                    isLoadingAd = false
                }
            }
        )
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour = 3600000
        return dateDifference < numHours * numMilliSecondsPerHour
    }

    fun showAdIfAvailable(activity: Activity) {
        if (isAdAvailable()) {
            appOpenAd?.show(activity)
        } else {
            fetchAd()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        showAdIfAvailable(activity)
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
