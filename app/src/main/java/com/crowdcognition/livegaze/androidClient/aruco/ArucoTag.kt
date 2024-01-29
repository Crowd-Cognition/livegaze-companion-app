package com.crowdcognition.livegaze.androidClient.aruco

class ArucoTag(private val corners: Array<DoubleArray>, private val id: Int) {
    val center : DoubleArray;

    init{
        var centerX = 0.0;
        var centerY = 0.0;
        corners.forEach {
            centerX += it[0]
            centerY += it[1]
        }
        center = doubleArrayOf(centerX / corners.size, centerY/corners.size)
    }



}