package com.crowdcognition.livegaze.androidClient.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
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
import kotlinx.coroutines.Job
import org.opencv.android.OpenCVLoader
import timber.log.Timber

private const val CLIENT_IP = "127.0.0.1"
private const val DEFAULT_RTSP_REQUEST = "rtsp://${CLIENT_IP}:8086/?camera=world"

class MainService : Service() {

    private val binder = LocalBinder()
    private var serviceJob: Job? = null
    var receivedBitmap: Bitmap? = null
    var gazePos: FloatArray = floatArrayOf(0.0f, 0.0f)
    var companionId: String = "test_id"
    private var surfaceHandler: RtspVideoHandler? = null
    private var resultParseThread: ResultParseThread? = null
    private val rtspRequest = MutableLiveData<String>().apply {
        value = DEFAULT_RTSP_REQUEST
    }

    companion object {
        var serverAddress: String = "http://10.181.202.21:5000"
        var socketIOManager: SocketManager? = null
    }

    inner class LocalBinder : Binder() {
        fun getService(): MainService = this@MainService
    }

    override fun onCreate() {
        super.onCreate()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                2, notification, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } else {
            startForeground(1, notification)
        }
        Timber.d("Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        Timber.d("Service destroyed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Timber.i("start")

        if (intent != null) {
            val action = intent.action
            when (action) {
                "START" -> startService()
                "STOP" -> stopService()
            }
        } else {
            Timber.i("service started with a null intent. It has been probably restarted by the system.")
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    private fun startService() {
        setupRTSPHandler()
    }


    private fun setupRTSPHandler() {
        surfaceHandler = RtspVideoHandler();
        surfaceHandler?.rtspFrameListener = rtspFrameListener;
        surfaceHandler?.setStatusListener(rtspStatusListener)
        surfaceHandler?.gazeDataListener = gazeDataListener;

        if (!OpenCVLoader.initDebug()) {
            Timber.i("OpenCV not loaded")
            Thread.sleep(1000)
        }

        val uri = Uri.parse(rtspRequest.value)
        val uriParts = rtspRequest.value!!.split("?").toMutableList()
        uriParts[1] = "camera\u003dgaze"
        val gazeUriText = uriParts.joinToString(separator = "?")
        val gazeUri = Uri.parse(gazeUriText)
        surfaceHandler?.init(uri, gazeUri, "", "", "rtsp-client-android")
        surfaceHandler?.debug = true
        resultParseThread = ResultParseThread(this, imageParseListener)
        surfaceHandler?.start(
            requestVideo = true,
            requestAudio = false,
            parseThread = resultParseThread
        )
    }

    fun stopService() {
        try {
            Timber.i("stopped")
            resultParseThread?.stopAsync()
            surfaceHandler?.stop()
            socketIOManager?.disconnect()
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Timber.i("Service stopped without being started: ${e.message}")
        }
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
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

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
                this,
                notificationChannelId
            ) else Notification.Builder(this)

        return builder
            .setContentTitle("Livegaze Companion Service")
            .setContentText("Livegaze companion is working on the background to send data to the server")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .build()
    }


    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private val rtspStatusListener = object : RtspVideoHandler.RtspStatusListener {

        override fun onRtspStatusConnecting() {
        }

        override fun onRtspStatusConnected() {
        }

        override fun onRtspStatusDisconnecting() {
        }

        override fun onRtspStatusDisconnected() {
        }

        override fun onRtspStatusFailedUnauthorized() {
        }

        override fun onRtspStatusFailed(message: String?) {
        }

        override fun onRtspFirstFrameRendered() {
        }
    }

    private val imageParseListener = object : ImageParseListener {
        override fun onObjectParseReady(bitmap: Bitmap) {
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