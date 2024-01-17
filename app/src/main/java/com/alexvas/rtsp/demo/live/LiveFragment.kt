package com.alexvas.rtsp.demo.live

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.alexvas.rtsp.RTSPClientListener
import com.google.android.renderscript.Toolkit
import com.google.android.renderscript.YuvFormat
import com.alexvas.rtsp.demo.databinding.FragmentLiveBinding
import com.alexvas.rtsp.widget.RtspVideoHandler
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.aruco.Aruco
import org.opencv.aruco.Dictionary
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

@SuppressLint("LogNotTimber")
class LiveFragment : Fragment() {

    private lateinit var binding: FragmentLiveBinding
    private lateinit var liveViewModel: LiveViewModel
    private var arucoDictionary : Dictionary? = null
    private var surfaceHandler : RtspVideoHandler? = null;

    private val rtspStatusListener = object: RtspVideoHandler.RtspStatusListener {
        override fun onRtspStatusConnecting() {
            binding.apply {
                tvStatus.text = "RTSP connecting"
                pbLoading.visibility = View.VISIBLE
                vShutter.visibility = View.VISIBLE
                etRtspRequest.isEnabled = false
                etRtspUsername.isEnabled = false
                etRtspPassword.isEnabled = false
                cbVideo.isEnabled = false
                cbAudio.isEnabled = false
                cbDebug.isEnabled = false
                tgRotation.isEnabled = false
            }
        }

        override fun onRtspStatusConnected() {
            binding.apply {
                tvStatus.text = "RTSP connected"
                bnStartStop.text = "Stop RTSP"
                pbLoading.visibility = View.GONE
            }
        }

        override fun onRtspStatusDisconnecting() {
            binding.apply {
                tvStatus.text = "RTSP disconnecting"
            }
        }

        override fun onRtspStatusDisconnected() {
            binding.apply {
                tvStatus.text = "RTSP disconnected"
                bnStartStop.text = "Start RTSP"
                pbLoading.visibility = View.GONE
                vShutter.visibility = View.VISIBLE
                bnSnapshot.isEnabled = false
                cbVideo.isEnabled = true
                cbAudio.isEnabled = true
                cbDebug.isEnabled = true
                etRtspRequest.isEnabled = true
                etRtspUsername.isEnabled = true
                etRtspPassword.isEnabled = true
                tgRotation.isEnabled = true
            }
        }

        override fun onRtspStatusFailedUnauthorized() {
            if (context == null) return
            binding.apply {
                tvStatus.text = "RTSP username or password invalid"
                pbLoading.visibility = View.GONE
            }
            Toast.makeText(context, binding.tvStatus.text , Toast.LENGTH_LONG).show()
        }

        override fun onRtspStatusFailed(message: String?) {
            if (context == null) return
            binding.apply {
                tvStatus.text = "Error: $message"
                Toast.makeText(context, tvStatus.text, Toast.LENGTH_LONG).show()
                pbLoading.visibility = View.GONE
            }
        }

        override fun onRtspFirstFrameRendered() {
            binding.apply {
                vShutter.visibility = View.GONE
                bnSnapshot.isEnabled = true
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        if (DEBUG) Log.v(TAG, "onCreateView()")

        if(!OpenCVLoader.initDebug()) {
        }
        arucoDictionary = Aruco.getPredefinedDictionary(Aruco.DICT_ARUCO_ORIGINAL)

        liveViewModel = ViewModelProvider(this).get(LiveViewModel::class.java)
        binding = FragmentLiveBinding.inflate(inflater, container, false)
        var surfaceHandler = RtspVideoHandler();
        surfaceHandler.rtspFrameListener = rtspFrameListener;
        surfaceHandler.setStatusListener(rtspStatusListener)
        binding.etRtspRequest.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString()
                if (text != liveViewModel.rtspRequest.value) {
                    liveViewModel.rtspRequest.value = text
                }
            }
        })
        binding.etRtspUsername.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString()
                if (text != liveViewModel.rtspUsername.value) {
                    liveViewModel.rtspUsername.value = text
                }
            }
        })
        binding.etRtspPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s.toString()
                if (text != liveViewModel.rtspPassword.value) {
                    liveViewModel.rtspPassword.value = text
                }
            }
        })

        liveViewModel.rtspRequest.observe(viewLifecycleOwner) {
            if (binding.etRtspRequest.text.toString() != it)
                binding.etRtspRequest.setText(it)
        }
        liveViewModel.rtspUsername.observe(viewLifecycleOwner) {
            if (binding.etRtspUsername.text.toString() != it)
                binding.etRtspUsername.setText(it)
        }
        liveViewModel.rtspPassword.observe(viewLifecycleOwner) {
            if (binding.etRtspPassword.text.toString() != it)
                binding.etRtspPassword.setText(it)
        }

        binding.bnStartStop.setOnClickListener {
            if (surfaceHandler.isStarted()) {
                surfaceHandler.stop()
            } else {
                val uri = Uri.parse(liveViewModel.rtspRequest.value)
                surfaceHandler.init(uri, liveViewModel.rtspUsername.value, liveViewModel.rtspPassword.value, "rtsp-client-android")
                surfaceHandler.debug = binding.cbDebug.isChecked
                surfaceHandler.start(binding.cbVideo.isChecked, binding.cbAudio.isChecked)
            }
        }
        return binding.root
    }

    override fun onResume() {
        if (DEBUG) Log.v(TAG, "onResume()")
        super.onResume()
        liveViewModel.loadParams(requireContext())
    }

    override fun onPause() {
        if (DEBUG) Log.v(TAG, "onPause()")
        super.onPause()
        liveViewModel.saveParams(requireContext())
        surfaceHandler?.stop()
    }

    companion object {
        private val TAG: String = LiveFragment::class.java.simpleName
        private const val DEBUG = true
    }

    private val rtspFrameListener = object : RTSPClientListener {
        override fun onRTSPFrameReceived(width: Int, height: Int, yuv420Bytes: ByteArray?) {
            if (yuv420Bytes == null || yuv420Bytes.size < 10) return;
            val bitmap = Toolkit.yuvToRgbBitmap(yuv420Bytes, width, height, YuvFormat.YUV_420_888)
            val img = Mat()
            Utils.bitmapToMat(bitmap, img)
            val imgGray = Mat()
            Utils.bitmapToMat(bitmap, imgGray)
            Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(imgGray, imgGray, Imgproc.COLOR_RGB2GRAY)
            var markerList = mutableListOf<Mat>()
            var ids = Mat()
            Aruco.detectMarkers(imgGray, arucoDictionary,markerList, ids)
            Log.d("RTSP Listener", "Image Received ${ids.size()}")
        }
    }

}
