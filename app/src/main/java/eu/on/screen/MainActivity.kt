package eu.on.screen

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import com.codemybrainsout.ratingdialog.RatingDialog
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import eu.on.screen.model.ListItemModel
import eu.on.screen.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
class MainActivity : AppCompatActivity() {
    private val REQUEST_OVERLAY_PERMISSION = 1001
    private var isServiceRunning = false
    lateinit var buttonCLick: ExtendedFloatingActionButton
    private var sharedPreferences: SharedPreferences? = null
    var editor: SharedPreferences.Editor? = null
    private var adView: AdManagerAdView? = null
    private var testBannerView: AdView? = null
    private var nativeAd: NativeAd? = null
    private lateinit var appOpenManager: AppOpenManager
    private lateinit var adContainer: FrameLayout
    private lateinit var nativeAdContainer: FrameLayout
    private lateinit var toolsSection: LinearLayout
    private lateinit var statusContainer: LinearLayout
    private lateinit var statusTitle: TextView
    private lateinit var statusSubtitle: TextView

    @SuppressLint("MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)
        editor = sharedPreferences?.edit()
        val sharedPreferencesCheckBox = this.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val ratingDialog: RatingDialog = RatingDialog.Builder(this)
            .threshold(3)
            .session(1)
            .onRatingBarFormSubmit { feedback -> Log.i(TAG, "onRatingBarFormSubmit: $feedback") }
            .build()
        actionBar?.show()
        setContentView(R.layout.activity_main)

        appOpenManager = (application as KidsDrawingApplication).appOpenManager
        val backgroundScope = CoroutineScope(Dispatchers.IO)
        adContainer = findViewById(R.id.adContainer)
        nativeAdContainer = findViewById(R.id.nativeAdContainer)
        toolsSection = findViewById(R.id.toolsSection)
        loadNativeAd()
        loadBannerAd()

        val dataList = mutableListOf<ListItemModel>()

        // Add Settings item at the top
        dataList.add(
            ListItemModel(
                R.drawable.settings,
                "Floating Icon Size",
                "Tap to customize the size of floating action buttons (Mini, Normal, or Large). Changes will apply after restarting the drawing overlay.",
                false
            )
        )
        
        dataList.add(
            ListItemModel(
                R.drawable.undo,
                "Undo Button",
                "Tap to undo your last drawing action. This button removes the most recent stroke or shape you drew, allowing you to correct mistakes easily.",
                false
            )
        )
        dataList.add(
            ListItemModel(
                R.drawable.redo,
                "Redo Button",
                "Tap to redo a previously undone action. If you accidentally undo something, use this button to restore it.",
                false
            )
        )
        dataList.add(
            ListItemModel(
                R.drawable.interests,
                "Shape Button",
                "Tap to switch to shape drawing mode. You can draw lines, circles, and rectangles. Tap again to adjust the size of shapes.",
                false
            )
        )
        dataList.add(
            ListItemModel(
                R.drawable.doodle,
                "Pen/Draw Button",
                "Tap to switch to free drawing mode. Use your finger to draw freely on the screen. Tap again to adjust brush size.",
                false
            )
        )
        dataList.add(
            ListItemModel(
                R.drawable.color_palette,
                "Color Picker",
                "Tap to open the color picker and choose a color for your drawings. Select from a wide range of colors to customize your artwork.",
                false
            )
        )
        dataList.add(
            ListItemModel(
                R.drawable.eraser,
                "Eraser Tool",
                "Tap to switch to eraser mode. Use your finger to erase parts of your drawing. Tap again to adjust eraser size.",
                false
            )
        )
        dataList.add(
            ListItemModel(
                R.drawable.bin,
                "Delete All",
                "Tap to clear all drawings on the screen. This action cannot be undone, so use it carefully.",
                false
            )
        )
        dataList.add(
            ListItemModel(
                R.drawable.hide,
                "Hide/Show",
                "Tap to hide or show the drawing overlay. When hidden, you can interact with other apps normally. Tap again to show the overlay.",
                false
            )
        )
        dataList.add(
            ListItemModel(
                R.drawable.logout,
                "Exit Button",
                "Tap to exit the drawing overlay and return to the main screen. Your drawings will be cleared when you exit.",
                false
            )
        )

        populateToolGuide(dataList)
        
        buttonCLick = findViewById(R.id.btnPlay)
        statusContainer = findViewById(R.id.statusContainer)
        statusTitle = findViewById(R.id.textStatusTitle)
        statusSubtitle = findViewById(R.id.textStatusSubtitle)
        renderServiceState()
        buttonCLick.setOnClickListener {

            if (isServiceRunning) {
                stopService(Intent(this, DrawService::class.java))
                stopService(Intent(this, DrawTestService::class.java))
                isServiceRunning = false
                renderServiceState()
            } else {
                checkOverlayPermission()
            }
        }



    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {

            Toast.makeText(baseContext, "Landscape Mode", Toast.LENGTH_SHORT).show()
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Toast.makeText(baseContext, "Portrait Mode", Toast.LENGTH_SHORT).show()
        }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.custom_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.nav_settings) {
            try {
                showFabSizeDialog()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error showing FAB size dialog: ${e.message}", e)
                Toast.makeText(this, "Error opening settings: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun showFabSizeDialog() {
        // Load saved FAB size (default: 1 = normal)
        val savedFabSize = sharedPreferences?.getInt("fabSize", 1) ?: 1
        
        // Inflate custom layout
        val dialogView = layoutInflater.inflate(R.layout.fab_size_dialog, null)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekBarFabSize)
        val textSizeValue = dialogView.findViewById<TextView>(R.id.textFabSizeValue)
        
        seekBar.progress = savedFabSize
        updateFabSizeText(textSizeValue, savedFabSize)
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateFabSizeText(textSizeValue, progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Create AlertDialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Floating Icon Size")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                val selectedSize = seekBar.progress
                editor?.putInt("fabSize", selectedSize)?.commit()
                Toast.makeText(this, "FAB size saved. Restart drawing to apply changes.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }

    private fun populateToolGuide(dataList: List<ListItemModel>) {
        toolsSection.removeAllViews()

        val headerView = layoutInflater.inflate(R.layout.list_header, toolsSection, false)
        toolsSection.addView(headerView)

        dataList.forEach { item ->
            val itemView = layoutInflater.inflate(R.layout.list_item_layout, toolsSection, false)
            val imageView = itemView.findViewById<ImageView>(R.id.sss)
            val titleView = itemView.findViewById<TextView>(R.id.minimize)
            val descriptionView = itemView.findViewById<TextView>(R.id.descript)
            val switchView = itemView.findViewById<Switch>(R.id.switch1)

            imageView.setImageResource(item.imageResId)
            titleView.text = item.title
            descriptionView.text = item.description
            switchView.visibility = View.GONE

            itemView.setOnClickListener {
                if (item.title == "Floating Icon Size") {
                    showFabSizeDialog()
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = (10 * resources.displayMetrics.density).toInt()
            itemView.layoutParams = params
            toolsSection.addView(itemView)
        }
    }
    
    private fun updateFabSizeText(textView: TextView?, size: Int) {
        val sizeText = when (size) {
            0 -> "Mini (40dp)"
            1 -> "Normal (56dp)"
            2 -> "Large (64dp)"
            else -> "Normal (56dp)"
        }
        textView?.text = sizeText
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "We need permission Display over other apps to work well",
                Toast.LENGTH_SHORT
            ).show()
            // Yêu cầu cấp phép từ người dùng
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:" + packageName)

            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        } else {

            val svc = Intent(this, DrawService::class.java)
            startForegroundService(svc)

            val aaa = Intent(this, DrawTestService::class.java)
            startForegroundService(aaa)

            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
        if (adView == null) {
            loadBannerAd()
        }
        isServiceRunning = isServiceRunning(this, DrawService::class.java)
        if (isServiceRunning) {
            Log.e("231", "service is running")
            val intentHide = Intent("action.hideDraw")
            intentHide.putExtra("hideDraw", true)
            sendBroadcast(intentHide)
        } else {
            Log.e("231", "not run is running")
        }
        renderServiceState()
    }

    override fun onPause() {
        adView?.pause()
        testBannerView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        adView?.destroy()
        adView = null
        testBannerView?.destroy()
        testBannerView = null
        nativeAd?.destroy()
        nativeAd = null
        super.onDestroy()
    }

    private fun loadNativeAd() {
        val adLoader = AdLoader.Builder(this, AdConfig.nativeUnitId)
            .forNativeAd { loadedNativeAd ->
                nativeAd?.destroy()
                nativeAd = loadedNativeAd

                val adView = layoutInflater.inflate(
                    R.layout.view_native_ad,
                    nativeAdContainer,
                    false
                ) as NativeAdView
                bindNativeAd(loadedNativeAd, adView)
                nativeAdContainer.removeAllViews()
                nativeAdContainer.addView(adView)
                nativeAdContainer.visibility = View.VISIBLE
                Log.d("MainActivity", "Native ad loaded. useTestAds=${AdConfig.useTestAds}")
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder().build()
            )
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("MainActivity", "Native ad failed to load: ${error.message}")
                    nativeAdContainer.removeAllViews()
                    nativeAdContainer.visibility = View.GONE
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    private fun bindNativeAd(ad: NativeAd, adView: NativeAdView) {
        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        val bodyView = adView.findViewById<TextView>(R.id.ad_body)
        val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
        val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
        val advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser)
        val mediaView = adView.findViewById<MediaView>(R.id.ad_media)

        adView.headlineView = headlineView
        adView.bodyView = bodyView
        adView.callToActionView = ctaView
        adView.iconView = iconView
        adView.advertiserView = advertiserView
        adView.mediaView = mediaView

        headlineView.text = ad.headline

        bodyView.text = ad.body
        bodyView.visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE

        ctaView.text = ad.callToAction
        ctaView.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE

        advertiserView.text = ad.advertiser
        advertiserView.visibility = if (ad.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE

        val icon = ad.icon
        if (icon != null) {
            iconView.setImageDrawable(icon.drawable)
            iconView.visibility = View.VISIBLE
        } else {
            iconView.visibility = View.GONE
        }

        mediaView.mediaContent = ad.mediaContent
        mediaView.visibility = if (ad.mediaContent == null) View.GONE else View.VISIBLE

        adView.setNativeAd(ad)
    }

    private fun loadBannerAd() {
        adContainer.post {
            val adWidthPixels = adContainer.width.takeIf { it > 0 }
                ?: resources.displayMetrics.widthPixels - (40 * resources.displayMetrics.density).toInt()
            val adWidth = (adWidthPixels / resources.displayMetrics.density).toInt()
            if (adWidth <= 0) {
                return@post
            }

            adView?.destroy()
            testBannerView?.destroy()
            adView = null
            testBannerView = null

            val adListener = object : AdListener() {
                override fun onAdLoaded() {
                    adContainer.visibility = View.VISIBLE
                    Log.d("MainActivity", "Banner loaded. useTestAds=${AdConfig.useTestAds}")
                }

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    Log.w("MainActivity", "Banner failed to load: ${error.message}")
                    adContainer.visibility = View.GONE
                    adView = null
                    testBannerView = null
                }
            }

            adContainer.removeAllViews()
            if (AdConfig.useTestAds) {
                val bannerView = AdView(this).apply {
                    adUnitId = AdConfig.bannerUnitId
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@MainActivity, adWidth))
                    this.adListener = adListener
                }
                adContainer.addView(bannerView)
                testBannerView = bannerView
                bannerView.loadAd(AdRequest.Builder().build())
            } else {
                val bannerView = AdManagerAdView(this).apply {
                    adUnitId = AdConfig.bannerUnitId
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@MainActivity, adWidth))
                    this.adListener = adListener
                }
                adContainer.addView(bannerView)
                adView = bannerView
                bannerView.loadAd(AdManagerAdRequest.Builder().build())
            }
        }
    }

    private fun renderServiceState() {
        val isActive = isServiceRunning
        val buttonColor = if (isActive) R.color.home_danger else R.color.home_hero_start
        val iconRes = if (isActive) R.drawable.stop_circle else R.drawable.play_arrow
        val titleRes =
            if (isActive) R.string.home_status_active_title else R.string.home_status_idle_title
        val subtitleRes =
            if (isActive) R.string.home_status_active_subtitle else R.string.home_status_idle_subtitle
        val statusBackgroundRes =
            if (isActive) R.drawable.bg_home_status_active else R.drawable.bg_home_status_idle
        val colorStateList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))

        buttonCLick.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, buttonColor))
        buttonCLick.setText(if (isActive) R.string.stop else R.string.start)
        buttonCLick.setTextColor(Color.WHITE)
        buttonCLick.iconTint = colorStateList
        buttonCLick.setIconResource(iconRes)

        statusTitle.setText(titleRes)
        statusSubtitle.setText(subtitleRes)
        statusContainer.background =
            ResourcesCompat.getDrawable(resources, statusBackgroundRes, theme)
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
