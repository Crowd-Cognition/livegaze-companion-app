package com.crowdcognition.livegaze.androidClient

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import com.alexvas.rtsp.demo.R
import com.crowdcognition.livegaze.androidClient.services.MainService
import com.crowdcognition.livegaze.androidClient.socket_io.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

private const val CLIENT_IP = "127.0.0.1"
private const val DEFAULT_HTTP_REQUEST = "http://$CLIENT_IP:8080/api/status"

class MainActivity : AppCompatActivity() {

    private var mainService: MainService? = null

    private var serviceBoundState = false
    private var socketIOManager: SocketManager? = null

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // we've bound to ExampleLocationForegroundService, cast the IBinder and get ExampleLocationForegroundService instance.
            Timber.d("onServiceConnected")

            val binder = service as MainService.LocalBinder
            mainService = binder.getService()
            serviceBoundState = true
            mainService!!.firstFrameDecodedLiveData.observe(this@MainActivity, {
                if (it) {
                    findViewById<TextView>(R.id.sent_first_data).visibility = Button.VISIBLE
                } else {
                    findViewById<TextView>(R.id.sent_first_data).visibility = Button.INVISIBLE
                }
            })
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            // This is called when the connection with the service has been disconnected. Clean up.
            Timber.d("onServiceDisconnected")

            serviceBoundState = false
            mainService = null
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // if permission was denied, the service can still run only the notification won't be visible
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        findViewById<EditText>(R.id.ip_input).let {
            it.doOnTextChanged() { text, _, _, _ ->
                if (text != null) {
//                    findViewById<Button>(R.id.start_button).isEnabled = text.isNotEmpty()
                    MainService.serverAddress = text.toString()
                }
            }
        }

        findViewById<Button>(R.id.refresh_connection_button).let {
            it.setOnClickListener {
                refreshConnection()
            }
        }

        findViewById<Button>(R.id.start_button).let {
            it.setOnClickListener {
                startStopButtonPressed()
            }
        }

        MainService.serverAddress =
            getPreferences(Context.MODE_PRIVATE).getString("serverIp", MainService.serverAddress)!!;
        findViewById<EditText>(R.id.ip_input).setText(MainService.serverAddress);
        checkAndRequestNotificationPermission()

    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            )) {
                android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    // permission already granted
                }

                else -> {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun refreshConnection() {

        //Stop service if running
        if (serviceBoundState) {
            stopService()
            val startButton = findViewById<Button>(R.id.start_button)
            startButton.visibility = Button.INVISIBLE

        }

        val companionConnectionText = findViewById<TextView>(R.id.companion_connection_text)
        companionConnectionText.visibility = TextView.VISIBLE
        companionConnectionText.text = getString(R.string.companion_connection)

        CoroutineScope(Dispatchers.IO).launch {
            val response = makeHttpRequest(DEFAULT_HTTP_REQUEST)
            parseResponse(response)
        }
    }

    private suspend fun makeHttpRequest(url: String): Response? {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()

        return try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            Timber.e("Error making HTTP request: ${e.message}")
            null
        }
    }

    private fun parseResponse(response: Response?) {
        val companionConnectionText = findViewById<TextView>(R.id.companion_connection_text)
        if (response == null) {
            Timber.i("Failed to get response")
            CoroutineScope(Dispatchers.Main).launch {
                companionConnectionText.text = getString(R.string.device_not_found)
            }
            return;
        }
        if (response.code != 200) {
            Timber.i("Failed to get companion id")
            Thread.sleep(1000)
            CoroutineScope(Dispatchers.IO).launch {
                val httpResponse = makeHttpRequest(DEFAULT_HTTP_REQUEST)
                parseResponse(httpResponse!!)
            }
            return;
        }

        val jsonString = response.body?.string()!!
        val responseJson = JSONObject(jsonString)
        val resultArray = responseJson.getJSONArray("result")
        var companionId = ""
        for (i in 0 until resultArray.length()) {
            val result = resultArray.getJSONObject(i)
            if (result.getString("model") == "Phone")
                companionId = result.getJSONObject("data").getString("device_id")
        }
        if (companionId == "") {
            //TODO: change texts to indicate failure
            CoroutineScope(Dispatchers.Main).launch {
                companionConnectionText.text = getString(R.string.device_not_found)
            }
            return;
        }

        CoroutineScope(Dispatchers.Main).launch {
            companionConnectionText.text = getString(R.string.companion_connected)
        }


        val serverConnectionText = findViewById<TextView>(R.id.server_connection_text)
        CoroutineScope(Dispatchers.Main).launch {
            serverConnectionText.visibility = TextView.VISIBLE
            serverConnectionText.text = getString(R.string.server_connection)
        }
        socketIOManager?.disconnect()
        socketIOManager = SocketManager(MainService.serverAddress);

        socketIOManager!!.connect()
        socketIOManager!!.listenToEvent("pong", { _ ->
            serverPongReceived()
        })
        socketIOManager!!.sendPing()
    }

    private fun serverPongReceived() {
        Timber.i("Server pong received")
        CoroutineScope(Dispatchers.Main).launch {
            val serverConnectionText = findViewById<TextView>(R.id.server_connection_text)
            serverConnectionText.text = getString(R.string.server_connected)
            findViewById<Button>(R.id.start_button).visibility = Button.VISIBLE
        }
//        CoroutineScope(Dispatchers.IO).launch {
//            val httpResponse = makeHttpRequest(DEFAULT_HTTP_REQUEST)
//            parseResponse(httpResponse!!)
//        }
    }

    private fun startStopButtonPressed() {
        val startButton = findViewById<Button>(R.id.start_button)
        if (serviceBoundState) {
            startButton.text = getString(R.string.start)
            stopService()
        } else {
            startButton.text = getString(R.string.stop)
            startService()
        }
    }

    private fun stopService() {
        mainService?.stopService()
        CoroutineScope(Dispatchers.Main).launch {
            findViewById<TextView>(R.id.companion_connection_text).visibility = TextView.INVISIBLE
            findViewById<TextView>(R.id.server_connection_text).visibility = TextView.INVISIBLE
            findViewById<TextView>(R.id.start_button).visibility = Button.INVISIBLE
        }
    }

    private fun startService() {
        MainService.serverAddress = findViewById<EditText>(R.id.ip_input).text.toString();
        MainService.socketIOManager = socketIOManager
        with(getPreferences(Context.MODE_PRIVATE).edit()) {
            putString("serverIp", MainService.serverAddress)
            apply()
        }
        Intent(this, MainService::class.java).also {
            //set main service socketManager

            it.action = "START"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(it)
            } else {
                startService(it)
            }
            tryToBindToServiceIfRunning()
        }
    }

    private fun tryToBindToServiceIfRunning() {
        Intent(this, MainService::class.java).also { intent ->
            bindService(intent, connection, 0)
        }
    }
}
