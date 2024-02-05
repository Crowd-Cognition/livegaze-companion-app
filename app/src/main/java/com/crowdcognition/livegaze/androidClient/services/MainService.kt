package com.crowdcognition.livegaze.androidClient.services

import android.app.Service
import android.content.ContentValues.TAG
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.alexvas.rtsp.GazeDataListener
import com.alexvas.rtsp.RTSPClientListener
import com.alexvas.rtsp.demo.databinding.FragmentLiveBinding
import com.alexvas.rtsp.widget.RtspVideoHandler
import com.crowdcognition.livegaze.androidClient.ImageParseListener
import com.crowdcognition.livegaze.androidClient.LiveViewModel
import com.crowdcognition.livegaze.androidClient.ResultParseThread
import com.crowdcognition.livegaze.androidClient.socket_io.SocketManager
import com.google.android.renderscript.Toolkit
import com.google.android.renderscript.YuvFormat
import kotlinx.coroutines.Job

private const val DEFAULT_RTSP_REQUEST = "rtsp://10.181.124.222:8086/?camera=world"

class MainService : Service() {

    private var serviceJob: Job? = null
    var socketIOManager: SocketManager = SocketManager("http://10.181.215.226:5000")
    var receivedBitmap : Bitmap? = null
    var gazePos: FloatArray = floatArrayOf(0.0f,0.0f)

    val rtspRequest = MutableLiveData<String>().apply {
        value = DEFAULT_RTSP_REQUEST
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        Log.d(TAG, "Service destroyed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

//        binding = FragmentLiveBinding.inflate(inflater, container, false)
        val surfaceHandler = RtspVideoHandler();
        surfaceHandler.rtspFrameListener = rtspFrameListener;
        surfaceHandler.setStatusListener(rtspStatusListener)
        surfaceHandler.gazeDataListener = gazeDataListener;

//        liveViewModel = ViewModelProvider(this).get(LiveViewModel::class.java)

        val uri = Uri.parse(rtspRequest.value)
        val uriParts = rtspRequest.value!!.split("?").toMutableList()
        uriParts[1] = "camera\u003dgaze"
        val gazeUriText = uriParts.joinToString(separator = "?")
        Log.i("GazeURI",gazeUriText)
        val gazeUri = Uri.parse(gazeUriText)
        surfaceHandler.init(uri, gazeUri,"","", "rtsp-client-android")
        surfaceHandler.debug = true
        val resultParseThread = ResultParseThread(this, imageParseListener)
        surfaceHandler.start(
            requestVideo = true,
            requestAudio = false,
            parseThread = resultParseThread
        )
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
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