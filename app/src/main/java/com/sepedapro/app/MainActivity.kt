package com.sepedapro.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
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

    private var tracking = false
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
            if (!tracking) return

            val now = System.currentTimeMillis()
            val delta = ((now - lastTickTime) / 1000L)
                .coerceAtLeast(0L)

            if (isMoving) {
                totalMovingTime += delta
            }

            lastTickTime = now

            // Statistik durasi utama menggunakan waktu bersepeda.
            totalElapsedTime = totalMovingTime

            // Periksa jeda otomatis berdasarkan waktu sejak kecepatan nol.
            if (currentSpeedKmh < 0.05 &&
                isMoving &&
                belowThresholdSince > 0L &&
                now - belowThresholdSince >= 5000L
            ) {
                isMoving = false
                belowThresholdSince = 0L
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
        speedText.textSize = 60f
        speedText.typeface = Typeface.DEFAULT_BOLD
        speedText.setTextColor(green)
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
        speedUnit.setTextColor(muted)
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
        maxSpeedText.setTextColor(green)
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
        startButton.text = "▶  MULAI BERSEPEDA"
        startButton.textSize = 15f
        startButton.setTextColor(white)
        startButton.setBackgroundColor(green)
        startButton.minHeight = dp(48)
        startButton.setOnClickListener {
            if (tracking) {
                stopTracking()
            } else {
                startTracking()
            }
        }

        root.addView(
            startButton,
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        val historyButton = Button(this)
        historyButton.text = "RIWAYAT PERJALANAN"
        historyButton.setOnClickListener {
            showTripHistory()
        }
        root.addView(
            historyButton,
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        val speedSettingsButton = Button(this)
        speedSettingsButton.text = "ATUR KECEPATAN MINIMUM"
        speedSettingsButton.setOnClickListener {
            showSpeedSettings()
        }
        root.addView(
            speedSettingsButton,
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )



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

        setContentView(scroll)
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
            setTextColor(muted)
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

        speedText.text = "0.0"
        updateStatistics()

        startButton.text = "■  BERHENTI BERSEPEDA"
        startButton.setBackgroundColor(Color.rgb(220, 65, 65))

        statusText.text = "MENCARI SINYAL GPS..."
        statusText.setTextColor(green)

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

            startButton.text = "▶  MULAI BERSEPEDA"
            startButton.setBackgroundColor(green)

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
            totalElapsedTime = totalMovingTime
        }

        if (tracking) {
            saveTripHistory()
        }

        tracking = false
        isMoving = false
        lastLocation = null

        timerHandler.removeCallbacks(timerRunnable)

        startButton.text = "▶  MULAI BERSEPEDA"
        startButton.setTextColor(white)
        startButton.setBackgroundColor(green)

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
        if (!tracking) return

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

        if (currentSpeedKmh < 0.05) {
            aboveThresholdSince = 0L

            if (belowThresholdSince == 0L) {
                belowThresholdSince = now
            }

            if (isMoving && now - belowThresholdSince >= 5000L) {
                isMoving = false
                belowThresholdSince = 0L
            }
        } else {
            belowThresholdSince = 0L
            aboveThresholdSince = 0L

            if (!isMoving) {
                isMoving = true
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
        val average = trip.optDouble("average", 0.0)
        val maxSpeed = trip.optDouble("maxSpeed", 0.0)
        val route = trip.optJSONArray("route") ?: JSONArray()

        val message = String.format(
            java.util.Locale.US,
            "Tanggal: %s\n" +
                "Jarak: %.2f km\n" +
                "Durasi: %s\n" +
                "Waktu bergerak: %s\n" +
                "Kecepatan rata-rata: %.1f km/jam\n" +
                "Kecepatan maksimum: %.1f km/jam\n" +
                "Titik rute tersimpan: %d",
            trip.optString("date", "-"),
            distance,
            formatDuration(duration),
            formatDuration(moving),
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
        if (tracking) {
            statusText.text = "GPS AKTIF"
            statusText.setTextColor(green)
        }
    }

    override fun onProviderDisabled(provider: String) {
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
