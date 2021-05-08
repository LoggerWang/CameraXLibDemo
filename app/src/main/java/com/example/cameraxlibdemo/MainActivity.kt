package com.example.cameraxlibdemo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import com.example.cameraxlib.LegendCameraXView
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

class MainActivity : AppCompatActivity() {
//    var ivTest:ImageView?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.tvGo).setOnClickListener { startActivity(Intent(this,PhotoActivity::class.java)) }
        findViewById<TextView>(R.id.tvGo2).setOnClickListener { startActivity(Intent(this,PhotoActivity2::class.java)) }
    ivTest.post {
        println("ivTest.post=====ivTest.width:${ivTest.width} ==ivTest.height:${ivTest.height}")
        println("ivTest.post====ivTest.measuredWidth:${ivTest.measuredWidth} ==ivTest.measuredWidth:${ivTest.measuredWidth}")
    }
    println("onCreate=====ivTest.width:${ivTest.width} ==ivTest.height:${ivTest.height}")
    println("onCreate====ivTest.measuredWidth:${ivTest.measuredWidth} ==ivTest.measuredWidth:${ivTest.measuredWidth}")

    window.decorView
    ivTest.viewTreeObserver.addOnGlobalLayoutListener(object :ViewTreeObserver.OnGlobalLayoutListener{
        override fun onGlobalLayout() {
            println("onGlobalLayout=====ivTest.width:${ivTest.width} ==ivTest.height:${ivTest.height}")
            println("onGlobalLayout====ivTest.measuredWidth:${ivTest.measuredWidth} ==ivTest.measuredWidth:${ivTest.measuredWidth}")
            ivTest.viewTreeObserver.removeOnGlobalLayoutListener(this)
        }

    })
    }

    override fun onStart() {
        super.onStart()
        println("onStart=====ivTest.width:${ivTest.width} ==ivTest.height:${ivTest.height}")
        println("onStart====ivTest.measuredWidth:${ivTest.measuredWidth} ==ivTest.measuredWidth:${ivTest.measuredWidth}")

    }

    override fun onResume() {
        super.onResume()
        println("onResume=====ivTest.width:${ivTest.width} ==ivTest.height:${ivTest.height}")
        println("onResume====ivTest.measuredWidth:${ivTest.measuredWidth} ==ivTest.measuredWidth:${ivTest.measuredWidth}")

    }


}