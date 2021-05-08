package com.example.cameraxlibdemo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.ImageCapture
import androidx.camera.view.CameraView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.blankj.utilcode.constant.PermissionConstants
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.PermissionUtils.FullCallback
import com.example.cameraxlib.R

/**
 * @author wanglezhi
 * @date 2021/2/24 15:16
 * @discription
 */
internal class LeCameraView : FrameLayout {
    var mContext: Context? = null
    var ivPreview: ImageView? = null
    var cameraView: CameraView? = null
    var tvTakePhoto: TextView? = null

    constructor(context: Context) : this(context,null) {}
    constructor(
        context: Context,
        attrs: AttributeSet?
    ) : this(context, attrs,0) {
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr) {
        mContext = context
        PermissionUtils.permission(
            PermissionConstants.STORAGE,
            PermissionConstants.CAMERA,
            PermissionConstants.MICROPHONE
        )
            .callback(object : FullCallback {
                override fun onGranted(granted: List<String>) {
                    initView()
                }

                override fun onDenied(
                    deniedForever: List<String>,
                    denied: List<String>
                ) {
                    if (!deniedForever.isEmpty()) {
                        PermissionUtils.launchAppDetailsSettings()
                    }
                }
            }).request()
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {

    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun initView() {
        setWillNotDraw(false)
        val contentView = (mContext as Activity?)!!.layoutInflater
            .inflate(R.layout.layout_legend_camera, this)
        ivPreview =
            contentView.findViewById(R.id.ivPreview)
        tvTakePhoto = contentView.findViewById(R.id.tvTakePhoto)
        tvTakePhoto?.setOnClickListener(OnClickListener { takePhoto() })
        cameraView = contentView.findViewById<CameraView>(R.id.cameraVieww)
        cameraView?.setFlash(ImageCapture.FLASH_MODE_ON)
        cameraView?.enableTorch(true)
        // 设置支持拍照和拍视频
        cameraView?.setCaptureMode(CameraView.CaptureMode.MIXED)
    }

    private fun takePhoto() {}

    // 绑定生命周期 否者界面可能一片黑
    fun setBindToLifecycle(lifecycleOwner: LifecycleOwner) {
        if (ActivityCompat.checkSelfPermission(
                (lifecycleOwner as Activity),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        var viewTreeObserver = cameraView?.viewTreeObserver
        viewTreeObserver?.addOnGlobalLayoutListener {
            cameraView?.bindToLifecycle(lifecycleOwner)

            lifecycleOwner.lifecycle
                .addObserver(LifecycleEventObserver { source, event -> })
        }

    }
}