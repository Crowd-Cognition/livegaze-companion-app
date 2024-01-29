package com.crowdcognition.livegaze.androidClient

import android.graphics.Bitmap

interface ImageParseListener {

    fun onObjectParseReady(bitmap: Bitmap);
}