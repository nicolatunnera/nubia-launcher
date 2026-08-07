package com.nubia.launcher

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Mostra l'ultimo crash log (testo selezionabile + pulsante Copia). */
class CrashReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, "crash.log")
        val text = if (file.exists()) {
            val t = runCatching { file.readText() }.getOrNull().orEmpty()
            file.delete()
            t
        } else {
            "Nessun errore registrato."
        }

        val title = TextView(this).apply {
            this.text = "Errore all'avvio"
            textSize = 20f
            setPadding(dp(16), dp(16), dp(16), 0)
        }

        val hint = TextView(this).apply {
            this.text = "Tocca Copia e incolla il testo qui sotto."
            textSize = 13f
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        val tv = TextView(this).apply {
            this.text = text
            setTextIsSelectable(true)
            textSize = 13f
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val scroll = ScrollView(this).apply { addView(tv) }

        val copy = Button(this).apply {
            this.text = "Copia"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", text))
                Toast.makeText(this@CrashReportActivity, "Errore copiato", Toast.LENGTH_SHORT).show()
            }
        }
        val close = Button(this).apply {
            this.text = "Chiudi"
            setOnClickListener { finish() }
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(close, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(hint, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()
}
