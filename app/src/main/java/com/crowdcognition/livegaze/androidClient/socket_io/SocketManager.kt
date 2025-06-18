package com.crowdcognition.livegaze.androidClient.socket_io

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException

class SocketManager
{
    private val socket: Socket?;

    constructor(uriString: String) {
        socket = try {
            IO.socket(uriString);
        } catch (e : URISyntaxException) {
            e.printStackTrace()
            null
        }
    }

    constructor(uri: URI) {
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

    fun listenToEvent(eventName: String, callback: (JSONArray?) -> Unit) {
        socket!!.on(eventName) { args ->
            val data = if(args.isNotEmpty()) args[0] as JSONArray else null
            callback(data)
        }
    }


    fun sendPing(trackerId : String?) {
        if (!trackerId.isNullOrEmpty()) {
            val dataJson = JSONObject();
            dataJson.put("camera_id", trackerId);
            socket!!.emit("ping", dataJson);
        } else {
            socket!!.emit("ping")
        }
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

//        socket!!.emit("new_gaze_data", dataJson);
        socket!!.volatile.emit("new_gaze_data", dataJson);
    }


    fun disconnect() {
        socket!!.disconnect();
        socket.off("new message");
    }

    fun socketConnected() : Boolean {
        return socket?.connected() ?: false
    }

}