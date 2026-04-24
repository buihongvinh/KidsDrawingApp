package eu.on.screen

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdRequest
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
    private var pendingShowRequest = false
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
        if (VipManager.isVip(application)) {
            pendingShowRequest = false
            appOpenAd = null
            return
        }

        val launchCount = preferences.getInt(PREF_APP_OPEN_LAUNCH_COUNT, 0) + 1
        preferences.edit().putInt(PREF_APP_OPEN_LAUNCH_COUNT, launchCount).apply()

        if (!hasStartedOnce) {
            hasStartedOnce = true
            fetchAd()
            return
        }

        if (shouldShowAd(currentActivity, launchCount)) {
            pendingShowRequest = true
            currentActivity?.let { showAdIfAvailable(it) }
        } else {
            pendingShowRequest = false
            fetchAd()
        }
    }

    fun fetchAd() {
        if (VipManager.isVip(application)) {
            appOpenAd = null
            pendingShowRequest = false
            isLoadingAd = false
            return
        }

        if (isLoadingAd || isAdAvailable()) {
            Log.d(TAG, "Skip fetch: isLoading=$isLoadingAd isAdAvailable=${isAdAvailable()}")
            return
        }

        isLoadingAd = true
        Log.d(TAG, "Fetching app open ad")
        if (AdConfig.useTestAds) {
            AppOpenAd.load(
                application,
                AdConfig.appOpenUnitId,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        handleAdLoaded(ad)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        handleAdFailedToLoad(loadAdError)
                    }
                }
            )
        } else {
            AppOpenAd.load(
                application,
                AdConfig.appOpenUnitId,
                AdManagerAdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        handleAdLoaded(ad)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        handleAdFailedToLoad(loadAdError)
                    }
                }
            )
        }
    }

    private fun handleAdLoaded(ad: AppOpenAd) {
        appOpenAd = ad
        loadTime = Date().time
        isLoadingAd = false
        Log.d(
            TAG,
            "App open loaded. useTestAds=${AdConfig.useTestAds} pendingShowRequest=$pendingShowRequest currentActivity=${currentActivity?.javaClass?.simpleName}"
        )
        ad.setOnPaidEventListener(
            OnPaidEventListener { adValue ->
                Log.d(
                    TAG,
                    "App open paid event valueMicros=${adValue.valueMicros} currency=${adValue.currencyCode}"
                )
            }
        )
        currentActivity?.let { activity ->
            if (pendingShowRequest && shouldShowAd(activity, preferences.getInt(PREF_APP_OPEN_LAUNCH_COUNT, 0))) {
                Log.d(TAG, "Showing app open immediately after load on ${activity.javaClass.simpleName}")
                showAdIfAvailable(activity)
            }
        }
    }

    private fun handleAdFailedToLoad(loadAdError: LoadAdError) {
        isLoadingAd = false
        Log.w(TAG, "App open failed to load: ${loadAdError.message}")
    }

    private fun showAdIfAvailable(activity: Activity) {
        if (VipManager.isVip(application)) {
            appOpenAd = null
            pendingShowRequest = false
            return
        }

        if (isShowingAd) {
            Log.d(TAG, "Skip show: already showing")
            return
        }

        val ad = appOpenAd
        if (ad == null) {
            Log.d(TAG, "No app open ad available yet, refetching")
            fetchAd()
            return
        }

        isShowingAd = true
        pendingShowRequest = false
        Log.d(TAG, "Showing app open on ${activity.javaClass.simpleName}")
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                preferences.edit().putLong(PREF_APP_OPEN_LAST_SHOW_AT, System.currentTimeMillis()).apply()
                Log.d(TAG, "App open dismissed")
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

    private fun shouldShowAd(activity: Activity?, launchCount: Int): Boolean {
        if (launchCount < MIN_LAUNCHES_BEFORE_SHOW) {
            return false
        }
        if (activity == null) {
            return true
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
        Log.d(TAG, "Activity started: ${activity.javaClass.simpleName} pendingShowRequest=$pendingShowRequest")
        maybeConsumePendingShow(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) {
        if (!isShowingAd) {
            currentActivity = activity
        }
        Log.d(TAG, "Activity resumed: ${activity.javaClass.simpleName} pendingShowRequest=$pendingShowRequest")
        maybeConsumePendingShow(activity)
    }
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun maybeConsumePendingShow(activity: Activity) {
        if (!pendingShowRequest || isShowingAd) {
            return
        }
        if (activity is Introduction || activity is ColorPickerActivity) {
            Log.d(TAG, "Pending show skipped on ${activity.javaClass.simpleName}")
            return
        }
        Log.d(TAG, "Consuming pending show on ${activity.javaClass.simpleName}")
        showAdIfAvailable(activity)
    }

    companion object {
        private const val TAG = "AppOpenManager"
        private const val PREF_APP_OPEN_LAUNCH_COUNT = "pref_app_open_launch_count"
        private const val PREF_APP_OPEN_LAST_SHOW_AT = "pref_app_open_last_show_at"
        private const val MIN_LAUNCHES_BEFORE_SHOW = 1
        private const val MIN_SHOW_INTERVAL_MS = 90 * 1000L
    }
}
