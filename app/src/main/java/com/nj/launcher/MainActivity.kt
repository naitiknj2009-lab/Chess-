package com.nj.launcher

import android.app.Activity
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.util.Xml
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private lateinit var widgetContainer: LinearLayout
    private var drawerOpen = false
    private var downY = 0f
    private var accent = Color.rgb(90, 110, 220)
    private val preferences by lazy { getSharedPreferences("nj_launcher", MODE_PRIVATE) }
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val widgetHost by lazy { AppWidgetHost(this, APP_WIDGET_HOST_ID) }
    private var iconPackPackage: String? = null
    private var iconPackResources: Resources? = null
    private val iconMappings = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accent = try {
            val colors = WallpaperManager.getInstance(this).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            colors?.primaryColor?.toArgb() ?: accent
        } catch (_: Exception) { accent }
        loadSelectedIconPack()
        showHome()
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (_: Exception) { }
    }

    override fun onStop() {
        try { widgetHost.stopListening() } catch (_: Exception) { }
        super.onStop()
    }

    private fun background(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun base(): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            setBackgroundColor(Color.rgb(18, 20, 29))
        }
        setContentView(root)
        return root
    }

    private fun label(text: String, size: Float, color: Int = Color.WHITE): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun pill(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = background(0xFF292D3D.toInt(), dp(18).toFloat())
            setPadding(dp(16), dp(13), dp(16), dp(13))
            setOnClickListener { onClick() }
        }

    private fun showHome() {
        drawerOpen = false
        base()

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(label("NJ", 23f).apply { setTextColor(accent) },
            LinearLayout.LayoutParams(0, dp(42), 1f))
        top.addView(pill("⚙", ::showSettings), LinearLayout.LayoutParams(dp(48), dp(42)))
        root.addView(top)

        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 54f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        }
        root.addView(clock, LinearLayout.LayoutParams(-1, dp(82)))
        val date = label(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
            15f, 0xFFB8BDCC.toInt()).apply { gravity = Gravity.CENTER }
        root.addView(date, LinearLayout.LayoutParams(-1, dp(28)))

        root.addView(pill("⌕   Search apps", ::showDrawer),
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(22) })

        widgetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        root.addView(widgetContainer, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(12); bottomMargin = dp(8)
        })
        displaySavedWidget()

        root.addView(label("Favourites", 16f).apply {
            setPadding(dp(2), dp(6), 0, dp(8))
        })
        val favourites = getLaunchableApps().take(8)
        val grid = GridLayout(this).apply { columnCount = 4; useDefaultMargins = false }
        favourites.forEach { app -> grid.addView(createAppShortcut(app, 56, 12)) }
        root.addView(grid, LinearLayout.LayoutParams(-1, dp(190)))

        root.addView(pill("⌃   All apps", ::showDrawer),
            LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downY = event.y; true }
                MotionEvent.ACTION_UP -> {
                    if (downY - event.y > dp(55)) showDrawer()
                    else if (event.y - downY > dp(90)) showDrawer()
                    true
                }
                else -> true
            }
        }
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(180).start()
    }

    private fun showSettings() {
        val options = arrayOf("Add widget", "Remove widget", "Choose icon pack", "Manage hidden apps", "About NJ Launcher")
        android.app.AlertDialog.Builder(this)
            .setTitle("Launcher settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickWidget()
                    1 -> removeWidget()
                    2 -> showIconPackPicker()
                    3 -> showHiddenApps()
                    4 -> android.app.AlertDialog.Builder(this)
                        .setTitle("NJ Launcher")
                        .setMessage("A lightweight, offline Android home screen.\nVersion 1.0")
                        .setPositiveButton("OK", null).show()
                }
            }.show()
    }

    private fun pickWidget() {
        val widgetId = widgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        try {
            startActivityForResult(pickIntent, REQUEST_PICK_WIDGET)
        } catch (_: Exception) {
            widgetHost.deleteAppWidgetId(widgetId)
            Toast.makeText(this, "Widget picker is unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeWidget() {
        val widgetId = preferences.getInt(KEY_WIDGET_ID, -1)
        if (widgetId != -1) {
            widgetHost.deleteAppWidgetId(widgetId)
            preferences.edit().remove(KEY_WIDGET_ID).apply()
            if (!drawerOpen) showHome()
        } else {
            Toast.makeText(this, "No widget added", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displaySavedWidget() {
        widgetContainer.removeAllViews()
        val widgetId = preferences.getInt(KEY_WIDGET_ID, -1)
        if (widgetId == -1) {
            widgetContainer.addView(label("Make this space yours", 14f, 0xFF9298AA.toInt()).apply {
                gravity = Gravity.CENTER
            })
            widgetContainer.addView(pill("+  Add a widget", ::pickWidget),
                LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })
            return
        }
        try {
            val info = widgetManager.getAppWidgetInfo(widgetId)
            if (info == null) {
                preferences.edit().remove(KEY_WIDGET_ID).apply()
                displaySavedWidget()
                return
            }
            val hostView: AppWidgetHostView = widgetHost.createView(this, widgetId, info)
            hostView.setAppWidget(widgetId, info)
            widgetContainer.addView(hostView, LinearLayout.LayoutParams(-1, -2))
        } catch (_: Exception) {
            widgetContainer.addView(pill("Widget unavailable · Remove", ::removeWidget))
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_WIDGET) {
            val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
            if (resultCode == RESULT_OK && widgetId != -1) {
                preferences.edit().putInt(KEY_WIDGET_ID, widgetId).apply()
            } else {
                val allocated = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
                if (allocated != -1) widgetHost.deleteAppWidgetId(allocated)
            }
            showHome()
        }
    }

    private fun getLaunchableApps() = packageManager
        .getInstalledApplications(PackageManager.GET_META_DATA)
        .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
        .filter { it.packageName != packageName && !preferences.getBoolean(it.packageName, false) }
        .sortedBy { packageManager.getApplicationLabel(it).toString().lowercase() }

    private fun createAppShortcut(app: android.content.pm.ApplicationInfo, iconSize: Int, textSize: Int): View {
        val pm = packageManager
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(7), dp(3), dp(7))
            setOnClickListener {
                pm.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
            }
            setOnLongClickListener {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle(pm.getApplicationLabel(app))
                    .setItems(arrayOf("Hide app", "Cancel")) { _, which ->
                        if (which == 0) {
                            preferences.edit().putBoolean(app.packageName, true).apply()
                            if (drawerOpen) renderApps(search.text.toString()) else showHome()
                        }
                    }.show()
                true
            }
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(getAppIcon(app))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
            addView(label(pm.getApplicationLabel(app).toString(), textSize.toFloat()).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-1, dp(26)))
        }
    }


    private fun findIconPacks(): List<Pair<String, String>> {
        return try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != packageName }
                .mapNotNull { app ->
                    val packResources = try { packageManager.getResourcesForApplication(app.packageName) }
                    catch (_: Exception) { null }
                    val xmlId = packResources?.getIdentifier("appfilter", "xml", app.packageName) ?: 0
                    if (xmlId == 0) null else Pair(
                        app.packageName,
                        packageManager.getApplicationLabel(app).toString()
                    )
                }.sortedBy { it.second.lowercase() }
        } catch (_: Exception) { emptyList() }
    }

    private fun showIconPackPicker() {
        val packs = findIconPacks()
        val options = listOf("System icons") + packs.map { it.second }
        android.app.AlertDialog.Builder(this)
            .setTitle("Icon pack")
            .setItems(options.toTypedArray()) { _, which ->
                val selected = if (which == 0) null else packs[which - 1].first
                if (selected == null) preferences.edit().remove(KEY_ICON_PACK).apply()
                else preferences.edit().putString(KEY_ICON_PACK, selected).apply()
                loadSelectedIconPack()
                if (drawerOpen) renderApps(search.text.toString()) else showHome()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadSelectedIconPack() {
        iconMappings.clear()
        iconPackPackage = null
        iconPackResources = null
        val pack = preferences.getString(KEY_ICON_PACK, null) ?: return
        try {
            val packResources = packageManager.getResourcesForApplication(pack)
            val xmlId = packResources.getIdentifier("appfilter", "xml", pack)
            if (xmlId == 0) return
            val parser = packResources.getXml(xmlId)
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component").orEmpty()
                    val drawable = parser.getAttributeValue(null, "drawable").orEmpty()
                    val targetPackage = component.substringAfter("{", component).substringBefore("/")
                    if (targetPackage.isNotBlank() && drawable.isNotBlank()) {
                        iconMappings[targetPackage] = drawable
                    }
                }
                parser.next()
            }
            parser.close()
            iconPackPackage = pack
            iconPackResources = packResources
        } catch (_: Exception) {
            Toast.makeText(this, "Could not load this icon pack", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getAppIcon(app: android.content.pm.ApplicationInfo): android.graphics.drawable.Drawable {
        val packResources = iconPackResources
        val pack = iconPackPackage
        val drawableName = iconMappings[app.packageName]
        if (packResources != null && pack != null && drawableName != null) {
            try {
                var drawableId = packResources.getIdentifier(drawableName, "drawable", pack)
                if (drawableId == 0) drawableId = packResources.getIdentifier(drawableName, "mipmap", pack)
                if (drawableId != 0) return packResources.getDrawable(drawableId, theme)
            } catch (_: Exception) { }
        }
        return packageManager.getApplicationIcon(app)
    }

    private fun showDrawer() {
        drawerOpen = true
        base()
        root.addView(label("All apps", 28f))
        search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine(true)
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9298AA.toInt())
            background = background(0xFF292D3D.toInt(), dp(18).toFloat())
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(16); bottomMargin = dp(12)
        })
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
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(160).start()
    }

    private fun renderApps(query: String) {
        content.removeAllViews()
        val apps = getLaunchableApps().filter {
            packageManager.getApplicationLabel(it).toString().contains(query, true)
        }
        val grid = GridLayout(this).apply { columnCount = 4; useDefaultMargins = false }
        apps.forEach { app ->
            grid.addView(createAppShortcut(app, 48, 11), GridLayout.LayoutParams().apply {
                width = 0
                height = dp(100)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        content.addView(grid)
        if (apps.isEmpty()) content.addView(label("No apps found", 15f, 0xFFB8BDCC.toInt()))
        content.addView(TextView(this).apply {
            text = "Manage hidden apps"
            gravity = Gravity.CENTER
            setTextColor(accent)
            setPadding(0, dp(18), 0, dp(18))
            setOnClickListener { showHiddenApps() }
        })
    }

    private fun showHiddenApps() {
        val hidden = packageManager.getInstalledApplications(0)
            .filter { preferences.getBoolean(it.packageName, false) }
        val names = hidden.map { packageManager.getApplicationLabel(it).toString() }.toTypedArray()
        if (names.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this).setTitle("Hidden apps")
            .setItems(names) { _, which ->
                preferences.edit().putBoolean(hidden[which].packageName, false).apply()
                if (drawerOpen) renderApps(search.text.toString()) else showHome()
            }.setNegativeButton("Close", null).show()
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (drawerOpen) showHome() else super.onBackPressed()
    }

    companion object {
        private const val APP_WIDGET_HOST_ID = 2048
        private const val REQUEST_PICK_WIDGET = 4102
        private const val KEY_WIDGET_ID = "home_widget_id"
        private const val KEY_ICON_PACK = "icon_pack_package"
    }
}
