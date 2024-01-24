package com.alexvas.rtsp.demo

import android.graphics.Bitmap

interface ImageParseListener {

    fun onObjectParseReady(bitmap: Bitmap);
}