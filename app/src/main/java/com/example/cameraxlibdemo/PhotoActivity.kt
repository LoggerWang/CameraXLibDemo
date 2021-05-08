package com.example.cameraxlibdemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.cameraxlib.LuminosityAnalyzer
import com.example.cameraxlib.Util
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 官方参考代码示例 ： https://codelabs.developers.google.com/codelabs/camerax-getting-started#0
 * 这个示例是使用了camerax的原始api
 */
class PhotoActivity : AppCompatActivity() {

    companion object{
        const val TAG = "PhotoActivity"
    }

    private  val PERMISSIONS_REQUEST_CODE = 10//请求码

    //动态请求权限的数组
    private val PERMISSIONS_REQUIRED = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    )//请求权限的数组，可以在数组中添加你需要动态获取的权限
    var previewView:PreviewView?=null
    var ivShow:ImageView?=null

    /*----------------------- 相机的初始化和绑定 ------------------------------*/
    private var preview: Preview? = null//定义Preview对象
    private var imageCapture: ImageCapture? = null//片拍摄用例对象
    private var imageAnalyzer: ImageAnalysis? = null//分析图片
    private lateinit var cameraExecutor: ExecutorService//阻塞相机操作是使用这个执行器执行
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK//设置相机的前后置
    private var displayId: Int = -1    //屏幕Id

    /*------------------- 拍照 -------------------------*/
    private lateinit var mHandler: Handler//定义Handler对象
    private var PhotoPath: String = ""
    var photoFile: File? = null

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo)
        previewView = findViewById<PreviewView>(R.id.previewView)
        ivShow = findViewById<ImageView>(R.id.ivPreview)

//创建线程池来执行Camera 的操作
        cameraExecutor = Executors.newSingleThreadExecutor()



        //请求动态获取权限
        if (!hasPermissions(this)) {
            // 请求camera权限
            requestPermissions(PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE)
        } else {
            //启动相机
            startCamera()
        }
    }

    private fun startCamera() {

        //请求CameraProvider
        //创建ProcessCameraProvider的实例。这用于将相机的生命周期绑定到生命周期所有者。由于CameraX具有生命周期意识，因此无需打开和关闭相机。
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        //检查 CameraProvider 可用性
        cameraProviderFuture.addListener(Runnable {

            //获取CameraProvider对象，并赋值我全局变量
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // 创建和绑定相机用例
            bindCameraUseCases()

        }, ContextCompat.getMainExecutor(this))
    }

    private var cameraProvider: ProcessCameraProvider? = null

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
                .setTargetAspectRatio(screenAspectRatio)//设置预览的宽高比（或者分辨率） Cannot use both setTargetResolution and setTargetAspectRatio on the same config.
//                .setTargetResolution(Size(1280,720)) //设置分辨率   分辨率和宽高比不能同时设置
                .setTargetRotation(rotation)//设定初始旋转方向（横竖）
                .build()
                .also {
                    //将取景器的视图与预览用例进行绑定
                    it.setSurfaceProvider(previewView?.surfaceProvider)
                }

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




        try {
            //必须在重新绑定用例之前解除绑定
            cameraProvider.unbindAll()
            //将所选相机和任意用例绑定到生命周期 将 Preview 连接到 PreviewView
            //Bind user cases to camera
//            var camera1: Camera? = cameraProvider.bindToLifecycle(this, cameraSelector, preview)
//            var camera2: Camera? = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            var camera3: Camera? = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)

        } catch (exc: Exception) {
            Log.e("TAG", "用例绑定失败", exc)
        }


    }

    /**  用于检查应用程序所需的所有权限是否都被授予的方法 **/
    fun hasPermissions(context: Context) = PERMISSIONS_REQUIRED.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun takePhoto(view: View) {
        //初始化Handler对象，并进行消息处理
        mHandler = Handler{
            when (it.what) {
                1 -> {
                    ivShow?.let { it1 ->
                        Glide.with(this).load(PhotoPath).apply(RequestOptions.circleCropTransform()).into(
                                it1
                        )
                    }
                }
            }
            false }
        // 获取可修改图像捕获用例的稳定参考
        imageCapture?.let { imageCapture ->

            // 创建文件对象来保存图像
            val photoFile: File? = try {
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
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile!!)
                    .setMetadata(metadata)
                    .build()

            // 设置图像捕捉监听器，在照片拍摄完成后触发
            imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {


                //拍照成功的回调（保存照片）
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {

                    //照片的存储路径
                    val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
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
            PhotoPath = absolutePath

        }
    }


    //将照片添加到图库
    private fun galleryAddPic() {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            photoFile = File(PhotoPath)
            mediaScanIntent.data = Uri.fromFile(photoFile)
            sendBroadcast(mediaScanIntent)
        }
    }
    fun recordVideo(view: View) {}
    fun changeCamera(view: View) {}
    fun changeFlash(view: View) {}
}