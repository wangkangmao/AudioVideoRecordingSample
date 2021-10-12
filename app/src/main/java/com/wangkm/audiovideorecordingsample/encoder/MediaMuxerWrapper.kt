package com.wangkm.audiovideorecordingsample.encoder

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author: created by wangkm
 * @time: 2021/10/11 16:26
 * @desc：
 * @email: 1240413544@qq.com
 */

class MediaMuxerWrapper {

    private val DEBUG = true // TODO set false on release

    private val TAG = "MediaMuxerWrapper"

    private val DIR_NAME = "AVRecSample"
    private val mDateTimeFormat =
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US)

    private var mLock = Object()
    private lateinit var mOutputPath: String
    private var mMediaMuxer: MediaMuxer? = null
    private var mEncoderCount = 0
    private var mStatredCount: Int = 0
    private var mIsStarted = false
    private var mVideoEncoder: MediaEncoder? = null
    private var mAudioEncoder: MediaEncoder? = null


    @SuppressLint("NewApi")
    @Throws(IOException::class)
    constructor(ext: String) {
        var ext = ext
        if (TextUtils.isEmpty(ext)) {
            ext = ".mp4"
        }
        mOutputPath = try {
            getCaptureFile(
                Environment.DIRECTORY_MOVIES,
                ext
            ).toString()
        } catch (e: NullPointerException) {
            throw RuntimeException("This app has no permission of writing external storage")
        }
        mMediaMuxer = MediaMuxer(mOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mEncoderCount = 0.also { mStatredCount = it }
        mIsStarted = false
    }

    //**********************************************************************
    //**********************************************************************
    /**
     * assign encoder to this calss. this is called from encoder.
     * @param encoder instance of MediaVideoEncoder or MediaAudioEncoder
     */
    fun addEncoder(encoder: MediaEncoder) {
        if (encoder is MediaVideoEncoder) {
            require(mVideoEncoder == null) { "Video encoder already added." }
            mVideoEncoder = encoder
        } else if (encoder is MediaAudioEncoder) {
            require(mAudioEncoder == null) { "Video encoder already added." }
            mAudioEncoder = encoder
        } else throw IllegalArgumentException("unsupported encoder")
        mEncoderCount =
            (if (mVideoEncoder != null) 1 else 0) + if (mAudioEncoder != null) 1 else 0
    }


    fun getOutputPath(): String? {
        return mOutputPath
    }

    @Throws(IOException::class)
    fun prepare() {
        if (mVideoEncoder != null) mVideoEncoder?.prepare()
        if (mAudioEncoder != null) mAudioEncoder?.prepare()
    }

    fun startRecording() {
        if (mVideoEncoder != null) mVideoEncoder?.startRecording()
        if (mAudioEncoder != null) mAudioEncoder?.startRecording()
    }

    fun stopRecording() {
        if (mVideoEncoder != null) mVideoEncoder?.stopRecording()
        mVideoEncoder = null
        if (mAudioEncoder != null) mAudioEncoder?.stopRecording()
        mAudioEncoder = null
    }

    @Synchronized
    fun isStarted(): Boolean {
        return mIsStarted
    }

    /**
     * request start recording from encoder
     * @return true when muxer is ready to write
     */
    /*package*/
    @SuppressLint("NewApi")
    @Synchronized
    fun start(): Boolean {
        if (DEBUG) Log.v(
            TAG,
            "start:"
        )
        mStatredCount++
        if (mEncoderCount > 0 && mStatredCount == mEncoderCount) {
            mMediaMuxer!!.start()
            mIsStarted = true
            mLock.notifyAll()
            if (DEBUG) Log.v(
                TAG,
                "MediaMuxer started:"
            )
        }
        return mIsStarted
    }

    /**
     * request stop recording from encoder when encoder received EOS
     */
    /*package*/
    @SuppressLint("NewApi")
    @Synchronized
    fun stop() {
        if (DEBUG) Log.v(
            TAG,
            "stop:mStatredCount=$mStatredCount"
        )
        mStatredCount--
        if (mEncoderCount > 0 && mStatredCount <= 0) {
            mMediaMuxer!!.stop()
            mMediaMuxer?.release()
            mIsStarted = false
            if (DEBUG) Log.v(
                TAG,
                "MediaMuxer stopped:"
            )
        }
    }

    /**
     * assign encoder to muxer
     * @param format
     * @return minus value indicate error
     */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Synchronized
    fun addTrack(format: MediaFormat): Int {
        check(!mIsStarted) { "muxer already started" }
        val trackIx = mMediaMuxer!!.addTrack(format)
        if (DEBUG) Log.i(
            TAG,
            "addTrack:trackNum=$mEncoderCount,trackIx=$trackIx,format=$format"
        )
        return trackIx
    }

    /**
     * write encoded data to muxer
     * @param trackIndex
     * @param byteBuf
     * @param bufferInfo
     */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Synchronized
    fun writeSampleData(
        trackIndex: Int,
        byteBuf: ByteBuffer?,
        bufferInfo: MediaCodec.BufferInfo?
    ) {
        if (mStatredCount > 0) mMediaMuxer!!.writeSampleData(
            trackIndex,
            byteBuf!!,
            bufferInfo!!
        )
    }

    //**********************************************************************
    //**********************************************************************
    /**
     * generate output file
     * @param type Environment.DIRECTORY_MOVIES / Environment.DIRECTORY_DCIM etc.
     * @param ext .mp4(.m4a for audio) or .png
     * @return return null when this app has no writing permission to external storage.
     */
    fun getCaptureFile(type: String?, ext: String): File? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(type),
            DIR_NAME
        )
        Log.d(TAG, "path=$dir")
        dir.mkdirs()
        return if (dir.canWrite()) {
            File(dir, getDateTimeString() + ext)
        } else null
    }

    /**
     * get current date and time as String
     * @return
     */
    private fun getDateTimeString(): String {
        val now = GregorianCalendar()
        return mDateTimeFormat.format(now.time)
    }

}