package com.crowdcognition.livegaze.androidClient

import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.alexvas.rtsp.demo.R
import com.crowdcognition.livegaze.androidClient.services.MainService

class MainActivity : AppCompatActivity() {

    private var mainService: MainService? = null

    private var serviceBoundState = false

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // we've bound to ExampleLocationForegroundService, cast the IBinder and get ExampleLocationForegroundService instance.
            Log.d(TAG, "onServiceConnected")

            val binder = service as MainService.LocalBinder
            mainService = binder.getService()
            serviceBoundState = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            // This is called when the connection with the service has been disconnected. Clean up.
            Log.d(TAG, "onServiceDisconnected")

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
        Log.i("activiy", "start")
        findViewById<Button>(R.id.start_button).let {
            it.setOnClickListener {
                startService()
            }
        }

        findViewById<Button>(R.id.stop_button).let {
            it.setOnClickListener {
                stopService()
            }
        }

        checkAndRequestNotificationPermission()

//        var mainService = MainService()
//        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
//        val appBarConfiguration = AppBarConfiguration(setOf(
//                R.id.navigation_live, R.id.navigation_logs))
//        setupActionBarWithNavController(navController, appBarConfiguration)
//        navView.setupWithNavController(navController)
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

    private fun stopService() {
        mainService?.stopService()
    }

    private fun startService() {
        Intent(this, MainService::class.java).also {
            it.action = "START"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.i("Service", "sdkHere")
                startForegroundService(it)

            }else {
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
