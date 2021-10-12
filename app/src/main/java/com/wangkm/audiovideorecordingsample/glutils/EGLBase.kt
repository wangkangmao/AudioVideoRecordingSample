package com.wangkm.audiovideorecordingsample.glutils

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLSurface
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * @author: created by wangkm
 * @time: 2021/10/11 18:58
 * @desc：
 * @email: 1240413544@qq.com
 */

class EGLBase {

    // API >= 17
    private val DEBUG = false // TODO set false on release

    private val TAG = "EGLBase"

    private val EGL_RECORDABLE_ANDROID = 0x3142

    private var mEglConfig: EGLConfig? = null

    @SuppressLint("NewApi")
    private var mEglContext = EGL14.EGL_NO_CONTEXT

    @SuppressLint("NewApi")
    private var mEglDisplay = EGL14.EGL_NO_DISPLAY

    @SuppressLint("NewApi")
    private var mDefaultContext = EGL14.EGL_NO_CONTEXT

    inner class EglSurface {
        private lateinit var mEgl: EGLBase

        @SuppressLint("NewApi")
        private var mEglSurface = EGL14.EGL_NO_SURFACE
        val width: Int
        val height: Int

        internal constructor(egl: EGLBase, surface: Any) {
            if (DEBUG) Log.v(
                TAG,
                "EglSurface:"
            )
            require(
                !(surface !is SurfaceView
                        && surface !is Surface
                        && surface !is SurfaceHolder
                        && surface !is SurfaceTexture)
            ) { "unsupported surface" }
            mEgl = egl
            mEglSurface = mEgl.createWindowSurface(surface)
            width = mEgl.querySurface(mEglSurface, EGL14.EGL_WIDTH)
            height = mEgl.querySurface(mEglSurface, EGL14.EGL_HEIGHT)
            if (DEBUG) Log.v(
                TAG,
                String.format("EglSurface:size(%d,%d)", width, height)
            )
        }

        internal constructor(egl: EGLBase, width: Int, height: Int) {
            if (DEBUG) Log.v(
                TAG,
                "EglSurface:"
            )
            mEgl = egl
            mEglSurface = mEgl.createOffscreenSurface(width, height)
            this.width = width
            this.height = height
        }

        fun makeCurrent() {
            mEgl.makeCurrent(mEglSurface)
        }

        fun swap() {
            mEgl.swap(mEglSurface)
        }

        val context: EGLContext
            get() = mEgl.getContext()!!

        @SuppressLint("NewApi")
        fun release() {
            if (DEBUG) Log.v(
                TAG,
                "EglSurface:release:"
            )
            mEgl.makeDefault()
            mEgl.destroyWindowSurface(mEglSurface)
            mEglSurface = EGL14.EGL_NO_SURFACE
        }

    }

    fun EGLBase(
        shared_context: EGLContext,
        with_depth_buffer: Boolean,
        isRecordable: Boolean
    ) {
        if (DEBUG) Log.v(
            TAG,
            "EGLBase:"
        )
        init(shared_context, with_depth_buffer, isRecordable)
    }

    @SuppressLint("NewApi")
    fun release() {
        if (DEBUG) Log.v(
            TAG,
            "release:"
        )
        if (mEglDisplay !== EGL14.EGL_NO_DISPLAY) {
            destroyContext()
            EGL14.eglTerminate(mEglDisplay)
            EGL14.eglReleaseThread()
        }
        mEglDisplay = EGL14.EGL_NO_DISPLAY
        mEglContext = EGL14.EGL_NO_CONTEXT
    }

    fun createFromSurface(surface: Any): EglSurface? {
        if (DEBUG) Log.v(
            TAG,
            "createFromSurface:"
        )
        val eglSurface: EglSurface =
            EglSurface(this, surface)
        eglSurface.makeCurrent()
        return eglSurface
    }

    fun createOffscreen(
        width: Int,
        height: Int
    ): EglSurface? {
        if (DEBUG) Log.v(
            TAG,
            "createOffscreen:"
        )
        val eglSurface: EglSurface =
            EglSurface(this, width, height)
        eglSurface.makeCurrent()
        return eglSurface
    }

    fun getContext(): EGLContext? {
        return mEglContext
    }

    @SuppressLint("NewApi")
    fun querySurface(eglSurface: EGLSurface?, what: Int): Int {
        val value = IntArray(1)
        EGL14.eglQuerySurface(mEglDisplay, eglSurface, what, value, 0)
        return value[0]
    }

    @SuppressLint("NewApi")
    private fun init(
        shared_context: EGLContext,
        with_depth_buffer: Boolean,
        isRecordable: Boolean
    ) {
        var shared_context: EGLContext? = shared_context
        if (DEBUG) Log.v(
            TAG,
            "init:"
        )
        if (mEglDisplay !== EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("EGL already set up")
        }
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            mEglDisplay = null
            throw RuntimeException("eglInitialize failed")
        }
        shared_context = shared_context ?: EGL14.EGL_NO_CONTEXT
        if (mEglContext === EGL14.EGL_NO_CONTEXT) {
            mEglConfig = getConfig(with_depth_buffer, isRecordable)
            if (mEglConfig == null) {
                throw RuntimeException("chooseConfig failed")
            }
            // create EGL rendering context
            mEglContext = createContext(shared_context)
        }
        // confirm whether the EGL rendering context is successfully created
        val values = IntArray(1)
        EGL14.eglQueryContext(mEglDisplay, mEglContext, EGL14.EGL_CONTEXT_CLIENT_VERSION, values, 0)
        if (DEBUG) Log.d(
            TAG,
            "EGLContext created, client version " + values[0]
        )
        makeDefault() // makeCurrent(EGL14.EGL_NO_SURFACE);
    }

    /**
     * change context to draw this window surface
     * @return
     */
    @SuppressLint("NewApi")
    private fun makeCurrent(surface: EGLSurface?): Boolean {
//		if (DEBUG) Log.v(TAG, "makeCurrent:");
        if (mEglDisplay == null) {
            if (DEBUG) Log.d(
                TAG,
                "makeCurrent:eglDisplay not initialized"
            )
        }
        if (surface == null || surface === EGL14.EGL_NO_SURFACE) {
            val error = EGL14.eglGetError()
            if (error == EGL14.EGL_BAD_NATIVE_WINDOW) {
                Log.e(
                    TAG,
                    "makeCurrent:returned EGL_BAD_NATIVE_WINDOW."
                )
            }
            return false
        }
        // attach EGL renderring context to specific EGL window surface
        if (!EGL14.eglMakeCurrent(mEglDisplay, surface, surface, mEglContext)) {
            Log.w(
                TAG,
                "eglMakeCurrent:" + EGL14.eglGetError()
            )
            return false
        }
        return true
    }

    @SuppressLint("NewApi")
    private fun makeDefault() {
        if (DEBUG) Log.v(
            TAG,
            "makeDefault:"
        )
        if (!EGL14.eglMakeCurrent(
                mEglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
        ) {
            Log.w("TAG", "makeDefault" + EGL14.eglGetError())
        }
    }

    @SuppressLint("NewApi")
    private fun swap(surface: EGLSurface): Int {
//		if (DEBUG) Log.v(TAG, "swap:");
        if (!EGL14.eglSwapBuffers(mEglDisplay, surface)) {
            val err = EGL14.eglGetError()
            if (DEBUG) Log.w(
                TAG,
                "swap:err=$err"
            )
            return err
        }
        return EGL14.EGL_SUCCESS
    }

    @SuppressLint("NewApi")
    private fun createContext(shared_context: EGLContext?): EGLContext {
//		if (DEBUG) Log.v(TAG, "createContext:");
        val attrib_list = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        val context =
            EGL14.eglCreateContext(mEglDisplay, mEglConfig, shared_context, attrib_list, 0)
        checkEglError("eglCreateContext")
        return context
    }

    @SuppressLint("NewApi")
    private fun destroyContext() {
        if (DEBUG) Log.v(
            TAG,
            "destroyContext:"
        )
        if (!EGL14.eglDestroyContext(mEglDisplay, mEglContext)) {
            Log.e(
                "destroyContext",
                "display:$mEglDisplay context: $mEglContext"
            )
            Log.e(
                TAG,
                "eglDestroyContex:" + EGL14.eglGetError()
            )
        }
        mEglContext = EGL14.EGL_NO_CONTEXT
        if (mDefaultContext !== EGL14.EGL_NO_CONTEXT) {
            if (!EGL14.eglDestroyContext(mEglDisplay, mDefaultContext)) {
                Log.e(
                    "destroyContext",
                    "display:$mEglDisplay context: $mDefaultContext"
                )
                Log.e(
                    TAG,
                    "eglDestroyContex:" + EGL14.eglGetError()
                )
            }
            mDefaultContext = EGL14.EGL_NO_CONTEXT
        }
    }

    @SuppressLint("NewApi")
    private fun createWindowSurface(nativeWindow: Any): EGLSurface? {
        if (DEBUG) Log.v(
            TAG,
            "createWindowSurface:nativeWindow=$nativeWindow"
        )
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_NONE
        )
        var result: EGLSurface? = null
        try {
            result = EGL14.eglCreateWindowSurface(
                mEglDisplay,
                mEglConfig,
                nativeWindow,
                surfaceAttribs,
                0
            )
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "eglCreateWindowSurface", e)
        }
        return result
    }

    /**
     * Creates an EGL surface associated with an offscreen buffer.
     */
    @SuppressLint("NewApi")
    private fun createOffscreenSurface(width: Int, height: Int): EGLSurface? {
        if (DEBUG) Log.v(
            TAG,
            "createOffscreenSurface:"
        )
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        var result: EGLSurface? = null
        try {
            result = EGL14.eglCreatePbufferSurface(mEglDisplay, mEglConfig, surfaceAttribs, 0)
            checkEglError("eglCreatePbufferSurface")
            if (result == null) {
                throw RuntimeException("surface was null")
            }
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "createOffscreenSurface", e)
        } catch (e: RuntimeException) {
            Log.e(TAG, "createOffscreenSurface", e)
        }
        return result
    }

    @SuppressLint("NewApi")
    private fun destroyWindowSurface(surface: EGLSurface) {
        var surface = surface
        if (DEBUG) Log.v(
            TAG,
            "destroySurface:"
        )
        if (surface !== EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(
                mEglDisplay,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(mEglDisplay, surface)
        }
        surface = EGL14.EGL_NO_SURFACE
        if (DEBUG) Log.v(
            TAG,
            "destroySurface:finished"
        )
    }

    @SuppressLint("NewApi")
    private fun checkEglError(msg: String) {
        var error: Int
        if (EGL14.eglGetError().also { error = it } != EGL14.EGL_SUCCESS) {
            throw RuntimeException(
                "$msg: EGL error: 0x" + Integer.toHexString(
                    error
                )
            )
        }
    }

    @SuppressLint("NewApi")
    private fun getConfig(
        with_depth_buffer: Boolean,
        isRecordable: Boolean
    ): EGLConfig? {
        val attribList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE,
            EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_RED_SIZE,
            8,
            EGL14.EGL_GREEN_SIZE,
            8,
            EGL14.EGL_BLUE_SIZE,
            8,
            EGL14.EGL_ALPHA_SIZE,
            8,
            EGL14.EGL_NONE,
            EGL14.EGL_NONE,  //EGL14.EGL_STENCIL_SIZE, 8,
            EGL14.EGL_NONE,
            EGL14.EGL_NONE,  //EGL_RECORDABLE_ANDROID, 1,	// this flag need to recording of MediaCodec
            EGL14.EGL_NONE,
            EGL14.EGL_NONE,  //	with_depth_buffer ? EGL14.EGL_DEPTH_SIZE : EGL14.EGL_NONE,
            // with_depth_buffer ? 16 : 0,
            EGL14.EGL_NONE
        )
        var offset = 10
        if (false) {                // ステンシルバッファ(常時未使用)
            attribList[offset++] = EGL14.EGL_STENCIL_SIZE
            attribList[offset++] = 8
        }
        if (with_depth_buffer) {    // デプスバッファ
            attribList[offset++] = EGL14.EGL_DEPTH_SIZE
            attribList[offset++] = 16
        }
        if (isRecordable && Build.VERSION.SDK_INT >= 18) { // MediaCodecの入力用Surfaceの場合
            attribList[offset++] = EGL_RECORDABLE_ANDROID
            attribList[offset++] = 1
        }
        for (i in attribList.size - 1 downTo offset) {
            attribList[i] = EGL14.EGL_NONE
        }
        val configs =
            arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                mEglDisplay,
                attribList,
                0,
                configs,
                0,
                configs.size,
                numConfigs,
                0
            )
        ) {
            // XXX it will be better to fallback to RGB565
            Log.w(
                TAG,
                "unable to find RGBA8888 / " + " EGLConfig"
            )
            return null
        }
        return configs[0]
    }
}