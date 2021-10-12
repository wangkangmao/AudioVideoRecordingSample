package com.wangkm.audiovideorecordingsample.encoder

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.util.Log
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer

/**
 * @author: created by wangkm
 * @time: 2021/10/11 15:26
 * @desc：
 * @email: 1240413544@qq.com
 */

abstract class MediaEncoder : Runnable {

    private val DEBUG = true // TODO set false on release

    private val TAG = "MediaEncoder"

    protected val TIMEOUT_USEC = 10000 // 10[msec]

    protected val MSG_FRAME_AVAILABLE = 1
    protected val MSG_STOP_RECORDING = 9

    interface MediaEncoderListener {
        fun onPrepared(encoder: MediaEncoder?)
        fun onStopped(encoder: MediaEncoder?)
    }

    protected val mSync = Object()

    /**
     * Flag that indicate this encoder is capturing now.
     */
    @Volatile
    protected var mIsCapturing = false

    /**
     * Flag that indicate the frame data will be available soon.
     */
    private var mRequestDrain = 0

    /**
     * Flag to request stop capturing
     */
    @Volatile
    protected var mRequestStop = false

    /**
     * Flag that indicate encoder received EOS(End Of Stream)
     */
    protected var mIsEOS = false

    /**
     * Flag the indicate the muxer is running
     */
    protected var mMuxerStarted = false

    /**
     * Track Number
     */
    protected var mTrackIndex = 0

    /**
     * MediaCodec instance for encoding
     */
    protected var mMediaCodec // API >= 16(Android4.1.2)
            : MediaCodec? = null

    /**
     * Weak refarence of MediaMuxerWarapper instance
     */
    protected var mWeakMuxer: WeakReference<MediaMuxerWrapper>? = null

    /**
     * BufferInfo instance for dequeuing
     */
    private var mBufferInfo // API >= 16(Android4.1.2)
            : MediaCodec.BufferInfo? = null

    protected var mListener: MediaEncoderListener? = null

    constructor(muxer:MediaMuxerWrapper?,listener:MediaEncoderListener){

        if (listener == null) throw NullPointerException("MediaEncoderListener is null")
        if (muxer == null) throw NullPointerException("MediaMuxerWrapper is null")
        mWeakMuxer = WeakReference(muxer)
        muxer.addEncoder(this)
        mListener = listener
        synchronized(mSync) {

            // create BufferInfo here for effectiveness(to reduce GC)
            mBufferInfo = MediaCodec.BufferInfo()
            // wait for starting thread
            Thread(this, javaClass.simpleName).start()
            try {
                mSync.wait()
            } catch (e: InterruptedException) {
            }
        }

    }
    fun getOutputPath(): String? {
        val muxer = mWeakMuxer!!.get()
        return muxer?.getOutputPath()
    }


    /**
     * the method to indicate frame data is soon available or already available
     * @return return true if encoder is ready to encod.
     */
    open fun frameAvailableSoon(): Boolean {
//    	if (DEBUG) Log.v(TAG, "frameAvailableSoon");
        synchronized(mSync) {
            if (!mIsCapturing || mRequestStop) {
                return false
            }
            mRequestDrain++
            mSync.notifyAll()
        }
        return true
    }

    /*
    * prepareing method for each sub class
    * this method should be implemented in sub class, so set this as abstract method
    * @throws IOException
    */
    @Throws(IOException::class)
    abstract fun prepare()

    /*package*/
    open fun startRecording() {
        if (DEBUG) Log.v(TAG, "startRecording")
        synchronized(mSync) {
            mIsCapturing = true
            mRequestStop = false
            mSync.notifyAll()
        }
    }

    /**
     * the method to request stop encoding
     */
    /*package*/
    open fun stopRecording() {
        if (DEBUG) Log.v(TAG, "stopRecording")
        synchronized(mSync) {
            if (!mIsCapturing || mRequestStop) {
                return
            }
            mRequestStop = true // for rejecting newer frame
            mSync.notifyAll()
        }
    }

    override fun run() {
//		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        synchronized(mSync) {
            mRequestStop = false
            mRequestDrain = 0
            mSync.notify()
        }
        val isRunning = true
        var localRequestStop: Boolean
        var localRequestDrain: Boolean
        while (isRunning) {
            synchronized(mSync) {
                localRequestStop = mRequestStop
                localRequestDrain = mRequestDrain > 0
                if (localRequestDrain) mRequestDrain--
            }
            if (localRequestStop) {
                drain()
                // request stop recording
                signalEndOfInputStream()
                // process output data again for EOS signale
                drain()
                // release all related objects
                release()
                break
            }
            if (localRequestDrain) {
                drain()
            } else {
                synchronized(mSync) {
                    try {
                        mSync.wait()
                    } catch (e: InterruptedException) {
                        return@synchronized
                    }
                }
            }
        } // end of while

        if (DEBUG) Log.d(TAG, "Encoder thread exiting")
        synchronized(mSync) {
            mRequestStop = true
            mIsCapturing = false
        }
    }
    //********************************************************************************
    //********************************************************************************
    /**
     * Release all releated objects
     */
    protected open fun release() {
        if (DEBUG) Log.d(TAG, "release:")
        try {
            mListener!!.onStopped(this)
        } catch (e: Exception) {
            Log.e(TAG, "failed onStopped", e)
        }
        mIsCapturing = false
        if (mMediaCodec != null) {
            try {
                mMediaCodec!!.stop()
                mMediaCodec!!.release()
                mMediaCodec = null
            } catch (e: Exception) {
                Log.e(TAG, "failed releasing MediaCodec", e)
            }
        }
        if (mMuxerStarted) {
            val muxer = if (mWeakMuxer != null) mWeakMuxer!!.get() else null
            if (muxer != null) {
                try {
                    muxer.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "failed stopping muxer", e)
                }
            }
        }
        mBufferInfo = null
    }

    protected open fun signalEndOfInputStream() {
        if (DEBUG) Log.d(TAG, "sending EOS to encoder")
        // signalEndOfInputStream is only avairable for video encoding with surface
        // and equivalent sending a empty buffer with BUFFER_FLAG_END_OF_STREAM flag.
//		mMediaCodec.signalEndOfInputStream();	// API >= 18
        encode(null, 0, getPTSUs())
    }

    /**
     * Method to set byte array to the MediaCodec encoder
     * @param buffer
     * @param length　length of byte array, zero means EOS.
     * @param presentationTimeUs
     */
    protected open fun encode(
        buffer: ByteBuffer?,
        length: Int,
        presentationTimeUs: Long
    ) {
        if (!mIsCapturing) return
        val inputBuffers = mMediaCodec!!.inputBuffers
        while (mIsCapturing) {
            val inputBufferIndex =
                mMediaCodec!!.dequeueInputBuffer(TIMEOUT_USEC.toLong())
            if (inputBufferIndex >= 0) {
                val inputBuffer = inputBuffers[inputBufferIndex]
                inputBuffer.clear()
                if (buffer != null) {
                    inputBuffer.put(buffer)
                }
                //	            if (DEBUG) Log.v(TAG, "encode:queueInputBuffer");
                if (length <= 0) {
                    // send EOS
                    mIsEOS = true
                    if (DEBUG) Log.i(
                        TAG,
                        "send BUFFER_FLAG_END_OF_STREAM"
                    )
                    mMediaCodec!!.queueInputBuffer(
                        inputBufferIndex, 0, 0,
                        presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    break
                } else {
                    mMediaCodec!!.queueInputBuffer(
                        inputBufferIndex, 0, length,
                        presentationTimeUs, 0
                    )
                }
                break
            } else if (inputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait for MediaCodec encoder is ready to encode
                // nothing to do here because MediaCodec#dequeueInputBuffer(TIMEOUT_USEC)
                // will wait for maximum TIMEOUT_USEC(10msec) on each call
            }
        }
    }

    /**
     * drain encoded data and write them to muxer
     */
    @SuppressLint("NewApi")
    protected open fun drain() {
        if (mMediaCodec == null) return
        var encoderOutputBuffers =
            mMediaCodec!!.outputBuffers
        var encoderStatus: Int
        var count = 0
        val muxer = mWeakMuxer!!.get()
        if (muxer == null) {
//        	throw new NullPointerException("muxer is unexpectedly null");
            Log.w(TAG, "muxer is unexpectedly null")
            return
        }
        while (mIsCapturing) {
            // get encoded data with maximum timeout duration of TIMEOUT_USEC(=10[msec])
            encoderStatus =
                mMediaCodec!!.dequeueOutputBuffer(mBufferInfo!!, TIMEOUT_USEC.toLong())
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // wait 5 counts(=TIMEOUT_USEC x 5 = 50msec) until data/EOS come
                if (!mIsEOS) {
                    if (++count > 5) break  // out of while
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (DEBUG) Log.v(
                    TAG,
                    "INFO_OUTPUT_BUFFERS_CHANGED"
                )
                // this shoud not come when encoding
                encoderOutputBuffers = mMediaCodec!!.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (DEBUG) Log.v(
                    TAG,
                    "INFO_OUTPUT_FORMAT_CHANGED"
                )
                // this status indicate the output format of codec is changed
                // this should come only once before actual encoded data
                // but this status never come on Android4.3 or less
                // and in that case, you should treat when MediaCodec.BUFFER_FLAG_CODEC_CONFIG come.
                if (mMuxerStarted) {    // second time request is error
                    throw RuntimeException("format changed twice")
                }
                // get output format from codec and pass them to muxer
                // getOutputFormat should be called after INFO_OUTPUT_FORMAT_CHANGED otherwise crash.
                val format = mMediaCodec!!.outputFormat // API >= 16
                mTrackIndex = muxer.addTrack(format)
                mMuxerStarted = true
                if (!muxer.start()) {
                    // we should wait until muxer is ready
                    synchronized(muxer) {
                        while (!muxer.isStarted()) try {
                            (muxer as Object).wait(100)
                        } catch (e: InterruptedException) {
                            break
                        }
                    }
                }
            } else if (encoderStatus < 0) {
                // unexpected status
                if (DEBUG) Log.w(
                    TAG,
                    "drain:unexpected result from encoder#dequeueOutputBuffer: $encoderStatus"
                )
            } else {
                val encodedData = encoderOutputBuffers[encoderStatus]
                    ?: // this never should come...may be a MediaCodec internal error
                    throw RuntimeException("encoderOutputBuffer $encoderStatus was null")
                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // You shoud set output format to muxer here when you target Android4.3 or less
                    // but MediaCodec#getOutputFormat can not call here(because INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                    // therefor we should expand and prepare output format from buffer data.
                    // This sample is for API>=18(>=Android 4.3), just ignore this flag here
                    if (DEBUG) Log.d(
                        TAG,
                        "drain:BUFFER_FLAG_CODEC_CONFIG"
                    )
                    mBufferInfo!!.size = 0
                }
                if (mBufferInfo!!.size != 0) {
                    // encoded data is ready, clear waiting counter
                    count = 0
                    if (!mMuxerStarted) {
                        // muxer is not ready...this will prrograming failure.
                        throw RuntimeException("drain:muxer hasn't started")
                    }
                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    mBufferInfo!!.presentationTimeUs = getPTSUs()
                    muxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo)
                    prevOutputPTSUs = mBufferInfo!!.presentationTimeUs
                }
                // return buffer to encoder
                mMediaCodec!!.releaseOutputBuffer(encoderStatus, false)
                if (mBufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    // when EOS come.
                    mIsCapturing = false
                    break // out of while
                }
            }
        }
    }

    /**
     * previous presentationTimeUs for writing
     */
    private var prevOutputPTSUs: Long = 0

    /**
     * get next encoding presentationTimeUs
     * @return
     */
    protected open fun getPTSUs(): Long {
        var result = System.nanoTime() / 1000L
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs) result = prevOutputPTSUs - result + result
        return result
    }


}