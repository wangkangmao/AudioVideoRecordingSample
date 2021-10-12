package com.wangkm.audiovideorecordingsample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.serenegiant.dialog.MessageDialogFragmentV4
import com.serenegiant.dialog.MessageDialogFragmentV4.MessageDialogListener

class MainActivity : AppCompatActivity() ,
        MessageDialogListener{
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .add(R.id.container, CameraFragment()).commit()
        }
    }

    override fun onMessageDialogResult(p0: MessageDialogFragmentV4, p1: Int, p2: Array<out String>, p3: Boolean) {

    }
}