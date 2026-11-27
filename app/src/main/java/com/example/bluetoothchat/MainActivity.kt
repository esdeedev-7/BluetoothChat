package com.example.bluetoothchat

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
    private lateinit var chatLog: TextView
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

        val manager = getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
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

    // ---------- UI ----------

    private fun buildUi(): LinearLayout {
        val pad = (16 * resources.displayMetrics.density).toInt()

        statusText = TextView(this).apply {
            text = "Status: idle"
            textSize = 16f
        }

        val hostButton = Button(this).apply {
            text = "Host: wait for the other phone"
            setOnClickListener { chatService.listen() }
        }

        val refreshButton = Button(this).apply {
            text = "Refresh paired devices"
            setOnClickListener { requestPermissionsIfNeeded() }
        }

        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        val deviceList = ListView(this).apply {
            this.adapter = deviceListAdapter
            setOnItemClickListener { _, _, position, _ ->
                chatService.connect(pairedDevices[position])
            }
        }

        chatLog = TextView(this).apply { textSize = 15f }
        chatScroll = ScrollView(this).apply { addView(chatLog) }

        messageInput = EditText(this).apply { hint = "Type a message" }
        val sendButton = Button(this).apply {
            text = "Send"
            setOnClickListener { sendMessage() }
        }
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(messageInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(sendButton)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(statusText)
            addView(hostButton)
            addView(refreshButton)
            addView(deviceList, weighted(1f))
            addView(chatScroll, weighted(2f))
            addView(inputRow)
        }
    }

    private fun weighted(weight: Float) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, weight
    )

    private fun appendChat(line: String) {
        chatLog.append("$line\n")
        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        if (text.isEmpty()) return
        if (chatService.send(text)) {
            appendChat("Me: $text")
            messageInput.setText("")
        } else {
            setStatus("Not connected yet")
        }
    }

    // Called from background threads, so switch to the UI thread.
    private fun setStatus(text: String) = runOnUiThread { statusText.text = "Status: $text" }

    private fun onMessage(text: String) = runOnUiThread { appendChat("Them: $text") }

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
        setStatus("Host on one phone, then tap its name on the other")
    }
}
