package com.alexvas.rtsp.demo.live

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import com.alexvas.rtsp.codec.VideoDecodeThread
import com.alexvas.rtsp.demo.databinding.FragmentLiveBinding
import org.opencv.android.Utils
import org.opencv.aruco.Aruco
import org.opencv.aruco.Dictionary
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ResultParseThread(var bitmaps : Array<Bitmap>, var currentlyParsingFirstIndex : AtomicBoolean,
                        var binding: FragmentLiveBinding) : Thread() {

    private var exitFlag = AtomicBoolean(false)
    private var arucoDictionary : Dictionary? = null


    @SuppressLint("LogNotTimber")
    fun stopAsync() {
        if (DEBUG) Log.v(TAG, "stopAsync()")
        exitFlag.set(true)
        // Wake up sleep() code
        interrupt()
    }
    override fun run() {
        arucoDictionary = Aruco.getPredefinedDictionary(Aruco.DICT_ARUCO_ORIGINAL)

        while(!exitFlag.get()) {
            var selectedBitmap : Bitmap;
            synchronized(bitmaps) {
                if (currentlyParsingFirstIndex.get()) {
                    selectedBitmap = bitmaps[0].copy(Bitmap.Config.ARGB_8888, false)
                    currentlyParsingFirstIndex.set(false)
                } else {
                    selectedBitmap = bitmaps[1].copy(Bitmap.Config.ARGB_8888, false)
                    currentlyParsingFirstIndex.set(true)
                }
            }
            val imgGray = Mat()
            Utils.bitmapToMat(selectedBitmap, imgGray)
            Imgproc.cvtColor(imgGray, imgGray, Imgproc.COLOR_RGB2GRAY)
            //            val img = Mat()
//            Utils.bitmapToMat(bitmap, img)
            val markerList = mutableListOf<Mat>()
            val ids = Mat()
            Aruco.detectMarkers(imgGray, arucoDictionary,markerList, ids)

            binding.apply {
//                arucoStats.text = "${ids.size()} \n ${getMatValues(markerList)}"
                arucoStats.text = "hehehe"
                vImage.setImageBitmap(selectedBitmap)
                vShutter.visibility = View.INVISIBLE
            }

        }
    }


    companion object {
        private val TAG: String = VideoDecodeThread::class.java.simpleName
        private const val DEBUG = true

        private val DEQUEUE_INPUT_TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(500)
        private val DEQUEUE_OUTPUT_BUFFER_TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(100)
    }
}