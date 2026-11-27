package com.example.bluetoothchat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btAdapter: BluetoothAdapter
    private lateinit var chatService: ChatService
    private lateinit var statusText: TextView
    private lateinit var messagesBox: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var messageInput: EditText
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val pairedDevices = mutableListOf<BluetoothDevice>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) loadPairedDevices()
        else setStatus("Bluetooth permission was denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null) {
            Toast.makeText(this, "This phone has no Bluetooth", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        btAdapter = adapter
        chatService = ChatService(btAdapter, ::onMessage, ::setStatus)

        setContentView(buildUi())
        requestPermissionsIfNeeded()
    }

    override fun onDestroy() {
        if (::chatService.isInitialized) chatService.disconnect()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // UI 

    private fun buildUi(): LinearLayout {
        statusText = TextView(this).apply {
            text = "Not connected"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            setPadding(dp(12), dp(14), dp(12), dp(14))
            setBackgroundColor(Color.parseColor("#F6F6F6"))
        }

        val hostButton = Button(this).apply {
            text = "Host"
            setOnClickListener { chatService.listen() }
        }
        val refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener { requestPermissionsIfNeeded() }
        }
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(hostButton, rowWeighted(1f))
            addView(refreshButton, rowWeighted(1f))
        }

        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        val deviceList = ListView(this).apply {
            adapter = deviceListAdapter
            setOnItemClickListener { _, _, position, _ ->
                chatService.connect(pairedDevices[position])
            }
        }

        messagesBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        chatScroll = ScrollView(this).apply { addView(messagesBox) }

        messageInput = EditText(this).apply {
            hint = "Message"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#C7C7CC"))
                setColor(Color.WHITE)
            }
        }
        val sendButton = TextView(this).apply {
            text = "↑"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#D97757"))
            }
            setOnClickListener { sendMessage() }
        }
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.parseColor("#F6F6F6"))
            addView(messageInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            })
            addView(sendButton, LinearLayout.LayoutParams(dp(36), dp(36)))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(statusText)
            addView(buttonRow)
            addView(deviceList, columnWeighted(1f))
            addView(chatScroll, columnWeighted(3f))
            addView(inputRow)
        }
    }

    private fun rowWeighted(weight: Float) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)

    private fun columnWeighted(weight: Float) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight)

    // Adds one chat bubble: orange on the right for me, gray on the left for them.
    private fun addBubble(text: String, mine: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(if (mine) Color.WHITE else Color.BLACK)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            maxWidth = (resources.displayMetrics.widthPixels * 0.7).toInt()
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (mine) Color.parseColor("#D97757") else Color.parseColor("#E9E9EB"))
            }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (mine) Gravity.END else Gravity.START
            setPadding(0, dp(3), 0, dp(3))
            addView(bubble)
        }
        messagesBox.addView(row)
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return
        if (chatService.send(text)) {
            addBubble(text, mine = true)
            messageInput.setText("")
        } else {
            setStatus("Not connected yet")
        }
    }

    // Called from background threads, so switch to the UI thread.
    private fun setStatus(text: String) = runOnUiThread { statusText.text = text }

    private fun onMessage(text: String) = runOnUiThread { addBubble(text, mine = false) }

    // ---------- Permissions and paired devices ----------

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionsIfNeeded() {
        if (hasPermissions()) loadPairedDevices()
        else permissionLauncher.launch(requiredPermissions())
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        if (!hasPermissions() || !btAdapter.isEnabled) {
            setStatus("Turn on Bluetooth and allow permissions")
            return
        }
        pairedDevices.clear()
        pairedDevices.addAll(btAdapter.bondedDevices)
        deviceListAdapter.clear()
        deviceListAdapter.addAll(pairedDevices.map { "${it.name ?: "Unknown"}  ${it.address}" })
        setStatus("Tap Host on one phone, then tap its name on the other")
    }
}
