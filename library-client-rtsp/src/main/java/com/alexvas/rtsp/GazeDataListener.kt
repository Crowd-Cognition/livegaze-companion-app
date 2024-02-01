package com.alexvas.rtsp

interface GazeDataListener {

    public fun onGazeDataReady(gazeData: FloatArray);
}