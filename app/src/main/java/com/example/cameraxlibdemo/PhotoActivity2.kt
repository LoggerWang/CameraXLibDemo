package com.example.cameraxlibdemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import com.example.cameraxlib.CaptureListener
import com.example.cameraxlib.LegendCameraXView
import com.example.cameraxlib.LegendCameraXView2
import com.example.cameraxlib.OnFocusTouchListener
import java.io.File

/**
 * 这是一个用了封装了CameraView的示例
 */
class PhotoActivity2 : AppCompatActivity() {
    companion object{
        const val TAG = "PhotoActivity2"
    }
    private var legendCameraView : LegendCameraXView?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo2)
        legendCameraView = findViewById(R.id.legendCameraView)
//        legendCameraView?.setBindToLifecycle(this)

        legendCameraView!!
            .getCameraConfig(this)
                //设置使用前后摄像头 默认后置摄像头
//            .setCameraSelect(CameraSelector.LENS_FACING_FRONT)
            //设置闪光灯模式 默认自动
            .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                //是否可以手动调整焦距
            .setZoomEnable(true)
                //拍完是否更新到相册
            .setUpdapteAfterTake(true)
                //拍照、拍视频的回调
            .setOnCaptureListener(object :CaptureListener{
                    override fun onTakePhotos(photoFile: File) {
                        Log.d(TAG,"=== onTakePhotos ${photoFile.path}")
                    }

                    override fun recordStart() {
                        Log.d(TAG,"=== recordStart recordStart")
                    }

                    override fun recordTick(time: Long) {
                        Log.d(TAG,"=== recordTick $time")
                    }

                    override fun recordEnd(videoFile: File, time: Long) {
                        Log.d(TAG,"=== recordEnd videoFile： ${videoFile.path} ，time:$time")
                    }

                    override fun recordError() {

                        Log.d(TAG,"=== recordError ")
                    }

                    override fun selectCamera(frontOrBackCamera: Int) {

                        Log.d(TAG,"=== selectCamera frontOrBackCamera： $frontOrBackCamera ")
                    }

                    override fun selectFlashMode(mode: Int) {

                        Log.d(TAG,"=== selectFlashMode： $mode ")
                    }

                })
                //触摸回调
                .setOnTouchFocusListener(object : OnFocusTouchListener {
                    override fun onTouch(view: View, motionEvent: MotionEvent) {
                        when (motionEvent?.action) {
                            MotionEvent.ACTION_DOWN -> {
                                var dx = motionEvent.x
                                var dy = motionEvent.y
                                var rawY = motionEvent.rawY
                                Log.d("onTouchEvent", "PhotoActivity2 " +
                                        "onTouchEvent ACTION_DOWN dx:$dx ===dy:$dy=====:rawY = $rawY")


                            }
                            MotionEvent.ACTION_UP -> {

                            }
                            else -> {
                            }
                        }
                    }

                })
            .startCamera()



    }

}