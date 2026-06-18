package com.crowdcognition.livegaze.androidClient

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.alexvas.rtsp.demo.R
import com.alexvas.rtsp.demo.databinding.ActivityMainBinding
import com.crowdcognition.livegaze.androidClient.services.MainService
import com.crowdcognition.livegaze.androidClient.socket_io.SocketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.URI
import androidx.core.content.edit

private const val CLIENT_IP = "127.0.0.1"
private const val DEFAULT_HTTP_REQUEST = "http://$CLIENT_IP:8080/api/status"

// Singleton OkHttpClient — reuse across requests
private val httpClient: OkHttpClient by lazy { OkHttpClient() }

class MainActivity : AppCompatActivity() {

    // ViewBinding replaces all findViewById calls
    private lateinit var binding: ActivityMainBinding

    private var mainService: MainService? = null
    private var serviceBoundState = false
    private var socketIOManager: SocketManager? = null

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Timber.d("onServiceConnected")
            val binder = service as MainService.LocalBinder
            mainService = binder.getService()
            serviceBoundState = true

            mainService?.firstFrameDecodedLiveData?.observe(this@MainActivity) { decoded ->
                binding.sentFirstData.visibility = if (decoded) VISIBLE else INVISIBLE
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            Timber.d("onServiceDisconnected")
            serviceBoundState = false
            mainService = null
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Permission denied: service still runs, but notification won't show
        }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        initSavedPreferences()
        setupListeners()
        checkAndRequestNotificationPermission()
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private fun initSavedPreferences() {
        val savedIp = getPreferences(Context.MODE_PRIVATE)
            .getString("serverIp", MainService.serverAddress)
            ?: MainService.serverAddress

        MainService.serverAddress = savedIp
        binding.ipInput.setText(savedIp)
    }

    private fun setupListeners() {
        binding.ipInput.doOnTextChanged { text, _, _, _ ->
            if (!text.isNullOrEmpty()) {
                MainService.serverAddress = text.toString()
            }
        }

        binding.refreshConnectionButton.setOnClickListener {
            refreshConnection()
        }

        binding.startButton.setOnClickListener {
            startStopButtonPressed()
        }
    }

    // -------------------------------------------------------------------------
    // Permission
    // -------------------------------------------------------------------------

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Connection
    // -------------------------------------------------------------------------

    private fun refreshConnection() {
        if (serviceBoundState) {
            stopService()
            binding.startButton.visibility = INVISIBLE
        }

        binding.companionConnectionText.visibility = VISIBLE
        binding.companionConnectionText.text = getString(R.string.companion_connection)

        // lifecycleScope is lifecycle-aware — auto-cancelled on destroy
        lifecycleScope.launch(Dispatchers.IO) {
            val response = makeHttpRequest(DEFAULT_HTTP_REQUEST)
            parseResponse(response)
        }
    }

    /** Executes a blocking HTTP request — must be called from an IO dispatcher. */
    private fun makeHttpRequest(url: String): Response? {
        val request = Request.Builder().url(url).build()
        return try {
            httpClient.newCall(request).execute()
        } catch (e: IOException) {
            Timber.e("HTTP request failed: ${e.message}")
            null
        }
    }

    /**
     * Parses the HTTP response and updates the UI accordingly.
     * Must be called from an IO dispatcher; UI updates are dispatched via [withContext].
     */
    private suspend fun parseResponse(response: Response?) {
        if (response == null) {
            Timber.i("No response received")
            withContext(Dispatchers.Main) {
                binding.companionConnectionText.text = getString(R.string.device_not_found)
            }
            return
        }

        if (response.code != 200) {
            Timber.i("Non-200 response (${response.code}), retrying…")
            delay(1_000)
            parseResponse(makeHttpRequest(DEFAULT_HTTP_REQUEST))
            return
        }

        val jsonString = response.body.string() ?: run {
            Timber.e("Response body is null")
            return
        }

        val companionId = extractCompanionId(jsonString)

        if (companionId.isEmpty()) {
            withContext(Dispatchers.Main) {
                binding.companionConnectionText.text = getString(R.string.device_not_found)
            }
            return
        }

        MainService.companionId = companionId

        withContext(Dispatchers.Main) {
            binding.companionConnectionText.text = getString(R.string.companion_connected)
            binding.serverConnectionText.visibility = VISIBLE
            binding.serverConnectionText.text = getString(R.string.server_connection)
        }

        connectToSocketServer(companionId)
    }

    private fun extractCompanionId(jsonString: String): String {
        return try {
            val responseJson = JSONObject(jsonString)
            val resultArray = responseJson.getJSONArray("result")
            (0 until resultArray.length())
                .map { resultArray.getJSONObject(it) }
                .firstOrNull { it.getString("model") == "Phone" }
                ?.getJSONObject("data")
                ?.getString("device_id")
                ?: ""
        } catch (e: Exception) {
            Timber.e("Failed to parse companion ID: ${e.message}")
            ""
        }
    }

    private fun connectToSocketServer(companionId: String) {
        socketIOManager?.disconnect()

        val parsedUri = URI.create(MainService.serverAddress)
        val gazeUri = URI(
            parsedUri.scheme,
            parsedUri.authority,
            "/gaze",
            parsedUri.query,
            parsedUri.fragment
        )

        socketIOManager = SocketManager(gazeUri).also { manager ->
            manager.connect()
            manager.listenToEvent("pong") { _ -> onServerPongReceived() }
            manager.sendPing(companionId)
        }
    }

    private fun onServerPongReceived() {
        Timber.i("Server pong received")
        lifecycleScope.launch(Dispatchers.Main) {
            binding.serverConnectionText.text = getString(R.string.server_connected)
            binding.startButton.visibility = VISIBLE
        }
    }

    // -------------------------------------------------------------------------
    // Service management
    // -------------------------------------------------------------------------

    private fun startStopButtonPressed() {
        if (serviceBoundState) {
            binding.startButton.text = getString(R.string.start)
            stopService()
        } else {
            binding.startButton.text = getString(R.string.stop)
            startService()
        }
    }

    private fun stopService() {
        mainService?.stopService()
        binding.companionConnectionText.visibility = INVISIBLE
        binding.serverConnectionText.visibility = INVISIBLE
        binding.startButton.visibility = INVISIBLE
    }

    private fun startService() {
        MainService.serverAddress = binding.ipInput.text.toString()
        MainService.socketIOManager = socketIOManager

        getPreferences(Context.MODE_PRIVATE).edit {
            putString("serverIp", MainService.serverAddress)
        }

        Intent(this, MainService::class.java).also { intent ->
            intent.action = "START"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
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