package com.wangkm.audiovideorecordingsample.glutils

import android.graphics.SurfaceTexture
import android.opengl.EGLContext
import android.opengl.Matrix
import android.text.TextUtils
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder

/**
 * @author: created by wangkm
 * @time: 2021/10/11 18:54
 * @desc：
 * @email: 1240413544@qq.com
 */

class RenderHandler:Runnable {



    private val mSync = Object()
    private var mShard_context: EGLContext? = null
    private var mIsRecordable = false
    private var mSurface: Any? = null
    private var mTexId = -1
    private val mMatrix = FloatArray(32)

    private var mRequestSetEglContext = false
    private var mRequestRelease = false
    private var mRequestDraw = 0

    companion object{

        private val DEBUG = false

        private val TAG = "RenderHandler"

        fun createHandler(name: String?): RenderHandler {
            if (DEBUG) Log.v(TAG, "createHandler:")
            val handler = RenderHandler()
            synchronized(handler.mSync) {
                Thread(handler, if (!TextUtils.isEmpty(name)) name else TAG)
                    .start()
                try {
                    handler.mSync.wait()
                } catch (e: InterruptedException) {
                }
            }
            return handler
        }

    }


    fun setEglContext(
        shared_context: EGLContext,
        tex_id: Int,
        surface: Any,
        isRecordable: Boolean
    ) {
        if (DEBUG) Log.i(TAG, "setEglContext:")
        if (surface !is Surface && surface !is SurfaceTexture && surface !is SurfaceHolder) throw RuntimeException(
            "unsupported window type:$surface"
        )
        synchronized(mSync) {
            if (mRequestRelease) return
            mShard_context = shared_context
            mTexId = tex_id
            mSurface = surface
            mIsRecordable = isRecordable
            mRequestSetEglContext = true
            Matrix.setIdentityM(mMatrix, 0)
            Matrix.setIdentityM(mMatrix, 16)
            mSync.notifyAll()
            try {
                mSync.wait()
            } catch (e: InterruptedException) {
            }
        }
    }

    fun draw() {
        draw(mTexId, mMatrix, null)
    }

    fun draw(tex_id: Int) {
        draw(tex_id, mMatrix, null)
    }

    fun draw(tex_matrix: FloatArray?) {
        draw(mTexId, tex_matrix, null)
    }

    fun draw(tex_matrix: FloatArray?, mvp_matrix: FloatArray?) {
        draw(mTexId, tex_matrix, mvp_matrix)
    }

    fun draw(tex_id: Int, tex_matrix: FloatArray?) {
        draw(tex_id, tex_matrix, null)
    }

    fun draw(
        tex_id: Int,
        tex_matrix: FloatArray?,
        mvp_matrix: FloatArray?
    ) {
        synchronized(mSync) {
            if (mRequestRelease) return
            mTexId = tex_id
            if (tex_matrix != null && tex_matrix.size >= 16) {
                System.arraycopy(tex_matrix, 0, mMatrix, 0, 16)
            } else {
                Matrix.setIdentityM(mMatrix, 0)
            }
            if (mvp_matrix != null && mvp_matrix.size >= 16) {
                System.arraycopy(mvp_matrix, 0, mMatrix, 16, 16)
            } else {
                Matrix.setIdentityM(mMatrix, 16)
            }
            mRequestDraw++
            mSync.notifyAll()
        }
    }

    fun isValid(): Boolean {
        synchronized(
            mSync
        ) { return mSurface !is Surface || (mSurface as Surface).isValid }
    }

    fun release() {
        if (DEBUG) Log.i(TAG, "release:")
        synchronized(mSync) {
            if (mRequestRelease) return
            mRequestRelease = true
            mSync.notifyAll()
            try {
                mSync.wait()
            } catch (e: InterruptedException) {
            }
        }
    }

    private val mEgl: EGLBase? = null
    private val mInputSurface: EGLBase.EglSurface? = null
    private val mDrawer: GLDrawer2D? = null

    override fun run() {
    }
}