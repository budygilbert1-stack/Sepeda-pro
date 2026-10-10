package com.sepedapro.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.content.Context
import android.os.Looper
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 24, 28, 24)
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        val title = TextView(this).apply {
            text = "SEPEDA PRO"
            textSize = 28f
            setTextColor(Color.rgb(30, 90, 60))
            gravity = Gravity.CENTER
        }

        speedText = TextView(this).apply {
            text = "0.0"
            textSize = 76f
            setTextColor(Color.rgb(30, 90, 60))
            gravity = Gravity.CENTER
        }

        val unitText = TextView(this).apply {
            text = "km/jam"
            textSize = 20f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }

        distanceText = TextView(this).apply {
            text = "Jarak: 0.00 km"
            textSize = 20f
            setTextColor(Color.BLACK)
        }

        averageText = TextView(this).apply {
            text = "Rata-rata: 0.0 km/jam"
            textSize = 20f
            setTextColor(Color.BLACK)
        }

        statusText = TextView(this).apply {
            text = "Siap memulai perjalanan"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }

        startButton = Button(this).apply {
            text = "MULAI BERSEPEDA"
            setOnClickListener {
                if (tracking) {
                    stopTracking()
                } else {
                    checkPermissionAndStart()
                }
            }
        }

        layout.addView(title, matchWidth())
        addSpace(layout, 24)
        layout.addView(speedText, matchWidth())
        layout.addView(unitText, matchWidth())
        addSpace(layout, 24)
        layout.addView(distanceText, matchWidth())
        addSpace(layout, 12)
        layout.addView(averageText, matchWidth())
        addSpace(layout, 24)
        layout.addView(startButton, matchWidth())
        addSpace(layout, 16)
        layout.addView(statusText, matchWidth())

        setContentView(layout)
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
        distanceText.text = "Jarak: 0.00 km"
        averageText.text = "Rata-rata: 0.0 km/jam"

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                1f,
                this,
                Looper.getMainLooper()
            )

            tracking = true
            startButton.text = "BERHENTI"
            statusText.text = "Mencari sinyal GPS..."

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

            // Abaikan perpindahan yang terlalu besar atau tidak wajar.
            if (
                distance in 0f..100f &&
                location.accuracy <= 30f &&
                previous.accuracy <= 30f
            ) {
                totalDistance += distance

                // Hitung waktu bergerak ketika kecepatan minimal 1 km/jam.
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
            "Jarak: %.2f km",
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
            "Rata-rata: %.1f km/jam",
            averageSpeed
        )

        statusText.text = String.format(
            Locale.getDefault(),
            "GPS aktif • Akurasi %.0f meter",
            location.accuracy
        )
    }

    private fun stopTracking() {
        locationManager.removeUpdates(this)
        tracking = false
        startButton.text = "MULAI BERSEPEDA"
        speedText.text = "0.0"
        statusText.text = "Perjalanan selesai"

        Toast.makeText(
            this,
            "Perekaman perjalanan dihentikan",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) {
            statusText.text = "GPS dimatikan"
        }
    }

    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER && tracking) {
            statusText.text = "GPS aktif, mencari lokasi..."
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
        // Versi awal ini belum mendukung perekaman saat aplikasi ditutup.
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
