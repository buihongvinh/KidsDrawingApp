package eu.on.screen

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.concurrent.TimeUnit

class InterstitialAdManager(
    private val application: Application
) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    private var interstitialAd: AdManagerInterstitialAd? = null
    private var testInterstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var isShowing = false

    init {
        preload()
    }

    fun preload() {
        if (VipManager.isVip(application)) {
            interstitialAd = null
            testInterstitialAd = null
            isLoading = false
            return
        }

        if (isLoading || interstitialAd != null || testInterstitialAd != null) {
            return
        }

        isLoading = true
        if (AdConfig.useTestAds) {
            InterstitialAd.load(
                application,
                AdConfig.interstitialUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        testInterstitialAd = ad
                        isLoading = false
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        isLoading = false
                        Log.w(TAG, "Interstitial failed to load: ${loadAdError.message}")
                    }
                }
            )
        } else {
            AdManagerInterstitialAd.load(
                application,
                AdConfig.interstitialUnitId,
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
    }

    fun recordDrawingSessionCompleted() {
        val completedSessions = preferences.getInt(PREF_COMPLETED_DRAWING_SESSIONS, 0) + 1
        preferences.edit().putInt(PREF_COMPLETED_DRAWING_SESSIONS, completedSessions).apply()
    }

    fun maybeShowAfterDrawingSession(activity: Activity) {
        if (VipManager.isVip(application)) {
            interstitialAd = null
            testInterstitialAd = null
            return
        }

        if (!isEligibleToShow()) {
            preload()
            return
        }

        if (isShowing) {
            preload()
            return
        }

        val prodAd = interstitialAd
        val debugAd = testInterstitialAd
        if (prodAd == null && debugAd == null) {
            preload()
            return
        }

        isShowing = true
        val fullScreenCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preferences.edit()
                    .putLong(PREF_LAST_INTERSTITIAL_SHOW_AT, System.currentTimeMillis())
                    .apply()
                interstitialAd = null
                testInterstitialAd = null
                isShowing = false
                preload()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                interstitialAd = null
                testInterstitialAd = null
                isShowing = false
                preload()
            }

            override fun onAdShowedFullScreenContent() {
                interstitialAd = null
                testInterstitialAd = null
            }
        }
        if (AdConfig.useTestAds) {
            debugAd?.fullScreenContentCallback = fullScreenCallback
            debugAd?.show(activity)
        } else {
            prodAd?.fullScreenContentCallback = fullScreenCallback
            prodAd?.show(activity)
        }
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
