package com.example.cameraxlibdemo

import android.app.Application
import com.blankj.utilcode.util.Utils

/**
 * @author wanglezhi
 * @date   2021/2/24 14:16
 * @discription
 */
class MyApplication:Application() {

    override fun onCreate() {
        super.onCreate()

        Utils.init(this)
    }
}