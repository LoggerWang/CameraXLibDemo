package com.example.cameraxlib

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.camera.view.CameraView

/**
 * @author wanglezhi
 * @date   2021/3/8 13:18
 * @discription
 */
class CameraViewExpand:FrameLayout{
    var mContext:Context ?= null
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
    }

}