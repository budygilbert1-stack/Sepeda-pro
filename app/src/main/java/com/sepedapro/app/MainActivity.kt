package com.sepedapro.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.SharedPreferences
import android.widget.EditText

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var map: MapView
    private lateinit var routeLine: Polyline
    private lateinit var bikeMarker: Marker

    private lateinit var speedText: TextView
    private lateinit var speedAlertText: TextView
    private lateinit var distanceText: TextView
    private lateinit var averageText: TextView
    private lateinit var durationText: TextView
    private lateinit var movingText: TextView
    private lateinit var stoppedText: TextView
    private lateinit var maxSpeedText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var resumeButton: Button
    private lateinit var stopButton: Button
    private lateinit var settingsButton: Button
    private lateinit var mapSpeedText: TextView
    private lateinit var gpsIndicator: TextView

    private var tracking = false
    private var manuallyPaused = false
    private var isMoving = false
    private var lastLocation: Location? = null
    private val routeCoordinates = mutableListOf<GeoPoint>()

    private var totalDistance = 0.0
    private var totalMovingTime = 0L
    private var totalElapsedTime = 0L
    private var startTime = 0L
    private var lastTickTime = 0L

    private var currentSpeedKmh = 0.0
    private var maxSpeedKmh = 0.0
    private var minimumSpeedKmh = 20.0
    private var lowSpeedSince = 0L
    private var lastWarningTime = 0L
    private var warningEnabled = true

    private lateinit var warningTone: ToneGenerator
    private lateinit var warningPreferences: SharedPreferences




    private var belowThresholdSince = 0L
    private var aboveThresholdSince = 0L

    private val permissionCode = 1001
    private val timerHandler = Handler(Looper.getMainLooper())

    private val background = Color.rgb(15, 23, 32)
    private val cardColor = Color.rgb(29, 40, 52)
    private val green = Color.rgb(39, 174, 96)
    private val white = Color.WHITE
    private val muted = Color.rgb(170, 184, 196)
    private val orange = Color.rgb(255, 170, 60)

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!tracking || manuallyPaused) return

            val now = System.currentTimeMillis()
            val delta = ((now - lastTickTime) / 1000L)
                .coerceAtLeast(0L)

            if (isMoving) {
                totalMovingTime += delta
            }

            lastTickTime = now

            // Statistik durasi utama menggunakan waktu bersepeda.
            totalElapsedTime += delta

            // Jeda otomatis jika kecepatan <= 5 km/jam selama 5 detik.
            if (
                isMoving &&
                belowThresholdSince > 0L &&
                now - belowThresholdSince >= 5000L
            ) {
                isMoving = false
                aboveThresholdSince = 0L
                statusText.text = "JEDA OTOMATIS"
                statusText.setTextColor(orange)
            }



            updateStatistics()
            checkSpeedWarning(now)

            timerHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        warningPreferences = getSharedPreferences(
            "sepeda_pro_settings",
            MODE_PRIVATE
        )

        minimumSpeedKmh = warningPreferences.getFloat(
            "minimum_speed",
            20.0f
        ).toDouble()

        warningEnabled = warningPreferences.getBoolean(
            "warning_enabled",
            true
        )

        warningTone = ToneGenerator(
            AudioManager.STREAM_MUSIC,
            100
        )

        Configuration.getInstance().userAgentValue = packageName

        locationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager

        createDashboard()
        updateStatistics()
    }



private fun styleTrackingControls(isTracking: Boolean) {
    startButton.text = if (isTracking) "Ⅱ" else "▶"
    startButton.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (isTracking) orange else green)
    }

    startButton.visibility = View.VISIBLE
    pauseButton.visibility = View.GONE
    resumeButton.visibility = View.GONE
    stopButton.visibility = View.GONE
    settingsButton.visibility = View.VISIBLE
}
    private fun showSettingsMenu() {
        AlertDialog.Builder(this)
            .setTitle("Pengaturan Sepeda Pro")
            .setItems(
                arrayOf(
                    "Batas kecepatan dan peringatan suara",
                    "Riwayat perjalanan"
                )
            ) { _, which ->
                when (which) {
                    0 -> showSpeedSettings()
                    1 -> showTripHistory()
                }
            }
            .show()
    }

    private fun showSpeedSettings() {
        val input = EditText(this)
        input.inputType =
            android.text.InputType.TYPE_CLASS_NUMBER or
            android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.setText(minimumSpeedKmh.toString())
        input.selectAll()
        input.hint = "Contoh: 20"

        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(dp(20), dp(8), dp(20), dp(8))

        val speedLabel = TextView(this)
        speedLabel.text = "Atur Kecepatan Minimum (km/jam)"
        speedLabel.setTextColor(white)
        container.addView(speedLabel)
        container.addView(input)

        val soundCheck = android.widget.CheckBox(this)
        soundCheck.text = "Aktifkan bunyi peringatan"
        soundCheck.setTextColor(white)
        soundCheck.isChecked = warningEnabled
        container.addView(soundCheck)

        AlertDialog.Builder(this)
            .setTitle("Atur Kecepatan Minimum")
            .setView(container)
            .setPositiveButton("SIMPAN") { _, _ ->
                val newSpeed = input.text.toString().toDoubleOrNull()

                if (newSpeed == null || newSpeed <= 0.0 || newSpeed > 100.0) {
                    Toast.makeText(
                        this,
                        "Masukkan kecepatan antara 0,1 dan 100 km/jam",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    minimumSpeedKmh = newSpeed
                    warningEnabled = soundCheck.isChecked

                    warningPreferences.edit()
                        .putFloat("minimum_speed", newSpeed.toFloat())
                        .putBoolean("warning_enabled", warningEnabled)
                        .apply()

                    lowSpeedSince = 0L

                    Toast.makeText(
                        this,
                        "Pengaturan kecepatan berhasil disimpan",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }
    private fun createDashboard() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(background)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(10), dp(12), dp(12))

        scroll.addView(root)

        val title = TextView(this)
        title.text = "SEPEDA PRO"
        title.textSize = 25f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(green)
        title.gravity = Gravity.CENTER

        root.addView(
            title,
            matchWrap().apply {
                bottomMargin = dp(4)
            }
        )

        val subtitle = TextView(this)
        subtitle.text = "GPS CYCLING COMPUTER"
        subtitle.textSize = 11f
        subtitle.setTextColor(muted)
        subtitle.gravity = Gravity.CENTER

        root.addView(
            subtitle,
            matchWrap().apply {
                bottomMargin = dp(12)
            }
        )

        val mapTitle = createLabel("PETA DAN RUTE PERJALANAN")
        root.addView(
            mapTitle,
            matchWrap().apply {
                bottomMargin = dp(6)
            }
        )

        map = MapView(this)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = true
        map.isTilesScaledToDpi = true
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(-6.9175, 107.6191))

        routeLine = Polyline()
        routeLine.outlinePaint.color = Color.rgb(30, 130, 255)
        routeLine.outlinePaint.strokeWidth = dp(5).toFloat()
        map.overlays.add(routeLine)

        bikeMarker = Marker(map)
        bikeMarker.title = "Posisi Sepeda"
        map.overlays.add(bikeMarker)

        root.addView(
            map,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(250)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        val speedCard = createCard()
        speedCard.setPadding(dp(8), dp(12), dp(8), dp(12))

        speedCard.addView(
            createLabel("KECEPATAN SAAT INI"),
            matchWrap().apply {
                bottomMargin = dp(4)
            }
        )

        speedText = TextView(this)
        speedText.text = "0.0"
        speedText.textSize = 120f
        speedText.typeface = Typeface.DEFAULT_BOLD
        speedText.setTextColor(white)
        speedText.gravity = Gravity.CENTER

        speedCard.addView(speedText, matchWrap())

        speedAlertText = TextView(this)
        speedAlertText.text = "❗ NAIKAN KECEPATAN !!!"
        speedAlertText.textSize = 18f
        speedAlertText.typeface = Typeface.DEFAULT_BOLD
        speedAlertText.setTextColor(Color.RED)
        speedAlertText.gravity = Gravity.CENTER
        speedAlertText.visibility = View.GONE
        speedCard.addView(
            speedAlertText,
            matchWrap().apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        )

        val speedUnit = TextView(this)
        speedUnit.text = "km/jam"
        speedUnit.textSize = 13f
        speedUnit.setTextColor(white)
        speedUnit.gravity = Gravity.CENTER
        speedCard.addView(speedUnit, matchWrap())

        root.addView(
            speedCard,
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        val distanceAverageRow = LinearLayout(this)
        distanceAverageRow.orientation = LinearLayout.HORIZONTAL
        distanceAverageRow.weightSum = 2f

        val distanceCard = createCard()
        distanceCard.setPadding(dp(4), dp(8), dp(4), dp(8))
        distanceCard.addView(
            createLabel("JARAK"),
            matchWrap().apply {
                bottomMargin = dp(5)
            }
        )

        distanceText = createStatValue("0.00 km")
        distanceCard.addView(distanceText, matchWrap())

        distanceAverageRow.addView(
            distanceCard,
            LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                marginEnd = dp(5)
            }
        )

        val averageCard = createCard()
        averageCard.setPadding(dp(4), dp(8), dp(4), dp(8))
        averageCard.addView(
            createLabel("RATA-RATA"),
            matchWrap().apply {
                bottomMargin = dp(5)
            }
        )

        averageText = createStatValue("0.0 km/j")
        averageCard.addView(averageText, matchWrap())

        distanceAverageRow.addView(
            averageCard,
            LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                marginStart = dp(5)
            }
        )

        root.addView(
            distanceAverageRow,
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )


        val durationMaxRow = LinearLayout(this)
        durationMaxRow.orientation = LinearLayout.HORIZONTAL
        durationMaxRow.weightSum = 2f

        val movingCard = createCard()
        movingCard.setPadding(dp(4), dp(8), dp(4), dp(8))
        movingCard.addView(
            createLabel("WAKTU BERSEPEDA"),
            matchWrap().apply {
                bottomMargin = dp(5)
            }
        )

        durationText = createStatValue("00:00:00")
        stoppedText = createStatValue("00:00:00")
        movingText = createStatValue("00:00:00")
        movingText.textSize = 17f
        movingCard.addView(movingText, matchWrap())

        durationMaxRow.addView(
            movingCard,
            LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                marginEnd = dp(5)
            }
        )

        val maxCard = createCard()
        maxCard.setPadding(dp(4), dp(8), dp(4), dp(8))
        maxCard.addView(
            createLabel("KECEPATAN MAKS."),
            matchWrap().apply {
                bottomMargin = dp(5)
            }
        )

        maxSpeedText = createStatValue("0.0 km/j")
        maxSpeedText.setTextColor(white)
        maxCard.addView(maxSpeedText, matchWrap())

        durationMaxRow.addView(
            maxCard,
            LinearLayout.LayoutParams(0, dp(76), 1f).apply {
                marginStart = dp(5)
            }
        )

        root.addView(
            durationMaxRow,
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        statusText = TextView(this)
        statusText.text = "GPS BELUM DIMULAI"
        statusText.textSize = 12f
        statusText.setTextColor(muted)
        statusText.gravity = Gravity.CENTER

        root.addView(
            statusText,
            matchWrap().apply {
                topMargin = dp(4)
                bottomMargin = dp(8)
            }
        )

        startButton = Button(this)
        pauseButton = Button(this)
        resumeButton = Button(this)
        stopButton = Button(this)
        settingsButton = Button(this)


        fun styleRoundButton(button: Button, symbol: String, color: Int) {
            button.text = symbol
            button.textSize = 25f
            button.setTextColor(white)
            button.isAllCaps = false
            button.gravity = Gravity.CENTER
            button.translationX = 0f
            button.translationY = 0f
            if (symbol == "▶") {
                button.text = " ▶"
            }
            button.minWidth = 0
            button.minHeight = 0
            button.minimumWidth = 0
            button.minimumHeight = 0
            button.setPadding(0, 0, 0, 0)
            button.includeFontPadding = false

            button.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }

            button.contentDescription = when (symbol) {
                "▶" -> "Mulai atau lanjutkan perjalanan"
                "Ⅱ" -> "Jeda perjalanan"
                "■" -> "Hentikan perjalanan"
                "⚙" -> "Pengaturan dan riwayat"
                else -> symbol
            }
        }

        val controlRow = LinearLayout(this)
        controlRow.orientation = LinearLayout.HORIZONTAL
        controlRow.gravity = Gravity.CENTER
        controlRow.setPadding(0, dp(8), 0, dp(8))

        fun addRoundControl(button: Button) {
            controlRow.addView(
                button,
                LinearLayout.LayoutParams(dp(54), dp(54)).apply {
                    setMargins(dp(5), 0, dp(5), 0)
                }
            )
        }

        styleRoundButton(startButton, "▶", green)
        styleRoundButton(pauseButton, "Ⅱ", orange)
        styleRoundButton(resumeButton, "▶", green)
        styleRoundButton(stopButton, "■", Color.rgb(220, 65, 65))
        styleRoundButton(settingsButton, "⚙", cardColor)

        pauseButton.visibility = View.GONE
        resumeButton.visibility = View.GONE
        stopButton.visibility = View.GONE

        startButton.setOnClickListener {
            if (tracking) {
                pauseButton.performClick()
            } else {
                startTracking()
            }
        }

        pauseButton.setOnClickListener {
            if (!tracking || manuallyPaused) return@setOnClickListener

            manuallyPaused = true

            if (hasLocationPermission()) {
                try {
                    locationManager.removeUpdates(this)
                } catch (_: SecurityException) {
                }
            }

            isMoving = false
            lastLocation = null
            currentSpeedKmh = 0.0
            speedText.text = "0.0"
            speedAlertText.clearAnimation()
            speedAlertText.visibility = View.GONE
            lowSpeedSince = 0L
            lastWarningTime = 0L
            statusText.text = "JEDA MANUAL"
            statusText.setTextColor(orange)
            timerHandler.removeCallbacks(timerRunnable)

            startButton.visibility = View.GONE
            pauseButton.visibility = View.GONE
            resumeButton.visibility = View.VISIBLE
            stopButton.visibility = View.VISIBLE

            updateStatistics()
        }

        resumeButton.setOnClickListener {
            if (!tracking || !manuallyPaused) return@setOnClickListener

            lastTickTime = System.currentTimeMillis()
            lastLocation = null

            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    this,
                    Looper.getMainLooper()
                )

                manuallyPaused = false
                statusText.text = "MENCARI SINYAL GPS..."
                statusText.setTextColor(green)

                timerHandler.removeCallbacks(timerRunnable)
                timerHandler.postDelayed(timerRunnable, 1000L)

		styleTrackingControls(true)
            } catch (_: SecurityException) {
                Toast.makeText(
                    this,
                    "Izin lokasi diperlukan untuk melanjutkan.",
                    Toast.LENGTH_LONG
                ).show()
            }

            updateStatistics()
        }

        stopButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Akhiri perjalanan sekarang?")
                .setMessage("Perjalanan akan dihentikan dan disimpan.")
                .setNegativeButton("BATAL", null)
                .setPositiveButton("YA, BERHENTI") { _, _ ->
                    stopTracking()
                }
                .show()
        }

        settingsButton.setOnClickListener {
            showSettingsMenu()
        }

        addRoundControl(startButton)
        addRoundControl(pauseButton)
        addRoundControl(resumeButton)
        addRoundControl(stopButton)
        addRoundControl(settingsButton)

        val info = TextView(this)
        info.textSize = 11f
        info.setTextColor(muted)
        info.gravity = Gravity.CENTER

        root.addView(
            info,
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        val footer = TextView(this)
        footer.text = "created by @sepedahankekantor V.1 2026"
        footer.textSize = 11f
        footer.setTextColor(muted)
        footer.gravity = Gravity.CENTER

        root.addView(footer, matchWrap())

        root.removeAllViews()

        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setBackgroundColor(background)

        val navigation = LinearLayout(this)
        navigation.orientation = LinearLayout.HORIZONTAL
        navigation.setPadding(dp(8), dp(6), dp(8), dp(6))
        navigation.setBackgroundColor(cardColor)

        gpsIndicator = TextView(this)
        gpsIndicator.text = "🛰 GPS"
        gpsIndicator.textSize = 14f
        gpsIndicator.gravity = Gravity.CENTER
        gpsIndicator.setTextColor(Color.RED)
        gpsIndicator.setPadding(dp(6), dp(4), dp(6), dp(4))

        navigation.addView(
            gpsIndicator,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(48)
            )
        )

        outer.addView(navigation, matchWrap())

        val pages = FrameLayout(this)

        val speedScroll = ScrollView(this)
        speedScroll.setBackgroundColor(background)

        val speedRoot = LinearLayout(this)
        speedRoot.orientation = LinearLayout.VERTICAL
        speedRoot.setPadding(dp(12), dp(10), dp(12), dp(12))

        speedRoot.addView(speedCard, matchWrap())
        speedRoot.addView(distanceAverageRow, matchWrap())
        speedRoot.addView(durationMaxRow, matchWrap())
        speedRoot.addView(controlRow, matchWrap())
        speedRoot.addView(info, matchWrap())
        val brandText = TextView(this)
        brandText.text = "Sepeda Pro"
        brandText.textSize = 11f
        brandText.setTextColor(muted)
        brandText.gravity = Gravity.CENTER
        speedRoot.addView(brandText, matchWrap())
        speedRoot.addView(footer, matchWrap())

        speedScroll.addView(speedRoot)

        val mapFrame = FrameLayout(this)

        fun createNavigationButton(
            symbol: String,
            description: String,
            color: Int,
            onClick: () -> Unit
        ): Button {
            return Button(this).apply {
                text = symbol
                textSize = 30f
                isAllCaps = false
                gravity = Gravity.CENTER
                setTextColor(white)
                includeFontPadding = false
                minWidth = 0
                minHeight = 0
                minimumWidth = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                contentDescription = description
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
                setOnClickListener { onClick() }
            }
        }

        val dashboardNavigation = FrameLayout(this)

        val openMapsButton = createNavigationButton(
            "",
            "Buka peta",
            green
        ) {
            speedScroll.visibility = View.GONE
            dashboardNavigation.visibility = View.GONE
            mapFrame.visibility = View.VISIBLE
            map.invalidate()
        }

        openMapsButton.background = LayerDrawable(
            arrayOf(
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                },
                getDrawable(R.drawable.ic_map_pin)!!
            )
        ).apply {
            setLayerInset(1, dp(9), dp(9), dp(9), dp(9))
        }

        val openDashboardButton = createNavigationButton(
            "⌂",
            "Kembali ke Dashboard",
            green
        ) {
            mapFrame.visibility = View.GONE
            speedScroll.visibility = View.VISIBLE
            dashboardNavigation.visibility = View.VISIBLE
        }

        dashboardNavigation.addView(
            openMapsButton,
            FrameLayout.LayoutParams(
                dp(52),
                dp(52),
                Gravity.END or Gravity.TOP
            ).apply {
                setMargins(0, dp(8), dp(8), 0)
            }
        )

        mapFrame.setBackgroundColor(background)

        mapTitle.text = "PETA DAN RUTE PERJALANAN"
        mapTitle.setPadding(dp(8), dp(6), dp(8), dp(6))

        mapFrame.addView(
            map,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        mapSpeedText = TextView(this)
        mapSpeedText.text = "0.0"
        mapSpeedText.textSize = 22f
        mapSpeedText.typeface = Typeface.DEFAULT_BOLD
        mapSpeedText.setTextColor(white)
        mapSpeedText.gravity = Gravity.CENTER

        val mapSpeedUnitText = TextView(this)
        mapSpeedUnitText.text = "km/j"
        mapSpeedUnitText.textSize = 10f
        mapSpeedUnitText.setTextColor(white)
        mapSpeedUnitText.gravity = Gravity.CENTER

        val speedOverlaySize = dp(76)

        val mapSpeedContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                mapSpeedText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                mapSpeedUnitText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val mapSpeedContainer = FrameLayout(this)
        mapSpeedContainer.background =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(cardColor)
                setStroke(dp(2), green)
            }

        mapSpeedContainer.addView(
            mapSpeedContent,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        mapFrame.addView(
            mapTitle,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        mapFrame.addView(
            openDashboardButton,
            FrameLayout.LayoutParams(
                dp(52),
                dp(52),
                Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, dp(8), dp(8), 0)
            }
        )

        val overlayParams = FrameLayout.LayoutParams(
            speedOverlaySize,
            speedOverlaySize,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = dp(70)
            marginEnd = dp(16)
        }

        mapFrame.addView(mapSpeedContainer, overlayParams)

        var dragStartX = 0f
        var dragStartY = 0f
        var overlayStartLeft = 0
        var overlayStartTop = 0

        mapSpeedContainer.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    val params =
                        view.layoutParams as FrameLayout.LayoutParams
                    overlayStartLeft = params.leftMargin
                    overlayStartTop = params.topMargin
                    true
                }

                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragStartX).toInt()
                    val dy = (event.rawY - dragStartY).toInt()
                    val params =
                        view.layoutParams as FrameLayout.LayoutParams

                    params.gravity = Gravity.TOP or Gravity.START
                    params.leftMargin =
                        (overlayStartLeft + dx).coerceAtLeast(0)
                    params.topMargin =
                        (overlayStartTop + dy).coerceAtLeast(0)

                    view.layoutParams = params
                    true
                }

                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> true

                else -> true
            }
        }

        pages.addView(
            speedScroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        pages.addView(
            dashboardNavigation,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(68),
                Gravity.TOP
            )
        )
        pages.addView(
            mapFrame,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        mapFrame.visibility = View.GONE

        var swipeStartX = 0f
        var swipeStartY = 0f

        pages.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.x
                    swipeStartY = event.y
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val dx = event.x - swipeStartX
                    val dy = event.y - swipeStartY

                    if (kotlin.math.abs(dx) > dp(70) &&
                        kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.3f
                    ) {
                        if (dx < 0) {
                            speedScroll.visibility = View.GONE
                            dashboardNavigation.visibility = View.GONE
                            mapFrame.visibility = View.VISIBLE
                            map.invalidate()
                        } else {
                            mapFrame.visibility = View.GONE
                            speedScroll.visibility = View.VISIBLE
                            dashboardNavigation.visibility = View.VISIBLE
                        }
                    }
                    true
                }
                else -> true
            }
        }


        outer.addView(
            pages,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(outer)
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cardColor)
            gravity = Gravity.CENTER
        }
    }

    private fun createLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(white)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun createStatValue(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 19f
            setTextColor(white)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun matchWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remaining = seconds % 60

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            hours,
            minutes,
            remaining
        )
    }

    private fun updateStatistics() {
        durationText.text = formatDuration(totalElapsedTime)
        movingText.text = formatDuration(totalMovingTime)

        val stoppedTime =
            (totalElapsedTime - totalMovingTime).coerceAtLeast(0L)

        stoppedText.text = formatDuration(stoppedTime)

        distanceText.text = String.format(
            Locale.US,
            "%.2f km",
            totalDistance / 1000.0
        )

        val average = if (totalMovingTime > 0L) {
            totalDistance / totalMovingTime * 3.6
        } else {
            0.0
        }

        averageText.text = String.format(
            Locale.US,
            "%.1f km/j",
            average
        )

        maxSpeedText.text = String.format(
            Locale.US,
            "%.1f km/j",
            maxSpeedKmh
        )
    }

    private fun hasLocationPermission(): Boolean {
        return checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startTracking() {
        if (!hasLocationPermission()) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                permissionCode
            )
            return
        }

        if (!locationManager.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )
        ) {
            Toast.makeText(
                this,
                "Aktifkan GPS terlebih dahulu.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        totalDistance = 0.0
        totalMovingTime = 0L
        totalElapsedTime = 0L
        currentSpeedKmh = 0.0
        maxSpeedKmh = 0.0
        lowSpeedSince = 0L
        lastWarningTime = 0L

        isMoving = false
        belowThresholdSince = 0L
        aboveThresholdSince = 0L
        lastLocation = null

        routeCoordinates.clear()
        routeLine.setPoints(ArrayList<GeoPoint>())
        map.invalidate()

        startTime = System.currentTimeMillis()
        lastTickTime = startTime
        tracking = true
        manuallyPaused = false
        styleTrackingControls(true)

        speedText.text = "0.0"
        updateStatistics()

        statusText.text = "MENCARI SINYAL GPS..."
        statusText.setTextColor(green)

        gpsIndicator.setTextColor(Color.YELLOW)
        gpsIndicator.clearAnimation()
        gpsIndicator.startAnimation(
            AlphaAnimation(0.25f, 1.0f).apply {
                duration = 650L
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
            }
        )

        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.postDelayed(timerRunnable, 1000L)

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                this,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            tracking = false
            timerHandler.removeCallbacks(timerRunnable)

            styleTrackingControls(false)

            Toast.makeText(
                this,
                "Izin lokasi belum diberikan.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopTracking() {
        if (hasLocationPermission()) {
            try {
                locationManager.removeUpdates(this)
            } catch (_: SecurityException) {
            }
        }

        if (tracking) {
            val now = System.currentTimeMillis()
            val delta = ((now - lastTickTime) / 1000L)
                .coerceAtLeast(0L)

            if (isMoving) {
                totalMovingTime += delta
            }

            lastTickTime = now
            totalElapsedTime += delta
        }

        if (tracking) {
            saveTripHistory()
        }

        tracking = false
        manuallyPaused = false
        styleTrackingControls(false)
        speedAlertText.clearAnimation()
        speedAlertText.visibility = View.GONE
        isMoving = false
        lastLocation = null

        timerHandler.removeCallbacks(timerRunnable)

        speedText.text = "0.0"
        currentSpeedKmh = 0.0

        statusText.text = "PERJALANAN SELESAI"
        statusText.setTextColor(muted)

        updateStatistics()

        Toast.makeText(
            this,
            "Perjalanan selesai. Statistik tetap di layar.",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onLocationChanged(location: Location) {
        if (!tracking || manuallyPaused) return

        if (location.accuracy > 30f) {
            statusText.text = "MENUNGGU GPS LEBIH AKURAT"
            statusText.setTextColor(muted)
            return
        }

        val point = GeoPoint(
            location.latitude,
            location.longitude
        )

        bikeMarker.position = point
        routeLine.addPoint(point)
        routeCoordinates.add(point)

        map.controller.animateTo(point)
        map.invalidate()

        val previous = lastLocation
        var calculatedSpeed = 0.0

        if (previous != null) {
            val elapsed = (location.time - previous.time) / 1000L

            if (elapsed > 0L && elapsed <= 10L) {
                val distance = previous.distanceTo(location).toDouble()

                calculatedSpeed = if (location.hasSpeed()) {
                    location.speed.toDouble()
                } else {
                    distance / elapsed
                }

                if (distance <= 100.0) {
                    totalDistance += distance
                }
            }
        }

        lastLocation = location

        val speedMps = if (location.hasSpeed()) {
            location.speed.toDouble()
        } else {
            calculatedSpeed
        }

        currentSpeedKmh = (speedMps * 3.6).coerceAtLeast(0.0)

        val now = System.currentTimeMillis()

        if (currentSpeedKmh <= 5.0) {
            aboveThresholdSince = 0L

            if (belowThresholdSince == 0L) {
                belowThresholdSince = now
            }
        } else {
            belowThresholdSince = 0L
            aboveThresholdSince = 0L

            if (!isMoving) {
                isMoving = true
                statusText.text = "GPS AKTIF · MEREKAM PERJALANAN"
                statusText.setTextColor(green)
            }
        }
        if (currentSpeedKmh > maxSpeedKmh) {
            maxSpeedKmh = currentSpeedKmh
        }

        speedText.text = String.format(
            Locale.US,
            "%.1f",
            currentSpeedKmh
        )

        if (::mapSpeedText.isInitialized) {
            mapSpeedText.text = String.format(
                Locale.US,
                "%.1f",
                currentSpeedKmh
            )
        }

        if (::gpsIndicator.isInitialized) {
            gpsIndicator.clearAnimation()
            gpsIndicator.setTextColor(green)
        }

        if (isMoving) {
            statusText.text = "GPS AKTIF · MEREKAM PERJALANAN"
            statusText.setTextColor(green)
        } else {
            statusText.text = "JEDA OTOMATIS"
            statusText.setTextColor(orange)
        }

        checkSpeedWarning(now)
        updateStatistics()
    }


    private fun checkSpeedWarning(now: Long) {
        val warningCondition =
            tracking &&
            !manuallyPaused &&
            warningEnabled &&
            isMoving &&
            currentSpeedKmh < minimumSpeedKmh

        if (!warningCondition) {
            lowSpeedSince = 0L
            speedAlertText.clearAnimation()
            speedAlertText.visibility = View.GONE
            return
        }

        if (lowSpeedSince == 0L) {
            lowSpeedSince = now
            return
        }

        if (now - lowSpeedSince >= 3000L) {
            if (speedAlertText.visibility != View.VISIBLE) {
                speedAlertText.visibility = View.VISIBLE

                val blink = AlphaAnimation(1.0f, 0.15f)
                blink.duration = 500L
                blink.repeatMode = AlphaAnimation.REVERSE
                blink.repeatCount = AlphaAnimation.INFINITE
                speedAlertText.startAnimation(blink)
            }

            if (lastWarningTime == 0L ||
                now - lastWarningTime >= 10000L
            ) {
                warningTone.startTone(ToneGenerator.TONE_PROP_BEEP, 500)
                lastWarningTime = now
            }
        }
    }

    private fun saveTripHistory() {
        if (totalElapsedTime <= 0L && totalDistance <= 0.0) {
            Toast.makeText(
                this,
                "Perjalanan terlalu singkat untuk disimpan.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val preferences = getSharedPreferences(
                "sepeda_pro_history",
                MODE_PRIVATE
            )

            val trips = JSONArray(
                preferences.getString("trips", "[]") ?: "[]"
            )

            val trip = JSONObject()
            trip.put(
                "date",
                SimpleDateFormat(
                    "dd MMM yyyy, HH:mm",
                    java.util.Locale("id", "ID")
                ).format(Date(startTime))
            )
            trip.put("distance", totalDistance)
            trip.put("duration", totalElapsedTime)
            trip.put("moving", totalMovingTime)
            trip.put("stopped", (totalElapsedTime - totalMovingTime).coerceAtLeast(0L))
            trip.put("maxSpeed", maxSpeedKmh)

            val average = if (totalMovingTime > 0L) {
                totalDistance / totalMovingTime * 3.6
            } else {
                0.0
            }
            trip.put("average", average)

            val route = JSONArray()
            for (point in routeCoordinates) {
                val coordinate = JSONObject()
                coordinate.put("lat", point.latitude)
                coordinate.put("lon", point.longitude)
                route.put(coordinate)
            }
            trip.put("route", route)

            trips.put(trip)

            val saved = preferences.edit()
                .putString("trips", trips.toString())
                .commit()

            Toast.makeText(
                this,
                if (saved) {
                    "Perjalanan berhasil disimpan."
                } else {
                    "Riwayat gagal disimpan. Silakan coba lagi."
                },
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Gagal menyimpan riwayat perjalanan.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showTripHistory() {
        val preferences = getSharedPreferences(
            "sepeda_pro_history",
            MODE_PRIVATE
        )

        val trips = try {
            JSONArray(preferences.getString("trips", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }

        if (trips.length() == 0) {
            AlertDialog.Builder(this)
                .setTitle("Riwayat Perjalanan")
                .setMessage("Belum ada perjalanan yang tersimpan.")
                .setPositiveButton("Tutup", null)
                .show()
            return
        }

        val tripList = (0 until trips.length())
            .map { trips.getJSONObject(it) }
            .reversed()

        val labels = tripList.mapIndexed { index, trip ->
            val distance = trip.optDouble("distance", 0.0) / 1000.0
            val date = trip.optString("date", "Tanggal tidak tersedia")
            "${index + 1}. $date\n" +
                String.format(
                    java.util.Locale.US,
                    "%.2f km",
                    distance
                )
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Riwayat Perjalanan")
            .setItems(labels) { _, which ->
                showTripDetails(tripList[which])
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun showTripDetails(trip: JSONObject) {
        val distance = trip.optDouble("distance", 0.0) / 1000.0
        val duration = trip.optLong("duration", 0L)
        val moving = trip.optLong("moving", 0L)
        val stopped = trip.optLong(
            "stopped",
            (duration - moving).coerceAtLeast(0L)
        )
        val average = trip.optDouble("average", 0.0)
        val maxSpeed = trip.optDouble("maxSpeed", 0.0)
        val route = trip.optJSONArray("route") ?: JSONArray()

        val message = String.format(
            java.util.Locale.US,
            "Tanggal: %s\n" +
                "Jarak: %.2f km\n" +
                "Durasi total: %s\n" +
                "Waktu bersepeda: %s\n" +
                "Waktu berhenti: %s\n" +
                "Kecepatan rata-rata: %.1f km/jam\n" +
                "Kecepatan maksimum: %.1f km/jam\n" +
                "Titik rute tersimpan: %d",
            trip.optString("date", "-"),
            distance,
            formatDuration(duration),
            formatDuration(moving),
            formatDuration(stopped),
            average,
            maxSpeed,
            route.length()
        )

        AlertDialog.Builder(this)
            .setTitle("Detail Perjalanan")
            .setMessage(message)
            .setPositiveButton("Tutup", null)
            .show()
    }

    override fun onProviderEnabled(provider: String) {
        if (provider != LocationManager.GPS_PROVIDER) return

        if (::gpsIndicator.isInitialized) {
            gpsIndicator.setTextColor(Color.YELLOW)
            gpsIndicator.clearAnimation()

            if (tracking) {
                gpsIndicator.startAnimation(
                    AlphaAnimation(0.25f, 1.0f).apply {
                        duration = 650L
                        repeatMode = android.view.animation.Animation.REVERSE
                        repeatCount = android.view.animation.Animation.INFINITE
                    }
                )
                statusText.text = "MENCARI SINYAL GPS..."
            }
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (provider != LocationManager.GPS_PROVIDER) return

        if (::gpsIndicator.isInitialized) {
            gpsIndicator.clearAnimation()
            gpsIndicator.setTextColor(Color.RED)
        }

        if (tracking) {
            statusText.text = "GPS TERPUTUS"
            statusText.setTextColor(orange)
            isMoving = false
        }
    }

    override fun onStatusChanged(
        provider: String?,
        status: Int,
        extras: Bundle?
    ) {
    }

    override fun onResume() {
        super.onResume()

        if (::map.isInitialized) {
            map.onResume()
        }
    }

    override fun onPause() {
        if (::map.isInitialized) {
            map.onPause()
        }

        super.onPause()
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerRunnable)

        if (tracking && hasLocationPermission()) {
            try {
                locationManager.removeUpdates(this)
            } catch (_: SecurityException) {
            }
        }

        if (::warningTone.isInitialized) {
            warningTone.release()
        }

        super.onDestroy()
    }
}
