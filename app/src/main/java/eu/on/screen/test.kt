package eu.on.screen

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import eu.on.screen.ViewModel.HideDrawViewModel
import yuku.ambilwarna.AmbilWarnaDialog
import yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener


class DrawTestService : Service() {
    private val handler: Handler = Handler()
    private lateinit var viewModel: HideDrawViewModel

    private var drawingView: DrawingView? = null
    private var mWindowManager: WindowManager? = null
    private var mFloatingView: View? = null
    var height: Int? = null
   // var hideDraw: Boolean = false
    var isAllFabsVisible: Boolean? = null
    var mPenFab: FloatingActionButton? = null
    var mPickColorFab: FloatingActionButton? = null
    var mPickSharpFab: FloatingActionButton? = null
    var mEarseFab: FloatingActionButton? = null
    var mUndoFab: FloatingActionButton? = null
    var mRedoFab: FloatingActionButton? = null
    var mDelete: FloatingActionButton? = null
    var mHide: FloatingActionButton? = null
    var mTouchThroughFab: FloatingActionButton? = null
    var mExits: FloatingActionButton? = null
    var isMoving = false
    var isChooseShape = false
    var isChooseErase = false
    var isPen = false
    var isTouchThroughEnabled = false
    var mAddFab: FloatingActionButton? = null
    private var fab_open: Animation? = null
    private var fab_close: Animation? = null
    private var fab_clock: Animation? = null
    private var fab_anticlock: Animation? = null
    private var initialX: Float = 0.0f
    private var initialY: Float = 0.0f
    private var initialTouchX: Float = 0.0f
    private var initialTouchY: Float = 0.0f
    private var mImageButtonCurrentPaint: ImageButton? =
        null // A variable for current color is picked from color pallet.
    var sharedPreferences: SharedPreferences? = null
    private val intent = Intent("action.PickShape")

    var editor: SharedPreferences.Editor? = null
    override fun onBind(intent: Intent): IBinder? {
        return null
    }



    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate() {
        super.onCreate()
        setTheme(R.style.customTheme); // (for Custom theme)
        viewModel = HideDrawViewModel.getInstance()

        if (Build.VERSION.SDK_INT >= 26) {
            var intentRegig = Intent(this, MainActivity::class.java)
            intentRegig.action = "custom_action"
            val contentIntent =
                PendingIntent.getActivity(
                    this,
                    0, intentRegig,
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

        // Initialize SharedPreferences first
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        editor = sharedPreferences?.edit()
        
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        drawingView = mFloatingView!!.findViewById(R.id.drawing_view)
        mAddFab = mFloatingView!!.findViewById(R.id.add_fab)


        // FAB button
        mPenFab = mFloatingView!!.findViewById(R.id.add_alarm_fab)
        mPickColorFab = mFloatingView!!.findViewById(R.id.add_person_fab)
        mPickSharpFab = mFloatingView!!.findViewById(R.id.btn_pick_sharp)
        mEarseFab = mFloatingView!!.findViewById(R.id.btn_erase)
        mUndoFab = mFloatingView!!.findViewById(R.id.btn_undo)
        mRedoFab = mFloatingView!!.findViewById(R.id.btn_redo)
        mDelete = mFloatingView!!.findViewById(R.id.btn_delete)
        mHide = mFloatingView!!.findViewById(R.id.btn_hide)
        mTouchThroughFab = mFloatingView!!.findViewById(R.id.btn_touch_through)
        mExits = mFloatingView!!.findViewById(R.id.btn_left)
        isTouchThroughEnabled = sharedPreferences!!.getBoolean("touchThroughEnabled", false)
        updateTouchThroughFabState()

        // Apply FAB size from preferences
        val fabSize = sharedPreferences!!.getInt("fabSize", 1) // Default: normal
        applyFabSize(fabSize)
        
        mPenFab?.visibility = View.GONE
        mPickColorFab?.visibility = View.GONE
        mPickSharpFab?.visibility = View.GONE
        mEarseFab?.visibility = View.GONE
        mUndoFab?.visibility = View.GONE
        mRedoFab?.visibility = View.GONE
        mDelete?.visibility = View.GONE
        mHide?.visibility = View.GONE
        mTouchThroughFab?.visibility = View.GONE
        mExits?.visibility = View.GONE
        isAllFabsVisible = false

        fab_close = AnimationUtils.loadAnimation(applicationContext, R.anim.fab_close);
        fab_open = AnimationUtils.loadAnimation(applicationContext, R.anim.fab_open);
        fab_clock = AnimationUtils.loadAnimation(applicationContext, R.anim.fab_rotate_click);
        fab_anticlock = AnimationUtils.loadAnimation(applicationContext, R.anim.fab_antiblock);

        //   mAddFab!!.shrink()
        drawingView?.setSizeForBrush(20.toFloat())
        val desiredWidth = WindowManager.LayoutParams.WRAP_CONTENT
        val desiredHeight = WindowManager.LayoutParams.WRAP_CONTENT
        Log.e("aaaa11111", desiredWidth.toString())
        Log.e("aaaa11111weight", desiredHeight.toString())

        var window = getSystemService(WINDOW_SERVICE) as WindowManager
        val displayMetrics = window?.currentWindowMetrics?.bounds
        // sharedPreferences already initialized above

        val fullWidth = displayMetrics?.width()
        val fullHeight = displayMetrics?.height()
        val params = WindowManager.LayoutParams(
            desiredWidth,
            desiredHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0 // Left offset
        params.y = 0 // Top offset
        mAddFab!!.setOnClickListener {
            isAllFabsVisible = if (!isAllFabsVisible!!) {

                mPickColorFab!!.show()
                mPenFab!!.show()
                mPickSharpFab!!.show()
                mEarseFab!!.show()
                mUndoFab!!.show()
                mRedoFab!!.show()
                mDelete!!.show()
                mHide!!.show()
                mTouchThroughFab!!.show()
                mExits!!.show()
                mPickColorFab?.startAnimation(fab_open);
                mPenFab?.startAnimation(fab_open);
                mAddFab?.startAnimation(fab_anticlock);
                mPickSharpFab?.startAnimation(fab_open);
                mEarseFab?.startAnimation(fab_open);
                mUndoFab?.startAnimation(fab_open);
                mRedoFab?.startAnimation(fab_open);
                mDelete?.startAnimation(fab_open);
                mHide?.startAnimation(fab_open);
                mTouchThroughFab?.startAnimation(fab_open);
                mExits?.startAnimation(fab_open);
                // Now extend the parent FAB, as
                // user clicks on the shrinked
                // parent FAB
                //       mAddFab!!.extend()

                // make the boolean variable true as
                // we have set the sub FABs
                // visibility to GONE
                true
            } else {

                mPenFab!!.hide()
                mPickColorFab!!.hide()
                mPickSharpFab!!.hide()
                mEarseFab!!.hide()
                mUndoFab!!.hide()
                mRedoFab!!.hide()
                mDelete!!.hide()
                mHide!!.hide()
                mTouchThroughFab!!.hide()
                mExits!!.hide()
                mPenFab?.startAnimation(fab_close)
                mPickColorFab?.startAnimation(fab_close)
                mAddFab?.startAnimation(fab_clock)
                mPickSharpFab?.startAnimation(fab_close)
                mEarseFab?.startAnimation(fab_close)
                mUndoFab?.startAnimation(fab_close)
                mRedoFab?.startAnimation(fab_close)
                mDelete?.startAnimation(fab_close)
                mHide?.startAnimation(fab_close)
                mTouchThroughFab?.startAnimation(fab_close)
                mExits?.startAnimation(fab_close)

                false
            }
        }

        mPickColorFab!!.setOnClickListener { v ->
            // Save current drawing mode before opening color picker
            // Determine current mode: 4 = pen, 5 = erase, 2 = circle, 3 = rectangle, 1 = line
            var currentMode = 4 // default to pen
            if (isPen) {
                currentMode = 4
            } else if (isChooseErase) {
                currentMode = 5
            } else if (isChooseShape) {
                currentMode = sharedPreferences!!.getInt("shapeType", 2)
            }
            editor?.putInt("lastDrawingMode", currentMode)
            editor?.commit()
            
            val intent = Intent("action.hideDraw")
          //  hideDraw = true
            intent.putExtra("hideDraw", true)
            sendBroadcast(intent)
            val colorPickerIntent = Intent(this, ColorPickerActivity::class.java)
            colorPickerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(colorPickerIntent)

//            ColorPickerDialog
//                .Builder(this)        				// Pass Activity Instance
//                .setTitle("Pick Theme")           	// Default "Choose Color"
//                .setColorShape(ColorShape.SQAURE)   // Default ColorShape.CIRCLE
//                .setDefaultColor(mDefaultColor)     // Pass Default Color
//                .setColorListener { color, colorHex ->
//                    // Handle Color Selection
//                }
//                .show()
//            Color.Builder(this).show()
//            ColorPickerView.Builder(this)
        }

//        mPickColorFab!!.setOnClickListener { v ->
//            ColorPickerPopup.Builder(this@DrawTestService).initialColor(
//                Color.RED
//            )
//                .enableBrightness(
//                    true
//                ) // enable color brightness
//                // slider or not
//                .enableAlpha(
//                    true
//                ) // enable color alpha
//                // changer on slider or
//                // not
//                .okTitle(
//                    "Choose"
//                ) // this is top right
//                // Choose button
//                .cancelTitle(
//                    "Cancel"
//                ) // this is top left
//                // Cancel button which
//                // closes the
//                .showIndicator(
//                    true
//                ) // this is the small box
//                .build()
//                .show(
//                    v,
//                    object : ColorPickerPopup.ColorPickerObserver() {
//                        override fun onColorPicked(color: Int) {
//                            val intent = Intent("action.PickColor")
//                            intent.putExtra("pickColor", color)
//                            sendBroadcast(intent)
//
//                            //    mDefaultColor = color
//
//                            //       mColorPreview.setBackgroundColor(mDefaultColor)
//                        }
//                    })
//        }
        mEarseFab!!.setOnClickListener {
            showToast(this, "Eraser Clicked")
            disableTouchThroughIfNeeded()
            intent.putExtra("pickShape", 5)
            sendBroadcast(intent)
            if (isChooseErase) {
                showEraseSizeChooserDialog()
            }
            isChooseErase = true
            isPen = false

        }

        // below is the sample action to handle add alarm
        // FAB. Here it shows simple Toast msg The Toast
        // will be shown only when they are visible and only
        // when user clicks on them
        mExits!!.setOnClickListener {

            val intentHide = Intent("action.hideDraw")
           // hideDraw = true
            intentHide.putExtra("hideDraw", true)
            sendBroadcast(intentHide)


            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
        }

        mPenFab!!.setOnClickListener {
            showToast(this, "Draw Pen Clicked")
            disableTouchThroughIfNeeded()

                val intentHide = Intent("action.hideDraw")
            //    hideDraw = false
                intentHide.putExtra("hideDraw", false)
                sendBroadcast(intentHide)


            intent.putExtra("pickShape", 4)
            sendBroadcast(intent)
            if (isChooseShape) {
                mPickSharpFab!!.background.setTint(ContextCompat.getColor(this, R.color.pink))
                isChooseShape = false
            }
            isChooseErase = false
            if (isPen) {
                showBrushSizeChooserDialog()
            }
            isPen = true
        }
        mDelete!!.setOnClickListener {
            showToast(this, "Draw Delete Clicked")

            val intentSizeShape = Intent("action.delete")
            sendBroadcast(intentSizeShape)
        }
        mPickSharpFab!!.setOnClickListener {
            showToast(this, "Pick Share Clicked")
            disableTouchThroughIfNeeded()

            var savedSeekBarValue: Int = sharedPreferences!!.getInt("seekBarValueShape", 10)
            val intentSizeShape = Intent("action.setSizeShape")
            intentSizeShape.putExtra("setSizeShape", savedSeekBarValue)
            sendBroadcast(intentSizeShape)
            mPickSharpFab!!.background.setTint(ContextCompat.getColor(this, R.color.red))

            intent.putExtra("pickShape", 2)
            sendBroadcast(intent)
            showShapeChooserDialog()
            isChooseErase = false
            isChooseShape = true
            isPen = false


        }
        mHide!!.setOnClickListener {
            println("check print")
            if (viewModel.hideDraw.value == true) {
                showToast(this, "Show Pen Clicked")

                mHide!!.setImageResource(R.drawable.view)

            } else {
                showToast(this, "Hide Pen Clicked")
                mHide!!.setImageResource(R.drawable.hide)


            }

            // This is for undo recent stroke.
            val intent = Intent("action.hideDraw")
            viewModel.setHideDraw()
            intent.putExtra("hideDraw", viewModel.hideDraw.value)
            sendBroadcast(intent)
            // mFloatingView?.visibility = View.INVISIBLE
            //    drawingView?.onClickUndo()
        }

        mTouchThroughFab!!.setOnClickListener {
            isTouchThroughEnabled = !isTouchThroughEnabled
            sharedPreferences?.edit()?.putBoolean("touchThroughEnabled", isTouchThroughEnabled)?.commit()
            updateTouchThroughFabState()

            val intentTouchThrough = Intent("action.touchThrough")
            intentTouchThrough.putExtra("touchThrough", isTouchThroughEnabled)
            sendBroadcast(intentTouchThrough)

            val message = if (isTouchThroughEnabled) {
                "Touch-through enabled"
            } else {
                "Touch-through disabled"
            }
            showToast(this, message)
        }


        mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        mUndoFab!!.setOnClickListener {
            showToast(this, "Undo Clicked")

            val intent = Intent("action.undo")
            intent.putExtra("undo", true)
            sendBroadcast(intent)

        }
        mRedoFab!!.setOnClickListener {
            showToast(this, "Redo Clicked")

            val intent = Intent("action.redo")
            intent.putExtra("redo", true)
            sendBroadcast(intent)

        }
        mAddFab!!.setOnLongClickListener {
            val gestureListener = View.OnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_MOVE -> {
                        if (!isMoving) {
                            initialX = view.x
                            initialY = view.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isMoving = true
                            // Apply the animation for scale-up
                            val scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_up)
                            mAddFab!!.startAnimation(scaleAnimation)
                        }
                        val offsetX = event.rawX - initialTouchX
                        val offsetY = event.rawY - initialTouchY

                        params.x = (initialX + offsetX).toInt()
                        params.y = (initialY + offsetY).toInt()
                        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_animation)
                        mFloatingView!!.startAnimation(animation)
                        mWindowManager!!.updateViewLayout(mFloatingView, params)
                    }

                    MotionEvent.ACTION_UP -> {
                        // Apply the animation for scale-down
                        val scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.scale_down)
                        mAddFab!!.startAnimation(scaleAnimation)
                        if (event.rawX < fullWidth!! / 2) {
                            params.x = 10
                        } else {
                            params.x = fullWidth!!
                        }
                        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_animation)
                        mFloatingView!!.startAnimation(animation)
                        mWindowManager!!.updateViewLayout(mFloatingView, params)
                        mAddFab!!.setOnTouchListener(null)
                    }

                    else -> {
                        // Reset the flag for other touch events
                        isMoving = false
                    }
                }
                true
            }

            // Attach the gestureListener to handle touch movement
            mAddFab!!.setOnTouchListener(gestureListener)
            true
        }
        mWindowManager!!.addView(mFloatingView, params)
    }

    private fun showBrushSizeChooserDialog() {

        val brushDialog = Dialog(this@DrawTestService)
        brushDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        
        // Set dialog width to ensure buttons don't wrap
        val displayMetrics = resources.displayMetrics
        val dialogWidth = (displayMetrics.widthPixels * 0.85).toInt()
        brushDialog.window?.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)

        brushDialog.setContentView(R.layout.brush_size_dialog)
        val seekBarBrushSize = brushDialog.findViewById<SeekBar>(R.id.seekBarBrushSize)
        val textSizeLabel = brushDialog.findViewById<TextView>(R.id.textSizeLabel)
        val textSizeValue = brushDialog.findViewById<TextView>(R.id.textSizeValue)
        
        // Set label text
        textSizeLabel?.text = "Select Brush Size"
        
        // Load saved SeekBar value from shared preferences
        // Read from setSizeForBrush to sync with DrawService
        var savedSeekBarValue: Int = sharedPreferences!!.getInt("setSizeForBrush", 10)
        seekBarBrushSize.progress = savedSeekBarValue
        textSizeValue?.text = savedSeekBarValue.toString()
        
        val buttonApply = brushDialog.findViewById<Button>(R.id.buttonApply)
        val buttonCancel = brushDialog.findViewById<Button>(R.id.buttonCancel)

        brushDialog.setTitle("Brush Size")

        seekBarBrushSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                textSizeValue?.text = p1.toString()
                brushDialog.setTitle("Brush Size: $p1")
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }

        })
        buttonApply.setOnClickListener {
            val selectedBrushSize = seekBarBrushSize.progress
            drawingView?.setSizeForBrush(selectedBrushSize.toFloat())

            val intent = Intent("action.setSize")
            intent.putExtra("setSize", selectedBrushSize)
            sendBroadcast(intent)
            // Save to both keys for backward compatibility
            editor?.putInt("seekBarValue", selectedBrushSize)
            editor?.putInt("setSizeForBrush", selectedBrushSize)
            editor?.commit() // Use commit() instead of apply() for immediate sync
            brushDialog.dismiss()
        }
        buttonCancel.setOnClickListener {
            brushDialog.dismiss()
        }

        brushDialog.show()
    }
    private fun showColorPickerDialog() {
        // Ensure the dialog is shown on the main thread
        handler.post(Runnable {
            // Create and show the AmbilWarnaDialog
            val initialColor = -0x996634 // Default color
            AmbilWarnaDialog(this@DrawTestService, initialColor, object : OnAmbilWarnaListener {
                override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                    // Handle the selected color
                    Toast.makeText(
                        this@DrawTestService,
                        "Selected color: #" + Integer.toHexString(color),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onCancel(dialog: AmbilWarnaDialog) {
                    // Handle the cancel event
                    Toast.makeText(this@DrawTestService, "Color picker canceled", Toast.LENGTH_SHORT)
                        .show()
                }
            }).show()
        })
    }
    private fun showEraseSizeChooserDialog() {

        val brushDialog = Dialog(this@DrawTestService)
        brushDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        
        // Set dialog width to ensure buttons don't wrap
        val displayMetrics = resources.displayMetrics
        val dialogWidth = (displayMetrics.widthPixels * 0.85).toInt()
        brushDialog.window?.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)

        brushDialog.setContentView(R.layout.brush_size_dialog)
        val seekBarBrushSize = brushDialog.findViewById<SeekBar>(R.id.seekBarBrushSize)
        val textSizeLabel = brushDialog.findViewById<TextView>(R.id.textSizeLabel)
        val textSizeValue = brushDialog.findViewById<TextView>(R.id.textSizeValue)
        
        // Set label text
        textSizeLabel?.text = "Select Eraser Size"
        
        var savedSeekBarValue: Int = sharedPreferences!!.getInt("seekBarEraseValue", 10)
        seekBarBrushSize.progress = savedSeekBarValue
        textSizeValue?.text = savedSeekBarValue.toString()
        
        val buttonApply = brushDialog.findViewById<Button>(R.id.buttonApply)
        val buttonCancel = brushDialog.findViewById<Button>(R.id.buttonCancel)

        brushDialog.setTitle("Eraser Size")

        seekBarBrushSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                textSizeValue?.text = p1.toString()
                brushDialog.setTitle("Eraser Size: $p1")
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }

        })
        buttonApply.setOnClickListener {
            val selectedBrushSize = seekBarBrushSize.progress

            val intent = Intent("action.setSizeErase")
            intent.putExtra("setSizeErase", selectedBrushSize)
            sendBroadcast(intent)
            editor?.putInt("seekBarEraseValue", selectedBrushSize)
            editor?.apply()
            brushDialog.dismiss()
        }
        buttonCancel.setOnClickListener {
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    private fun showShapeChooserDialog() {

        val brushDialog = Dialog(this@DrawTestService)
        brushDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        
        // Set dialog width to ensure buttons don't wrap
        val displayMetrics = resources.displayMetrics
        val dialogWidth = (displayMetrics.widthPixels * 0.85).toInt()
        brushDialog.window?.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)

        brushDialog.setContentView(R.layout.pick_shape_dialog)
        val seekBarBrushSize = brushDialog.findViewById<SeekBar>(R.id.seekBarBrushSizeShape)
        val textShapeSizeLabel = brushDialog.findViewById<TextView>(R.id.textShapeSizeLabel)
        val textShapeSizeValue = brushDialog.findViewById<TextView>(R.id.textShapeSizeValue)
        
        // Set label text
        textShapeSizeLabel?.text = "Select Shape Size"
        
        var savedSeekBarValue: Int = sharedPreferences!!.getInt("seekBarValueShape", 5)
        seekBarBrushSize.progress = savedSeekBarValue
        textShapeSizeValue?.text = savedSeekBarValue.toString()
        
        val buttonApply = brushDialog.findViewById<Button>(R.id.buttonApplyShape)
        val buttonCancel = brushDialog.findViewById<Button>(R.id.buttonCancelShape)
        val btnCheckBox = brushDialog.findViewById<CheckBox>(R.id.checkBox)
        val btnCheckBox1 = brushDialog.findViewById<CheckBox>(R.id.checkBox2)
        val btnCheckBox2 = brushDialog.findViewById<CheckBox>(R.id.checkBox3)
        val typeShape = sharedPreferences!!.getInt("shapeType", 2)
        btnCheckBox.isChecked = false
        btnCheckBox1.isChecked = false
        btnCheckBox2.isChecked = false
        when (typeShape) {
            1 -> {
                btnCheckBox2.isChecked = true
                intent.putExtra("pickShape", 1)
                sendBroadcast(intent)
            }

            2 -> {
                btnCheckBox.isChecked = true
                intent.putExtra("pickShape", 2)
                sendBroadcast(intent)
            }

            3 -> {
                btnCheckBox1.isChecked = true
                intent.putExtra("pickShape", 3)
                sendBroadcast(intent)
            }
        }
        btnCheckBox.setOnClickListener {
            btnCheckBox.isChecked = true
            btnCheckBox1.isChecked = false
            btnCheckBox2.isChecked = false
            editor?.putInt("shapeType", 2)
            editor?.apply()
            intent.putExtra("pickShape", 2)
            sendBroadcast(intent)

        }
        btnCheckBox1.setOnClickListener {
            btnCheckBox.isChecked = false
            btnCheckBox1.isChecked = true
            btnCheckBox2.isChecked = false
            editor?.putInt("shapeType", 3)
            editor?.apply()
            intent.putExtra("pickShape", 3)
            sendBroadcast(intent)
        }
        btnCheckBox2.setOnClickListener {
            btnCheckBox.isChecked = false
            btnCheckBox1.isChecked = false
            btnCheckBox2.isChecked = true
            editor?.putInt("shapeType", 1)
            editor?.apply()
            intent.putExtra("pickShape", 1)
            sendBroadcast(intent)
        }
        brushDialog.setTitle("Shape Size")

        seekBarBrushSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                textShapeSizeValue?.text = p1.toString()
                brushDialog.setTitle("Shape Size: $p1")
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }

        })
        buttonApply.setOnClickListener {
            val selectedBrushSize = seekBarBrushSize.progress

            val intent = Intent("action.setSizeShape")
            intent.putExtra("setSizeShape", selectedBrushSize)
            sendBroadcast(intent)
            editor?.putInt("seekBarValueShape", selectedBrushSize)
            editor?.apply()
            brushDialog.dismiss()
        }
        buttonCancel.setOnClickListener {
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onDestroy() {
        try {
            mFloatingView?.let {
                if (it.isAttachedToWindow) {
                    mWindowManager?.removeView(it)
                }
            }
        } catch (e: Exception) {
            Log.e("DrawTestService", "Error removing floating view: ${e.message}")
        }
        super.onDestroy()
    }

}

fun showToast(context: Context, message: String) {
    // Use Handler to ensure toast is shown on main thread
    // For overlay services, we need to use application context
    try {
        Handler(Looper.getMainLooper()).post {
            val toast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        }
    } catch (e: Exception) {
        // Fallback if toast fails
        Log.e("Toast", "Failed to show toast: ${e.message}")
    }
}

fun DrawTestService.applyFabSize(size: Int) {
    val fabSizeValue = when (size) {
        0 -> com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_MINI
        1 -> com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_NORMAL
        2 -> com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_NORMAL // Use normal for large
        else -> com.google.android.material.floatingactionbutton.FloatingActionButton.SIZE_NORMAL
    }
    
    // Set size programmatically for all FABs
    val fabList = listOf(mAddFab, mPenFab, mPickColorFab, mPickSharpFab, mEarseFab, 
        mUndoFab, mRedoFab, mDelete, mHide, mTouchThroughFab, mExits)
    
    fabList.forEach { fab ->
        fab?.size = fabSizeValue
    }
    
    // For large size (2), we need to use custom size
    if (size == 2) {
        val largeSizeDp = 64
        val largeSizePx = (largeSizeDp * resources.displayMetrics.density).toInt()
        fabList.forEach { fab ->
            fab?.layoutParams?.width = largeSizePx
            fab?.layoutParams?.height = largeSizePx
            fab?.requestLayout()
        }
    }
}

private fun DrawTestService.disableTouchThroughIfNeeded() {
    if (!isTouchThroughEnabled) {
        return
    }

    isTouchThroughEnabled = false
    sharedPreferences?.edit()?.putBoolean("touchThroughEnabled", false)?.commit()
    updateTouchThroughFabState()

    val intentTouchThrough = Intent("action.touchThrough")
    intentTouchThrough.putExtra("touchThrough", false)
    sendBroadcast(intentTouchThrough)
}

private fun DrawTestService.updateTouchThroughFabState() {
    val backgroundColor = if (isTouchThroughEnabled) {
        R.color.home_primary
    } else {
        R.color.home_panel_background
    }
    val iconColor = if (isTouchThroughEnabled) {
        R.color.white
    } else {
        R.color.home_primary
    }

    mTouchThroughFab?.backgroundTintList =
        ContextCompat.getColorStateList(this, backgroundColor)
    mTouchThroughFab?.imageTintList =
        ContextCompat.getColorStateList(this, iconColor)
}
