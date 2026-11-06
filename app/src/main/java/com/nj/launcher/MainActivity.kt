package com.nj.launcher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private var drawerOpen = false
    private var downY = 0f
    private var accent = Color.rgb(90, 110, 220)
    private val hiddenApps by lazy { getSharedPreferences("nj_launcher", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accent = try {
            val colors = WallpaperManager.getInstance(this).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            colors?.primaryColor?.toArgb() ?: accent
        } catch (_: Exception) { accent }
        showHome()
    }

    private fun background(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun base(): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(20))
            setBackgroundColor(Color.rgb(18, 20, 29))
        }
        setContentView(root)
        return root
    }

    private fun label(text: String, size: Float, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun showHome() {
        drawerOpen = false
        base()
        root.addView(label("NJ", 22f).apply { setTextColor(accent) })
        root.addView(label("Good day", 30f).apply { setPadding(0, dp(24), 0, dp(4)) })
        root.addView(label("Your space. Your style.", 14f, 0xFFB8BDCC.toInt()))
        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val open = TextView(this).apply {
            text = "⌃   Swipe up to open apps"; textSize = 15f
            gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = background(0xFF292D3D.toInt(), dp(24).toFloat())
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setOnClickListener { showDrawer() }
        }
        root.addView(open, LinearLayout.LayoutParams(-1, -2))
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downY = event.y; true }
                MotionEvent.ACTION_UP -> {
                    if (downY - event.y > dp(55)) showDrawer()
                    true
                }
                else -> true
            }
        }
    }

    private fun showDrawer() {
        drawerOpen = true
        base()
        root.addView(label("All apps", 28f))
        search = EditText(this).apply {
            hint = "Search apps"; setSingleLine(true); textSize = 16f
            setTextColor(Color.WHITE); setHintTextColor(0xFF9298AA.toInt())
            background = background(0xFF292D3D.toInt(), dp(18).toFloat())
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16); bottomMargin = dp(12) })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        search.addTextChangedListener(SimpleTextWatcher { renderApps(it) })
        renderApps("")
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) downY = event.y
            if (event.action == MotionEvent.ACTION_UP && event.y - downY > dp(70)) showHome()
            true
        }
    }

    private fun renderApps(query: String) {
        content.removeAllViews()
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != packageName && !hiddenApps.getBoolean(it.packageName, false) }
            .filter { pm.getApplicationLabel(it).toString().contains(query, true) }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val grid = GridLayout(this).apply { columnCount = 4; useDefaultMargins = false }
        apps.forEach { app ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(4), dp(12), dp(4), dp(12))
                setOnClickListener {
                    pm.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
                }
                setOnLongClickListener {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle(pm.getApplicationLabel(app))
                        .setItems(arrayOf("Hide app", "Cancel")) { dialog, which ->
                            if (which == 0) {
                                hiddenApps.edit().putBoolean(app.packageName, true).apply()
                                renderApps(search.text.toString())
                            }
                        }.show()
                    true
                }
            }
            val icon = android.widget.ImageView(this).apply {
                setImageDrawable(pm.getApplicationIcon(app))
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            item.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
            item.addView(label(pm.getApplicationLabel(app).toString(), 11f).apply {
                gravity = Gravity.CENTER; maxLines = 2
            }, LinearLayout.LayoutParams(-1, dp(36)))
            grid.addView(item, GridLayout.LayoutParams().apply {
                width = 0; height = dp(100); columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        content.addView(grid)
        if (apps.isEmpty()) content.addView(label("No apps found", 15f, 0xFFB8BDCC.toInt()))
        val unhide = TextView(this).apply {
            text = "Manage hidden apps"; gravity = Gravity.CENTER; setTextColor(accent)
            setPadding(0, dp(18), 0, dp(18))
            setOnClickListener { showHiddenApps() }
        }
        content.addView(unhide)
    }

    private fun showHiddenApps() {
        val pm = packageManager
        val hidden = pm.getInstalledApplications(0).filter { hiddenApps.getBoolean(it.packageName, false) }
        val names = hidden.map { pm.getApplicationLabel(it).toString() }.toTypedArray()
        if (names.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show(); return
        }
        android.app.AlertDialog.Builder(this).setTitle("Hidden apps")
            .setItems(names) { _, which ->
                hiddenApps.edit().putBoolean(hidden[which].packageName, false).apply()
                renderApps(search.text.toString())
            }.setNegativeButton("Close", null).show()
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (drawerOpen) showHome() else super.onBackPressed()
    }
}
