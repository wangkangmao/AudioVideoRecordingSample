package com.wangkm.audiovideorecordingsample

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.wangkm.audiovideorecordingsample.encoder.MediaAudioEncoder
import com.wangkm.audiovideorecordingsample.encoder.MediaEncoder
import com.wangkm.audiovideorecordingsample.encoder.MediaMuxerWrapper
import com.wangkm.audiovideorecordingsample.encoder.MediaVideoEncoder
import java.io.IOException

/**
 * @author: created by wangkm
 * @time: 2021/10/11 10:22
 * @desc：
 * @email: 1240413544@qq.com
 */

class CameraFragment: Fragment() {

    private val DEBUG = true // TODO set false on release

    private val TAG = "CameraFragment"

    /**
     * for camera preview display
     */
    private var mCameraView: CameraGLView? = null

    /**
     * for scale mode display
     */
    private var mScaleModeView: TextView? = null

    /**
     * button for start/stop recording
     */
    private var mRecordButton: ImageButton? = null

    /**
     * muxer for audio/video recording
     */
    private var mMuxer: MediaMuxerWrapper? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_main, container, false)

        mCameraView = rootView.findViewById<View>(R.id.cameraView) as CameraGLView
        mCameraView?.setVideoSize(1280, 720)
        mCameraView?.setOnClickListener(mOnClickListener)
        mScaleModeView = rootView.findViewById(R.id.scalemode_textview)
        updateScaleModeText()
        mRecordButton = rootView.findViewById(R.id.record_button)
        mRecordButton?.setOnClickListener(mOnClickListener)
        return rootView
    }

    override fun onResume() {
        super.onResume()
        if (DEBUG) Log.v(TAG, "onResume:")
        mCameraView!!.onResume()
    }

    override fun onPause() {
        if (DEBUG) Log.v(TAG, "onPause:")
        stopRecording()
        mCameraView!!.onPause()
        super.onPause()
    }

    /**
     * method when touch record button
     */
    private val mOnClickListener =
        View.OnClickListener { view ->
            when (view.id) {
                R.id.cameraView -> {
                    val scale_mode = (mCameraView!!.getScaleMode() + 1) % 4
                    mCameraView!!.setScaleMode(scale_mode)
                    updateScaleModeText()
                }
                R.id.record_button -> if (mMuxer == null) startRecording() else stopRecording()
            }
        }

    private fun updateScaleModeText() {
        val scale_mode = mCameraView!!.getScaleMode()
        mScaleModeView!!.text =
            if (scale_mode == 0) "scale to fit" else if (scale_mode == 1) "keep aspect(viewport)" else if (scale_mode == 2) "keep aspect(matrix)" else if (scale_mode == 3) "keep aspect(crop center)" else ""
    }

    /**
     * start resorcing
     * This is a sample project and call this on UI thread to avoid being complicated
     * but basically this should be called on private thread because prepareing
     * of encoder is heavy work
     */
    private fun startRecording() {
        if (DEBUG) Log.v(TAG, "startRecording:")
        try {
            mRecordButton!!.setColorFilter(-0x10000) // turn red
            mMuxer = MediaMuxerWrapper(".mp4") // if you record audio only, ".m4a" is also OK.
            if (true) {
                // for video capturing
                MediaVideoEncoder(
                    mMuxer,
                    mMediaEncoderListener,
                    mCameraView!!.getVideoWidth(),
                    mCameraView!!.getVideoHeight()
                )
            }
            if (true) {
                // for audio capturing
                MediaAudioEncoder(mMuxer, mMediaEncoderListener)
            }
            mMuxer!!.prepare()
            mMuxer!!.startRecording()
        } catch (e: IOException) {
            mRecordButton!!.setColorFilter(0)
            Log.e(TAG, "startCapture:", e)
        }
    }

    /**
     * request stop recording
     */
    private fun stopRecording() {
        if (DEBUG) Log.v(
            TAG,
            "stopRecording:mMuxer=$mMuxer"
        )
        mRecordButton!!.setColorFilter(0) // return to default color
        if (mMuxer != null) {
            mMuxer?.stopRecording()
            mMuxer = null
            // you should not wait here
        }
    }

    /**
     * callback methods from encoder
     */
    private val mMediaEncoderListener: MediaEncoder.MediaEncoderListener = object : MediaEncoder.MediaEncoderListener {
        override fun onPrepared(encoder: MediaEncoder?) {
            if (DEBUG) Log.v(
                TAG,
                "onPrepared:encoder=$encoder"
            )
            if (encoder is MediaVideoEncoder) mCameraView!!.setVideoEncoder(encoder as MediaVideoEncoder)
        }

        override fun onStopped(encoder: MediaEncoder?) {
            if (DEBUG) Log.v(
                TAG,
                "onStopped:encoder=$encoder"
            )
            if (encoder is MediaVideoEncoder) mCameraView!!.setVideoEncoder(null)
        }
    }
}