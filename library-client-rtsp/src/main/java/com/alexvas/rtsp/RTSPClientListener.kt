package com.alexvas.rtsp

import android.graphics.Bitmap
import android.media.Image

interface RTSPClientListener {
    fun onRTSPFrameReceived(width: Int, height: Int, yuv420Bytes: ByteArray?)
    fun onRTSPFirstFrameRendered()
}