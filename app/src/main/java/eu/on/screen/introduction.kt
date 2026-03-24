package eu.on.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import eu.on.screen.R

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentPagerAdapter
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.google.android.material.tabs.TabLayout


class Introduction : AppCompatActivity() {
    private var viewPager: ViewPager? = null
    private var button: Button? = null
    private var adapter: SliderPagerAdapter? = null
    var currentIndex: Int = 0
    private var sharedPreferences: SharedPreferences? = null

    var editor: SharedPreferences.Editor? = null

    @SuppressLint("ResourceType")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        sharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)
        editor = sharedPreferences?.edit()
        val introductionStatus = sharedPreferences?.getBoolean("introduction", false) ?: false
        if (introductionStatus) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
        // making activity full screen
        if (Build.VERSION.SDK_INT >= 21) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        setContentView(R.layout.activity_introduction)
        // hide action bar you can use NoAction theme as well
        // bind views
        viewPager = findViewById<ViewPager>(R.id.pagerIntroSlider)
        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        button = findViewById<Button>(R.id.button)
        // init slider pager adapter
        adapter = SliderPagerAdapter(
            supportFragmentManager,
            FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
        )
        // set adapter
        viewPager?.adapter = adapter
        // set dot indicators

        tabLayout.setBackgroundColor(Color.TRANSPARENT)
        tabLayout.setupWithViewPager(viewPager)
        // make status bar transparent
        changeStatusBarColor()
        button?.setOnClickListener(View.OnClickListener {
            val currentViewPagerItem = viewPager?.currentItem ?: 0
            val adapterCount = adapter?.count ?: 0
            
            if (currentViewPagerItem < adapterCount) {
                Log.e(
                    "eee",
                    "$adapterCount total () $currentViewPagerItem"
                )

                viewPager?.currentItem = currentViewPagerItem + 1
                currentIndex++
            }

            if (currentIndex == adapter?.count) {
                editor?.putBoolean("introduction", true)
                editor?.apply()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }


        })
        /**
         * Add a listener that will be invoked whenever the page changes
         * or is incrementally scrolled
         */
        viewPager?.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                val adapterCount = adapter?.count ?: 0
                if (position == adapterCount - 1) {
                    button?.setText(R.string.get_started)
                } else {
                    button?.setText(R.string.next)
                }
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
    }

    private fun changeStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window = window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
        }
    }
}