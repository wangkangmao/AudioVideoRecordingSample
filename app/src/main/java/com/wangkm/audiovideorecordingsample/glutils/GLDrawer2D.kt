package com.wangkm.audiovideorecordingsample.glutils

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
 * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 *
 * File name: GLDrawer2D.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/ /**
 * Helper class to draw to whole view using specific texture and texture matrix
 */
class GLDrawer2D {
    private val pVertex: FloatBuffer
    private val pTexCoord: FloatBuffer
    private var hProgram: Int
    var maPositionLoc: Int
    var maTextureCoordLoc: Int
    var muMVPMatrixLoc: Int
    var muTexMatrixLoc: Int
    private val mMvpMatrix = FloatArray(16)

    /**
     * terminatinng, this should be called in GL context
     */
    fun release() {
        if (hProgram >= 0) GLES20.glDeleteProgram(hProgram)
        hProgram = -1
    }

    /**
     * draw specific texture with specific texture matrix
     * @param tex_id texture ID
     * @param tex_matrix texture matrix、if this is null, the last one use(we don't check size of this array and needs at least 16 of float)
     */
    fun draw(tex_id: Int, tex_matrix: FloatArray?) {
        GLES20.glUseProgram(hProgram)
        if (tex_matrix != null) GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, tex_matrix, 0)
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex_id)
        GLES20.glDrawArrays(
            GLES20.GL_TRIANGLE_STRIP,
            0,
            VERTEX_NUM
        )
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glUseProgram(0)
    }

    /**
     * Set model/view/projection transform matrix
     * @param matrix
     * @param offset
     */
    fun setMatrix(matrix: FloatArray?, offset: Int) {
        if (matrix != null && matrix.size >= offset + 16) {
            System.arraycopy(matrix, offset, mMvpMatrix, 0, 16)
        } else {
            Matrix.setIdentityM(mMvpMatrix, 0)
        }
    }

    companion object {
        private const val DEBUG = false // TODO set false on release
        private const val TAG = "GLDrawer2D"
        private const val vss = ("uniform mat4 uMVPMatrix;\n"
                + "uniform mat4 uTexMatrix;\n"
                + "attribute highp vec4 aPosition;\n"
                + "attribute highp vec4 aTextureCoord;\n"
                + "varying highp vec2 vTextureCoord;\n"
                + "\n"
                + "void main() {\n"
                + "	gl_Position = uMVPMatrix * aPosition;\n"
                + "	vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n"
                + "}\n")
        private const val fss = ("#extension GL_OES_EGL_image_external : require\n"
                + "precision mediump float;\n"
                + "uniform samplerExternalOES sTexture;\n"
                + "varying highp vec2 vTextureCoord;\n"
                + "void main() {\n"
                + "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
                + "}")
        private val VERTICES =
            floatArrayOf(1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f)
        private val TEXCOORD =
            floatArrayOf(1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f)
        private const val FLOAT_SZ = java.lang.Float.SIZE / 8
        private const val VERTEX_NUM = 4
        private const val VERTEX_SZ =
            VERTEX_NUM * 2

        /**
         * create external texture
         * @return texture ID
         */
        fun initTex(): Int {
            if (DEBUG) Log.v(
                TAG,
                "initTex:"
            )
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST
            )
            GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST
            )
            return tex[0]
        }

        /**
         * delete specific texture
         */
        fun deleteTex(hTex: Int) {
            if (DEBUG) Log.v(
                TAG,
                "deleteTex:"
            )
            val tex = intArrayOf(hTex)
            GLES20.glDeleteTextures(1, tex, 0)
        }

        /**
         * load, compile and link shader
         * @param vss source of vertex shader
         * @param fss source of fragment shader
         * @return
         */
        fun loadShader(vss: String?, fss: String?): Int {
            if (DEBUG) Log.v(
                TAG,
                "loadShader:"
            )
            var vs = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
            GLES20.glShaderSource(vs, vss)
            GLES20.glCompileShader(vs)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(vs, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                if (DEBUG) Log.e(
                    TAG,
                    "Failed to compile vertex shader:"
                            + GLES20.glGetShaderInfoLog(vs)
                )
                GLES20.glDeleteShader(vs)
                vs = 0
            }
            var fs = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
            GLES20.glShaderSource(fs, fss)
            GLES20.glCompileShader(fs)
            GLES20.glGetShaderiv(fs, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                if (DEBUG) Log.w(
                    TAG,
                    "Failed to compile fragment shader:"
                            + GLES20.glGetShaderInfoLog(fs)
                )
                GLES20.glDeleteShader(fs)
                fs = 0
            }
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vs)
            GLES20.glAttachShader(program, fs)
            GLES20.glLinkProgram(program)
            return program
        }
    }

    /**
     * Constructor
     * this should be called in GL context
     */
    init {
        pVertex =
            ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
        pVertex.put(VERTICES)
        pVertex.flip()
        pTexCoord =
            ByteBuffer.allocateDirect(VERTEX_SZ * FLOAT_SZ)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
        pTexCoord.put(TEXCOORD)
        pTexCoord.flip()
        hProgram = loadShader(
            vss,
            fss
        )
        GLES20.glUseProgram(hProgram)
        maPositionLoc = GLES20.glGetAttribLocation(hProgram, "aPosition")
        maTextureCoordLoc = GLES20.glGetAttribLocation(hProgram, "aTextureCoord")
        muMVPMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uMVPMatrix")
        muTexMatrixLoc = GLES20.glGetUniformLocation(hProgram, "uTexMatrix")
        Matrix.setIdentityM(mMvpMatrix, 0)
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mMvpMatrix, 0)
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, mMvpMatrix, 0)
        GLES20.glVertexAttribPointer(
            maPositionLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            VERTEX_SZ,
            pVertex
        )
        GLES20.glVertexAttribPointer(
            maTextureCoordLoc,
            2,
            GLES20.GL_FLOAT,
            false,
            VERTEX_SZ,
            pTexCoord
        )
        GLES20.glEnableVertexAttribArray(maPositionLoc)
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc)
    }
}