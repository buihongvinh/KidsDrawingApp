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
import android.content.res.Configuration
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
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import eu.on.screen.ViewModel.HideDrawViewModel
import yuku.ambilwarna.AmbilWarnaDialog
import yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener

private const val FAB_ID_PEN = "pen"
private const val FAB_ID_COLOR = "color"
private const val FAB_ID_SHAPE = "shape"
private const val FAB_ID_ERASE = "erase"
private const val FAB_ID_UNDO = "undo"
private const val FAB_ID_REDO = "redo"
private const val FAB_ID_DELETE = "delete"
private const val FAB_ID_HIDE = "hide"
private const val FAB_ID_TOUCH = "touch"
private const val FAB_ID_EXIT = "exit"

class DrawTestService : Service() {
    private val handler: Handler = Handler()
    private lateinit var viewModel: HideDrawViewModel

    private var drawingView: DrawingView? = null
    private var mWindowManager: WindowManager? = null
    private var mFloatingView: View? = null
    private var toolbarContainer: LinearLayout? = null
    private var toolbarDivider: View? = null
    private var fabRecyclerView: RecyclerView? = null
    private var fabAdapter: FabAdapter? = null
    private var fabTouchCallback: FabItemTouchCallback? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private val fabItems: MutableList<FabItem> = mutableListOf()
    var height: Int? = null
   // var hideDraw: Boolean = false
    var isAllFabsVisible: Boolean? = null
    var isMoving = false
    var isChooseShape = false
    var isChooseErase = false
    var isPen = false
    var isTouchThroughEnabled = false
    var mAddFab: FloatingActionButton? = null
    private var initialOverlayX: Int = 0
    private var initialOverlayY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var lastDownTouchX: Float = 0f
    private var lastDownTouchY: Float = 0f
    private var isDraggingOverlay = false
    private var fab_open: Animation? = null
    private var fab_close: Animation? = null
    private var fab_clock: Animation? = null
    private var fab_anticlock: Animation? = null
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
        toolbarContainer = mFloatingView!!.findViewById(R.id.overlay_toolbar_container)
        toolbarDivider = mFloatingView!!.findViewById(R.id.toolbar_divider)
        fabRecyclerView = mFloatingView!!.findViewById(R.id.rv_fabs)
        mAddFab = mFloatingView!!.findViewById(R.id.add_fab)
        isTouchThroughEnabled = sharedPreferences!!.getBoolean("touchThroughEnabled", false)

        // Apply FAB size from preferences
        val fabSize = sharedPreferences!!.getInt("fabSize", 1) // Default: normal
        applyFabSize(fabSize)

        fabItems.clear()
        fabItems.addAll(loadFabOrder().toMutableList())
        refreshFabItems()
        setupFabRecyclerView()
        isAllFabsVisible = false

        fab_close = AnimationUtils.loadAnimation(applicationContext, R.anim.fab_close);
        fab_open = AnimationUtils.loadAnimation(applicationContext, R.anim.fab_open);
        fab_clock = AnimationUtils.loadAnimation(applicationContext, R.anim.fab_rotate_click);
        fab_anticlock = AnimationUtils.loadAnimation(applicationContext, R.anim.fab_antiblock);

        val desiredWidth = WindowManager.LayoutParams.WRAP_CONTENT
        val desiredHeight = WindowManager.LayoutParams.WRAP_CONTENT
        Log.e("aaaa11111", desiredWidth.toString())
        Log.e("aaaa11111weight", desiredHeight.toString())

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
            if (isDraggingOverlay) {
                return@setOnClickListener
            }
            isAllFabsVisible = if (!isAllFabsVisible!!) {
                mAddFab?.startAnimation(fab_anticlock)
                toolbarDivider?.visibility = View.VISIBLE
                fabRecyclerView?.visibility = View.VISIBLE
                updateToolbarLayout()
                maybeShowReorderHint()
                true
            } else {
                mAddFab?.startAnimation(fab_clock)
                toolbarDivider?.visibility = View.GONE
                fabRecyclerView?.visibility = View.GONE
                false
            }
        }
        mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        attachParentFabDrag(params)
        updateToolbarLayout()
        mWindowManager!!.addView(mFloatingView, params)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateToolbarLayout()
    }

    private fun setupFabRecyclerView() {
        val recyclerView = fabRecyclerView ?: return
        fabAdapter = FabAdapter(
            context = this,
            items = fabItems,
            onItemClick = ::handleFabClick,
            onStartDrag = { holder -> itemTouchHelper?.startDrag(holder) }
        )
        fabTouchCallback = FabItemTouchCallback(fabAdapter!!) { currentToolbarOrientation() }.apply {
            onOrderChanged = { items -> saveFabOrder(items) }
        }
        itemTouchHelper = ItemTouchHelper(fabTouchCallback!!)
        recyclerView.adapter = fabAdapter
        recyclerView.layoutManager = LinearLayoutManager(this, currentToolbarOrientation(), false)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
    }

    private fun currentToolbarOrientation(): Int {
        return RecyclerView.VERTICAL
    }

    private fun updateToolbarLayout() {
        val isLandscape = isLandscapeLayout()
        toolbarContainer?.orientation = LinearLayout.VERTICAL
        toolbarContainer?.gravity = Gravity.CENTER_HORIZONTAL

        val addFabLayoutParams = mAddFab?.layoutParams as? LinearLayout.LayoutParams
        if (addFabLayoutParams != null) {
            addFabLayoutParams.marginEnd = 0
            addFabLayoutParams.bottomMargin = dpToPx(8)
            mAddFab?.layoutParams = addFabLayoutParams
        }

        val dividerLayoutParams = toolbarDivider?.layoutParams as? LinearLayout.LayoutParams
        if (dividerLayoutParams != null) {
            dividerLayoutParams.width = dpToPx(28)
            dividerLayoutParams.height = dpToPx(1)
            dividerLayoutParams.marginEnd = 0
            dividerLayoutParams.bottomMargin = dpToPx(8)
            toolbarDivider?.layoutParams = dividerLayoutParams
        }

        val recyclerView = fabRecyclerView ?: return
        val currentManager = recyclerView.layoutManager as? LinearLayoutManager
        val targetOrientation = RecyclerView.VERTICAL
        if (currentManager == null || currentManager.orientation != targetOrientation) {
            recyclerView.layoutManager = LinearLayoutManager(this, targetOrientation, false)
        }

        val metricsBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mWindowManager?.currentWindowMetrics?.bounds
        } else {
            null
        }
        val screenWidth = metricsBounds?.width() ?: resources.displayMetrics.widthPixels
        val screenHeight = metricsBounds?.height() ?: resources.displayMetrics.heightPixels
        val layoutParams = recyclerView.layoutParams
        if (isLandscape) {
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
            layoutParams.height = (screenHeight * 0.55f).toInt().coerceAtLeast(dpToPx(220))
        } else {
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
            layoutParams.height = (screenHeight * 0.6f).toInt().coerceAtLeast(dpToPx(220))
        }
        recyclerView.layoutParams = layoutParams
    }

    private fun attachParentFabDrag(params: WindowManager.LayoutParams) {
        mAddFab?.setOnLongClickListener {
            isDraggingOverlay = true
            isMoving = false
            initialOverlayX = params.x
            initialOverlayY = params.y
            initialTouchX = lastDownTouchX
            initialTouchY = lastDownTouchY
            mAddFab?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_up))
            true
        }

        mAddFab?.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                lastDownTouchX = event.rawX
                lastDownTouchY = event.rawY
            }

            if (!isDraggingOverlay) {
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    isMoving = true
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    params.x = initialOverlayX + dx
                    params.y = initialOverlayY + dy
                    mWindowManager?.updateViewLayout(mFloatingView, params)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mAddFab?.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_down))
                    isDraggingOverlay = false
                    isMoving = false
                    true
                }

                else -> true
            }
        }
    }

    private fun isLandscapeLayout(): Boolean {
        val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mWindowManager?.currentWindowMetrics?.bounds
        } else {
            null
        }
        val width = bounds?.width() ?: resources.displayMetrics.widthPixels
        val height = bounds?.height() ?: resources.displayMetrics.heightPixels
        return width > height
    }

    private fun handleFabClick(item: FabItem) {
        when (item.id) {
            FAB_ID_PEN -> handlePenClick()
            FAB_ID_COLOR -> handleColorClick()
            FAB_ID_SHAPE -> handleShapeClick()
            FAB_ID_ERASE -> handleEraseClick()
            FAB_ID_UNDO -> {
                showToast(this, "Undo Clicked")
                sendBroadcast(Intent("action.undo").putExtra("undo", true))
            }
            FAB_ID_REDO -> {
                showToast(this, "Redo Clicked")
                sendBroadcast(Intent("action.redo").putExtra("redo", true))
            }
            FAB_ID_DELETE -> {
                showToast(this, "Draw Delete Clicked")
                sendBroadcast(Intent("action.delete"))
            }
            FAB_ID_HIDE -> handleHideClick()
            FAB_ID_TOUCH -> handleTouchThroughClick()
            FAB_ID_EXIT -> handleExitClick()
        }
    }

    private fun handleColorClick() {
        var currentMode = 4
        if (isPen) {
            currentMode = 4
        } else if (isChooseErase) {
            currentMode = 5
        } else if (isChooseShape) {
            currentMode = sharedPreferences!!.getInt("shapeType", 2)
        }
        editor?.putInt("lastDrawingMode", currentMode)
        editor?.commit()

        sendBroadcast(Intent("action.hideDraw").putExtra("hideDraw", true))
        val colorPickerIntent = Intent(this, ColorPickerActivity::class.java)
        colorPickerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(colorPickerIntent)
    }

    private fun handleEraseClick() {
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

    private fun handleExitClick() {
        sendBroadcast(Intent("action.hideDraw").putExtra("hideDraw", true))
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun handlePenClick() {
        showToast(this, "Draw Pen Clicked")
        disableTouchThroughIfNeeded()
        sendBroadcast(Intent("action.hideDraw").putExtra("hideDraw", false))
        intent.putExtra("pickShape", 4)
        sendBroadcast(intent)
        isChooseErase = false
        if (isPen) {
            showBrushSizeChooserDialog()
        }
        isChooseShape = false
        isPen = true
    }

    private fun handleShapeClick() {
        showToast(this, "Pick Share Clicked")
        disableTouchThroughIfNeeded()
        val savedSeekBarValue: Int = sharedPreferences!!.getInt("seekBarValueShape", 10)
        sendBroadcast(Intent("action.setSizeShape").putExtra("setSizeShape", savedSeekBarValue))
        intent.putExtra("pickShape", 2)
        sendBroadcast(intent)
        showShapeChooserDialog()
        isChooseErase = false
        isChooseShape = true
        isPen = false
    }

    private fun handleHideClick() {
        val hideIntent = Intent("action.hideDraw")
        viewModel.setHideDraw()
        hideIntent.putExtra("hideDraw", viewModel.hideDraw.value)
        sendBroadcast(hideIntent)

        val isHidden = viewModel.hideDraw.value == true
        showToast(this, if (isHidden) "Hide Pen Clicked" else "Show Pen Clicked")
        findFabItem(FAB_ID_HIDE)?.iconRes = if (isHidden) R.drawable.hide else R.drawable.view
        fabAdapter?.notifyItemChangedById(FAB_ID_HIDE)
    }

    private fun handleTouchThroughClick() {
        isTouchThroughEnabled = !isTouchThroughEnabled
        sharedPreferences?.edit()?.putBoolean("touchThroughEnabled", isTouchThroughEnabled)?.commit()
        updateTouchThroughFabState()
        sendBroadcast(Intent("action.touchThrough").putExtra("touchThrough", isTouchThroughEnabled))
        showToast(this, if (isTouchThroughEnabled) "Touch-through enabled" else "Touch-through disabled")
    }

    private fun loadFabOrder(): List<FabItem> {
        val defaultItems = defaultFabItems()
        val savedOrder = sharedPreferences!!.getString("fabOrder", null) ?: return defaultItems
        val orderedIds = savedOrder.split(",").filter { it.isNotBlank() }
        val orderedItems = orderedIds.mapNotNull { id -> defaultItems.find { it.id == id } }.toMutableList()
        defaultItems.forEach { item ->
            if (orderedItems.none { it.id == item.id }) {
                orderedItems.add(item)
            }
        }
        return orderedItems
    }

    private fun saveFabOrder(items: List<FabItem>) {
        val orderString = items.joinToString(",") { it.id }
        editor?.putString("fabOrder", orderString)
        editor?.apply()
    }

    private fun refreshFabItems() {
        findFabItem(FAB_ID_HIDE)?.apply {
            iconRes = if (viewModel.hideDraw.value == true) R.drawable.hide else R.drawable.view
            isActive = viewModel.hideDraw.value == true
        }
        findFabItem(FAB_ID_TOUCH)?.isActive = isTouchThroughEnabled
    }

    private fun defaultFabItems(): List<FabItem> {
        return listOf(
            FabItem(FAB_ID_PEN, R.drawable.ic_brush, R.string.overlay_pen),
            FabItem(FAB_ID_COLOR, R.drawable.color_palette, R.string.overlay_color),
            FabItem(FAB_ID_SHAPE, R.drawable.interests, R.string.overlay_shape),
            FabItem(FAB_ID_ERASE, R.drawable.eraser, R.string.overlay_eraser),
            FabItem(FAB_ID_UNDO, R.drawable.undo, R.string.overlay_undo),
            FabItem(FAB_ID_REDO, R.drawable.redo, R.string.overlay_redo),
            FabItem(FAB_ID_DELETE, R.drawable.bin, R.string.overlay_delete),
            FabItem(FAB_ID_HIDE, R.drawable.view, R.string.overlay_hide),
            FabItem(FAB_ID_TOUCH, R.drawable.install_desktop, R.string.overlay_touch_through),
            FabItem(FAB_ID_EXIT, R.drawable.logout, R.string.overlay_exit)
        )
    }

    private fun findFabItem(id: String): FabItem? = fabItems.find { it.id == id }

    private fun dpToPx(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun maybeShowReorderHint() {
        val prefs = sharedPreferences ?: return
        if (prefs.getBoolean("fab_reorder_hint_shown_v2", false)) {
            return
        }
        showToast(this, "New feature: Long press a tool to move it.", Toast.LENGTH_LONG)
        prefs.edit().putBoolean("fab_reorder_hint_shown_v2", true).apply()
    }

    private fun applyFabSize(size: Int) {
        val addFab = mAddFab ?: return
        when (size) {
            0 -> {
                addFab.size = FloatingActionButton.SIZE_MINI
                addFab.customSize = 0
            }
            2 -> {
                addFab.size = FloatingActionButton.SIZE_NORMAL
                addFab.customSize = dpToPx(64)
            }
            else -> {
                addFab.size = FloatingActionButton.SIZE_NORMAL
                addFab.customSize = 0
            }
        }
        fabAdapter?.updateFabSize(size)
    }

    private fun disableTouchThroughIfNeeded() {
        if (!isTouchThroughEnabled) {
            return
        }

        isTouchThroughEnabled = false
        sharedPreferences?.edit()?.putBoolean("touchThroughEnabled", false)?.commit()
        updateTouchThroughFabState()
        sendBroadcast(Intent("action.touchThrough").putExtra("touchThrough", false))
    }

    private fun updateTouchThroughFabState() {
        findFabItem(FAB_ID_TOUCH)?.isActive = isTouchThroughEnabled
        fabAdapter?.notifyItemChangedById(FAB_ID_TOUCH)
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

fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
    // Use Handler to ensure toast is shown on main thread
    // For overlay services, we need to use application context
    try {
        Handler(Looper.getMainLooper()).post {
            val toast = Toast.makeText(context.applicationContext, message, duration)
            toast.setGravity(Gravity.CENTER, 0, 0)
            toast.show()
        }
    } catch (e: Exception) {
        // Fallback if toast fails
        Log.e("Toast", "Failed to show toast: ${e.message}")
    }
}
