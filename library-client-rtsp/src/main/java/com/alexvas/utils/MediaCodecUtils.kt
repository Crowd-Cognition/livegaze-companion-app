package com.alexvas.utils

import android.util.Log
import android.util.Range
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
//import com.google.android.exoplayer2.mediacodec.MediaCodecInfo
//import com.google.android.exoplayer2.mediacodec.MediaCodecUtil
import java.lang.Exception

object MediaCodecUtils {

    // key - codecs mime type
    // value - list of codecs able to handle this mime type
    private val decoderInfosMap = HashMap<String, List<MediaCodecInfo>>()

    private val TAG: String = MediaCodecUtils::class.java.simpleName

    @OptIn(UnstableApi::class)
    private fun getDecoderInfos(mimeType: String): List<MediaCodecInfo> {
        val list = decoderInfosMap[mimeType]
        return if (list.isNullOrEmpty()) {
            val decoderInfos = try {
                MediaCodecUtil.getDecoderInfos(mimeType, false, false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize '$mimeType' decoders list (${e.message})", e)
                ArrayList()
            }
            decoderInfosMap[mimeType] = decoderInfos
            decoderInfos
        } else {
            list
        }
    }

    /**
     * Get software decoders list. Usually used as fallback.
     */
    @OptIn(UnstableApi::class) @Synchronized
    fun getSoftwareDecoders(mimeType: String): List<MediaCodecInfo> {
        val decoderInfos = getDecoderInfos(mimeType)
        val list = ArrayList<MediaCodecInfo>()
        for (codec in decoderInfos) {
            if (codec.softwareOnly)
                list.add(codec)
        }
        return list
    }

    /**
     * Get hardware accelerated decoders list. Used as default.
     */
    @OptIn(UnstableApi::class) @Synchronized
    fun getHardwareDecoders(mimeType: String): List<MediaCodecInfo> {
        val decoderInfos = getDecoderInfos(mimeType)
        val list = ArrayList<MediaCodecInfo>()
        for (codec in decoderInfos) {
            if (codec.hardwareAccelerated)
                list.add(codec)
        }
        return list
    }

}

fun android.media.MediaCodecInfo.CodecCapabilities.capabilitiesToString(): String {
    var heights = videoCapabilities?.supportedHeights
    if (heights == null)
        heights = Range(-1, -1)
    var widths = videoCapabilities?.supportedWidths
    if (widths == null)
        widths = Range(-1, -1)
    return "max instances: ${maxSupportedInstances}, max resolution: ${heights.upper}x${widths.upper}"
}
