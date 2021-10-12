package com.wangkm.audiovideorecordingsample.encoder

import android.media.*
import android.os.Process
import android.util.Log
import java.nio.ByteBuffer

/**
 * @author: created by wangkm
 * @time: 2021/10/11 16:34
 * @desc：
 * @email: 1240413544@qq.com
 */

class MediaAudioEncoder: MediaEncoder {

    private val DEBUG = false // TODO set false on release

    private val TAG = "MediaAudioEncoder"

    private val MIME_TYPE = "audio/mp4a-latm"
    private val SAMPLE_RATE =
        44100 // 44.1[KHz] is only setting guaranteed to be available on all devices.

    private val BIT_RATE = 64000
    val SAMPLES_PER_FRAME = 1024 // AAC, bytes/frame/channel

    val FRAMES_PER_BUFFER = 25 // AAC, frame/buffer/sec


    private var mAudioThread: AudioThread? = null

    constructor(muxer:MediaMuxerWrapper?,listener:MediaEncoderListener):super(muxer, listener){
    }

    override fun startRecording() {
        super.startRecording()
        // create and execute audio capturing thread using internal mic
        if (mAudioThread == null) {
            mAudioThread = AudioThread()
            mAudioThread!!.start()
        }
    }



    override fun prepare() {
        if (DEBUG) Log.v(TAG, "prepare:")
        mTrackIndex = -1
        mMuxerStarted = false.also { mIsEOS = it }
        // prepare MediaCodec for AAC encoding of audio data from inernal mic.
        // prepare MediaCodec for AAC encoding of audio data from inernal mic.
        val audioCodecInfo: MediaCodecInfo? =
            selectAudioCodec(MIME_TYPE)
        if (audioCodecInfo == null) {
            Log.e(
                TAG,
                "Unable to find an appropriate codec for " + MIME_TYPE
            )
            return
        }
        if (DEBUG) Log.i(
            TAG,
            "selected codec: " + audioCodecInfo.name
        )

        val audioFormat = MediaFormat.createAudioFormat(
            MIME_TYPE,
            SAMPLE_RATE,
            1
        )
        audioFormat.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AACObjectLC
        )
        audioFormat.setInteger(
            MediaFormat.KEY_CHANNEL_MASK,
            AudioFormat.CHANNEL_IN_MONO
        )
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
//		audioFormat.setLong(MediaFormat.KEY_MAX_INPUT_SIZE, inputFile.length());
//      audioFormat.setLong(MediaFormat.KEY_DURATION, (long)durationInMs );
        //		audioFormat.setLong(MediaFormat.KEY_MAX_INPUT_SIZE, inputFile.length());
//      audioFormat.setLong(MediaFormat.KEY_DURATION, (long)durationInMs );
        if (DEBUG) Log.i(
            TAG,
            "format: $audioFormat"
        )
        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE)
        mMediaCodec!!.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mMediaCodec!!.start()
        if (DEBUG) Log.i(TAG, "prepare finishing")
        if (mListener != null) {
            try {
                mListener!!.onPrepared(this)
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "prepare:", e)
            }
        }
    }

    override fun release() {
        mAudioThread = null
        super.release()
    }

    private val AUDIO_SOURCES = intArrayOf(
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.DEFAULT,
        MediaRecorder.AudioSource.CAMCORDER,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.VOICE_RECOGNITION
    )

    /**
     * Thread to capture audio data from internal mic as uncompressed 16bit PCM data
     * and write them to the MediaCodec encoder
     */
    inner private class AudioThread : Thread() {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            var cnt = 0
            try {
                val min_buffer_size = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                var buffer_size: Int =
                    SAMPLES_PER_FRAME * FRAMES_PER_BUFFER
                if (buffer_size < min_buffer_size) buffer_size =
                    (min_buffer_size / SAMPLES_PER_FRAME + 1) * SAMPLES_PER_FRAME * 2
                var audioRecord: AudioRecord? = null
                for (source in AUDIO_SOURCES) {
                    try {
                        audioRecord = AudioRecord(
                            source,
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            buffer_size
                        )
                        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) audioRecord =
                            null
                    } catch (e: java.lang.Exception) {
                        audioRecord = null
                    }
                    if (audioRecord != null) break
                }
                if (audioRecord != null) {
                    try {
                        if (mIsCapturing) {
                            if (DEBUG) Log.v(
                                TAG,
                                "AudioThread:start audio recording"
                            )
                            val buf =
                                ByteBuffer.allocateDirect(SAMPLES_PER_FRAME)
                            var readBytes: Int
                            audioRecord.startRecording()
                            try {
                                while (mIsCapturing && !mRequestStop && !mIsEOS) {

                                    // read audio data from internal mic
                                    buf.clear()
                                    readBytes =
                                        audioRecord.read(buf, SAMPLES_PER_FRAME)
                                    if (readBytes > 0) {
                                        // set audio data to encoder
                                        buf.position(readBytes)
                                        buf.flip()
                                        encode(buf, readBytes, getPTSUs())
                                        frameAvailableSoon()
                                        cnt++
                                    }
                                }
                                frameAvailableSoon()
                            } finally {
                                audioRecord.stop()
                            }
                        }
                    } finally {
                        audioRecord.release()
                    }
                } else {
                    Log.e(TAG, "failed to initialize AudioRecord")
                }
            } catch (e: java.lang.Exception) {
                Log.e(TAG, "AudioThread#run", e)
            }
            if (cnt == 0) {
                val buf =
                    ByteBuffer.allocateDirect(SAMPLES_PER_FRAME)
                var i = 0
                while (mIsCapturing && i < 5) {
                    buf.position(SAMPLES_PER_FRAME)
                    buf.flip()
                    try {
                        encode(buf, SAMPLES_PER_FRAME, getPTSUs())
                        frameAvailableSoon()
                    } catch (e: java.lang.Exception) {
                        break
                    }
                    synchronized(this) {
                        try {
                            (this as Object).wait(50)
                        } catch (e: InterruptedException) {
                        }
                    }
                    i++
                }
            }
            if (DEBUG) Log.v(
                TAG,
                "AudioThread:finished"
            )
        }
    }

    /**
     * select the first codec that match a specific MIME type
     * @param mimeType
     * @return
     */
    private fun selectAudioCodec(mimeType: String): MediaCodecInfo? {
        if (DEBUG) Log.v(TAG, "selectAudioCodec:")
        var result: MediaCodecInfo? = null
        // get the list of available codecs
        val numCodecs = MediaCodecList.getCodecCount()
        LOOP@ for (i in 0 until numCodecs) {
            val codecInfo = MediaCodecList.getCodecInfoAt(i)
            if (!codecInfo.isEncoder) {    // skipp decoder
                continue
            }
            val types = codecInfo.supportedTypes
            for (j in types.indices) {
                if (DEBUG) Log.i(
                    TAG,
                    "supportedType:" + codecInfo.name + ",MIME=" + types[j]
                )
                if (types[j].equals(mimeType, ignoreCase = true)) {
                    if (result == null) {
                        result = codecInfo
                        break@LOOP
                    }
                }
            }
        }
        return result
    }
}