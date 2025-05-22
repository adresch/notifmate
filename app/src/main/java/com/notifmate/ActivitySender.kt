package com.notifmate

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notifmate.adapter.BluetoothDeviceAdapter
import com.notifmate.adapter.ExcludedAppsAdapter
import com.notifmate.helper.CustomUtils
import com.notifmate.service.NotifMateNotificationHandlerService
import com.google.android.material.card.MaterialCardView
import com.notifmate.R
import com.notifmate.helper.MapsShortLinkResolver
import com.notifmate.helper.NotificationListener.Companion.ACTION_MEDIA_UPDATED
import com.notifmate.helper.resolveMapsShortLink
import com.notifmate.model.NotifMateActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.regex.Pattern

class ActivitySender : NotifMateActivity() {

    private var excludeAppsDialog: AlertDialog? = null

    companion object {
        const val ACTION_COORDINATES_SHARED = "com.notifmate.COORDINATES_SHARED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sender)

        handleIncomingIntent(intent)

        val selectReceiver = findViewById<MaterialCardView>(R.id.selectReceiver)
        val excludeApps = findViewById<MaterialCardView>(R.id.excludeApps)
        val senderStop = findViewById<MaterialCardView>(R.id.senderStop)
        val settings = findViewById<ImageView>(R.id.settings)
        val receiverName = findViewById<TextView>(R.id.receiverName)

        var prefDeviceName = CustomUtils.getReceiverNamePreference(this)
        var prefDeviceAddress = CustomUtils.getReceiverAddressPreference(this)

        if (prefDeviceName.isNotEmpty() && prefDeviceAddress.isNotEmpty()) {
            receiverName.text = prefDeviceName
        }

        Log.d("MYDEBUG", "Global BroadcastReceiver Registered in ActivitySender")

        selectReceiver.setOnClickListener {
            val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!bluetoothAdapter.isEnabled) {
                Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices

            if (pairedDevices.isNullOrEmpty()) {
                Toast.makeText(this, "No paired devices found", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val deviceList = pairedDevices.toList()

            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bluetooth_list, null)
            val recyclerView: RecyclerView = dialogView.findViewById(R.id.recyclerView)

            recyclerView.layoutManager = LinearLayoutManager(this)

            val dialog = AlertDialog.Builder(this)
                .setTitle("Select Bluetooth Device")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .create()

            val adapter = BluetoothDeviceAdapter(deviceList) { selectedDevice ->
                Log.i("MYDEBUG", "Selected device: ${selectedDevice.name}")
                Toast.makeText(this, "Selected: ${selectedDevice.name}", Toast.LENGTH_SHORT).show()

                prefDeviceName = selectedDevice.name
                prefDeviceAddress = selectedDevice.address
                CustomUtils.saveReceiverNamePreference(this, prefDeviceName)
                CustomUtils.saveReceiverAddressPreference(this, prefDeviceAddress)

                val intent = Intent("com.notifmate.UPDATE_RECEIVER_ADDRESS")
                sendBroadcast(intent)

                receiverName.text = prefDeviceName

                dialog.dismiss()
            }

            recyclerView.adapter = adapter
            dialog.show()
        }

        excludeApps.setOnClickListener {
            Log.i("MYDEBUG", "excludeApps clicked")
            showExcludeAppsDialog()
        }

        settings.setOnClickListener {
            Log.i("MYDEBUG", "Settings clicked")
            val intent = Intent(this, ActivitySettingsSender::class.java)
            startActivity(intent)
        }

        val serviceIntent = Intent(this, NotifMateNotificationHandlerService::class.java)
        serviceIntent.putExtra("caller_activity", this::class.java.simpleName)
        senderStop.setOnClickListener {
            Log.i("MYDEBUG", "senderStop clicked")
            CustomUtils.saveAppScreenPreference(this, "")
            stopService(serviceIntent)
            onBackPressedDispatcher.onBackPressed()
        }

        startService(serviceIntent)
    }

    private fun showExcludeAppsDialog() {
        CustomUtils.showLoader(this) // Show the loading dialog before fetching apps

        lifecycleScope.launch(Dispatchers.IO) {  // Use lifecycleScope here
            val apps = CustomUtils.getInstalledApps(this@ActivitySender) // Fetch installed apps

            withContext(Dispatchers.Main) {
                CustomUtils.stopLoader() // Stop loader once data is fetched

                val dialogView = layoutInflater.inflate(R.layout.exclude_apps_dialog, null)
                val recyclerView = dialogView.findViewById<RecyclerView>(R.id.apps_recycler_view)

                recyclerView.layoutManager = LinearLayoutManager(this@ActivitySender)
                recyclerView.adapter = ExcludedAppsAdapter(this@ActivitySender, apps)

                excludeAppsDialog = AlertDialog.Builder(this@ActivitySender)
                    .setTitle(getString(R.string.excludeApps))
                    .setView(dialogView)
                    .setPositiveButton("Close", null)
                    .create()

                excludeAppsDialog?.setOnDismissListener {
                    CustomUtils.stopLoader() // Ensure loader is stopped when dialog is dismissed
                    val intent = Intent("com.notifmate.UPDATE_EXCLUDED_APP_LIST")
                    sendBroadcast(intent)
                }

                excludeAppsDialog?.show()
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
    }

    private fun handleIncomingIntent(intent: Intent) {
        val extraText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val lower = extraText.lowercase()

        Log.d("MYDEBUG", "Shared Text or Data: $extraText")

        when {
            lower.contains("maps.app.goo.gl") -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    val result = MapsShortLinkResolver.resolve(this@ActivitySender, extraText)
                    if (result != null) {
                        val (lat, lon, name) = result
                        sendLocationBroadcast(lat, lon, name)
                    } else {
                        Toast.makeText(this@ActivitySender, "Could not resolve location", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            lower.contains("geo:") -> {
                val geoMatch = Regex("""geo:([-\d.]+),([-\d.]+)""").find(lower)
                if (geoMatch != null) {
                    val lat = geoMatch.groupValues[1].toDouble()
                    val lon = geoMatch.groupValues[2].toDouble()
                    val name = extraText.lines().firstOrNull() ?: "Geo Location"
                    sendLocationBroadcast(lat, lon, name)
                } else {
                    Toast.makeText(this, "Invalid geo: URI", Toast.LENGTH_SHORT).show()
                }
            }

            lower.contains("osmand.net/map?pin=") -> {
                val pinMatch = Regex("""pin=([-\d.]+),([-\d.]+)""").find(lower)
                if (pinMatch != null) {
                    val lat = pinMatch.groupValues[1].toDouble()
                    val lon = pinMatch.groupValues[2].toDouble()
                    val name = extraText.lines().firstOrNull() ?: "OsmAnd Location"
                    sendLocationBroadcast(lat, lon, name)
                } else {
                    Toast.makeText(this, "Could not extract pin from OsmAnd link", Toast.LENGTH_SHORT).show()
                }
            }

            else -> {
                //Toast.makeText(this, "Not a supported map link", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendLocationBroadcast(lat: Double, lon: Double, name: String) {
        val broadcastIntent = Intent(ACTION_COORDINATES_SHARED).apply {
            putExtra("lat", lat)
            putExtra("lon", lon)
            putExtra("name", name)
        }
        sendBroadcast(broadcastIntent)
        Toast.makeText(this, "Coordinates sent to receiver", Toast.LENGTH_SHORT).show()
        Log.d("MYDEBUG", "Broadcasted: $lat, $lon, $name")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }
}
