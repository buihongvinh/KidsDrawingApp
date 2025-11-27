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
import androidx.preference.PreferenceManager
import com.codemybrainsout.ratingdialog.RatingDialog
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
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
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
class MainActivity : AppCompatActivity() {
    private val REQUEST_OVERLAY_PERMISSION = 1001
    private var isServiceRunning = false
    lateinit var buttonCLick: ExtendedFloatingActionButton
    private var sharedPreferences: SharedPreferences? = null
    var editor: SharedPreferences.Editor? = null
    private lateinit var adView: AdView
    private lateinit var appOpenManager: AppOpenManager

    private var appOpenAd: AppOpenAd? = null

    private var isAdDisplayed: Boolean = false
    private val appOpenAdLoadCallback = object : AppOpenAdLoadCallback() {
        override fun onAdLoaded(ad: AppOpenAd) {
            appOpenAd = ad // Initialize the appOpenAd property here
         //   appOpenAd!!.show(this@MainActivity)
        }

        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
            // Handle ad loading failure
        }
    }

    private fun loadAppOpenAd() {
        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            this,
            "/21849154601,23155531379/Ad.Plus-APP-APPOpen",
            adRequest,
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            appOpenAdLoadCallback
        )
    }



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
        val backgroundScope = CoroutineScope(Dispatchers.IO)
        MobileAds.initialize(this) {}
        loadAppOpenAd()

        backgroundScope.launch {
            // Initialize the Google Mobile Ads SDK on a background thread.
            MobileAds.initialize(this@MainActivity) {}
        }
        // Find the AdView as defined in the layout XML
        adView = findViewById(R.id.adView)

        // Create an ad request
        val adRequest = AdRequest.Builder().build()

        // Load the ad into the AdView
        adView.loadAd(adRequest)
        //   setSupportActionBar(findViewById(R.id.toolbar))
        val listView: ListView = findViewById(R.id.listView)
        
        // Add header to ListView
        val headerView = layoutInflater.inflate(R.layout.list_header, null)
        listView.addHeaderView(headerView, null, false)
        
        val dataList = mutableListOf<ListItemModel>()

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

        val adapter = SettingsAdapter(this, dataList)
        listView.adapter = adapter
        buttonCLick = findViewById(R.id.btnPlay)
        buttonCLick.setOnClickListener {

            if (isServiceRunning) {
                stopService(Intent(this, DrawService::class.java))
                stopService(Intent(this, DrawTestService::class.java))
                stopService(intent)
                buttonCLick.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_gradient_start))

                buttonCLick.text = "START"
                buttonCLick.setTextColor(Color.WHITE)
                val colorStateList =
                    ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))

                buttonCLick.iconTint = colorStateList
                buttonCLick.icon =
                    (ContextCompat.getDrawable(applicationContext, R.drawable.play_arrow));

                isServiceRunning = !isServiceRunning
                // to do stop
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
            Toast.makeText(this, "Clicked Settings Icon..", Toast.LENGTH_SHORT).show()
        }
        return super.onOptionsItemSelected(item)
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
        if (isAdDisplayed) {
            // Do not show the ad if it's already displayed
            return
        }
        appOpenAd?.let {
            it.show(this)
        } ?: run {
            // If the ad is null, load it again
            loadAppOpenAd()
        }
        isServiceRunning = isServiceRunning(this, DrawService::class.java)

        if (isServiceRunning) {
            Log.e("231", "service is running")
            buttonCLick.setBackgroundColor(Color.RED)
            buttonCLick.text = "STOP"
            buttonCLick.setTextColor(Color.WHITE)
            val colorStateList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
            val intentHide = Intent("action.hideDraw")
            intentHide.putExtra("hideDraw", true)
            sendBroadcast(intentHide)
            buttonCLick.iconTint = colorStateList
            buttonCLick.icon =
                (ContextCompat.getDrawable(applicationContext, R.drawable.stop_circle));
            // The service is running
            // You can take appropriate actions here
        } else {
            Log.e("231", "not run is running")

            // The service is not running
            // You can take different actions here if needed
        }
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