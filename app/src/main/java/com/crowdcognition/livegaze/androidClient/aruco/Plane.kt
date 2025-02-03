package com.crowdcognition.livegaze.androidClient.aruco

import android.util.Log
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters
import java.lang.StringBuilder
import kotlin.math.PI
import kotlin.math.atan2


class Plane(val tags: ArrayList<ArucoTag>) {

    fun getPosInPlane(pos: FloatArray) : FloatArray {
        // sorting tags: calculate center, sort by the angle of diff vectors (clockwise?)
        val inputCenter = doubleArrayOf(0.0,0.0)
        for (tag in tags)
        {
            inputCenter[0] = inputCenter[0] + tag.center[0]
            inputCenter[1] = inputCenter[1] + tag.center[1]
        }
        inputCenter[0] = inputCenter[0] / 4;
        inputCenter[1] = inputCenter[1] / 4;


        val degs = HashMap<ArucoTag, Double>();
        for (tag in tags) {
            val xDiff = tag.center[0] - inputCenter[0]
            val yDiff = tag.center[1] - inputCenter[1]
            degs[tag] = (tangentDegStart - atan2(yDiff, xDiff)) % degDiff
        }

        tags.sortBy { degs[it] }
        //TODO sort by degs

//        val inputPts = Mat(1,4, CvType.CV_32FC4)
//        val outputPts = Mat(4, 1, CvType.CV_32FC4)
        val inputPts = ArrayList<Point>()
        val outputPts = ArrayList<Point>()
        val stringBuilder = StringBuilder()
        for (tag in tags) {
//            stringBuilder.append(" [${tag.center[0]},${tag.center[1]}]")
            stringBuilder.append(" ${tag.id} " )
            inputPts.add(Point(tag.center[0], tag.center[1]))
        }
        if (DEBUG) Log.i("tagg", stringBuilder.toString())

        outputPts.add(Point(0.0, 1.0))
        outputPts.add(Point(1.0, 1.0))
        outputPts.add(Point(1.0, 0.0))
        outputPts.add(Point(0.0, 0.0))


        val srcMat = Converters.vector_Point2f_to_Mat(inputPts)
        val dstMat = Converters.vector_Point2f_to_Mat(outputPts)

        val M = Imgproc.getPerspectiveTransform(srcMat, dstMat)

        val worldPoint = Point(pos[0].toDouble(), pos[1].toDouble())
        val points = ArrayList<Point>()
        points.add(worldPoint)
        val pointsMat = Converters.vector_Point2f_to_Mat(points)
        if (DEBUG)Log.i("Pos OutPlane", "${pos[0]} ${pos[1]}")
        val res = Mat()
        Core.perspectiveTransform(pointsMat, res, M)
//        Log.i("MappedPoint", "${res[0,0][0]}  ${res[0,0][1]}  realpoint ${worldPoint.x}  ${worldPoint.y}")
        return floatArrayOf(res[0,0][0].toFloat(),res[0,0][1].toFloat())
    }

    companion object {
        const val DEBUG = false
        const val tangentDegStart = (-135 * PI / 180)
        const val degDiff = 2 * PI
    }
}