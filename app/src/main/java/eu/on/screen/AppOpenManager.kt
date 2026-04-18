package eu.on.screen

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

class AppOpenManager(
    private val application: Application
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime = 0L
    private var currentActivity: Activity? = null
    private var hasStartedOnce = false
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        application.registerActivityLifecycleCallbacks(this)
        fetchAd()
    }

    override fun onStart(owner: LifecycleOwner) {
        val launchCount = preferences.getInt(PREF_APP_OPEN_LAUNCH_COUNT, 0) + 1
        preferences.edit().putInt(PREF_APP_OPEN_LAUNCH_COUNT, launchCount).apply()

        val activity = currentActivity
        if (!hasStartedOnce) {
            hasStartedOnce = true
            fetchAd()
            return
        }

        if (activity != null && shouldShowAd(activity, launchCount)) {
            showAdIfAvailable(activity)
        } else {
            fetchAd()
        }
    }

    fun fetchAd() {
        if (isLoadingAd || isAdAvailable()) {
            return
        }

        isLoadingAd = true
        AppOpenAd.load(
            application,
            AdIds.APP_OPEN,
            AdManagerAdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadTime = Date().time
                    isLoadingAd = false
                    ad.setOnPaidEventListener(
                        OnPaidEventListener { adValue ->
                            Log.d(
                                TAG,
                                "App open paid event valueMicros=${adValue.valueMicros} currency=${adValue.currencyCode}"
                            )
                        }
                    )
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    Log.w(TAG, "App open failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    private fun showAdIfAvailable(activity: Activity) {
        if (isShowingAd) {
            return
        }

        val ad = appOpenAd
        if (ad == null) {
            fetchAd()
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                preferences.edit().putLong(PREF_APP_OPEN_LAST_SHOW_AT, System.currentTimeMillis()).apply()
                fetchAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.w(TAG, "App open failed to show: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                fetchAd()
            }

            override fun onAdShowedFullScreenContent() {
                appOpenAd = null
            }
        }
        ad.show(activity)
    }

    private fun shouldShowAd(activity: Activity, launchCount: Int): Boolean {
        if (launchCount < MIN_LAUNCHES_BEFORE_SHOW) {
            return false
        }
        if (activity is Introduction || activity is ColorPickerActivity) {
            return false
        }
        val lastShownAt = preferences.getLong(PREF_APP_OPEN_LAST_SHOW_AT, 0L)
        if (System.currentTimeMillis() - lastShownAt < MIN_SHOW_INTERVAL_MS) {
            return false
        }
        return isAdAvailable()
    }

    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        return Date().time - loadTime < numHours * 60 * 60 * 1000
    }

    override fun onActivityStarted(activity: Activity) {
        if (!isShowingAd) {
            currentActivity = activity
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    companion object {
        private const val TAG = "AppOpenManager"
        private const val PREF_APP_OPEN_LAUNCH_COUNT = "pref_app_open_launch_count"
        private const val PREF_APP_OPEN_LAST_SHOW_AT = "pref_app_open_last_show_at"
        private const val MIN_LAUNCHES_BEFORE_SHOW = 3
        private const val MIN_SHOW_INTERVAL_MS = 3 * 60 * 1000L
    }
}
