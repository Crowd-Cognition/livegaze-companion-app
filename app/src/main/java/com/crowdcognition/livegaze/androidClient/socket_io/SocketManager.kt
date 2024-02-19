package com.crowdcognition.livegaze.androidClient.socket_io

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URISyntaxException

class SocketManager(uri: String) {
    private val socket: Socket?;

    init {
        socket = try {
            IO.socket(uri);
        } catch (e : URISyntaxException) {
            e.printStackTrace()
            null
        }

    }

    fun connect() {
        socket!!.connect();
    }

    fun sendData(arucoTagIds: Array<Int>, positionX: Float, positionY: Float, cameraId: String) {
        val dataJson = JSONObject();

        try {
            dataJson.put("x",positionX);
            dataJson.put("y",positionY);
            dataJson.put("camera_id", cameraId);
            dataJson.put("stim_id", arucoTagIds[1]);
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        Log.i("data", dataJson.toString())

        socket!!.emit("new_gaze_data", dataJson);
    }


    fun disconnect() {
        socket!!.disconnect();
        socket.off("new message");
    }

}