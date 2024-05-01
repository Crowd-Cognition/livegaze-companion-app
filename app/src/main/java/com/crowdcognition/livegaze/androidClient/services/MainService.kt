package com.crowdcognition.livegaze.androidClient.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.alexvas.rtsp.GazeDataListener
import com.alexvas.rtsp.RTSPClientListener
import com.alexvas.rtsp.demo.R
import com.alexvas.rtsp.widget.RtspVideoHandler
import com.crowdcognition.livegaze.androidClient.ImageParseListener
import com.crowdcognition.livegaze.androidClient.MainActivity
import com.crowdcognition.livegaze.androidClient.ResultParseThread
import com.crowdcognition.livegaze.androidClient.socket_io.SocketManager
import com.google.android.renderscript.Toolkit
import com.google.android.renderscript.YuvFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONObject
import org.opencv.android.OpenCVLoader


private const val CLIENT_IP = "127.0.0.1"
private const val DEFAULT_RTSP_REQUEST = "rtsp://${CLIENT_IP}:8086/?camera=world"
private const val DEFAULT_HTTP_REQUEST = "http://${CLIENT_IP}:8080/api/status"

class MainService : Service() {

    private val binder = LocalBinder()
    private var serviceJob: Job? = null
    var socketIOManager: SocketManager? = null;
    var receivedBitmap : Bitmap? = null
    var gazePos: FloatArray = floatArrayOf(0.0f,0.0f)
    var companionId: String = "test_id"
    private var surfaceHandler: RtspVideoHandler? = null
    private var resultParseThread : ResultParseThread? = null
    private val rtspRequest = MutableLiveData<String>().apply {
        value = DEFAULT_RTSP_REQUEST
    }

    companion object{
        var serverAddress : String = "http://10.181.202.21:5000"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MainService = this@MainService
    }

    override fun onCreate() {
        super.onCreate()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, notification, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else { 0 })
        } else {
            startForeground(1, notification,)
        }
        Log.d(TAG, "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        Log.d(TAG, "Service destroyed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.i("MainService", "start")
        socketIOManager = SocketManager(serverAddress)
        socketIOManager!!.connect()

        if (intent != null) {
            val action = intent.action
//            log.i("useAction","using an intent with action $action")
            when (action) {
                "START" -> startService()
                "STOP" -> stopService()
            }
        } else {
            Log.i("ee",
                "with a null intent. It has been probably restarted by the system."
            )
        }
        // by returning this we make sure the service is restarted if the system kills the service


        
        return START_STICKY
    }

    private fun startService() {
        CoroutineScope(Dispatchers.IO).launch {
            val httpResponse = makeHttpRequest(DEFAULT_HTTP_REQUEST)
            receivedResponse(httpResponse!!)
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
            Log.e(TAG, "Error making HTTP request: ${e.message}")
            null
        }
    }

    private fun receivedResponse(response: Response) {


//        Log.i("re", response.message)
//        Log.i("re", response.body?.string()!!)
        val jsonString = response.body?.string()!!
        Log.i("json", jsonString)
        val responseJson = JSONObject(jsonString)
        val resultArray = responseJson.getJSONArray("result")
        for(i in 0 until resultArray.length()) {
            val result = resultArray.getJSONObject(i)
            Log.i("jsonTags", result.getString("model"))
            if (result.getString("model") == "Phone")
                companionId = result.getJSONObject("data").getString("device_id")
        }

        /*
            {"message":"Success","result":[{"data":{"conn_type":"WEBSOCKET","connected":true,"ip":"10.181.112.159","params":"camera\u003dimu","port":8686,"protocol":"rtsp","sensor":"imu"},"model":"Sensor"},{"data":{"conn_type":"DIRECT","connected":true,"ip":"10.181.112.159","params":"camera\u003dimu","port":8086,"protocol":"rtsp","sensor":"imu"},"model":"Sensor"},{"data":{"conn_type":"WEBSOCKET","connected":true,"ip":"10.181.112.159","params":"camera\u003dworld","port":8686,"protocol":"rtsp","sensor":"world"},"model":"Sensor"},{"data":{"conn_type":"DIRECT","connected":true,"ip":"10.181.112.159","params":"camera\u003dworld","port":8086,"protocol":"rtsp","sensor":"world"},"model":"Sensor"},{"data":{"conn_type":"WEBSOCKET","connected":true,"ip":"10.181.112.159","params":"camera\u003dgaze","port":8686,"protocol":"rtsp","sensor":"gaze"},"model":"Sensor"},{"data":{"conn_type":"DIRECT","connected":true,"ip":"10.181.112.159","params":"camera\u003dgaze","port":8086,"protocol":"rtsp","sensor":"gaze"},"model":"Sensor"},{"data":{"conn_type":"WEBSOCKET","connected":true,"ip":"10.181.112.159","params":"camera\u003deyes","port":8686,"protocol":"rtsp","sensor":"eyes"},"model":"Sensor"},{"data":{"conn_type":"DIRECT","connected":true,"ip":"10.181.112.159","params":"camera\u003deyes","port":8086,"protocol":"rtsp","sensor":"eyes"},"model":"Sensor"},{"data":{"battery_level":39,"battery_state":"OK","device_id":"1ee2d98fa1fa0d2f","device_name":"Neon Companion","ip":"10.181.112.159","memory":32512851968,"memory_state":"OK","time_echo_port":12321},"model":"Phone"},{"data":{"frame_name":"Just act natural","glasses_serial":"-1","module_serial":"396621","version":"2.0","world_camera_serial":"-1"},"model":"Hardware"}]}
         */
//        val jsonObject = JSONObject(result)
//        val resultList = jsonObject.getJSONArray("result")

//        Log.i("re", "${resultList.length()}")

        surfaceHandler = RtspVideoHandler();
        surfaceHandler?.rtspFrameListener = rtspFrameListener;
        surfaceHandler?.setStatusListener(rtspStatusListener)
        surfaceHandler?.gazeDataListener = gazeDataListener;
        if(!OpenCVLoader.initDebug()) {
        }

//        liveViewModel = ViewModelProvider(this).get(LiveViewModel::class.java)

        val uri = Uri.parse(rtspRequest.value)
        val uriParts = rtspRequest.value!!.split("?").toMutableList()
        uriParts[1] = "camera\u003dgaze"
        val gazeUriText = uriParts.joinToString(separator = "?")
        Log.i("GazeURI",gazeUriText)
        val gazeUri = Uri.parse(gazeUriText)
        surfaceHandler?.init(uri, gazeUri,"","", "rtsp-client-android")
        surfaceHandler?.debug = true
        resultParseThread = ResultParseThread(this, imageParseListener)
        surfaceHandler?.start(
            requestVideo = true,
            requestAudio = false,
            parseThread = resultParseThread
        )
    }

    fun stopService() {
//        Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        try {
            Log.i("MainService", "stopped")
            resultParseThread?.stopAsync()
            surfaceHandler?.stop()
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.i("MainService","Service stopped without being started: ${e.message}")
        }
//        isServiceStarted = false
//        setServiceState(this, ServiceState.STOPPED)
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Endless Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
            this,
            notificationChannelId
        ) else Notification.Builder(this)

        return builder
            .setContentTitle("Livegaze Companion Service")
            .setContentText("Livegaze companion is working on the background to send data to the server")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
//            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }


    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private val rtspStatusListener = object: RtspVideoHandler.RtspStatusListener {
        override fun onRtspStatusConnecting() {
//            binding.apply {
//                tvStatus.text = "RTSP connecting"
//                pbLoading.visibility = View.VISIBLE
//                vShutter.visibility = View.VISIBLE
//                etRtspRequest.isEnabled = false
//                etRtspUsername.isEnabled = false
//                etRtspPassword.isEnabled = false
//                cbVideo.isEnabled = false
//                cbAudio.isEnabled = false
//                cbDebug.isEnabled = false
//                tgRotation.isEnabled = false
//            }
        }

        override fun onRtspStatusConnected() {
//            binding.apply {
//                tvStatus.text = "RTSP connected"
//                bnStartStop.text = "Stop RTSP"
//                pbLoading.visibility = View.GONE
//            }
        }

        override fun onRtspStatusDisconnecting() {
//            binding.apply {
//                tvStatus.text = "RTSP disconnecting"
//            }
        }

        override fun onRtspStatusDisconnected() {
//            binding.apply {
//                tvStatus.text = "RTSP disconnected"
//                bnStartStop.text = "Start RTSP"
//                pbLoading.visibility = View.GONE
//                vShutter.visibility = View.VISIBLE
////                bnSnapshot.isEnabled = false
//                cbVideo.isEnabled = true
//                cbAudio.isEnabled = true
//                cbDebug.isEnabled = true
//                etRtspRequest.isEnabled = true
//                etRtspUsername.isEnabled = true
//                etRtspPassword.isEnabled = true
//                tgRotation.isEnabled = true
//            }
        }

        override fun onRtspStatusFailedUnauthorized() {
//            if (context == null) return
//            binding.apply {
//                tvStatus.text = "RTSP username or password invalid"
//                pbLoading.visibility = View.GONE
//            }
//            Toast.makeText(context, binding.tvStatus.text , Toast.LENGTH_LONG).show()
        }

        override fun onRtspStatusFailed(message: String?) {
//            if (context == null) return
//            binding.apply {
//                tvStatus.text = "Error: $message"
//                Toast.makeText(context, tvStatus.text, Toast.LENGTH_LONG).show()
//                pbLoading.visibility = View.GONE
//            }
        }

        override fun onRtspFirstFrameRendered() {
//            binding.apply {
//                vShutter.visibility = View.GONE
////                bnSnapshot.isEnabled = true
//            }
        }
    }

    private val imageParseListener = object : ImageParseListener {
        override fun onObjectParseReady(bitmap: Bitmap) {
//            activity!!.runOnUiThread {
//                binding.apply {
////                arucoStats.text = "${ids.size()} \n ${getMatValues(markerList)}"
//                    arucoStats.text = "hehehe"
//                    vImage.setImageBitmap(bitmap)
//                    vShutter.visibility = View.INVISIBLE
//                }
//            }
        }

    }

    private val gazeDataListener = object : GazeDataListener {
        override fun onGazeDataReady(gazeData: FloatArray) {
            gazePos = gazeData;
        }
    }

    private val rtspFrameListener = object : RTSPClientListener {
        override fun onRTSPFrameReceived(width: Int, height: Int, yuv420Bytes: ByteArray?) {
            if (yuv420Bytes == null || yuv420Bytes.size < 10) return;
            val bitmap = Toolkit.yuvToRgbBitmap(yuv420Bytes, width, height, YuvFormat.YUV_420_888)
            if (receivedBitmap == null) {
                receivedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            } else {
                synchronized(receivedBitmap!!) {
                    receivedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                }
            }
        }
    }
}