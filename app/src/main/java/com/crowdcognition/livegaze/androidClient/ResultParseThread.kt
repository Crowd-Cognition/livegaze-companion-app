package com.crowdcognition.livegaze.androidClient

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import com.alexvas.rtsp.codec.VideoDecodeThread
import com.crowdcognition.livegaze.androidClient.aruco.ArucoTag
import com.crowdcognition.livegaze.androidClient.aruco.Plane
import org.opencv.android.Utils
import org.opencv.aruco.Aruco
import org.opencv.aruco.Dictionary
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ResultParseThread(private var frag: LiveFragment, private var imageParseListener: ImageParseListener) : Thread() {

    private var exitFlag = AtomicBoolean(false)
    private var arucoDictionary : Dictionary? = null


    @SuppressLint("LogNotTimber")
    fun stopAsync() {
        if (DEBUG) Log.v(TAG, "stopAsync()")
        exitFlag.set(true)
        // Wake up sleep() code
        interrupt()
    }

    private fun getMatValues(markerList : MutableList<Mat>, ids: Mat) : String {
        val builder = StringBuilder();
        for ((i, marker) in markerList.withIndex()) {
            val id = intArrayOf(0)
            ids.get(i,0, id)
            builder.append("iii["+ marker.get(0,0).size + ",," + marker.get(0, 0)[0] + ", " + marker.get(0,0)[1] + "] ["
                    + marker.get(0, 1)[0] + ", " +marker.get(0,1)[1] + "] [" +
                    + marker.get(0, 2)[0] + ", " + marker.get(0,2)[1] + "] [" +
                    + marker.get(0,3)[0] + ", " + marker.get(0,3)[1] + "] + ${id[0]}   ")
        }
        return builder.toString()
    }

    override fun run() {
        arucoDictionary = Aruco.getPredefinedDictionary(Aruco.DICT_ARUCO_ORIGINAL)

        while(!exitFlag.get()) {
            if (frag.receivedBitmap == null) {
                Log.d("RTSP Listener", "bitmap null")
                Thread.sleep(40);
                continue;
            }
            var selectedBitmap : Bitmap? = null;
            synchronized(frag.receivedBitmap!!) {
                selectedBitmap = frag.receivedBitmap!!.copy(Bitmap.Config.ARGB_8888, false)
//                bitmap = null;
            }
            if (selectedBitmap == null) continue;
            val imgGray = Mat()
            Utils.bitmapToMat(selectedBitmap, imgGray)

            Imgproc.cvtColor(imgGray, imgGray, Imgproc.COLOR_RGB2GRAY)

//            val img = Mat()
//            Utils.bitmapToMat(selectedBitmap, img)
//            Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2RGB)

            val markerList = mutableListOf<Mat>()
            val ids = Mat()
            Aruco.detectMarkers(imgGray, arucoDictionary,markerList, ids)
            if (markerList.size == 4) {
                val arucoTags = ArrayList<ArucoTag>()
                for ((i, marker) in markerList.withIndex()) {
                    val corners = Array(4){ doubleArrayOf(0.0,0.0) }
                    for (j in 0..3){
                        corners[j] = marker.get(0,j)
                    }
                    arucoTags.add(ArucoTag(corners, ids.get(i,0)[0].toInt()))
                }
                val plane = Plane(arucoTags)
                plane.getPosInPlane(frag.gazePos)
            }
            Log.d("RTSP Listener", "Image Received ${ids.size()} ${getMatValues(markerList, ids)}")
            imageParseListener.onObjectParseReady(selectedBitmap!!);

        }
    }


    companion object {
        private val TAG: String = VideoDecodeThread::class.java.simpleName
        private const val DEBUG = true

        private val DEQUEUE_INPUT_TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(500)
        private val DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(100)
    }
}