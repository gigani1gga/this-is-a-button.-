package com.example.thisisabutton

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val label = TextView(this).apply {
            text = "This is a button"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val button = Button(this).apply {
            text = "Button"
            setOnClickListener { overloadAndCrash() }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(label)
            addView(button)
        })
    }

    /** Overloads CPU + memory *inside this app's process*, then crashes the app. */
    private fun overloadAndCrash() {
        // CPU: busy-loop threads, 4x the number of cores
        repeat(Runtime.getRuntime().availableProcessors() * 4) {
            Thread {
                var x = 0.0
                while (true) { x += Math.sqrt(x + 1.0) * Math.sin(x) }
            }.start()
        }

        // Memory: allocate until the app runs out, then crash on purpose
        Thread {
            val hog = ArrayList<ByteArray>()
            try {
                while (true) hog.add(ByteArray(10 * 1024 * 1024))
            } catch (e: OutOfMemoryError) {
                hog.clear()
                throw RuntimeException("This is a button. It crashed.")
            }
        }.start()

        // UI thread: freeze the screen too
        while (true) { Math.random() }
    }
}
