package com.example.myapplication // Jeśli masz inny package, zachowaj swój.

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.Task
import android.view.View
import android.widget.ScrollView


class MainActivity : ComponentActivity() {

    private val client by lazy { Nearby.getConnectionsClient(this) }
    private val serviceId = "com.example.laya.TEST" // Oba telefony muszą używać tej samej nazwy.
    private val strategy = Strategy.P2P_CLUSTER
    private lateinit var status: TextView
    private lateinit var input: EditText
    private var connectedPeer: String? = null


    private lateinit var errorLog: TextView
    private lateinit var errorScroll: ScrollView
    private lateinit var messageLog: TextView
    private lateinit var messageScroll: ScrollView

    private fun timestamp(): String =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())

    private fun addError(text: String) {
        errorLog.append("[${timestamp()}] $text\n")
        errorScroll.post { errorScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addMessage(text: String) {
        messageLog.append("[${timestamp()}] $text\n")
        messageScroll.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private val permissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION, // Wymagane przez Nearby na Twojej konfiguracji.
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
            Build.VERSION.SDK_INT >= 31 -> arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION, // Uprawnienie brakujące przy błędzie 8034.
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
                Manifest.permission.BLUETOOTH_SCAN
            )
            else -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }

        // Log błędów — około 5 wierszy
        layout.addView(TextView(this).apply {
            text = "Log błędów"
            textSize = 14f
        })

        errorLog = TextView(this).apply {
            textSize = 12f
            setPadding(8, 4, 8, 4)
        }
        errorScroll = ScrollView(this).apply {
            addView(errorLog)
            isFillViewport = true
        }
        layout.addView(errorScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (100 * resources.displayMetrics.density).toInt()
        ))

        // Funkcja tworząca przyciski
        fun addButton(label: String, action: () -> Unit) {
            layout.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 })
        }

        // Status aplikacji
        status = TextView(this).apply {
            text = "Laya: gotowa do testu"
            textSize = 18f
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(status)

        // Przyciski połączenia
        addButton("Udostępniaj urządzenie") {
            withPermissions { advertise() }
        }
        addButton("Szukaj urządzeń") {
            withPermissions { discover() }
        }

        // Pole wiadomości i przycisk wysyłania
        input = EditText(this).apply {
            hint = "Wpisz wiadomość"
            textSize = 16f
        }
        layout.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        addButton("Wyślij wiadomość") { sendMessage() }

        // Log wiadomości — zajmuje pozostałe miejsce
        layout.addView(TextView(this).apply {
            text = "Wiadomości"
            textSize = 14f
        })

        messageLog = TextView(this).apply {
            textSize = 14f
            setPadding(8, 4, 8, 4)
        }
        messageScroll = ScrollView(this).apply {
            addView(messageLog)
        }
        layout.addView(messageScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(layout)
    }



    private fun withPermissions(action: () -> Unit) {
        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) action()
        else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 1 && results.isNotEmpty() &&
            results.all { it == PackageManager.PERMISSION_GRANTED }
        ) status.text = "Uprawnienia przyznane. Kliknij ponownie przycisk."
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let {
                runOnUiThread { addMessage("Odebrano: ${String(it, Charsets.UTF_8)}") }
            }
        }

        override fun onPayloadTransferUpdate(
            endpointId: String, update: PayloadTransferUpdate
        ) = Unit
    }

    private val connectionLifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            // Testowo akceptujemy połączenie; w prawdziwej aplikacji potwierdzaj tożsamość.
            client.acceptConnection(id, payloadCallback)
            runOnUiThread { status.text = "Łączenie z ${info.endpointName}..." }
        }

        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            runOnUiThread {
                if (result.status.isSuccess) {
                    connectedPeer = id
                    status.text = "Połączono z urządzeniem!"
                } else status.text = "Połączenie nieudane: ${result.status.statusCode}"
            }
        }

        override fun onDisconnected(id: String) {
            if (connectedPeer == id) connectedPeer = null
            runOnUiThread { status.text = "Rozłączono" }
        }
    }

    private fun advertise() {
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()
        client.startAdvertising("Laya-${Build.MODEL}", serviceId, connectionLifecycle, options)
            .addOnSuccessListener { status.text = "Udostępniam urządzenie..." }
            .addOnFailureListener {  addError("Błąd reklamowania: ${it.message}") }
    }

    private fun discover() {

        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        client.startDiscovery(serviceId, object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
                status.text = "Znaleziono ${info.endpointName}; łączę..."
                client.requestConnection("Laya-${Build.MODEL}", id, connectionLifecycle)
                    .addOnFailureListener {
                        status.text = "Błąd połączenia: ${it.message}"
                    }
            }

            override fun onEndpointLost(id: String) {
                status.text = "Urządzenie zniknęło z zasięgu"
            }
        }, options).addOnSuccessListener {
            status.text = "Szukam urządzeń..."
        }.addOnFailureListener {
            status.text = "Błąd wyszukiwania: ${it.message}"
        }
    }

    private fun sendMessage() {
        val peer = connectedPeer
        if (peer == null) {
            status.text = "Najpierw połącz się z drugim urządzeniem"
            return
        }
        val bytes = input.text.toString().toByteArray(Charsets.UTF_8)
        client.sendPayload(peer, Payload.fromBytes(bytes))
            .addOnSuccessListener { status.text = "Wysłano wiadomość" }
            .addOnFailureListener { status.text = "Błąd wysyłania: ${it.message}" }
    }

    override fun onDestroy() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        super.onDestroy()
    }
}

private val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem()
        .displayMetrics.density).toInt()
