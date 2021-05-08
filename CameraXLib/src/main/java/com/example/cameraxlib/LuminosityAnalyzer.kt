package com.example.cameraxlib

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.ArrayDeque



/** 用于分析用例回调的助手类型别名 */
typealias LumaListener = (luma: Double) -> Unit

/**
 * 自定义图像分析类.
 *
 * 作用：提供可供 CPU 访问的图片来执行图片处理、计算机视觉或机器学习推断
 *
 * 通过观察YUV格式的数据里的Y平面来计算图像的平均光度
 */
class LuminosityAnalyzer(listener: LumaListener? = null) : ImageAnalysis.Analyzer {

    private val frameRateWindow = 8
    private val frameTimestamps = ArrayDeque<Long>(5)//存放时间戳数组
    private val listeners = ArrayList<LumaListener>().apply { listener?.let { add(it) } }//存放监听回调的数组
    private var lastAnalyzedTimestamp = 0L//最后分析的时间戳
    var framesPerSecond: Double = -1.0//帧频
    private set

    /**
     * 用于添加将在计算每个luma时调用的侦听器
     */
    fun onFrameAnalyzed(listener: LumaListener) = listeners.add(listener)

    //用于从图像平面缓冲区提取字节数组
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    //将缓冲区归零，刷新缓冲区
        val data = ByteArray(remaining())
        get(data)   //将缓冲区复制到字节数组中
        return data //返回字节数组
    }


    /**
    * analyze(image: ImageProxy)方法：https://developer.android.google.cn/reference/androidx/camera/core/ImageAnalysis.Analyzer#analyze(androidx.camera.core.ImageProxy)
    *
    * image参数：格式ImageFormat.YUV_420_888
    *
    * */
    override fun analyze(image: ImageProxy) {

        //如果没有附加侦听器，我们就不需要执行分析
        if (listeners.isEmpty()) {
            image.close()
            return
        }

        //跟踪分析的帧
        val currentTime = System.currentTimeMillis()//获取系统的时间戳
        frameTimestamps.push(currentTime)

        //使用移动平均线计算FPS
        while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()

        val timestampFirst = frameTimestamps.peekFirst() ?: currentTime

        val timestampLast = frameTimestamps.peekLast() ?: currentTime
        framesPerSecond = 1.0 / ((timestampFirst - timestampLast) / frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0

        //分析可能会花费任意长的时间，因为我们在不同的线程中运行，它不会让其他用例停顿
        lastAnalyzedTimestamp = frameTimestamps.first

        //由于图像分析的格式是YUV，图像[0]包含亮度平面
        val buffer = image.planes[0].buffer

        //从回调对象中提取图像数据
        val data = buffer.toByteArray()

        //将数据转换为0-255像素值的数组
        val pixels = data.map { it.toInt() and 0xFF }

        // 计算图像的平均亮度
        val luma = pixels.average()

        //使用新值调用所有侦听器
        listeners.forEach { it(luma) }

        //处理完一定要关闭（否则可能会阻止生成其他图像（导致预览停滞）或丢弃图像）
        image.close()
    }
}
