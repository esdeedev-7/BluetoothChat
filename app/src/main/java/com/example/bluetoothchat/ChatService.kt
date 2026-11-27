package com.example.bluetoothchat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Handles the Bluetooth Classic (RFCOMM) connection.
 * One phone calls listen(), the other calls connect(device).
 */
@SuppressLint("MissingPermission")
class ChatService(
    private val btAdapter: BluetoothAdapter,
    private val onMessage: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        // Any fixed UUID works, as long as both phones use the same one.
        val APP_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    private var server: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var out: DataOutputStream? = null

    /** Phone A: wait for another phone to connect. */
    @Synchronized
    fun listen() {
        disconnect()
        onStatus("Waiting for the other phone...")
        Thread {
            try {
                val srv = btAdapter.listenUsingRfcommWithServiceRecord("BluetoothChat", APP_UUID)
                synchronized(this) { server = srv }
                val s = srv.accept()          // blocks until a phone connects
                synchronized(this) {
                    server?.close()
                    server = null
                }
                startSocket(s)
            } catch (e: IOException) {
                onStatus("Listen stopped: ${e.message}")
            }
        }.start()
    }

    /** Phone B: connect to a paired phone that is hosting. */
    fun connect(device: BluetoothDevice) {
        disconnect()
        onStatus("Connecting to ${device.name ?: "device"}...")
        Thread {
            try {
                val s = device.createRfcommSocketToServiceRecord(APP_UUID)
                s.connect()                   // blocks until connected or failed
                startSocket(s)
            } catch (e: IOException) {
                onStatus("Connect failed. Is the other phone hosting? (${e.message})")
            }
        }.start()
    }

    private fun startSocket(s: BluetoothSocket) {
        synchronized(this) {
            disconnect()
            socket = s
            out = DataOutputStream(s.outputStream)
        }
        onStatus("Connected to ${s.remoteDevice.name ?: "device"}")

        // Read messages on a background thread.
        Thread {
            try {
                val input = DataInputStream(s.inputStream)
                while (true) {
                    onMessage(input.readUTF())   // readUTF handles message framing
                }
            } catch (e: IOException) {
                // Connection closed or lost.
            }
            synchronized(this) {
                if (socket === s) {
                    disconnect()
                    onStatus("Disconnected")
                }
            }
        }.start()
    }

    /** Sends a message. Returns false if there is no active connection. */
    fun send(text: String): Boolean {
        val stream = synchronized(this) { out } ?: return false
        return try {
            synchronized(this) {
                stream.writeUTF(text)
                stream.flush()
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    @Synchronized
    fun disconnect() {
        try { server?.close() } catch (e: IOException) { }
        try { socket?.close() } catch (e: IOException) { }
        server = null
        socket = null
        out = null
    }
}
