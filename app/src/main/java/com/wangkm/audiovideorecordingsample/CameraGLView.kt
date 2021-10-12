package com.wangkm.audiovideorecordingsample

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import com.wangkm.audiovideorecordingsample.encoder.MediaVideoEncoder
import com.wangkm.audiovideorecordingsample.glutils.GLDrawer2D
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * @author: created by wangkm
 * @time: 2021/10/11 10:24
 * @desc：
 * @email: 1240413544@qq.com
 */

class CameraGLView : GLSurfaceView {

    companion object {
        private val DEBUG = false // TODO set false on release
        private val TAG = "CameraGLView"
        private val CAMERA_ID = 0
    }

    private val SCALE_STRETCH_FIT = 0
    private val SCALE_KEEP_ASPECT_VIEWPORT = 1
    private val SCALE_KEEP_ASPECT = 2
    private val SCALE_CROP_CENTER = 3

    private var mRenderer: CameraGLView.CameraSurfaceRenderer? = null
    private var mHasSurface = false
    private var mCameraHandler: CameraGLView.CameraHandler? = null
    private var mVideoWidth = 0
    private var mVideoHeight: Int = 0
    private var mRotation = 0
    private var mScaleMode = SCALE_STRETCH_FIT

    constructor(context: Context?) : this(context, null, 0)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs) {
        if (DEBUG) Log.v(TAG, "CameraGLView:")
        mRenderer = CameraSurfaceRenderer(this)
        setEGLContextClientVersion(2) // GLES 2.0, API >= 8
        setRenderer(mRenderer)
    }

    /**
     * GLSurfaceViewのRenderer
     */
    inner private class CameraSurfaceRenderer(parent: CameraGLView) : Renderer,
        SurfaceTexture.OnFrameAvailableListener {
        // API >= 11
        private val mWeakParent: WeakReference<CameraGLView>
        var mSTexture // API >= 11
                : SurfaceTexture? = null
        var hTex = 0
        private var mDrawer: GLDrawer2D? = null
        private val mStMatrix = FloatArray(16)
        private val mMvpMatrix = FloatArray(16)
        var mVideoEncoder: MediaVideoEncoder? = null
        override fun onSurfaceCreated(
            unused: GL10,
            config: EGLConfig
        ) {
            if (DEBUG) Log.v(TAG, "onSurfaceCreated:")
            // This renderer required OES_EGL_image_external extension
            val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS) // API >= 8
            if (!extensions.contains("OES_EGL_image_external")) throw RuntimeException("This system does not support OES_EGL_image_external.")
            // create textur ID
            hTex = GLDrawer2D.initTex()
            // create SurfaceTexture with texture ID.
            mSTexture = SurfaceTexture(hTex)
            mSTexture!!.setOnFrameAvailableListener(this)
            // clear screen with yellow color so that you can see rendering rectangle
            GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f)
            val parent = mWeakParent.get()
            if (parent != null) {
                parent.mHasSurface = true
            }
            // create object for preview display
            mDrawer = GLDrawer2D()
            mDrawer?.setMatrix(mMvpMatrix, 0)
        }

        override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
            if (DEBUG) {
                Log.v(
                    TAG,
                    String.format("onSurfaceChanged:(%d,%d)", width, height)
                )
            }
            // if at least with or height is zero, initialization of this view is still progress.
            if (width == 0 || height == 0) return
            updateViewport()
            val parent = mWeakParent.get()
            parent?.startPreview(width, height)
        }

        /**
         * when GLSurface context is soon destroyed
         */
        fun onSurfaceDestroyed() {
            if (DEBUG) Log.v(TAG, "onSurfaceDestroyed:")
            if (mDrawer != null) {
                mDrawer?.release()
                mDrawer = null
            }
            if (mSTexture != null) {
                mSTexture!!.release()
                mSTexture = null
            }
            GLDrawer2D.deleteTex(hTex)
        }

        fun updateViewport() {
            val parent = mWeakParent.get()
            if (parent != null) {
                val view_width = parent.width
                val view_height = parent.height
                GLES20.glViewport(0, 0, view_width, view_height)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                val video_width = parent.mVideoWidth.toDouble()
                val video_height: Double = parent.mVideoHeight.toDouble()
                if (video_width == 0.0 || video_height == 0.0) return
                Matrix.setIdentityM(mMvpMatrix, 0)
                val view_aspect = view_width / view_height.toDouble()
                Log.i(
                    TAG,
                    String.format(
                        "view(%d,%d)%f,video(%1.0f,%1.0f)",
                        view_width,
                        view_height,
                        view_aspect,
                        video_width,
                        video_height
                    )
                )
                when (parent.mScaleMode) {
                    SCALE_STRETCH_FIT -> {
                    }
                    SCALE_KEEP_ASPECT_VIEWPORT -> {
                        val req = video_width / video_height
                        val x: Int
                        val y: Int
                        val width: Int
                        val height: Int
                        if (view_aspect > req) {
                            // if view is wider than camera image, calc width of drawing area based on view height
                            y = 0
                            height = view_height
                            width = (req * view_height) as Int
                            x = (view_width - width) / 2
                        } else {
                            // if view is higher than camera image, calc height of drawing area based on view width
                            x = 0
                            width = view_width
                            height = (view_width / req).toInt()
                            y = (view_height - height) / 2
                        }
                        // set viewport to draw keeping aspect ration of camera image
                        if (DEBUG) Log.v(
                            TAG,
                            String.format("xy(%d,%d),size(%d,%d)", x, y, width, height)
                        )
                        GLES20.glViewport(x, y, width, height)
                    }
                    SCALE_KEEP_ASPECT, SCALE_CROP_CENTER -> {
                        val scale_x = view_width / video_width
                        val scale_y = view_height / video_height
                        val scale =
                            if (parent.mScaleMode == SCALE_CROP_CENTER) Math.max(
                                scale_x,
                                scale_y
                            ) else Math.min(scale_x, scale_y)
                        val width = scale * video_width
                        val height = scale * video_height
                        Log.v(
                            TAG, String.format(
                                "size(%1.0f,%1.0f),scale(%f,%f),mat(%f,%f)",
                                width,
                                height,
                                scale_x,
                                scale_y,
                                width / view_width,
                                height / view_height
                            )
                        )
                        Matrix.scaleM(
                            mMvpMatrix,
                            0,
                            (width / view_width).toFloat(),
                            ((height / view_height).toFloat()),
                            1.0f
                        )
                    }
                }
                if (mDrawer != null) mDrawer?.setMatrix(mMvpMatrix, 0)
            }
        }

        @Volatile
        private var requesrUpdateTex = false
        private var flip = true

        /**
         * drawing to GLSurface
         * we set renderMode to GLSurfaceView.RENDERMODE_WHEN_DIRTY,
         * this method is only called when #requestRender is called(= when texture is required to update)
         * if you don't set RENDERMODE_WHEN_DIRTY, this method is called at maximum 60fps
         */
        override fun onDrawFrame(unused: GL10) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (requesrUpdateTex) {
                requesrUpdateTex = false
                // update texture(came from camera)
                mSTexture!!.updateTexImage()
                // get texture matrix
                mSTexture!!.getTransformMatrix(mStMatrix)
            }
            // draw to preview screen
            mDrawer?.draw(hTex, mStMatrix)
            flip = !flip
            if (flip) {    // ~30fps
                synchronized(this) {
                    if (mVideoEncoder != null) {
                        // notify to capturing thread that the camera frame is available.
                        //						mVideoEncoder.frameAvailableSoon(mStMatrix);
                        mVideoEncoder?.frameAvailableSoon(mStMatrix, mMvpMatrix)
                    }
                }
            }
        }

        override fun onFrameAvailable(st: SurfaceTexture) {
            requesrUpdateTex = true
        }

        @Synchronized
        private fun startPreview(width: Int, height: Int) {
            if (mCameraHandler == null) {
                val thread: CameraThread =
                    CameraThread(this@CameraGLView)
                thread.start()
                mCameraHandler = thread.getHandler()
            }
            mCameraHandler?.startPreview(1280, 720 /*width, height*/)
        }

        init {
            if (DEBUG) Log.v(TAG, "CameraSurfaceRenderer:")
            mWeakParent = WeakReference(parent)
            Matrix.setIdentityM(mMvpMatrix, 0)
        }
    }


    /**
     * Handler class for asynchronous camera operation
     */
    private class CameraHandler(private var mThread: CameraThread?) : Handler() {

        private val lock = Object()

        fun startPreview(width: Int, height: Int) {
            sendMessage(obtainMessage(MSG_PREVIEW_START, width, height))
        }

        /**
         * request to stop camera preview
         * @param needWait need to wait for stopping camera preview
         */
        fun stopPreview(needWait: Boolean) {
            synchronized(lock) {
                sendEmptyMessage(MSG_PREVIEW_STOP)
                if (needWait && mThread!!.mIsRunning) {
                    try {
                        if (DEBUG) Log.d(
                            TAG,
                            "wait for terminating of camera thread"
                        )
                        lock.wait()
                    } catch (e: InterruptedException) {
                    }
                }
            }
        }

        /**
         * message handler for camera thread
         */
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_PREVIEW_START -> mThread!!.startPreview(
                    msg.arg1,
                    msg.arg2
                )
                MSG_PREVIEW_STOP -> {
                    mThread!!.stopPreview()
                    synchronized(lock) { lock.notifyAll() }
                    Looper.myLooper()!!.quit()
                    mThread = null
                }
                else -> throw java.lang.RuntimeException("unknown message:what=" + msg.what)
            }
        }

        companion object {
            private const val MSG_PREVIEW_START = 1
            private const val MSG_PREVIEW_STOP = 2
        }

    }


    /**
     * Thread for asynchronous operation of camera preview
     */
    private class CameraThread(parent: CameraGLView) :
        Thread("Camera thread") {
        private val mReadyFence = Object()
        private lateinit var mWeakParent: WeakReference<CameraGLView>
        private var mHandler: CameraHandler? = null

        @Volatile
        var mIsRunning = false
        private var mCamera: Camera? = null
        private var mIsFrontFace = false


        fun getHandler(): CameraHandler? {
            synchronized(mReadyFence) {
                try {
                    mReadyFence.wait()
                } catch (e: InterruptedException) {
                }
            }
            return mHandler
        }

        /**
         * message loop
         * prepare Looper and create Handler for this thread
         */
        override fun run() {
            if (DEBUG) Log.d(TAG, "Camera thread start")
            Looper.prepare()
            synchronized(mReadyFence) {
                mHandler = CameraGLView.CameraHandler(this)
                mIsRunning = true
                mReadyFence.notify()
            }
            Looper.loop()
            if (CameraGLView.DEBUG) Log.d(CameraGLView.TAG, "Camera thread finish")
            synchronized(mReadyFence) {
                mHandler = null
                mIsRunning = false
            }
        }

        /**
         * start camera preview
         * @param width
         * @param height
         */
        fun startPreview(width: Int, height: Int) {
            if (DEBUG) Log.v(CameraGLView.TAG, "startPreview:")
            val parent = mWeakParent.get()
            if (parent != null && mCamera == null) {
                // This is a sample project so just use 0 as camera ID.
                // it is better to selecting camera is available
                try {
                    mCamera = Camera.open(CAMERA_ID)
                    val params = mCamera?.getParameters()
                    val focusModes =
                        params?.supportedFocusModes
                    if (focusModes!!.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                        params?.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                    } else if (focusModes!!.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        params?.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                    } else {
                        if (CameraGLView.DEBUG) Log.i(
                            CameraGLView.TAG,
                            "Camera does not support autofocus"
                        )
                    }
                    // let's try fastest frame rate. You will get near 60fps, but your device become hot.
                    val supportedFpsRange =
                        params.supportedPreviewFpsRange
                    //					final int n = supportedFpsRange != null ? supportedFpsRange.size() : 0;
//					int[] range;
//					for (int i = 0; i < n; i++) {
//						range = supportedFpsRange.get(i);
//						Log.i(TAG, String.format("supportedFpsRange(%d)=(%d,%d)", i, range[0], range[1]));
//					}
                    val max_fps = supportedFpsRange[supportedFpsRange.size - 1]
                    Log.i(
                        CameraGLView.TAG,
                        String.format("fps:%d-%d", max_fps[0], max_fps[1])
                    )
                    params.setPreviewFpsRange(max_fps[0], max_fps[1])
                    params.setRecordingHint(true)
                    // request closest supported preview size
                    val closestSize =
                        getClosestSupportedSize(
                            params.supportedPreviewSizes, width, height
                        )
                    params.setPreviewSize(closestSize!!.width, closestSize.height)
                    // request closest picture size for an aspect ratio issue on Nexus7
                    val pictureSize =
                        getClosestSupportedSize(
                            params.supportedPictureSizes, width, height
                        )
                    params.setPictureSize(pictureSize!!.width, pictureSize.height)
                    // rotate camera preview according to the device orientation
                    setRotation(params)
                    mCamera?.parameters = params
                    // get the actual preview size
                    val previewSize =
                        mCamera?.parameters?.previewSize
                    Log.i(
                        CameraGLView.TAG,
                        String.format(
                            "previewSize(%d, %d)",
                            previewSize?.width,
                            previewSize?.height
                        )
                    )
                    // adjust view size with keeping the aspect ration of camera preview.
                    // here is not a UI thread and we should request parent view to execute.
                    parent.post(Runnable {
                        parent.setVideoSize(
                            previewSize!!.width,
                            previewSize.height
                        )
                    })
                    val st: SurfaceTexture = parent.getSurfaceTexture()!!
                    st.setDefaultBufferSize(previewSize!!.width, previewSize.height)
                    mCamera?.setPreviewTexture(st)
                } catch (e: IOException) {
                    Log.e(CameraGLView.TAG, "startPreview:", e)
                    if (mCamera != null) {
                        mCamera?.release()
                        mCamera = null
                    }
                } catch (e: java.lang.RuntimeException) {
                    Log.e(CameraGLView.TAG, "startPreview:", e)
                    if (mCamera != null) {
                        mCamera?.release()
                        mCamera = null
                    }
                }
                if (mCamera != null) {
                    // start camera preview display
                    mCamera?.startPreview()
                }
            }
        }


        private fun getClosestSupportedSize(
            supportedSizes: List<Camera.Size>,
            requestedWidth: Int,
            requestedHeight: Int
        ): Camera.Size? {
            return Collections.min(
                supportedSizes,
                object : Comparator<Camera.Size> {
                    private fun diff(size: Camera.Size): Int {
                        return Math.abs(requestedWidth - size.width) + Math.abs(
                            requestedHeight - size.height
                        )
                    }

                    override fun compare(lhs: Camera.Size, rhs: Camera.Size): Int {
                        return diff(lhs) - diff(rhs)
                    }

                }) as Camera.Size
        }

        /**
         * stop camera preview
         */
        fun stopPreview() {
            if (CameraGLView.DEBUG) Log.v(CameraGLView.TAG, "stopPreview:")
            if (mCamera != null) {
                mCamera!!.stopPreview()
                mCamera!!.release()
                mCamera = null
            }
            val parent = mWeakParent.get() ?: return
            parent.mCameraHandler = null
        }

        /**
         * rotate preview screen according to the device orientation
         * @param params
         */
        private fun setRotation(params: Camera.Parameters) {
            if (DEBUG) Log.v(CameraGLView.TAG, "setRotation:")
            val parent = mWeakParent.get() ?: return
            val display = (parent.context
                .getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
            val rotation = display.rotation
            var degrees = 0
            when (rotation) {
                Surface.ROTATION_0 -> degrees = 0
                Surface.ROTATION_90 -> degrees = 90
                Surface.ROTATION_180 -> degrees = 180
                Surface.ROTATION_270 -> degrees = 270
            }
            // get whether the camera is front camera or back camera
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(CAMERA_ID, info)
            mIsFrontFace = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
            if (mIsFrontFace) {    // front camera
                degrees = (info.orientation + degrees) % 360
                degrees = (360 - degrees) % 360 // reverse
            } else {  // back camera
                degrees = (info.orientation - degrees + 360) % 360
            }
            // apply rotation setting
            mCamera!!.setDisplayOrientation(degrees)
            parent.mRotation = degrees
            // XXX This method fails to call and camera stops working on some devices.
//			params.setRotation(degrees);
        }

        companion object {
            private fun getClosestSupportedSize(
                supportedSizes: List<Camera.Size>,
                requestedWidth: Int,
                requestedHeight: Int
            ): Camera.Size {
                return Collections.min(
                    supportedSizes,
                    object : Comparator<Camera.Size?> {
                        private fun diff(size: Camera.Size?): Int {
                            return Math.abs(requestedWidth - size!!.width) + Math.abs(
                                requestedHeight - size!!.height
                            )
                        }

                        override fun compare(
                            lhs: Camera.Size?,
                            rhs: Camera.Size?
                        ): Int {
                            return diff(lhs) - diff(rhs)
                        }

                    }) as Camera.Size
            }
        }

        init {
            mWeakParent = WeakReference(parent)
        }
    }

    //********************************************************************************
    //********************************************************************************
    @Synchronized
    private fun startPreview(width: Int, height: Int) {
        if (mCameraHandler == null) {
            val thread = CameraThread(this)
            thread.start()
            mCameraHandler = thread.getHandler()
        }
        mCameraHandler?.startPreview(1280, 720 /*width, height*/)
    }

    override fun onResume() {
        if (DEBUG) Log.v(TAG, "onResume:")
        super.onResume()
        if (mHasSurface) {
            if (mCameraHandler == null) {
                if (DEBUG) Log.v(
                    TAG,
                    "surface already exist"
                )
                startPreview(width, height)
            }
        }
    }

    override fun onPause() {
        if (DEBUG) Log.v(TAG, "onPause:")
        if (mCameraHandler != null) {
            // just request stop prviewing
            mCameraHandler!!.stopPreview(false)
        }
        super.onPause()
    }

    fun setScaleMode(mode: Int) {
        if (mScaleMode != mode) {
            mScaleMode = mode
            queueEvent { mRenderer!!.updateViewport() }
        }
    }

    fun getScaleMode(): Int {
        return mScaleMode
    }

    fun setVideoSize(width: Int, height: Int) {
        if (mRotation % 180 == 0) {
            mVideoWidth = width
            mVideoHeight = height
        } else {
            mVideoWidth = height
            mVideoHeight = width
        }
        queueEvent { mRenderer!!.updateViewport() }
    }

    fun getVideoWidth(): Int {
        return mVideoWidth
    }

    fun getVideoHeight(): Int {
        return mVideoHeight
    }

    fun getSurfaceTexture(): SurfaceTexture? {
        if (DEBUG) Log.v(TAG, "getSurfaceTexture:")
        return if (mRenderer != null) mRenderer!!.mSTexture else null
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (DEBUG) Log.v(TAG, "surfaceDestroyed:")
        if (mCameraHandler != null) {
            // wait for finish previewing here
            // otherwise camera try to display on un-exist Surface and some error will occure
            mCameraHandler!!.stopPreview(true)
        }
        mCameraHandler = null
        mHasSurface = false
        mRenderer!!.onSurfaceDestroyed()
        super.surfaceDestroyed(holder!!)
        super.surfaceDestroyed(holder)
    }

    @SuppressLint("NewApi")
    fun setVideoEncoder(encoder: MediaVideoEncoder?) {
        if (DEBUG) Log.v(
            TAG,
            "setVideoEncoder:tex_id=" + mRenderer!!.hTex + ",encoder=" + encoder
        )
        queueEvent {
            synchronized(mRenderer!!) {
                encoder?.setEglContext(EGL14.eglGetCurrentContext(), mRenderer!!.hTex)
                mRenderer!!.mVideoEncoder = encoder
            }
        }
    }

}