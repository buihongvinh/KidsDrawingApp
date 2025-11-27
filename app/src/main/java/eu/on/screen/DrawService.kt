package eu.on.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager


class DrawService : Service() {

    private var drawingView: DrawingView? = null
    private var mWindowManager: WindowManager? = null
    private var mFloatingView: View? = null
    var height: Int? = null
    var weight: Int? = null
    var receiver: BroadcastReceiver? = null
    private val params = WindowManager.LayoutParams(
        100,
       100,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )
    private var sharedPreferences: SharedPreferences? = null
    var editor: SharedPreferences.Editor? = null

    private var mImageButtonCurrentPaint: ImageButton? =
        null // A variable for current color is picked from color pallet.

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)
        editor = sharedPreferences?.edit()
        if (Build.VERSION.SDK_INT >= 26) {

            val contentIntent =
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )


            val CHANNEL_ID = "channel1"
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay notification",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Draw on Screen")
                .setContentText("Click to to back app")
                .setSmallIcon(eu.on.screen.R.drawable.ic_brush)
                .setContentIntent(contentIntent)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+
                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1, notification)
            }

        }

        var widthInPixels: Int = applicationContext.resources.displayMetrics.widthPixels
        var heightInPixels: Int = applicationContext.resources.displayMetrics.heightPixels
        mFloatingView = LayoutInflater.from(this)
            .inflate(eu.on.screen.R.layout.draw_overlay, null)
        drawingView = mFloatingView!!.findViewById(eu.on.screen.R.id.drawing_view)
        var getSizeForBrush: Int = sharedPreferences!!.getInt("setSizeForBrush", 10)
        drawingView?.setSizeForBrush(getSizeForBrush.toFloat())
        var getSetColor: Int = sharedPreferences!!.getInt("setColor", 10)
        if(getSetColor != 10){
            drawingView?.setColor(getSetColor)
        }
        var getSetSizeForBrushErase: Int = sharedPreferences!!.getInt("setSizeForBrushErase", 10)
        drawingView?.setSizeForBrushErase(getSetSizeForBrushErase.toFloat())

        params.width = widthInPixels
        params.height = heightInPixels

         receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action.equals("action.hideDraw")) {
                    val message = intent?.getBooleanExtra("hideDraw", true)
                    if (message == true) {
                        mFloatingView?.visibility = View.INVISIBLE
                    } else {
                        mFloatingView?.visibility = View.VISIBLE
                    }
                } else if (intent?.action.equals("action.setSize")) {
                    val setSize = intent?.getIntExtra("setSize", 10)
                    if (setSize != null) {
                        // Use commit() instead of apply() to ensure synchronous write
                        // This prevents cache issues where old values are read before new value is saved
                        sharedPreferences?.edit()?.putInt("setSizeForBrush", setSize)?.commit()
                        drawingView?.setSizeForBrush(setSize.toFloat())
                    }

                } else if (intent?.action.equals("action.setSizeShape")) {
                    val setSize = intent?.getIntExtra("setSizeShape", 10)
                    if (setSize != null) {
                        drawingView?.setSizeForBrushShape(setSize.toFloat())
                    }

                } else if (intent?.action.equals("action.PickColor")) {
                    var setSize = intent?.getIntExtra("pickColor", 10)
                    if (setSize != null && setSize != 10) {
                        // Use commit() instead of apply() to ensure synchronous write
                        sharedPreferences?.edit()?.putInt("setColor", setSize)?.commit()
                        drawingView?.setColor(setSize)
                    }
                } else if (intent?.action.equals("action.PickShape")) {
                    var setShape = intent?.getIntExtra("pickShape", 2)
                    if (setShape != null) {
                        drawingView?.setShapeType(setShape)
                    }
                }
                else if (intent?.action.equals("action.setSizeErase")){
                    var setShape = intent?.getIntExtra("setSizeErase", 20)
                    if (setShape != null) {
                        // Use commit() instead of apply() to ensure synchronous write
                        sharedPreferences?.edit()?.putInt("setSizeForBrushErase", setShape)?.commit()
                        drawingView?.setSizeForBrushErase(setShape.toFloat())
                    }

                }
                else if (intent?.action.equals("action.undo")){
                        drawingView?.onClickUndo()
                }
                else if (intent?.action.equals("action.redo")){
                    drawingView?.onClickRedo()
                }
                else if (intent?.action.equals("action.delete")){
                    drawingView?.clearAllDrawings()
                }
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction("action.hideDraw") // Action1 to filter
        intentFilter.addAction("action.setSize") // Action2 to filter
        intentFilter.addAction("action.setSizeShape") // Action2 to filter

        intentFilter.addAction("action.PickColor") // Action2 to filter
        intentFilter.addAction("action.PickShape") // Action2 to filter
        intentFilter.addAction("action.setSizeErase") // Action2 to filter
        intentFilter.addAction("action.undo") // Action2 to filter
        intentFilter.addAction("action.redo") // Action2 to filter
        intentFilter.addAction("action.delete") // Action2 to filter
        registerReceiver(receiver, IntentFilter(intentFilter), RECEIVER_EXPORTED
        )

        mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mWindowManager!!.addView(mFloatingView, params)


    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val width =  Resources.getSystem().displayMetrics.widthPixels;
        val height =  Resources.getSystem().displayMetrics.heightPixels;
        params.width = width
        params.height = height
        mWindowManager!!.updateViewLayout(mFloatingView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}