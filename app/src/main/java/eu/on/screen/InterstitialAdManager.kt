package eu.on.screen

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import java.util.concurrent.TimeUnit

class InterstitialAdManager(
    private val application: Application
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private var interstitialAd: AdManagerInterstitialAd? = null
    private var isLoading = false
    private var isShowing = false

    init {
        preload()
    }

    fun preload() {
        if (isLoading || interstitialAd != null) {
            return
        }

        isLoading = true
        AdManagerInterstitialAd.load(
            application,
            AdIds.INTERSTITIAL,
            AdManagerAdRequest.Builder().build(),
            object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: AdManagerInterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    ad.setOnPaidEventListener(
                        OnPaidEventListener { adValue ->
                            Log.d(
                                TAG,
                                "Interstitial paid event valueMicros=${adValue.valueMicros} currency=${adValue.currencyCode}"
                            )
                        }
                    )
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoading = false
                    Log.w(TAG, "Interstitial failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    fun recordDrawingSessionCompleted() {
        val completedSessions = preferences.getInt(PREF_COMPLETED_DRAWING_SESSIONS, 0) + 1
        preferences.edit().putInt(PREF_COMPLETED_DRAWING_SESSIONS, completedSessions).apply()
    }

    fun maybeShowAfterDrawingSession(activity: Activity) {
        if (!isEligibleToShow()) {
            preload()
            return
        }

        val ad = interstitialAd
        if (ad == null || isShowing) {
            preload()
            return
        }

        isShowing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preferences.edit()
                    .putLong(PREF_LAST_INTERSTITIAL_SHOW_AT, System.currentTimeMillis())
                    .apply()
                interstitialAd = null
                isShowing = false
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                interstitialAd = null
                isShowing = false
                preload()
            }

            override fun onAdShowedFullScreenContent() {
                interstitialAd = null
            }
        }
        ad.show(activity)
    }

    private fun isEligibleToShow(): Boolean {
        val completedSessions = preferences.getInt(PREF_COMPLETED_DRAWING_SESSIONS, 0)
        if (completedSessions < MIN_COMPLETED_SESSIONS_BEFORE_SHOW) {
            return false
        }

        val lastShownAt = preferences.getLong(PREF_LAST_INTERSTITIAL_SHOW_AT, 0L)
        val cooldownPassed =
            System.currentTimeMillis() - lastShownAt >= MIN_INTERSTITIAL_INTERVAL_MS
        return cooldownPassed
    }

    companion object {
        private const val TAG = "InterstitialAdManager"
        private const val PREF_COMPLETED_DRAWING_SESSIONS = "pref_completed_drawing_sessions"
        private const val PREF_LAST_INTERSTITIAL_SHOW_AT = "pref_last_interstitial_show_at"
        private const val MIN_COMPLETED_SESSIONS_BEFORE_SHOW = 2
        private val MIN_INTERSTITIAL_INTERVAL_MS = TimeUnit.MINUTES.toMillis(8)
    }
}
