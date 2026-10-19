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
import android.location.Geocoder
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.SharedPreferences
import android.widget.EditText
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.text.Editable
import android.text.TextWatcher

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
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors
import java.net.URL
import java.net.HttpURLConnection

class MainActivity : Activity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var map: MapView
    private var hasAutoZoomedToGps = false
    private var userHasChosenMapLocation = false
    private lateinit var mapFrame: FrameLayout
    private lateinit var locationHeaderText: TextView
    private val locationHeaderExecutor = Executors.newSingleThreadExecutor()
    private var lastGeocodedLocation: Location? = null
    private var lastGeocodeAttemptMs = 0L
    private var lastLocationHeaderLabel = ""
    private lateinit var routeLine: Polyline
    private lateinit var bikeMarker: Marker


    private fun searchLocationsOnline(query: String): List<android.location.Address> {
        val results = mutableListOf<android.location.Address>()

        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = URL(
                "https://photon.komoot.io/api/?q=$encodedQuery&limit=5&lang=en"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "SepedaPro-Android/1.0")
                setRequestProperty("Accept", "application/json")
            }

            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = connection.inputStream.bufferedReader().use {
                        it.readText()
                    }
                    val features = JSONObject(body).optJSONArray("features")
                        ?: JSONArray()

                    for (i in 0 until features.length()) {
                        val feature = features.optJSONObject(i) ?: continue
                        val geometry = feature.optJSONObject("geometry") ?: continue
                        val coordinates = geometry.optJSONArray("coordinates")
                            ?: continue
                        if (coordinates.length() < 2) continue

                        val longitude = coordinates.optDouble(0, Double.NaN)
                        val latitude = coordinates.optDouble(1, Double.NaN)
                        if (!latitude.isFinite() || !longitude.isFinite()) continue

                        val properties = feature.optJSONObject("properties")
                            ?: JSONObject()
                        val parts = listOf(
                            properties.optString("name"),
                            properties.optString("district"),
                            properties.optString("city"),
                            properties.optString("county"),
                            properties.optString("state"),
                            properties.optString("country")
                        ).filter { it.isNotBlank() }.distinct()

                        val label = parts.joinToString(", ")
                        if (label.isBlank()) continue

                        val address = android.location.Address(Locale("id", "ID"))
                        address.latitude = latitude
                        address.longitude = longitude
                        address.featureName = parts.first()
                        address.setAddressLine(0, label)
                        results.add(address)
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            // Jika layanan online gagal, pemanggil dapat mencoba Geocoder Android.
        }

        return results
    }

    private fun selectMapLocation(point: GeoPoint, title: String = "Lokasi pilihan") {
        userHasChosenMapLocation = true
        hasAutoZoomedToGps = true
        selectedLocationPoint = point

        val marker = selectedLocationMarker ?: Marker(map).also {
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(it)
            selectedLocationMarker = it
        }

        marker.position = point
        marker.title = title
        marker.snippet = String.format(
            Locale.US,
            "Lintang: %.6f, Bujur: %.6f",
            point.latitude,
            point.longitude
        )
        marker.setOnMarkerClickListener { clicked, _ ->
            clicked.showInfoWindow()
            true
        }

        map.controller.animateTo(point)
        map.invalidate()

        Toast.makeText(
            this,
            String.format(
                Locale("id", "ID"),
                "Lokasi dipilih: %.6f, %.6f",
                point.latitude,
                point.longitude
            ),
            Toast.LENGTH_LONG
        ).show()
    }
    private var selectedLocationMarker: Marker? = null
    private var selectedLocationPoint: GeoPoint? = null
    private var manualMapSelectionEnabled = true

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
    private lateinit var speedIndicator: TextView
    private lateinit var pauseIndicator: TextView
    private var speedIndicatorEnabled = true

    private var tracking = false
    private var manuallyPaused = false
    private var automaticallyPaused = false
    private var isMoving = false
    private var lastLocation: Location? = null
    private val routeCoordinates = mutableListOf<GeoPoint>()

    private var totalDistance = 0.0
    private var totalMovingTime = 0L
    private var totalElapsedTime = 0L
    private var startTime = 0L
    private var lastTickTime = 0L

    private lateinit var gradientText: TextView
    private lateinit var altitudeText: TextView
    private lateinit var elevationGainText: TextView
    private var currentGradientPercent = 0.0

    private var currentAltitudeMeters: Double? = null
    private var totalElevationGainMeters = 0.0
    private var lastElevationBaselineMeters: Double? = null
    private val elevationSamples = ArrayDeque<Double>()

    private data class GradientSample(
        val distanceMeters: Double,
        val altitudeMeters: Double
    )

    private val gradientSamples = ArrayDeque<GradientSample>()
    private val smoothedGradients = ArrayDeque<Double>()
    private var gradientDistanceMeters = 0.0

    private var currentSpeedKmh = 0.0
    private var maxSpeedKmh = 0.0
    private var minimumSpeedKmh = 0.0
    private var lowSpeedSince = 0L
    private var lastWarningTime = 0L
    private var warningEnabled = true

    private lateinit var warningTone: ToneGenerator
    private lateinit var warningPreferences: SharedPreferences

    private lateinit var weatherTemperatureText: TextView
    private val weatherHandler = Handler(Looper.getMainLooper())
    private val weatherExecutor = Executors.newSingleThreadExecutor()
    private val weatherPreferences by lazy {
        getSharedPreferences("sepeda_weather", MODE_PRIVATE)
    }
    private var lastWeatherRequestTime = 0L
    private val weatherRefreshInterval = 15 * 60 * 1000L




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

            // Jeda otomatis jika kecepatan <= 5 km/jam selama 5 detik.
            if (
                isMoving &&
                belowThresholdSince > 0L &&
                now - belowThresholdSince >= 5000L
            ) {
                isMoving = false
                automaticallyPaused = true
                aboveThresholdSince = 0L
                statusText.text = "JEDA OTOMATIS"
                statusText.setTextColor(orange)
                pauseIndicator.clearAnimation()
                pauseIndicator.alpha = 1.0f
                pauseIndicator.setTextColor(Color.RED)
                pauseIndicator.text = "⏸"
                pauseIndicator.visibility = View.VISIBLE
                pauseIndicator.startAnimation(
                    AlphaAnimation(1.0f, 0.15f).apply {
                        duration = 500L
                        repeatMode = AlphaAnimation.REVERSE
                        repeatCount = AlphaAnimation.INFINITE
                    }
                )
            }

            // Hitung durasi setelah status jeda otomatis diperbarui.
            if (!automaticallyPaused) {
                totalElapsedTime += delta
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
            0.0f
        ).toDouble()

        speedIndicatorEnabled = warningPreferences.getBoolean(
            "speed_indicator_enabled",
            false
        )
        // Bunyi peringatan otomatis mengikuti pengaturan kecepatan minimum.
        warningEnabled = speedIndicatorEnabled



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
                    "Atur kecepatan minimum",
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
        speedLabel.text = "Kecepatan minimum (km/jam)"
        speedLabel.setTextColor(white)
        container.addView(speedLabel)
        container.addView(input)

        val indicatorCheck = android.widget.CheckBox(this)
        indicatorCheck.text = "Atur kecepatan minimum"
        indicatorCheck.setTextColor(white)
        indicatorCheck.isChecked = speedIndicatorEnabled
        container.addView(indicatorCheck)


        AlertDialog.Builder(this)
            .setTitle("Atur Kecepatan Minimum")
            .setView(container)
            .setPositiveButton("SIMPAN") { _, _ ->
                val newSpeed = input.text.toString().toDoubleOrNull()

                if (newSpeed == null || newSpeed < 0.0 || newSpeed > 100.0) {
                    Toast.makeText(
                        this,
                        "Masukkan kecepatan antara 0,0 dan 100,0 km/jam",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    minimumSpeedKmh = newSpeed
                    speedIndicatorEnabled = indicatorCheck.isChecked
                    warningEnabled = speedIndicatorEnabled

                    warningPreferences.edit()
                        .putFloat("minimum_speed", newSpeed.toFloat())
                        .putBoolean("warning_enabled", speedIndicatorEnabled)
                        .putBoolean("speed_indicator_enabled", speedIndicatorEnabled)
                        .apply()

                    lowSpeedSince = 0L
                    speedIndicator.clearAnimation()
                    speedIndicator.alpha = 1.0f
                    speedIndicator.text = String.format(
                        java.util.Locale.US,
                        "⚡ %.1f km/j",
                        minimumSpeedKmh
                    )
                    speedIndicator.visibility = if (speedIndicatorEnabled) View.VISIBLE else View.GONE
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

        locationHeaderText = TextView(this)
        locationHeaderText.text = "Mendeteksi lokasi..."
        locationHeaderText.textSize = 10f
        locationHeaderText.setTextColor(muted)
        locationHeaderText.gravity = Gravity.CENTER
        root.addView(
            locationHeaderText,
            matchWrap().apply { bottomMargin = dp(2) }
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
        RotationGestureOverlay(map).also { rotationOverlay ->
            rotationOverlay.isEnabled = true
            map.overlays.add(rotationOverlay)
        }
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

        val mapEventsReceiver = object : org.osmdroid.events.MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                selectMapLocation(point, "Lokasi pilihan manual")
                return true
            }

            override fun longPressHelper(point: GeoPoint): Boolean {
                selectMapLocation(point, "Lokasi pilihan manual")
                return true
            }
        }
        map.overlays.add(
            org.osmdroid.views.overlay.MapEventsOverlay(mapEventsReceiver)
        )

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

        val gradientCard = createCard()
        gradientCard.setPadding(dp(8), dp(8), dp(8), dp(8))
        gradientCard.addView(
            createLabel("GRADIEN JALAN"),
            matchWrap().apply { bottomMargin = dp(5) }
        )

        gradientText = createStatValue("--,-%")
        gradientText.textSize = 20f
        gradientCard.addView(gradientText, matchWrap())

        root.addView(
            gradientCard,
            matchWrap().apply { bottomMargin = dp(8) }
        )

        val elevationRow = LinearLayout(this)
        elevationRow.orientation = LinearLayout.HORIZONTAL
        elevationRow.weightSum = 2f

        val altitudeCard = createCard()
        altitudeCard.setPadding(dp(6), dp(8), dp(6), dp(8))
        altitudeCard.addView(
            createLabel("KETINGGIAN (MDPL)"),
            matchWrap().apply { bottomMargin = dp(5) }
        )
        altitudeText = createStatValue("-- mdpl")
        altitudeText.textSize = 18f
        altitudeCard.addView(altitudeText, matchWrap())

        elevationRow.addView(
            altitudeCard,
            LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                marginEnd = dp(5)
            }
        )

        val elevationGainCard = createCard()
        elevationGainCard.setPadding(dp(6), dp(8), dp(6), dp(8))
        elevationGainCard.addView(
            createLabel("ELEVATION GAIN"),
            matchWrap().apply { bottomMargin = dp(5) }
        )
        elevationGainText = createStatValue("0 m")
        elevationGainText.textSize = 18f
        elevationGainCard.addView(elevationGainText, matchWrap())

        elevationRow.addView(
            elevationGainCard,
            LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                marginStart = dp(5)
            }
        )

        root.addView(
            elevationRow,
            matchWrap().apply { bottomMargin = dp(8) }
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
            resetGradientSamples()

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
            speedIndicator.clearAnimation()
            speedIndicator.alpha = 1.0f
            speedIndicator.text = String.format(
                java.util.Locale.US,
                "⚡ %.1f km/j",
                minimumSpeedKmh
            )
            speedIndicator.visibility = if (speedIndicatorEnabled) View.VISIBLE else View.GONE
            speedIndicator.setTextColor(Color.RED)

            pauseIndicator.visibility = View.VISIBLE
            pauseIndicator.alpha = 1.0f
            pauseIndicator.clearAnimation()
            pauseIndicator.startAnimation(
                AlphaAnimation(1.0f, 0.15f).apply {
                    duration = 500L
                    repeatMode = AlphaAnimation.REVERSE
                    repeatCount = AlphaAnimation.INFINITE
                }
            )
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
            resetGradientSamples()

            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    this,
                    Looper.getMainLooper()
                )

                manuallyPaused = false
                pauseIndicator.clearAnimation()
                pauseIndicator.alpha = 1.0f
                pauseIndicator.visibility = View.GONE
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

        // Indikator batas kecepatan minimum
        speedIndicator = TextView(this)
        speedIndicator.text = String.format(
            java.util.Locale.US,
            "⚡ %.1f km/j",
            minimumSpeedKmh
        )
        speedIndicator.textSize = 14f
        speedIndicator.gravity = Gravity.CENTER
        speedIndicator.setTextColor(Color.GREEN)
        speedIndicator.setPadding(dp(8), dp(4), dp(8), dp(4))
        speedIndicator.visibility = if (speedIndicatorEnabled) View.VISIBLE else View.GONE

        navigation.addView(
            speedIndicator,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(48)
            )
        )

        pauseIndicator = TextView(this)
        pauseIndicator.text = "⏸"
        pauseIndicator.textSize = 14f
        pauseIndicator.gravity = Gravity.CENTER
        pauseIndicator.setTextColor(Color.RED)
        pauseIndicator.setPadding(dp(8), dp(4), dp(8), dp(4))
        pauseIndicator.visibility = View.GONE
        navigation.addView(
            pauseIndicator,
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

        // Baris pertama: Gradien Jalan | Elevation Gain
        val gradientElevationRow = LinearLayout(this)
        gradientElevationRow.orientation = LinearLayout.HORIZONTAL
        gradientElevationRow.weightSum = 2f

        // Lepaskan kartu dari induk lama sebelum dipindahkan.
        (gradientCard.parent as? android.view.ViewGroup)?.removeView(gradientCard)
        (elevationGainCard.parent as? android.view.ViewGroup)?.removeView(elevationGainCard)

        gradientElevationRow.addView(
            gradientCard,
            LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                marginEnd = dp(5)
            }
        )
        gradientElevationRow.addView(
            elevationGainCard,
            LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                marginStart = dp(5)
            }
        )
        speedRoot.addView(
            gradientElevationRow,
            matchWrap().apply { bottomMargin = dp(8) }
        )

        // Baris kedua: Ketinggian (MDPL) | Suhu Lingkungan
        val altitudeWeatherRow = LinearLayout(this)
        altitudeWeatherRow.orientation = LinearLayout.HORIZONTAL
        altitudeWeatherRow.weightSum = 2f

        (altitudeCard.parent as? android.view.ViewGroup)?.removeView(altitudeCard)
        altitudeWeatherRow.addView(
            altitudeCard,
            LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                marginEnd = dp(5)
            }
        )

        val weatherCard = createCard()
        weatherCard.setPadding(dp(6), dp(8), dp(6), dp(8))
        weatherCard.addView(
            createLabel("SUHU"),
            matchWrap().apply { bottomMargin = dp(5) }
        )
        weatherTemperatureText = createStatValue("--.- °C")
        weatherTemperatureText.textSize = 18f
        weatherCard.addView(weatherTemperatureText, matchWrap())

        altitudeWeatherRow.addView(
            weatherCard,
            LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                marginStart = dp(5)
            }
        )
        speedRoot.addView(
            altitudeWeatherRow,
            matchWrap().apply { bottomMargin = dp(8) }
        )
        showCachedWeatherTemperature()

        speedRoot.addView(controlRow, matchWrap())
        speedRoot.addView(info, matchWrap())
        val brandText = TextView(this)
        brandText.text = "Sapedahan"
        brandText.textSize = 11f
        brandText.setTextColor(muted)
        brandText.gravity = Gravity.CENTER
        speedRoot.addView(brandText, matchWrap())
        speedRoot.addView(footer, matchWrap())

        speedScroll.addView(speedRoot)

        mapFrame = FrameLayout(this)

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

            // Minta GPS saat Maps dibuka, tanpa memulai perekaman.
            if (hasLocationPermission()) {
                try {
                    if (locationManager.isProviderEnabled(
                            LocationManager.GPS_PROVIDER
                        )
                    ) {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1000L,
                            1f,
                            this,
                            Looper.getMainLooper()
                        )
                    } else {
                        Toast.makeText(
                            this,
                            "Aktifkan GPS untuk melihat posisi.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (_: SecurityException) {
                    Toast.makeText(
                        this,
                        "Izin lokasi belum tersedia.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    permissionCode
                )
            }
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

        // Kontrol peta: zoom in, zoom out, dan lokasi GPS manual.
        val mapControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun createMapControlButton(
            label: String,
            description: String,
            onClick: () -> Unit
        ): TextView {
            return TextView(this).apply {
                text = label
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                contentDescription = description
                setTextColor(white)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(cardColor)
                    setStroke(dp(1), green)
                }
                setOnClickListener { onClick() }
            }
        }

        val zoomInButton = createMapControlButton(
            "+",
            "Perbesar peta"
        ) {
            map.controller.zoomIn()
        }

        val zoomOutButton = createMapControlButton(
            "−",
            "Perkecil peta"
        ) {
            map.controller.zoomOut()
        }

        val manualLocationButton = createMapControlButton(
            "📍",
            "Minta lokasi GPS terbaru"
        ) {
            if (!hasLocationPermission()) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    permissionCode
                )
            } else {
                try {
                    if (locationManager.isProviderEnabled(
                            LocationManager.GPS_PROVIDER
                        )
                    ) {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            1000L,
                            0f,
                            this,
                            Looper.getMainLooper()
                        )
                        Toast.makeText(
                            this,
                            "Meminta lokasi GPS terbaru...",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Aktifkan GPS terlebih dahulu.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (_: SecurityException) {
                    Toast.makeText(
                        this,
                        "Izin lokasi belum tersedia.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        val searchLocationButton = createMapControlButton(
            "🔍",
            "Cari lokasi pada peta"
        ) {
            val searchInput = AutoCompleteTextView(this).apply {
                hint = "Ketik nama tempat, misalnya bale"
                setSingleLine(true)
                threshold = 1
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }

            val suggestionAdapter = object : ArrayAdapter<String>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                mutableListOf<String>()
            ) {
                private val noFilter = object : android.widget.Filter() {
                    override fun performFiltering(
                        constraint: CharSequence?
                    ): FilterResults {
                        val results = FilterResults()
                        val labels = (0 until count).mapNotNull { getItem(it) }
                        results.values = labels
                        results.count = labels.size
                        return results
                    }

                    override fun publishResults(
                        constraint: CharSequence?,
                        results: FilterResults?
                    ) {
                        notifyDataSetChanged()
                    }
                }

                override fun getFilter(): android.widget.Filter = noFilter
            }
            searchInput.setAdapter(suggestionAdapter)

            val searchHandler = Handler(Looper.getMainLooper())
            var searchRequestId = 0
            var suggestionAddresses = emptyList<android.location.Address>()

            val searchDialog = AlertDialog.Builder(this)
                .setTitle("Cari lokasi")
                .setView(searchInput)
                .setNegativeButton("Batal", null)
                .setPositiveButton("Cari", null)
                .create()

            fun moveToAddress(address: android.location.Address) {
                val point = GeoPoint(address.latitude, address.longitude)
                val title = try {
                    address.getAddressLine(0)
                } catch (_: Exception) {
                    null
                } ?: address.featureName ?: "Lokasi pilihan"

                map.controller.setZoom(17.0)
                selectMapLocation(point, title)
            }

            searchInput.setOnItemClickListener { _, _, position, _ ->
                val chosen = suggestionAddresses.getOrNull(position)
                if (chosen != null) {
                    searchDialog.dismiss()
                    moveToAddress(chosen)
                }
            }

            searchInput.addTextChangedListener(object : TextWatcher {
                private var pending: Runnable? = null

                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?, start: Int, before: Int, count: Int
                ) {
                    val query = s?.toString()?.trim().orEmpty()
                    searchRequestId++
                    val requestId = searchRequestId

                    pending?.let { searchHandler.removeCallbacks(it) }

                    if (query.length < 3) {
                        suggestionAdapter.clear()
                        suggestionAddresses = emptyList()
                        return
                    }

                    val task = Runnable {
                        locationHeaderExecutor.execute {
                            val results = searchLocationsOnline(query).ifEmpty {
                                try {
                                    Geocoder(this@MainActivity, Locale("id", "ID"))
                                        .getFromLocationName(query, 5)
                                        ?: emptyList()
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }

                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread
                                if (!searchDialog.isShowing || requestId != searchRequestId) {
                                    return@runOnUiThread
                                }

                                val labels = results.mapIndexed { index, address ->
                                    val line = try {
                                        address.getAddressLine(0)
                                    } catch (_: Exception) {
                                        null
                                    }
                                    line ?: address.featureName
                                        ?: "$query (hasil ${index + 1})"
                                }

                                suggestionAddresses = results
                                suggestionAdapter.clear()
                                suggestionAdapter.addAll(labels)
                                suggestionAdapter.notifyDataSetChanged()

                                if (labels.isNotEmpty() && searchInput.hasFocus()) {
                                    searchInput.showDropDown()
                                }
                            }
                        }
                    }
                    pending = task
                    searchHandler.postDelayed(task, 600L)
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })

            searchDialog.setOnShowListener {
                searchDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener {
                        val query = searchInput.text.toString().trim()

                        if (query.isEmpty()) {
                            searchInput.error = "Masukkan nama lokasi"
                            return@setOnClickListener
                        }

                        searchDialog.dismiss()
                        Toast.makeText(
                            this,
                            "Mencari lokasi...",
                            Toast.LENGTH_SHORT
                        ).show()

                        locationHeaderExecutor.execute {
                            val results = searchLocationsOnline(query).ifEmpty {
                                try {
                                    Geocoder(this@MainActivity, Locale("id", "ID"))
                                        .getFromLocationName(query, 5)
                                        ?: emptyList()
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }

                            runOnUiThread {
                                if (isFinishing || isDestroyed) return@runOnUiThread

                                if (results.isEmpty()) {
                                    Toast.makeText(
                                        this,
                                        "Lokasi tidak ditemukan. Periksa koneksi internet atau nama tempat.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@runOnUiThread
                                }

                                val labels = results.mapIndexed { index, address ->
                                    val line = try {
                                        address.getAddressLine(0)
                                    } catch (_: Exception) {
                                        null
                                    }
                                    line ?: address.featureName
                                        ?: "$query (hasil ${index + 1})"
                                }

                                AlertDialog.Builder(this)
                                    .setTitle("Pilih lokasi")
                                    .setItems(labels.toTypedArray()) { _, which ->
                                        if (which in results.indices) {
                                            moveToAddress(results[which])
                                        }
                                    }
                                    .setNegativeButton("Batal", null)
                                    .show()
                            }
                        }
                    }
            }

            searchDialog.show()
            searchInput.requestFocus()
        }

        val controlSize = dp(48)
        val controlGap = dp(6)

        for (button in listOf(
            zoomInButton,
            zoomOutButton,
            manualLocationButton,
            searchLocationButton
        )) {
            mapControls.addView(
                button,
                LinearLayout.LayoutParams(
                    controlSize,
                    controlSize
                ).apply {
                    marginEnd = controlGap
                }
            )
        }

        mapFrame.addView(
            mapControls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                setMargins(dp(8), dp(8), dp(12), dp(16))
            }
        )

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


    private fun resetGradientSamples() {
        gradientSamples.clear()
        smoothedGradients.clear()
        gradientDistanceMeters = 0.0
    }


    private fun showCachedWeatherTemperature() {
        if (!::weatherTemperatureText.isInitialized) return
        val cached = weatherPreferences.getFloat("temperature_c", Float.NaN)
        if (!cached.isNaN()) {
            weatherTemperatureText.text = String.format(
                Locale.getDefault(), "%.1f °C", cached
            )
        }
    }

    private fun fetchWeatherTemperature(location: Location) {
        val now = System.currentTimeMillis()
        if (now - lastWeatherRequestTime < weatherRefreshInterval) return
        lastWeatherRequestTime = now

        val latitude = location.latitude
        val longitude = location.longitude

        weatherExecutor.execute {
            var connection: HttpURLConnection? = null
            try {
                val address =
                    "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m&temperature_unit=celsius"

                connection = URL(address).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.requestMethod = "GET"

                val response = connection.inputStream.bufferedReader().use {
                    it.readText()
                }
                val temperature = JSONObject(response)
                    .getJSONObject("current")
                    .getDouble("temperature_2m")

                if (temperature.isFinite() && temperature in -100.0..70.0) {
                    weatherPreferences.edit()
                        .putFloat("temperature_c", temperature.toFloat())
                        .putLong("temperature_saved_at", System.currentTimeMillis())
                        .apply()

                    weatherHandler.post {
                        if (::weatherTemperatureText.isInitialized) {
                            weatherTemperatureText.text = String.format(
                                Locale.getDefault(), "%.1f °C", temperature
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                weatherHandler.post { showCachedWeatherTemperature() }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun resetElevation() {
        currentAltitudeMeters = null
        totalElevationGainMeters = 0.0
        lastElevationBaselineMeters = null
        elevationSamples.clear()

        if (::altitudeText.isInitialized) {
            altitudeText.text = "-- mdpl"
        }
        if (::elevationGainText.isInitialized) {
            elevationGainText.text = "0 m"
        }
    }

    private fun updateElevation(location: Location) {
        if (!location.hasAltitude() || location.accuracy > 15f) return

        val altitude = location.altitude
        if (!altitude.isFinite() || altitude !in -500.0..9000.0) return

        // Jika akurasi vertikal tersedia, abaikan sampel yang terlalu buruk.
        if (location.hasVerticalAccuracy() &&
            location.verticalAccuracyMeters > 10f
        ) return

        elevationSamples.addLast(altitude)
        while (elevationSamples.size > 5) {
            elevationSamples.removeFirst()
        }

        // Median dari sampel terakhir membantu menekan lonjakan GPS.
        val sorted = elevationSamples.toList().sorted()
        val filteredAltitude = sorted[sorted.size / 2]

        currentAltitudeMeters = filteredAltitude
        altitudeText.text = String.format(
            Locale.US,
            "%.0f mdpl",
            filteredAltitude
        )

        val baseline = lastElevationBaselineMeters
        if (baseline == null) {
            lastElevationBaselineMeters = filteredAltitude
        } else {
            val change = filteredAltitude - baseline

            // Kenaikan kecil diabaikan untuk mengurangi noise.
            if (change >= 3.0) {
                totalElevationGainMeters += change
                lastElevationBaselineMeters = filteredAltitude
            } else if (change <= -3.0) {
                // Saat turun, perbarui acuan tanpa mengurangi total gain.
                lastElevationBaselineMeters = filteredAltitude
            }
        }

        elevationGainText.text = String.format(
            Locale.US,
            "%.0f m",
            totalElevationGainMeters
        )
    }

    private fun updateGradient(location: Location, previous: Location?) {
        if (!location.hasAltitude() || location.accuracy > 15f) return

        val altitude = location.altitude
        if (!altitude.isFinite()) return

        val previousAltitude = previous?.takeIf {
            it.hasAltitude() && it.accuracy <= 15f
        }

        if (previousAltitude != null) {
            val segmentDistance =
                previousAltitude.distanceTo(location).toDouble()

            if (segmentDistance in 2.0..25.0) {
                gradientDistanceMeters += segmentDistance
            }
        }

        val sample = GradientSample(
            distanceMeters = gradientDistanceMeters,
            altitudeMeters = altitude
        )

        val last = gradientSamples.peekLast()
        if (last == null) {
            gradientSamples.addLast(sample)
            return
        }

        val horizontalDistance =
            sample.distanceMeters - last.distanceMeters

        if (horizontalDistance < 60.0) return

        val altitudeChange = sample.altitudeMeters - last.altitudeMeters

        if (horizontalDistance <= 120.0 &&
            kotlin.math.abs(altitudeChange) <= 6.0
        ) {
            val slope = altitudeChange / horizontalDistance * 100.0

            if (slope.isFinite() && kotlin.math.abs(slope) <= 20.0) {
                smoothedGradients.addLast(slope)
                while (smoothedGradients.size > 5) {
                    smoothedGradients.removeFirst()
                }

                val result = smoothedGradients.average()
                    .let { if (kotlin.math.abs(it) < 1.5) 0.0 else it }

                currentGradientPercent = result
                gradientText.text = String.format(
                    Locale.US,
                    "%.0f%%",
                    result
                )
            }
        }

        gradientSamples.addLast(sample)
        while (gradientSamples.size > 2) {
            gradientSamples.removeFirst()
        }
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
        hasAutoZoomedToGps = false
        userHasChosenMapLocation = false
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
        automaticallyPaused = false
        pauseIndicator.clearAnimation()
        pauseIndicator.alpha = 1.0f
        pauseIndicator.visibility = View.GONE
        currentSpeedKmh = 0.0
        maxSpeedKmh = 0.0
        lowSpeedSince = 0L
        lastWarningTime = 0L

        isMoving = false
        belowThresholdSince = 0L
        aboveThresholdSince = 0L
        resetGradientSamples()
        resetElevation()
        lastLocation = null

        routeCoordinates.clear()
        routeLine.setPoints(ArrayList<GeoPoint>())
        map.invalidate()

        startTime = System.currentTimeMillis()
        lastTickTime = startTime
        tracking = true
        manuallyPaused = false
        pauseIndicator.clearAnimation()
        pauseIndicator.alpha = 1.0f
        pauseIndicator.visibility = View.GONE
        speedIndicator.clearAnimation()
        speedIndicator.alpha = 1.0f
        speedIndicator.visibility = if (speedIndicatorEnabled) View.VISIBLE else View.GONE
        speedIndicator.text = String.format(
            java.util.Locale.US,
            "⚡ %.1f km/j",
            minimumSpeedKmh
        )
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

        if (tracking && !manuallyPaused) {
            val now = System.currentTimeMillis()
            val delta = ((now - lastTickTime) / 1000L)
                .coerceAtLeast(0L)

            if (isMoving) {
                totalMovingTime += delta
            }

            lastTickTime = now
            if (!automaticallyPaused) {
                totalElapsedTime += delta
            }
        }

        if (tracking) {
            saveTripHistory()
        }

        tracking = false
        manuallyPaused = false
        automaticallyPaused = false
        pauseIndicator.clearAnimation()
        pauseIndicator.alpha = 1.0f
        pauseIndicator.visibility = View.GONE
        styleTrackingControls(false)
        speedAlertText.clearAnimation()
        speedAlertText.visibility = View.GONE
        isMoving = false
        lastLocation = null

        timerHandler.removeCallbacks(timerRunnable)

        speedText.text = "0.0"
        currentSpeedKmh = 0.0

        speedIndicator.clearAnimation()
        speedIndicator.alpha = 1.0f
        speedIndicator.text = String.format(
            java.util.Locale.US,
            "⚡ %.1f km/j",
            minimumSpeedKmh
        )
        speedIndicator.setTextColor(Color.RED)
        speedIndicator.visibility = if (speedIndicatorEnabled) View.VISIBLE else View.GONE

        statusText.text = "PERJALANAN SELESAI"
        statusText.setTextColor(muted)

        updateStatistics()

        Toast.makeText(
            this,
            "Perjalanan selesai. Statistik tetap di layar.",
            Toast.LENGTH_LONG
        ).show()
    }


    private fun updateLocationHeader(location: Location) {
        val previous = lastGeocodedLocation
        val now = System.currentTimeMillis()

        if (previous != null &&
            previous.distanceTo(location) < 250f &&
            now - lastGeocodeAttemptMs < 60_000L
        ) return

        lastGeocodeAttemptMs = now
        lastGeocodedLocation = Location(location)

        locationHeaderExecutor.execute {
            try {
                val geocoder = Geocoder(this, Locale("id", "ID"))
                val addresses = geocoder.getFromLocation(
                    location.latitude,
                    location.longitude,
                    1
                )
                val address = addresses?.firstOrNull() ?: return@execute

                val kelurahan = address.subLocality?.trim().orEmpty()
                val kecamatan = address.locality?.trim().orEmpty()
                val kotaKabupaten = address.subAdminArea?.trim().orEmpty()

                val wilayah = listOf(kelurahan, kecamatan, kotaKabupaten)
                    .filter { it.isNotBlank() }
                    .distinct()

                if (wilayah.isEmpty()) return@execute

                val label = when {
                    wilayah.size >= 3 ->
                        "${wilayah[0]}, ${wilayah[1]} – ${wilayah[2]}"
                    wilayah.size == 2 ->
                        "${wilayah[0]}, ${wilayah[1]}"
                    else -> wilayah[0]
                }

                if (label != lastLocationHeaderLabel) {
                    runOnUiThread {
                        lastLocationHeaderLabel = label
                        locationHeaderText.text = label
                    }
                }
            } catch (_: Exception) {
                // Lokasi terakhir tetap ditampilkan jika geocoder gagal.
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        if (location.accuracy > 30f) {
            if (tracking && !manuallyPaused) {
                statusText.text = "MENUNGGU GPS LEBIH AKURAT"
                statusText.setTextColor(muted)
            }
            return
        }

        updateLocationHeader(location)

        val point = GeoPoint(
            location.latitude,
            location.longitude
        )

        bikeMarker.position = point
        if (!hasAutoZoomedToGps) {
            if (!userHasChosenMapLocation) {
                map.controller.setZoom(18.0)
                map.controller.animateTo(point)
            }
            hasAutoZoomedToGps = true
        }
        map.invalidate()

        // GPS pratinjau: jangan merebut fokus peta jika pengguna
        // sudah memilih lokasi secara manual atau melalui pencarian.
        if (!tracking || manuallyPaused) {
            if (mapFrame.visibility == View.VISIBLE &&
                !userHasChosenMapLocation
            ) {
                map.controller.animateTo(point)
            }
            return
        }

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

        updateElevation(location)
        updateGradient(location, previous)
        fetchWeatherTemperature(location)
        lastLocation = location

        val speedMps = if (location.hasSpeed()) {
            location.speed.toDouble()
        } else {
            calculatedSpeed
        }

currentSpeedKmh = (speedMps * 3.6).coerceAtLeast(0.0)

// Perbarui indikator batas kecepatan minimum.
if (speedIndicatorEnabled) {
    speedIndicator.visibility = View.VISIBLE
    speedIndicator.text = String.format(
        java.util.Locale.US,
        "⚡ %.1f km/j",
        minimumSpeedKmh
    )

    if (currentSpeedKmh >= minimumSpeedKmh) {
        speedIndicator.clearAnimation()
        speedIndicator.alpha = 1.0f
        speedIndicator.setTextColor(Color.GREEN)
    } else {
        speedIndicator.setTextColor(Color.RED)
        if (speedIndicator.animation == null) {
            val blink = AlphaAnimation(1.0f, 0.15f)
            blink.duration = 500L
            blink.repeatMode = AlphaAnimation.REVERSE
            blink.repeatCount = AlphaAnimation.INFINITE
            speedIndicator.startAnimation(blink)
        }
    }
} else {
    speedIndicator.clearAnimation()
    speedIndicator.alpha = 1.0f
    speedIndicator.visibility = View.GONE
}


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
                automaticallyPaused = false
                pauseIndicator.clearAnimation()
                pauseIndicator.alpha = 1.0f
                pauseIndicator.visibility = View.GONE
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
        weatherHandler.removeCallbacksAndMessages(null)
        weatherExecutor.shutdownNow()

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
