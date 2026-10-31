package com.dopavin

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Crash report screen. Plain Android views on purpose: it runs in its own process and must not depend
 * on Compose, the theme store or anything else that may be what crashed.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = runCatching { CrashReporter.pending(this) }.getOrNull() ?: "No report was saved."
        // Shown now, so it is not shown again on the next start.
        runCatching { CrashReporter.clear(this) }
        try {
            buildScreen(report)
        } catch (t: Throwable) {
            // The nice screen failed: show the report and this error as plain text, nothing else.
            val sw = java.io.StringWriter().also { t.printStackTrace(java.io.PrintWriter(it)) }
            setContentView(
                ScrollView(this).apply {
                    addView(
                        TextView(this@CrashActivity).apply {
                            text = "Dopavin crash report\n\n$report\n\n--- crash screen error ---\n$sw"
                            setTextIsSelectable(true)
                            setPadding(24, 48, 24, 24)
                        },
                    )
                },
            )
        }
    }

    private fun buildScreen(report: String) {
        runCatching {
            window.statusBarColor = BG
            window.navigationBarColor = BG
        }

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(pad, pad * 2, pad, pad)
        }
        root.addView(label(getString(R.string.crash_title), 26f, Color.WHITE, bold = true))
        root.addView(label(getString(R.string.crash_body), 15f, 0xFFB8B8C0.toInt()).apply { setPadding(0, pad / 2, 0, pad) })

        val box = TextView(this).apply {
            text = report
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFFE6E6EA.toInt())
            setTextIsSelectable(true)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(0xFF16161D.toInt())
                cornerRadius = 14f * resources.displayMetrics.density
            }
        }
        root.addView(
            ScrollView(this).apply { addView(box) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val row = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, pad, 0, 0) }
        row.addView(button(getString(R.string.crash_copy)) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Dopavin crash report", report))
            Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
        })
        row.addView(button(getString(R.string.crash_share)) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
            startActivity(Intent.createChooser(send, getString(R.string.crash_share)))
        })
        row.addView(button(getString(R.string.crash_restart), accent = true) {
            packageManager.getLaunchIntentForPackage(packageName)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            finish()
        })
        root.addView(row)
        setContentView(root)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(text: String, accent: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(if (accent) 0xFFE5533D.toInt() else 0xFF2A2A33.toInt())
            cornerRadius = 100f * resources.displayMetrics.density
        }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = 6; marginEnd = 6 }
        setOnClickListener { onClick() }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() = finish()

    private companion object {
        val BG = 0xFF0B0B10.toInt()
    }
}
