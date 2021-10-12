package com.wangkm.audiovideorecordingsample.encoder

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import android.media.MediaFormat
import android.opengl.EGLContext
import android.util.Log
import android.view.Surface
import com.wangkm.audiovideorecordingsample.glutils.RenderHandler

/**
 * @author: created by wangkm
 * @time: 2021/10/11 15:26
 * @desc：
 * @email: 1240413544@qq.com
 */

class MediaVideoEncoder : MediaEncoder {

    private val DEBUG = false // TODO set false on release

    private val TAG = "MediaVideoEncoder"

    private val MIME_TYPE = "video/avc"

    /**
     * color formats that we can use in this class
     */
    private lateinit var recognizedFormats: IntArray

    // parameters for recording
    private val FRAME_RATE = 25
    private val BPP = 0.25f

    private var mWidth = 0
    private var mHeight = 0
    private var mRenderHandler: RenderHandler? = null
    private var mSurface: Surface? = null

    init {
        recognizedFormats =
            intArrayOf(
                //MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                //MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                //MediaCodecInfo.CodecCapabilities.COLOR_QCOM_FormatYUV420SemiPlanar,
                CodecCapabilities.COLOR_FormatSurface
            )
    }

    constructor(muxer:MediaMuxerWrapper?,listener:MediaEncoderListener,width:Int,height:Int): super(muxer, listener){
        if (DEBUG) Log.i(
            TAG,
            "MediaVideoEncoder: "
        )
        mWidth = width
        mHeight = height
        mRenderHandler = RenderHandler.createHandler(TAG)

    }


    fun frameAvailableSoon(tex_matrix: FloatArray?): Boolean {
        var result: Boolean
        if (super.frameAvailableSoon().also { result = it }) mRenderHandler?.draw(tex_matrix)
        return result
    }

    fun frameAvailableSoon(
        tex_matrix: FloatArray?,
        mvp_matrix: FloatArray?
    ): Boolean {
        var result: Boolean
        if (super.frameAvailableSoon().also { result = it }) mRenderHandler?.draw(
            tex_matrix,
            mvp_matrix
        )
        return result
    }

    override fun frameAvailableSoon(): Boolean {
        var result: Boolean
        if (super.frameAvailableSoon().also { result = it }) mRenderHandler?.draw(null)
        return result
    }

    @SuppressLint("NewApi")
    override fun prepare() {
        if (DEBUG) Log.i(TAG, "prepare: ")
        mTrackIndex = -1
        mMuxerStarted = false.also { mIsEOS = it }

        val videoCodecInfo: MediaCodecInfo? =
            selectVideoCodec(MIME_TYPE)
        if (videoCodecInfo == null) {
            Log.e(
                TAG,
                "Unable to find an appropriate codec for $MIME_TYPE"
            )
            return
        }
        if (DEBUG) Log.i(
            TAG,
            "selected codec: " + videoCodecInfo.name
        )

        val format =
            MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        ) // API >= 18

        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate())
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
        if (DEBUG) Log.i(TAG, "format: $format")

        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        mMediaCodec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        // get Surface for encoder input
        // this method only can call between #configure and #start
        // get Surface for encoder input
        // this method only can call between #configure and #start
        mSurface = mMediaCodec!!.createInputSurface() // API >= 18

        mMediaCodec!!.start()
        if (DEBUG) Log.i(TAG, "prepare finishing")
        if (mListener != null) {
            try {
                mListener!!.onPrepared(this)
            } catch (e: Exception) {
                Log.e(TAG, "prepare:", e)
            }
        }
    }

    /**
     * select the first codec that match a specific MIME type
     * @param mimeType
     * @return null if no codec matched
     */
    private fun selectVideoCodec(mimeType: String): MediaCodecInfo? {
        if (DEBUG) Log.v(TAG, "selectVideoCodec:")

        // get the list of available codecs
        val numCodecs = MediaCodecList.getCodecCount()
        for (i in 0 until numCodecs) {
            val codecInfo = MediaCodecList.getCodecInfoAt(i)
            if (!codecInfo.isEncoder) {    // skipp decoder
                continue
            }
            // select first codec that match a specific MIME type and color format
            val types = codecInfo.supportedTypes
            for (j in types.indices) {
                if (types[j].equals(mimeType, ignoreCase = true)) {
                    if (DEBUG) Log.i(
                        TAG,
                        "codec:" + codecInfo.name + ",MIME=" + types[j]
                    )
                    val format = selectColorFormat(codecInfo, mimeType)
                    if (format > 0) {
                        return codecInfo
                    }
                }
            }
        }
        return null
    }

    /**
     * select color format available on specific codec and we can use.
     * @return 0 if no colorFormat is matched
     */
    protected fun selectColorFormat(codecInfo: MediaCodecInfo, mimeType: String): Int {
        if (DEBUG) Log.i(
            TAG,
            "selectColorFormat: "
        )
        var result = 0
        val caps: CodecCapabilities
        try {
            Thread.currentThread().priority = Thread.MAX_PRIORITY
            caps = codecInfo.getCapabilitiesForType(mimeType)
        } finally {
            Thread.currentThread().priority = Thread.NORM_PRIORITY
        }
        var colorFormat: Int
        for (i in caps.colorFormats.indices) {
            colorFormat = caps.colorFormats[i]
            if (isRecognizedViewoFormat(colorFormat)) {
                if (result == 0) result = colorFormat
                break
            }
        }
        if (result == 0) Log.e(
            TAG,
            "couldn't find a good color format for " + codecInfo.name + " / " + mimeType
        )
        return result
    }


    private fun isRecognizedViewoFormat(colorFormat: Int): Boolean {
        if (DEBUG) Log.i(
            TAG,
            "isRecognizedViewoFormat:colorFormat=$colorFormat"
        )
        val n = if (recognizedFormats != null) recognizedFormats!!.size else 0
        for (i in 0 until n) {
            if (recognizedFormats!![i] == colorFormat) {
                return true
            }
        }
        return false
    }


    override fun run() {
    }

    fun setEglContext(shared_context: EGLContext?, tex_id: Int) {
        mRenderHandler!!.setEglContext(shared_context!!, tex_id, mSurface!!, true)
    }


    override fun release() {
        if (DEBUG) Log.i(TAG, "release:")
        if (mSurface != null) {
            mSurface!!.release()
            mSurface = null
        }
        if (mRenderHandler != null) {
            mRenderHandler?.release()
            mRenderHandler = null
        }
        super.release()
    }

    private fun calcBitRate(): Int {
        val bitrate =
            (BPP * FRAME_RATE * mWidth * mHeight) as Int
        Log.i(
            TAG,
            String.format("bitrate=%5.2f[Mbps]", bitrate / 1024f / 1024f)
        )
        return bitrate
    }






}