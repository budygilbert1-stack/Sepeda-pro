package com.sepedapro.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var speedText: TextView
    private lateinit var distanceText: TextView
    private lateinit var averageText: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        val startButton = Button(this).apply {
            text = "MULAI BERSEPEDA"
            setOnClickListener {
                statusText.text =
                    "Tampilan siap. GPS akan ditambahkan pada tahap berikutnya."
            }
        }

        layout.addView(title, matchWidth())
        addSpace(layout, 32)
        layout.addView(speedText, matchWidth())
        layout.addView(unitText, matchWidth())
        addSpace(layout, 28)
        layout.addView(distanceText, matchWidth())
        addSpace(layout, 12)
        layout.addView(averageText, matchWidth())
        addSpace(layout, 28)
        layout.addView(startButton, matchWidth())
        addSpace(layout, 16)
        layout.addView(statusText, matchWidth())

        setContentView(layout)
    }

    private fun matchWidth(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun addSpace(layout: LinearLayout, height: Int) {
        val space = android.view.View(this)
        layout.addView(
            space,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        )
    }
}
