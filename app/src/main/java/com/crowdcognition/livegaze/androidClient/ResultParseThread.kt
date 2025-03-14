package com.crowdcognition.livegaze.androidClient

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import com.alexvas.rtsp.codec.VideoDecodeThread
import com.crowdcognition.livegaze.androidClient.aruco.ArucoTag
import com.crowdcognition.livegaze.androidClient.aruco.Plane
import com.crowdcognition.livegaze.androidClient.services.MainService
import org.opencv.android.Utils
import org.opencv.aruco.Aruco
import org.opencv.aruco.Dictionary
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ResultParseThread(private var mainService: MainService, private var imageParseListener: ImageParseListener) : Thread() {

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
            if (mainService.receivedBitmap == null) {
                if (DEBUG) Log.d("RTSP Listener", "bitmap null")
                try {
                    sleep(40);
                } catch (e: InterruptedException) {
                    continue
                }
                continue;
            }
            var selectedBitmap : Bitmap? = null;
            synchronized(mainService.receivedBitmap!!) {
                selectedBitmap = mainService.receivedBitmap!!.copy(Bitmap.Config.ARGB_8888, false)
//                bitmap = null;
            }
            if (selectedBitmap == null) continue;
            val imgGray = Mat()
            Utils.bitmapToMat(selectedBitmap, imgGray)

            val img = Mat()
            Utils.bitmapToMat(selectedBitmap, img)

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
                var values = plane.getPosInPlane(mainService.gazePos)
                MainService.socketIOManager!!.sendData(arucoTags.map{it.id}.toTypedArray(),
                    values[0], values[1], MainService.companionId)
                if (DEBUG_IMAGE_PARSE) {
                    for ((i, tag) in plane.tags.withIndex()) {
                        Imgproc.circle(
                            img,
                            Point(tag.center[0], tag.center[1]),
                            10,
                            Scalar(0.0, 255.0, 255.0)
                        )
                        Imgproc.putText(
                            img,
                            "$i",
                            Point(tag.center[0], tag.center[1]),
                            Imgproc.FONT_HERSHEY_TRIPLEX,
                            10.0,
                            Scalar(0.0, 255.0, 0.0)
                        )
                    }
                }
            }
            if (DEBUG_IMAGE_PARSE){
                Utils.matToBitmap(img, selectedBitmap)
                imageParseListener.onObjectParseReady(selectedBitmap!!);
            }
            if (DEBUG)Log.d("RTSP Listener", "Image Received ${ids.size()}")

        }
    }


    companion object {
        private val TAG: String = VideoDecodeThread::class.java.simpleName
        private const val DEBUG = false
        private const val DEBUG_IMAGE_PARSE = false;

        private val DEQUEUE_INPUT_TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(500)
        private val DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(100)
    }
}