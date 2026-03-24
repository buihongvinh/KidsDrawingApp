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

// Redundant ad loading removed, handled by AppOpenManager



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
        
        // Initialize appOpenManager
        appOpenManager = AppOpenManager(this)
        
        val backgroundScope = CoroutineScope(Dispatchers.IO)
        MobileAds.initialize(this) {}
        // Redundant ad loading removed

        // Initialize the Google Mobile Ads SDK on a background thread.
        // MobileAds.initialize(this@MainActivity) {} // Removed redundant initialization
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

        val adapter = SettingsAdapter(this, dataList)
        listView.adapter = adapter
        
        // Add click listener for ListView items
        listView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            // Account for header view (position 0 is header)
            val itemPosition = position - 1 // Subtract 1 for header
            
            if (itemPosition >= 0 && itemPosition < dataList.size) {
                val item = dataList[itemPosition]
                
                // Check if it's the Floating Icon Size setting
                if (item.title == "Floating Icon Size") {
                    showFabSizeDialog()
                }
                // Other items are just informational, no action needed
            }
        }
        
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
        if (isAdDisplayed) {
            // Do not show the ad if it's already displayed
            return
        }
        // Redundant ad showing removed, handled by AppOpenManager
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