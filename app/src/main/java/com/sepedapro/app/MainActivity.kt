package com.sepedapro.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity(), LocationListener {

    private lateinit var locationManager: LocationManager

    private lateinit var speedText: TextView
    private lateinit var distanceText: TextView
    private lateinit var averageText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button

    private lateinit var durationText: TextView
    private lateinit var movingText: TextView
    private lateinit var stoppedText: TextView
    private lateinit var maxSpeedText: TextView

    private var tracking = false
    private var lastLocation: Location? = null

    private var totalDistance = 0.0
    private var totalMovingTime = 0L
    private var totalElapsedTime = 0L
    private var startTime = 0L
    private var lastTickTime = 0L

    private var currentSpeedKmh = 0.0
    private var maxSpeedKmh = 0.0
    private var isMoving = false

    private val timerHandler = Handler(Looper.getMainLooper())

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!tracking) return

            val now = System.currentTimeMillis()
            val deltaSeconds = ((now - lastTickTime) / 1000L)
                .coerceAtLeast(0L)

            totalElapsedTime = (now - startTime) / 1000L

            if (isMoving) {
                totalMovingTime += deltaSeconds
            }

            lastTickTime = now

            updateTimeStatistics()

            timerHandler.postDelayed(this, 1000L)
        }
    }

    private val background = Color.rgb(15, 23, 32)
    private val cardColor = Color.rgb(29, 40, 52)
    private val green = Color.rgb(39, 174, 96)
    private val white = Color.WHITE
    private val muted = Color.rgb(170, 184, 196)
    private val orange = Color.rgb(255, 170, 60)

    private val permissionCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager

        createDashboard()
        updateTimeStatistics()
    }

    private fun createDashboard() {

        val scrollView = ScrollView(this)
        scrollView.setBackgroundColor(background)
        scrollView.isFillViewport = true

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(18), dp(20), dp(12))
        root.gravity = Gravity.CENTER_HORIZONTAL

        scrollView.addView(
            root,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // JUDUL APLIKASI
        val title = TextView(this)
        title.text = "SEPEDA PRO"
        title.textSize = 27f
        title.setTextColor(white)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER

        root.addView(
            title,
            matchWrap().apply {
                bottomMargin = dp(4)
            }
        )

        val subtitle = TextView(this)
        subtitle.text = "TEMAN PERJALANAN SEPEDAMU"
        subtitle.textSize = 11f
        subtitle.setTextColor(muted)
        subtitle.gravity = Gravity.CENTER
        subtitle.letterSpacing = 0.12f

        root.addView(
            subtitle,
            matchWrap().apply {
                bottomMargin = dp(24)
            }
        )

        // KARTU KECEPATAN
        val speedCard = createCard()
        speedCard.gravity = Gravity.CENTER
        speedCard.setPadding(dp(12), dp(24), dp(12), dp(24))

        speedCard.addView(
            createLabel("KECEPATAN SAAT INI"),
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        val speedRow = LinearLayout(this)
        speedRow.orientation = LinearLayout.HORIZONTAL
        speedRow.gravity = Gravity.CENTER

        speedText = TextView(this)
        speedText.text = "0.0"
        speedText.textSize = 66f
        speedText.setTextColor(green)
        speedText.typeface = Typeface.DEFAULT_BOLD
        speedText.gravity = Gravity.CENTER

        speedRow.addView(
            speedText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val speedUnit = TextView(this)
        speedUnit.text = " km/j"
        speedUnit.textSize = 17f
        speedUnit.setTextColor(white)
        speedUnit.gravity = Gravity.CENTER_VERTICAL

        speedRow.addView(
            speedUnit,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        speedCard.addView(speedRow, matchWrap())

        root.addView(
            speedCard,
            matchWrap().apply {
                bottomMargin = dp(16)
            }
        )

        // KARTU JARAK DAN RATA-RATA
        val statsRow = LinearLayout(this)
        statsRow.orientation = LinearLayout.HORIZONTAL
        statsRow.gravity = Gravity.CENTER
        statsRow.weightSum = 2f

        val distanceCard = createCard()
        distanceCard.setPadding(dp(8), dp(16), dp(8), dp(16))

        distanceCard.addView(
            createLabel("JARAK TEMPUH"),
            matchWrap().apply {
                bottomMargin = dp(12)
            }
        )

        distanceText = createStatValue("0.00 km")
        distanceCard.addView(distanceText, matchWrap())

        statsRow.addView(
            distanceCard,
            LinearLayout.LayoutParams(0, dp(110), 1f).apply {
                marginEnd = dp(6)
            }
        )

        val averageCard = createCard()
        averageCard.setPadding(dp(8), dp(16), dp(8), dp(16))

        averageCard.addView(
            createLabel("RATA-RATA"),
            matchWrap().apply {
                bottomMargin = dp(12)
            }
        )

        averageText = createStatValue("0.0 km/j")
        averageCard.addView(averageText, matchWrap())

        statsRow.addView(
            averageCard,
            LinearLayout.LayoutParams(0, dp(110), 1f).apply {
                marginStart = dp(6)
            }
        )

        root.addView(
            statsRow,
            matchWrap().apply {
                bottomMargin = dp(12)
            }
        )

        // KARTU DURASI
        val durationCard = createCard()
        durationCard.setPadding(dp(10), dp(14), dp(10), dp(14))

        durationCard.addView(
            createLabel("DURASI PERJALANAN"),
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        durationText = createStatValue("00:00:00")
        durationText.textSize = 25f
        durationCard.addView(durationText, matchWrap())

        root.addView(
            durationCard,
            matchWrap().apply {
                bottomMargin = dp(12)
            }
        )

        // KARTU WAKTU BERGERAK DAN BERHENTI
        val timeRow = LinearLayout(this)
        timeRow.orientation = LinearLayout.HORIZONTAL
        timeRow.gravity = Gravity.CENTER
        timeRow.weightSum = 2f

        val movingCard = createCard()
        movingCard.setPadding(dp(6), dp(14), dp(6), dp(14))

        movingCard.addView(
            createLabel("WAKTU BERGERAK"),
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        movingText = createStatValue("00:00:00")
        movingText.textSize = 17f
        movingCard.addView(movingText, matchWrap())

        timeRow.addView(
            movingCard,
            LinearLayout.LayoutParams(0, dp(100), 1f).apply {
                marginEnd = dp(6)
            }
        )

        val stoppedCard = createCard()
        stoppedCard.setPadding(dp(6), dp(14), dp(6), dp(14))

        stoppedCard.addView(
            createLabel("WAKTU BERHENTI"),
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        stoppedText = createStatValue("00:00:00")
        stoppedText.textSize = 17f
        stoppedCard.addView(stoppedText, matchWrap())

        timeRow.addView(
            stoppedCard,
            LinearLayout.LayoutParams(0, dp(100), 1f).apply {
                marginStart = dp(6)
            }
        )

        root.addView(
            timeRow,
            matchWrap().apply {
                bottomMargin = dp(12)
            }
        )

        // KARTU KECEPATAN MAKSIMUM
        val maxSpeedCard = createCard()
        maxSpeedCard.setPadding(dp(10), dp(14), dp(10), dp(14))

        maxSpeedCard.addView(
            createLabel("KECEPATAN MAKSIMUM"),
            matchWrap().apply {
                bottomMargin = dp(8)
            }
        )

        maxSpeedText = createStatValue("0.0 km/j")
        maxSpeedText.textSize = 23f
        maxSpeedText.setTextColor(green)
        maxSpeedCard.addView(maxSpeedText, matchWrap())

        root.addView(
            maxSpeedCard,
            matchWrap().apply {
                bottomMargin = dp(18)
            }
        )

        // STATUS GPS
        statusText = TextView(this)
        statusText.text = "●  GPS SIAP DIGUNAKAN"
        statusText.textSize = 13f
        statusText.setTextColor(muted)
        statusText.gravity = Gravity.CENTER
        statusText.setPadding(dp(4), dp(8), dp(4), dp(8))

        root.addView(
            statusText,
            matchWrap().apply {
                bottomMargin = dp(14)
            }
        )

        // TOMBOL MULAI DAN BERHENTI
        startButton = Button(this)
        startButton.text = "▶  MULAI BERSEPEDA"
        startButton.textSize = 16f
        startButton.setTextColor(white)
        startButton.typeface = Typeface.DEFAULT_BOLD
        startButton.isAllCaps = false
        startButton.gravity = Gravity.CENTER
        startButton.minHeight = dp(64)
        startButton.minimumHeight = dp(64)
        startButton.setPadding(dp(12), dp(12), dp(12), dp(12))
        startButton.setBackgroundColor(green)

        startButton.setOnClickListener {
            if (tracking) {
                stopTracking()
            } else {
                startTracking()
            }
        }

        root.addView(
            startButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(68)
            ).apply {
                bottomMargin = dp(12)
            }
        )

        // KETERANGAN
        val info = TextView(this)
        info.text = "Pastikan GPS aktif sebelum mulai bersepeda."
        info.textSize = 12f
        info.setTextColor(muted)
        info.gravity = Gravity.CENTER
        info.setPadding(dp(4), dp(4), dp(4), dp(12))

        root.addView(info, matchWrap())

        // FOOTER WAJIB
        val footer = TextView(this)
        footer.text = "created by @sepedahankekantor V.1 2026"
        footer.textSize = 10f
        footer.setTextColor(muted)
        footer.gravity = Gravity.CENTER
        footer.setPadding(dp(4), dp(12), dp(4), dp(8))

        root.addView(footer, matchWrap())

        setContentView(scrollView)
    }

    private fun createCard(): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setBackgroundColor(cardColor)
        card.gravity = Gravity.CENTER
        return card
    }

    private fun createLabel(text: String): TextView {
        val view = TextView(this)
        view.text = text
        view.textSize = 11f
        view.setTextColor(muted)
        view.gravity = Gravity.CENTER
        view.typeface = Typeface.DEFAULT_BOLD
        view.letterSpacing = 0.05f
        return view
    }

    private fun createStatValue(text: String): TextView {
        val view = TextView(this)
        view.text = text
        view.textSize = 19f
        view.setTextColor(white)
        view.gravity = Gravity.CENTER
        view.typeface = Typeface.DEFAULT_BOLD
        return view
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
        val remainingSeconds = seconds % 60

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            hours,
            minutes,
            remainingSeconds
        )
    }

    private fun updateTimeStatistics() {
        durationText.text = formatDuration(totalElapsedTime)
        movingText.text = formatDuration(totalMovingTime)

        val stoppedTime =
            (totalElapsedTime - totalMovingTime).coerceAtLeast(0L)

        stoppedText.text = formatDuration(stoppedTime)

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
        maxSpeedKmh = 0.0
        currentSpeedKmh = 0.0
        isMoving = false

        lastLocation = null

        startTime = System.currentTimeMillis()
        lastTickTime = startTime
        tracking = true

        speedText.text = "0.0"
        distanceText.text = "0.00 km"
        averageText.text = "0.0 km/j"

        updateTimeStatistics()

        startButton.text = "■  BERHENTI BERSEPEDA"
        startButton.setTextColor(white)
        startButton.setBackgroundColor(Color.rgb(220, 65, 65))

        statusText.text = "●  MENCARI SINYAL GPS..."
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
        } catch (e: SecurityException) {
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

        // Perbarui durasi hingga saat tombol berhenti ditekan.
        if (tracking) {
            val now = System.currentTimeMillis()
            val deltaSeconds = ((now - lastTickTime) / 1000L)
                .coerceAtLeast(0L)

            totalElapsedTime = (now - startTime) / 1000L

            if (isMoving) {
                totalMovingTime += deltaSeconds
            }
        }

        tracking = false
        isMoving = false
        lastLocation = null

        timerHandler.removeCallbacks(timerRunnable)

        startButton.text = "▶  MULAI BERSEPEDA"
        startButton.setTextColor(white)
        startButton.setBackgroundColor(green)

        speedText.text = "0.0"

        statusText.text = "●  PERJALANAN SELESAI"
        statusText.setTextColor(muted)

        val averageKmh = if (totalMovingTime > 0L) {
            (totalDistance / totalMovingTime) * 3.6
        } else {
            0.0
        }

        averageText.text = String.format(
            Locale.US,
            "%.1f km/j",
            averageKmh
        )

        updateTimeStatistics()

        Toast.makeText(
            this,
            "Perjalanan selesai. Statistik tetap tersimpan di layar.",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onLocationChanged(location: Location) {

        if (!tracking) return

        if (location.accuracy > 30f) {
            statusText.text = "●  MENUNGGU GPS LEBIH AKURAT"
            statusText.setTextColor(muted)
            return
        }

        val previous = lastLocation

        var calculatedSpeedMps = 0.0

        if (previous != null) {
            val elapsedSeconds =
                (location.time - previous.time) / 1000L

            if (elapsedSeconds > 0L && elapsedSeconds <= 10L) {

                val distanceMeters =
                    previous.distanceTo(location).toDouble()

                calculatedSpeedMps = if (location.hasSpeed()) {
                    location.speed.toDouble()
                } else {
                    distanceMeters / elapsedSeconds
                }

                // Abaikan lonjakan GPS yang tidak masuk akal.
                if (distanceMeters <= 100.0) {
                    totalDistance += distanceMeters
                }
            }
        }

        lastLocation = location

        val speedMps = if (location.hasSpeed()) {
            location.speed.toDouble()
        } else {
            calculatedSpeedMps
        }

        currentSpeedKmh = (speedMps * 3.6).coerceAtLeast(0.0)

        // Gunakan ambang 1 m/s untuk membedakan bergerak dan berhenti.
        isMoving = speedMps >= 1.0

        if (currentSpeedKmh > maxSpeedKmh) {
            maxSpeedKmh = currentSpeedKmh
        }

        speedText.text = String.format(
            Locale.US,
            "%.1f",
            currentSpeedKmh
        )

        distanceText.text = String.format(
            Locale.US,
            "%.2f km",
            totalDistance / 1000.0
        )

        val averageKmh = if (totalMovingTime > 0L) {
            (totalDistance / totalMovingTime) * 3.6
        } else {
            0.0
        }

        averageText.text = String.format(
            Locale.US,
            "%.1f km/j",
            averageKmh
        )

        updateTimeStatistics()

        statusText.text = "●  GPS AKTIF · MEREKAM PERJALANAN"
        statusText.setTextColor(green)
    }

    override fun onProviderEnabled(provider: String) {
        if (tracking) {
            statusText.text = "●  GPS AKTIF"
            statusText.setTextColor(green)
        }
    }

    override fun onProviderDisabled(provider: String) {
        if (tracking) {
            statusText.text = "●  GPS TERPUTUS"
            statusText.setTextColor(orange)
            isMoving = false
        }
    }

    override fun onStatusChanged(
        provider: String?,
        status: Int,
        extras: Bundle?
    ) {
        // Tidak diperlukan untuk dashboard.
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == permissionCode) {
            if (hasLocationPermission()) {
                startTracking()
            } else {
                Toast.makeText(
                    this,
                    "Izin lokasi diperlukan untuk GPS.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Timer dan GPS tidak sengaja dihentikan saat aplikasi
        // berpindah ke latar belakang.
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerRunnable)

        if (tracking && hasLocationPermission()) {
            try {
                locationManager.removeUpdates(this)
            } catch (_: SecurityException) {
            }
        }

        super.onDestroy()
    }
}
