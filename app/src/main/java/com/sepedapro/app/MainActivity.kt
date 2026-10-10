package com.sepedapro.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.content.Context
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity(), LocationListener {

    private lateinit var speedText: TextView
    private lateinit var distanceText: TextView
    private lateinit var averageText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var locationManager: LocationManager

    private var tracking = false
    private var totalDistance = 0f
    private var totalMovingTime = 0L
    private var lastLocation: Location? = null
    private var lastMovingTime = 0L

    private val locationPermissionCode = 1001

    private val green = Color.rgb(39, 174, 96)
    private val dark = Color.rgb(18, 25, 22)
    private val cardColor = Color.rgb(31, 41, 36)
    private val backgroundColor = Color.rgb(13, 18, 15)
    private val muted = Color.rgb(170, 185, 176)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 12)
            setBackgroundColor(backgroundColor)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "SEPEDA PRO"
            textSize = 27f
            setTextColor(green)
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }

        val subtitle = TextView(this).apply {
            text = "RIDE • TRACK • EXPLORE"
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
        }

        header.addView(title, matchWidth())
        header.addView(subtitle, matchWidth())
        root.addView(header, matchWidth())
        addSpace(root, 24)

        val speedCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 26, 16, 26)
            setBackgroundColor(cardColor)
        }

        val speedLabel = TextView(this).apply {
            text = "KECEPATAN SAAT INI"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
            letterSpacing = 0.1f
        }

        speedText = TextView(this).apply {
            text = "0.0"
            textSize = 82f
            setTextColor(white)
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }

        val speedUnit = TextView(this).apply {
            text = "KM/JAM"
            textSize = 15f
            setTextColor(green)
            gravity = Gravity.CENTER
            letterSpacing = 0.15f
        }

        speedCard.addView(speedLabel, matchWidth())
        speedCard.addView(speedText, matchWidth())
        speedCard.addView(speedUnit, matchWidth())

        root.addView(
            speedCard,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(root, 18)

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val distanceCard = createStatCard(
            "JARAK TEMPUH",
            "0.00 km"
        )
        distanceText = distanceCard.second

        val averageCard = createStatCard(
            "KECEPATAN RATA-RATA",
            "0.0 km/jam"
        )
        averageText = averageCard.second

        val statParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )

        statParams.marginEnd = 7
        stats.addView(distanceCard.first, statParams)

        val secondParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
        secondParams.marginStart = 7
        stats.addView(averageCard.first, secondParams)

        root.addView(stats, matchWidth())
        addSpace(root, 22)

        startButton = Button(this).apply {
            text = "▶  MULAI BERSEPEDA"
            textSize = 15f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(green)
            typeface = Typeface.DEFAULT_BOLD

            setOnClickListener {
                if (tracking) {
                    stopTracking()
                } else {
                    checkPermissionAndStart()
                }
            }
        }

        root.addView(
            startButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                58
            )
        )

        addSpace(root, 14)

        statusText = TextView(this).apply {
            text = "●  SIAP MEMULAI PERJALANAN"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }

        root.addView(statusText, matchWidth())

        val spacer = View(this)
        root.addView(
            spacer,
            LinearLayout.LayoutParams(
                1,
                0,
                1f
            )
        )

        val footer = TextView(this).apply {
            text = "created by @sepedahankekantor"
            textSize = 10f
            setTextColor(Color.rgb(120, 140, 128))
            gravity = Gravity.CENTER
        }

        root.addView(footer, matchWidth())

        setContentView(root)
    }

    private fun createStatCard(
        label: String,
        initialValue: String
    ): Pair<LinearLayout, TextView> {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 20, 8, 20)
            setBackgroundColor(cardColor)
        }

        val labelText = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }

        val valueText = TextView(this).apply {
            text = initialValue
            textSize = 18f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        card.addView(labelText, matchWidth())
        addSpace(card, 10)
        card.addView(valueText, matchWidth())

        return Pair(card, valueText)
    }

    private fun checkPermissionAndStart() {
        if (
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                locationPermissionCode
            )
            return
        }

        startTracking()
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

        if (requestCode == locationPermissionCode) {
            if (grantResults.any {
                    it == PackageManager.PERMISSION_GRANTED
                }) {
                startTracking()
            } else {
                statusText.text = "Izin lokasi diperlukan untuk GPS"
                Toast.makeText(
                    this,
                    "Izinkan akses lokasi untuk menggunakan GPS",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startTracking() {
        if (
            !locationManager.isProviderEnabled(
                LocationManager.GPS_PROVIDER
            )
        ) {
            statusText.text = "Aktifkan GPS HP terlebih dahulu"
            Toast.makeText(
                this,
                "Silakan aktifkan Lokasi/GPS di HP",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        totalDistance = 0f
        totalMovingTime = 0L
        lastLocation = null
        lastMovingTime = 0L

        speedText.text = "0.0"
        distanceText.text = "0.00 km"
        averageText.text = "0.0 km/jam"

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                this,
                Looper.getMainLooper()
            )

            tracking = true
            startButton.text = "■  BERHENTI"
            statusText.text = "●  MENCARI SINYAL GPS"
            startButton.setBackgroundColor(Color.rgb(192, 57, 43))

        } catch (e: SecurityException) {
            statusText.text = "Izin GPS belum diberikan"
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!tracking) return

        val previous = lastLocation
        val now = System.currentTimeMillis()

        if (previous != null) {
            val distance = previous.distanceTo(location)

            if (
                distance in 0f..100f &&
                location.accuracy <= 30f &&
                previous.accuracy <= 30f
            ) {
                totalDistance += distance

                val speedKmh = if (location.hasSpeed()) {
                    location.speed * 3.6f
                } else if (distance > 0f) {
                    distance /
                        ((now - lastMovingTime).coerceAtLeast(1000L) / 1000f) *
                        3.6f
                } else {
                    0f
                }

                if (speedKmh >= 1f && lastMovingTime > 0L) {
                    totalMovingTime += now - lastMovingTime
                }
            }
        }

        lastLocation = location
        lastMovingTime = now

        val currentSpeed = if (location.hasSpeed()) {
            location.speed * 3.6f
        } else {
            0f
        }

        speedText.text = String.format(
            Locale.getDefault(),
            "%.1f",
            currentSpeed
        )

        distanceText.text = String.format(
            Locale.getDefault(),
            "%.2f km",
            totalDistance / 1000f
        )

        val averageSpeed = if (totalMovingTime > 0L) {
            (totalDistance / 1000f) /
                (totalMovingTime / 3600000f)
        } else {
            0f
        }

        averageText.text = String.format(
            Locale.getDefault(),
            "%.1f km/jam",
            averageSpeed
        )

        statusText.text = String.format(
            Locale.getDefault(),
            "●  GPS AKTIF • AKURASI %.0f M",
            location.accuracy
        )
        statusText.setTextColor(green)
    }

    private fun stopTracking() {
        locationManager.removeUpdates(this)
        tracking = false
        startButton.text = "▶  MULAI BERSEPEDA"
        startButton.setBackgroundColor(green)
        speedText.text = "0.0"
        statusText.text = "●  PERJALANAN SELESAI"
        statusText.setTextColor(muted)

        Toast.makeText(
            this,
            "Perekaman perjalanan dihentikan",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            statusText.text = "GPS DIMATIKAN"
            statusText.setTextColor(Color.RED)
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER && tracking) {
            statusText.text = "●  GPS AKTIF, MENCARI LOKASI"
        }
    }

    override fun onStatusChanged(
        provider: String?,
        status: Int,
        extras: Bundle?
    ) {
        // Tidak diperlukan untuk versi ini.
    }

    override fun onPause() {
        super.onPause()
        // Perekaman saat layar mati akan dikembangkan pada tahap berikutnya.
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
    }

    private fun matchWidth(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun addSpace(layout: LinearLayout, height: Int) {
        val space = View(this)
        layout.addView(
            space,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        )
    }
}

