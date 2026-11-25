package com.chartbot.ai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var apiKeyInput: EditText
    private lateinit var statusText: TextView

    companion object {
        private const val REQ_OVERLAY = 101
        private const val REQ_CAPTURE = 102
        private const val REQ_NOTIF = 103
        const val PREFS_NAME = "chart_bot_prefs"
        const val KEY_API = "gemini_api_key"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(48, 64, 48, 48)
        }

        val title = TextView(this).apply {
            text = "🤖 ১-মিনিট চার্ট AI বট"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        val desc = TextView(this).apply {
            text = "বট চালু করলে স্ক্রিনের কোণায় ভাসমান আইকন আসবে। ক্লিক করলেই সে বলবে 'আমাকে ট্রেডিং চার্ট দেখাও' এবং চার্ট দেখে ভয়েসে BUY/SELL বলবে।"
            textSize = 14f
            setTextColor(Color.parseColor("#D1D5DB"))
            setPadding(0, 0, 0, 32)
        }

        val keyLabel = TextView(this).apply {
            text = "Gemini API Key (Google AI Studio থেকে নিন):"
            textSize = 14f
            setTextColor(Color.parseColor("#93C5FD"))
        }

        apiKeyInput = EditText(this).apply {
            hint = "AIzaSy..."
            setText(prefs.getString(KEY_API, ""))
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#1F2937"))
            setPadding(28, 28, 28, 28)
        }

        val btnOverlay = Button(this).apply {
            text = "১. ভাসমান ওভারলে অনুমতি দিন"
            setBackgroundColor(Color.parseColor("#374151"))
            setTextColor(Color.WHITE)
            setOnClickListener { requestOverlayPermission() }
        }

        val btnStart = Button(this).apply {
            text = "২. ভাসমান বট চালু করুন ▶"
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.WHITE)
            setOnClickListener { startBotFlow() }
        }

        val btnStop = Button(this).apply {
            text = "বট বন্ধ করুন ⏹"
            setBackgroundColor(Color.parseColor("#EF4444"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                stopService(Intent(this@MainActivity, BotService::class.java))
                statusText.text = "স্ট্যাটাস: বট বন্ধ করা হয়েছে।"
            }
        }

        statusText = TextView(this).apply {
            text = "স্ট্যাটাস: প্রস্তুত"
            textSize = 14f
            setTextColor(Color.parseColor("#34D399"))
            setPadding(0, 32, 0, 0)
        }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 16, 0, 16) }

        root.addView(title)
        root.addView(desc)
        root.addView(keyLabel)
        root.addView(apiKeyInput, lp)
        root.addView(btnOverlay, lp)
        root.addView(btnStart, lp)
        root.addView(btnStop, lp)
        root.addView(statusText)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#111827"))
            addView(root)
        }
        setContentView(scroll)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            }
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "ওভারলে অনুমতি আগেই দেওয়া আছে!", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQ_OVERLAY)
    }

    private fun startBotFlow() {
        val key = apiKeyInput.text.toString().trim()
        if (key.isEmpty()) {
            Toast.makeText(this, "আগে আপনার Gemini API Key বসান!", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API, key)
            .apply()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "আগে ১ নম্বর বাটন থেকে ওভারলে অনুমতি দিন!", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val svcIntent = Intent(this, BotService::class.java).apply {
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                }
                startForegroundService(svcIntent)
                statusText.text = "স্ট্যাটাস: বট চালু! এখন আপনার ট্রেডিং চার্ট ওপেন করুন।"
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "স্ক্রিন দেখার অনুমতি দেওয়া হয়নি!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
