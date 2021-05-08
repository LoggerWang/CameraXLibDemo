package com.example.cameraxlib

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.MediaPlayer.OnPreparedListener
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.CountDownTimer
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.FlashMode
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.impl.ImageCaptureConfig
import androidx.camera.view.CameraView
import androidx.camera.view.video.OnVideoSavedCallback
import androidx.camera.view.video.OutputFileResults
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.blankj.utilcode.constant.PermissionConstants
import com.blankj.utilcode.util.PermissionUtils
import com.blankj.utilcode.util.ScreenUtils
import com.bumptech.glide.Glide
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * @author wanglezhi
 * @date   2021/2/23 11:28
 * @discription
 */
class LegendCameraXView: FrameLayout{

    companion object{
        //闪关灯状态
        private const val TYPE_FLASH_AUTO = 0x021
        private const val TYPE_FLASH_ON = 0x022
        private const val TYPE_FLASH_OFF = 0x023

    }


    var mContext:Context ?=null
    var cameraView: CameraView?=null
    /**拍照完预览图*/
    var ivPreview: ImageView?=null
    /**录像按钮*/
    var tvTakeVideo: TextView?=null
    /**录像预览图*/
    var mTextureView:TextureView ?=null
    private var mMediaPlayer: MediaPlayer? = null
    var tvFlash:TextView ?= null
    /**实际录制时长*/
    var recordTime=0L
    /**最多可录制市场*/
    var maxCanRecordTime = Long.MAX_VALUE
    private var photoFile: File? = null
    private var mediaPath: String = ""
    private var videoFile: File? = null
    /**前后摄像头选择 默认后置摄像头*/
    private var defaultLensFacing: Int = CameraSelector.LENS_FACING_BACK//设置相机的前后置
    /**闪光灯模式 默认自动*/
    var defaultFlashMode = ImageCapture.FLASH_MODE_AUTO
    private  var typeFlash = TYPE_FLASH_AUTO
    /**拍照为完是否更新相册 默认更新*/
    var updateAfterTakePhotoDefault = true
    /**是否可以手动缩放*/
    var mIsPinchToZoomEnabled = true
    var captureListener:CaptureListener?=null
    //touch监听
    var touchFocusListener:OnFocusTouchListener?=null
    /**是否更新到相册 相册可见*/
    var saveToGallery:Boolean = true
    constructor(context: Context) : this(context, null)
    constructor(context: Context,
                attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context,
                attrs: AttributeSet?,
                defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context,
                attrs: AttributeSet?,
                defStyleAttr: Int,
                defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes){
        mContext = context
        PermissionUtils.permission(
            PermissionConstants.STORAGE,
            PermissionConstants.CAMERA,
            PermissionConstants.MICROPHONE
        )
            .callback(object : PermissionUtils.FullCallback {
                override fun onGranted(permissionsGranted: List<String>) {
                    initView()
                }

                override fun onDenied(
                    permissionsDeniedForever: List<String>,
                    permissionsDenied: List<String>
                ) {
                    if (permissionsDeniedForever.isNotEmpty()) {
                        PermissionUtils.launchAppDetailsSettings()
                    }
                }
            }).request()
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun initView() {
        setWillNotDraw(false)
        var contentView =
            (mContext as Activity).layoutInflater.inflate(R.layout.layout_legend_camera, this)
         ivPreview = contentView.findViewById<ImageView>(R.id.ivPreview)
        var tvTakePhoto = contentView.findViewById<TextView>(R.id.tvTakePhoto)
        tvTakeVideo = contentView.findViewById<TextView>(R.id.tvTakeVideo)
        mTextureView = contentView.findViewById<TextureView>(R.id.mVideo)
        var tvChange = contentView.findViewById<TextView>(R.id.tvChange)
        tvFlash = contentView.findViewById<TextView>(R.id.tvFlash)
        tvTakePhoto.setOnClickListener {
            takePhoto()
        }
        tvTakeVideo?.setOnClickListener {
            if (cameraView?.isRecording!!){
                cameraView?.stopRecording()
                tvTakeVideo?.text = "录像"
            }else{
                startRecordVideo()
//                tvTakeVideo?.text = "停止"
            }
        }
        tvChange.setOnClickListener {
            changeCameraFace()
        }
        tvFlash?.setOnClickListener {
            changeFlash()
        }
        cameraView = contentView.findViewById(R.id.cameraVieww)
        cameraView?.cameraLensFacing = defaultLensFacing
        cameraView?.flash = defaultFlashMode
        cameraView?.enableTorch(true)
        // 设置支持拍照和拍视频
        cameraView?.captureMode = CameraView.CaptureMode.MIXED
        cameraView?.setOnTouchListener { view, motionEvent ->
            touchFocusListener?.onTouch(view,motionEvent)
//            when (motionEvent?.action) {
//                MotionEvent.ACTION_DOWN-> {
//                    var dx = motionEvent.x
//                    var dy = motionEvent.y
//                    Log.d("onTouchEvent","LegendCameraXView " +
//                            "onTouchEvent ACTION_DOWN dx:$dx ===dy:$dy")
//
//
//                }
//                MotionEvent.ACTION_UP-> {
//
//                }
//                else -> {
//                }
//            }
            false
        }
    }

     fun takePhoto() {
        // 修复了前置摄像头拍照后  预览左右镜像的问题
        var lensFacing: Int? = cameraView?.cameraLensFacing
        if (lensFacing == null) {
            lensFacing = CameraSelector.LENS_FACING_BACK
        }
        val outputFileOptions =
            ImageCapture.OutputFileOptions.Builder(mContext?.let {
                initTakePicPath(it).also {
                    photoFile = it
                }
            }!!)
        val metadata = ImageCapture.Metadata()
        metadata.isReversedHorizontal = CameraSelector.LENS_FACING_FRONT == lensFacing
        outputFileOptions.setMetadata(metadata)

         cameraView



        //测试新版本 CameraView
        cameraView?.takePicture(
            outputFileOptions.build(),
            ContextCompat.getMainExecutor(mContext),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (!photoFile?.exists()!!) {
                        Toast.makeText(mContext, "图片保存出错!", Toast.LENGTH_LONG).show()
                        return
                    }
                    ivPreview?.let {
                        Glide.with(mContext!!)
                            .load(photoFile)
                            .into(it)
                    }
//                    if (updateAfterTakePhotoDefault) galleryAddPic()
                    if (updateAfterTakePhotoDefault) scanPhotoAlbum(photoFile)
                    photoFile?.apply {
                        captureListener?.onTakePhotos(this)
                    }
//                    mCaptureLayout.startTypeBtnAnimator()

                    // If the folder selected is an external media directory, this is unnecessary
                    // but otherwise other apps will not be able to access our images unless we
                    // scan them using [MediaScannerConnection]
                }

                override fun onError(exception: ImageCaptureException) {
//                    if (flowCameraListener != null) {
//                        flowCameraListener.onError(
//                            exception.imageCaptureError,
//                            Objects.requireNonNull(exception.message),
//                            exception.cause
//                        )
//                    }
                }
            })
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    fun startRecordVideo(){
        videoFile = initStartRecordingPath(mContext!!)
        if (videoFile==null) {
            return
        }
        /**
         * Runnable：实现了Runnable接口，jdk就知道这个类是一个线程
         */
        /**
         * Runnable：实现了Runnable接口，jdk就知道这个类是一个线程
         */
        val runnable: Runnable = Runnable{
            recordTime+=1000
            captureListener?.recordTick(recordTime)
            tvTakeVideo?.text = "停止 ${recordTime/1000}"
        }
        // ScheduledExecutorService:是从Java SE5的java.util.concurrent里，
        // 做为并发工具类被引进的，这是最理想的定时任务实现方式。
        // ScheduledExecutorService:是从Java SE5的java.util.concurrent里，
        // 做为并发工具类被引进的，这是最理想的定时任务实现方式。
        val service: ScheduledExecutorService = Executors
                .newSingleThreadScheduledExecutor()
        // 第二个参数为首次执行的延时时间，第三个参数为定时执行的间隔时间
        // 10：秒   5：秒
        // 第一次执行的时间为10秒，然后每隔五秒执行一次
        // 第二个参数为首次执行的延时时间，第三个参数为定时执行的间隔时间
        // 10：秒   5：秒
        // 第一次执行的时间为10秒，然后每隔五秒执行一次
        service.scheduleAtFixedRate(runnable, 500, 1000, TimeUnit.MILLISECONDS)



        cameraView?.startRecording(videoFile!!, ContextCompat.getMainExecutor(mContext), object : OnVideoSavedCallback {
            override fun onVideoSaved(outputFileResults: OutputFileResults) {
//                if (recordTime < 1500 && videoFile?.exists()!! && videoFile!!.delete()) {
//                if (recordTime < 1500 && videoFile?.exists()!! && videoFile!!.delete()) {
//                    return
//                }

                service.shutdownNow()
                captureListener?.recordEnd(videoFile!!,recordTime)
                recordTime = 0L
                if (updateAfterTakePhotoDefault) scanPhotoAlbum(videoFile)

                var playVideoAfterRecordFinish = false
                if (playVideoAfterRecordFinish) {
                    mTextureView?.visibility = View.VISIBLE
                    mTextureView?.let { transformsTextureView(it) }
                    if (mTextureView?.isAvailable!!) {
                        startVideoPlay(videoFile!!, object : OnVideoPlayPrepareListener {
                            override fun onPrepared() {
                                cameraView?.visibility = View.GONE
                            }
                        }
                        )
                    } else {
                        mTextureView?.surfaceTextureListener = object : SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                startVideoPlay(videoFile!!, object : OnVideoPlayPrepareListener {
                                    override fun onPrepared() {
                                        cameraView?.visibility = View.GONE
                                    }
                                }
                                )
                            }

                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                return false
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                    }
                }

            }

            override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
//                if (flowCameraListener != null) {
//                    flowCameraListener.onError(videoCaptureError, message, cause)
//                }
            }
        })
    }

    /**
     * 预览自拍视频时 旋转TextureView 解决左右镜像的问题
     *
     * @param textureView
     */
    private fun transformsTextureView(textureView: TextureView) {
        val matrix = Matrix()
        val screenHeight: Int = ScreenUtils.getScreenHeight()
        val screenWidth: Int = ScreenUtils.getScreenWidth()
        var lensFacing = 1
        if (cameraView?.cameraLensFacing != null) {
            lensFacing = cameraView?.cameraLensFacing!!
        }
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            matrix.postScale(-1f, 1f, 1f * screenWidth / 2, 1f * screenHeight / 2)
        } else {
            matrix.postScale(1f, 1f, 1f * screenWidth / 2, 1f * screenHeight / 2)
        }
        textureView.setTransform(matrix)
    }

    /**
     * 开始循环播放视频
     *
     * @param videoFile
     */
    private fun startVideoPlay(videoFile: File, onVideoPlayPrepareListener: OnVideoPlayPrepareListener?) {
        try {
            if (mMediaPlayer == null) {
                mMediaPlayer = MediaPlayer()
            }
            mMediaPlayer?.apply {
                this.setDataSource(videoFile.absolutePath)
                this.setSurface(Surface(mTextureView!!.surfaceTexture))
                this.setLooping(true)
                this.setOnPreparedListener(OnPreparedListener { mp: MediaPlayer ->
                    mp.start()
                    val ratio = mp.videoWidth * 1f / mp.videoHeight
                    val width1 = mTextureView!!.width
                    val layoutParams = mTextureView!!.layoutParams
                    layoutParams.height = (width1 / ratio).toInt()
                    mTextureView!!.layoutParams = layoutParams
                    if (onVideoPlayPrepareListener != null) {
                        onVideoPlayPrepareListener.onPrepared()
                    }
                })
                this.prepareAsync()
            }

        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * 停止视频播放
     */
    private fun stopVideoPlay() {
        if (mMediaPlayer != null) {
            mMediaPlayer?.stop()
            mMediaPlayer?.release()
            mMediaPlayer = null
        }
        mTextureView!!.visibility = View.GONE
    }

    // 绑定生命周期 否者界面可能一片黑
    fun setBindToLifecycle(lifecycleOwner: LifecycleOwner) {
        if (ActivityCompat.checkSelfPermission(
                (lifecycleOwner as Context),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }
        cameraView?.bindToLifecycle(lifecycleOwner)
        lifecycleOwner.lifecycle
            .addObserver(LifecycleEventObserver { source: LifecycleOwner?, event: Lifecycle.Event ->
            })
    }

    /**
     * 当确认保存此文件时才去扫描相册更新并显示视频和图片
     *
     * @param dataFile
     */
    private fun scanPhotoAlbum(dataFile: File?) {
        if (dataFile == null) {
            return
        }
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            dataFile.absolutePath.substring(dataFile.absolutePath.lastIndexOf(".") + 1)
        )
        MediaScannerConnection.scanFile(
            mContext,
            arrayOf(dataFile.absolutePath),
            arrayOf(mimeType),
            null
        )
    }

    fun initTakePicPath(mContext: Context): File? {
        //支持jpg jpeg 当存png时会存成jpg
        return File(
            mContext.externalMediaDirs[0],
            System.currentTimeMillis().toString() + ".jpeg"
        ).apply {
            //返回文件的路径
            mediaPath = absolutePath

        }
    }

    fun initStartRecordingPath(mContext: Context): File? {
        return File(
            mContext.externalMediaDirs[0],
            System.currentTimeMillis().toString() + ".mp4"
        )
    }

    /**
     * 将照片添加到图库
     */
    private fun galleryAddPic() {
        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
            photoFile = File(mediaPath)
            mediaScanIntent.data = Uri.fromFile(photoFile)
            mContext?.sendBroadcast(mediaScanIntent)
        }
    }


    /**
     * 切换前后摄像头
     */
    fun changeCameraFace(){
        cameraView?.toggleCamera()
        cameraView?.cameraLensFacing?.let { captureListener?.selectCamera(it) }
       Log.d("切换","摄像头状态 ${cameraView?.previewStreamState}")
    }

    /**
     * 切换闪光灯模式
     */
    fun changeFlash(){
        typeFlash++
        if (typeFlash > 0x023) typeFlash = TYPE_FLASH_AUTO
        when (typeFlash) {
            TYPE_FLASH_AUTO -> {
                cameraView?.flash = ImageCapture.FLASH_MODE_AUTO
                tvFlash?.text ="自动"
            }
            TYPE_FLASH_ON -> {
                cameraView?.flash = ImageCapture.FLASH_MODE_ON
                tvFlash?.text ="ON"
            }
            TYPE_FLASH_OFF -> {
                cameraView?.flash = ImageCapture.FLASH_MODE_OFF
                tvFlash?.text ="OFF"

            }
        }
        cameraView?.flash?.let { captureListener?.selectFlashMode(it) }
    }

    fun initCamera(config:CameraConfig,context: Context){
        config.apply {
            cameraView?.cameraLensFacing = lensFacing
            cameraView?.flash  = config.flashMode
            updateAfterTakePhotoDefault = config.updateAfterTakePhoto
            captureListener = config.listener
            cameraView?.isPinchToZoomEnabled = config.zoomEnabled
            touchFocusListener = config.touchFocusTouchListener
        }


        setBindToLifecycle(context as LifecycleOwner)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {

        when (event?.action) {
            MotionEvent.ACTION_DOWN-> {
                var dx = event.x
                var dy = event.y
                Log.d("onTouchEvent","onTouchEvent ACTION_DOWN dx:$dx ===dy:$dy")
            }
            MotionEvent.ACTION_UP-> {

            }
            else -> {
            }
        }
        return true
    }

    inner class RecordCountDownTimer(totalTime:Long,countDownInterval: Long):CountDownTimer(totalTime,countDownInterval){
        override fun onFinish() {

        }

        override fun onTick(p0: Long) {
            recordTime = p0
            tvTakeVideo?.text = "停止 $p0"
        }

    }

    @NonNull
    fun getCameraConfig(context: Context):CameraConfig{
        return CameraConfig(context)
    }

    inner class CameraConfig(var context: Context){
        var listener:CaptureListener ?=null
        //设置相机的前后置
         var lensFacing: Int = defaultLensFacing
        //设置闪光灯模式
         var flashMode = defaultFlashMode
        //拍照完是否更新到相册
        var updateAfterTakePhoto = updateAfterTakePhotoDefault
        //是否可以手动调整焦距
        var zoomEnabled = mIsPinchToZoomEnabled
        //touch监听
        var touchFocusTouchListener:OnFocusTouchListener?=null
        @NonNull
        fun setCameraSelect(lensFacing:Int):CameraConfig{
            this.lensFacing = lensFacing
            return this
        }
        @NonNull
        fun setFlashMode(flashMode:Int):CameraConfig{
            this.flashMode = flashMode
            return this
        }
        @NonNull
        fun setUpdapteAfterTake(updateAfterTakePhoto:Boolean):CameraConfig{
            this.updateAfterTakePhoto = updateAfterTakePhoto
            return this
        }

        @NonNull
        fun setOnCaptureListener(listener:CaptureListener):CameraConfig{
            this.listener = listener
            return this
        }

        @NonNull
        fun setZoomEnable(zoomEnabled:Boolean):CameraConfig{
            this.zoomEnabled = zoomEnabled
            return this
        }


        fun startCamera(){
            initCamera(this,context)
        }


        @NonNull
        fun setOnTouchFocusListener(touchFocusTouchListener:OnFocusTouchListener):CameraConfig{
            this.touchFocusTouchListener = touchFocusTouchListener
            return this
        }
    }

}

interface CaptureListener {

    fun onTakePhotos(photoFile:File)

    fun recordStart()

    fun recordTick(time: Long)

    fun recordEnd(videoFile:File,time: Long)

    fun recordError()
    /**选择了前/后摄像头
     * @param frontOrBackCamera
     * 前置摄像头 LENS_FACING_FRONT = 0
     * 后置置摄像头 LENS_FACING_BACK = 1
     * */
    fun selectCamera(@CameraSelector.LensFacing frontOrBackCamera:Int)
    /**选择了何种闪光灯模式
     * @param mode
     *  int FLASH_MODE_UNKNOWN = -1;
     *  自动 int FLASH_MODE_AUTO = 0;
     *  开 int FLASH_MODE_ON = 1;
     *  关 int FLASH_MODE_OFF = 2
     * */
    fun selectFlashMode(@FlashMode mode:Int)
}
interface OnVideoPlayPrepareListener {
    fun onPrepared()
}

interface OnFocusTouchListener{
    fun onTouch(view:View, event:MotionEvent )
}

