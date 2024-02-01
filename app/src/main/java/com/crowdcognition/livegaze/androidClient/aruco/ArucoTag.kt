package com.crowdcognition.livegaze.androidClient.aruco

import android.util.Log

class ArucoTag(corners: Array<DoubleArray>, val id: Int) {
    val center : DoubleArray;

    init{
        var centerX = 0.0;
        var centerY = 0.0;
//        var stringBuilder = StringBuilder()
        corners.forEach {
//            stringBuilder.append("[${it[0]},${it[1]}] ")
            centerX += it[0]
            centerY += it[1]
        }
        center = doubleArrayOf(centerX / corners.size, centerY/corners.size)
//        Log.i("tag ", "$id  ${stringBuilder}  center: ${center[0]} ${center[1]}")
    }



}