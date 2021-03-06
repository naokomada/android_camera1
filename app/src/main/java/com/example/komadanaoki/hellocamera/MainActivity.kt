package com.example.komadanaoki.hellocamera

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.OpenCVLoader
import android.R.attr.src
import android.graphics.BitmapFactory
import com.example.komadanaoki.hellocamera.lib.MyOpenCV
import org.opencv.android.Utils
import org.opencv.core.*
import java.nio.file.Files.size
import org.opencv.imgproc.Imgproc


class MainActivity : AppCompatActivity() {

    val CAMERA_REQUEST_CODE = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        cameraButton.setOnClickListener {
//            val callCameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
//            if (callCameraIntent.resolveActivity(packageManager) != null) {
//                startActivityForResult(callCameraIntent, CAMERA_REQUEST_CODE)
//            }
//        }
        if(!OpenCVLoader.initDebug()){
            Log.i("OpenCV", "Failed");
        }else{
            Log.i("OpenCV", "successfully built !");
        }

        val opencv = MyOpenCV()
        val canvasView = findViewById<CanvasView>(R.id.CanvasView)
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.sample10)
        opencv.onImageScan(bmp, canvasView)
    }


//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        when(requestCode) {
//            CAMERA_REQUEST_CODE -> {
//                if (resultCode == Activity.RESULT_OK && data != null) {
//                    photoImageView.setImageBitmap(data.extras.get("data") as Bitmap)
//                }
//            }
//            else -> {
//                Toast.makeText(this, "Unrecognized request code.", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
}
