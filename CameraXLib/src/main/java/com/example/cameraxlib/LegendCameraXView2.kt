package com.example.cameraxlib

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.View.OnClickListener
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraView
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.blankj.utilcode.constant.PermissionConstants
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.PermissionUtils.FullCallback
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.util.Util
import com.example.cameraxlib.R
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * @author wanglezhi
 * @date 2021/2/24 15:16
 * @discription
 */
class LegendCameraXView2 : FrameLayout {
    companion object{
        const val TAG = "LegendCameraXView2"
    }
    
    var mContext: Context? = null
    var ivPreview: ImageView? = null
    var previewView: PreviewView? = null
    var tvTakePhoto: TextView? = null
    var tvChange: TextView? = null


    /*----------------------- 相机的初始化和绑定 ------------------------------*/
    private var preview: Preview? = null//定义Preview对象
    private var imageCapture: ImageCapture? = null//片拍摄用例对象
    private var imageAnalyzer: ImageAnalysis? = null//分析图片
    private lateinit var cameraExecutor: ExecutorService//阻塞相机操作是使用这个执行器执行
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK//设置相机的前后置
    private var displayId: Int = -1    //屏幕Id

    /*------------------- 拍照 -------------------------*/
    private lateinit var mHandler: Handler//定义Handler对象
    private var mediaPath: String = ""
    var mediaFile: File? = null

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


    @SuppressLint("UnsafeExperimentalUsageError")
    private fun initView() {
        setWillNotDraw(false)
        val contentView = (mContext as Activity?)!!.layoutInflater
            .inflate(R.layout.layout_legend_camera2, this)
        ivPreview =
            contentView.findViewById(R.id.ivPreview)
        tvTakePhoto = contentView.findViewById(R.id.tvTakePhoto)
        tvChange = contentView.findViewById(R.id.tvChange)
        tvTakePhoto?.setOnClickListener(OnClickListener { takePhoto()})
        tvChange?.setOnClickListener(OnClickListener { changeCameraFace()})
        previewView = contentView.findViewById(R.id.previewView)
        //创建线程池来执行Camera 的操作
        cameraExecutor = Executors.newSingleThreadExecutor()
        //启动相机
        startCamera()
        //初始化Handler对象，并进行消息处理
        mHandler = Handler{
            when (it.what) {
                1 -> {
                    ivPreview?.let { it1 ->
                        Glide.with(this).load(mediaPath).apply(RequestOptions.circleCropTransform()).into(
                                it1
                        )
                    }
                }
            }
            false }
    }


    //启动相机
    private fun startCamera() {

        //打开相机
        previewView?.post {
            // 跟踪这个视图所附加的显示
            displayId = previewView?.display?.displayId!!

            //初始化CameraX，并准备绑定相机用例
            setUpCamera()
        }

    }


    // 创建和绑定相机用例(CameraX 的API的使用都在这个方法里)
    private fun bindCameraUseCases() {

        // 获取用于设置摄像头为全屏分辨率的屏幕指标
        val metrics = DisplayMetrics().also { previewView?.display?.getRealMetrics(it) }
        //获取预览框长宽比
        val screenAspectRatio = Util().aspectRatio(metrics.widthPixels, metrics.heightPixels)
        //获取屏幕的旋转方向
        val rotation = previewView?.display?.rotation!!


        //把全局CameraProvider赋值给局部对象
        val cameraProvider = cameraProvider ?: throw IllegalStateException("相机初始化失败")



        //创建Preview对象
        preview = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)//设置预览的宽高比（或者分辨率）
            .setTargetRotation(rotation)//设定初始旋转方向（横竖）
            .build()

        //指定相机是 前置 还是 后置
        /*
        * 前置相机：CameraSelector.LENS_FACING_FRONT
        * 后置相机：CameraSelector.LENS_FACING_BACK
        * */
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()


        /*
        * 设置分析图片用例
        *
        * 作用：提供可供 CPU 访问的图片来执行图片处理、计算机视觉或机器学习推断
        * */
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)//指定分辨率
            .setTargetRotation(rotation)
            /*
            * 阻塞模式：ImageAnalysis.STRATEGY_BLOCK_PRODUCER  （在此模式下，执行器会依序从相应相机接收帧；这意味着，如果 analyze() 方法所用的时间超过单帧在当前帧速率下的延迟时间，所接收的帧便可能不再是最新的帧，因为在该方法返回之前，新帧会被阻止进入流水线）
            * 非阻塞模式： ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST （在此模式下，执行程序在调用 analyze() 方法时会从相机接收最新的可用帧。如果此方法所用的时间超过单帧在当前帧速率下的延迟时间，它可能会跳过某些帧，以便 analyze() 在下一次接收数据时获取相机流水线中的最新可用帧）
            * */
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)//图片分析模式
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->

                    //分析图片用例返回的结果
//                    Log.d("分析图片用例返回的结果：", "$luma")
                })
            }

        /*
        * 设置图片拍摄用例
        * ImageCapture的介绍：https://developer.android.google.cn/reference/androidx/camera/core/ImageCapture?hl=zh_cn#CAPTURE_MODE_MINIMIZE_LATENCY
        * */
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)//设置拍照模式
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)//设置旋转
            .build()



        //必须在重新绑定用例之前解除绑定
        cameraProvider.unbindAll()

        try {
            //将所选相机和任意用例绑定到生命周期 将 Preview 连接到 PreviewView
            var camera: Camera? = cameraProvider.bindToLifecycle(mContext as AppCompatActivity, cameraSelector, preview, imageCapture, imageAnalyzer)

            //将取景器的视图与预览用例进行绑定
            preview?.setSurfaceProvider(previewView?.surfaceProvider)

        } catch (exc: Exception) {
            Log.e("TAG", "相机启动失败", exc)
        }

    }
    private var cameraProvider: ProcessCameraProvider? = null
    /** 初始化CameraX，并准备绑定相机用例  */
    private fun setUpCamera() {

        //请求CameraProvider
        val cameraProviderFuture = mContext?.let { ProcessCameraProvider.getInstance(it) }

        //检查 CameraProvider 可用性
        cameraProviderFuture?.addListener(Runnable {

            //获取CameraProvider对象，并赋值我全局变量
            cameraProvider = cameraProviderFuture.get()

            // 创建和绑定相机用例
            bindCameraUseCases()

        }, ContextCompat.getMainExecutor(mContext))
    }



    //拍照的方法
    private fun takePhoto() {

        // 获取可修改图像捕获用例的稳定参考
        imageCapture?.let { imageCapture ->

            // 创建文件对象来保存图像
            val mediaFile: File? = try {
                createImageFile()
            } catch (ex: IOException) {

                Log.d("文件创建失败","${ex}")
                null
            }

            // 设置图像捕获元数据
            val metadata = ImageCapture.Metadata().apply {

                //使用前置摄像头时的镜像
                isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
            }

            // 创建输出选项对象（file：输出的文件；metadata：照片的元数据）
            val outputOptions = ImageCapture.OutputFileOptions.Builder(mediaFile!!)
                    .setMetadata(metadata)
                    .build()

            // 设置图像捕捉监听器，在照片拍摄完成后触发
            imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {


                //拍照成功的回调（保存照片）
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {

                    //照片的存储路径
                    val savedUri = output.savedUri ?: Uri.fromFile(mediaFile)
                    val savedUrl = savedUri.toString()

                    //将照片添加到图库
                    galleryAddPic()

                    //使用Handler发送消息
                    val msg: Message = Message.obtain()
                    msg.what = 1
                    mHandler.sendMessage(msg)

                    Log.d(TAG, "拍照成功返回的路径: $savedUri")

                }

                //拍照失败的回调
                override fun onError(exc: ImageCaptureException) {
                    Log.e("TAG", "拍照失败: ${exc.message}", exc)
                }
            })
        }

    }


    //创建文件对象
    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {

        //文件名
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())

        //文件路径（SD卡路径）
        val storageDir = Environment.getExternalStorageDirectory()

        return File.createTempFile(
                "${timeStamp}_",
                ".jpg",
                storageDir
        ).apply {

            //返回文件的路径
            mediaPath = absolutePath

        }
    }


    //将照片添加到图库
    private fun galleryAddPic() {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            mediaFile = File(mediaPath)
            mediaScanIntent.data = Uri.fromFile(mediaFile)
            mContext?.sendBroadcast(mediaScanIntent)
        }
    }

    /**
     * 切换前后摄像头
     */
    fun changeCameraFace(){
        lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        //重新启动相机
        bindCameraUseCases()

    }
}