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
import org.opencv.android.Utils
import org.opencv.core.*
import java.nio.file.Files.size
import org.opencv.imgproc.Imgproc


class MainActivity : AppCompatActivity() {

    val CAMERA_REQUEST_CODE = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraButton.setOnClickListener {
            val callCameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (callCameraIntent.resolveActivity(packageManager) != null) {
                startActivityForResult(callCameraIntent, CAMERA_REQUEST_CODE)
            }
        }
        if(!OpenCVLoader.initDebug()){
            Log.i("OpenCV", "Failed");
        }else{
            Log.i("OpenCV", "successfully built !");
        }

        onImageScan()
    }

    fun onImageScan() {
        /* ①：画像読み込み */
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.profile)
        var mat = Mat()
        Utils.bitmapToMat(bmp, mat, true)

        /* ②：画像を二値化 */
        mat = getThreshold(mat)

        /* ③：輪郭の座標を取得 */
        val contours = getContour(mat)
        val points = contour2point(contours)
    }

    private fun getThreshold(mat: Mat): Mat {
        /* ②-1-1：RGB空間チャネルの取得 */
        val mat_rgb = mat.clone()
        val channels_rgb = ArrayList<Mat>()
        Core.split(mat_rgb, channels_rgb)

        /* ②-1-2：RGB空間 → グレースケール変換 → 二値化 */
        Imgproc.cvtColor(mat_rgb, mat_rgb, Imgproc.COLOR_RGB2GRAY)
        Core.subtract(channels_rgb[0], mat_rgb, channels_rgb[0])
        Core.subtract(channels_rgb[1], mat_rgb, channels_rgb[1])
        Core.subtract(channels_rgb[2], mat_rgb, channels_rgb[2])
        Imgproc.threshold(channels_rgb[0], channels_rgb[0], 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        Imgproc.threshold(channels_rgb[1], channels_rgb[1], 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        Imgproc.threshold(channels_rgb[2], channels_rgb[2], 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)

        /* ②-1-3：RGBの輪郭を取得 */
        val contour_rgb0 = getContour(channels_rgb[0])
        val contour_rgb1 = getContour(channels_rgb[1])
        val contour_rgb2 = getContour(channels_rgb[2])


        /* ②-2-1：YUV空間チャネルの取得 */
        val mat_yuv = mat.clone()
        Imgproc.cvtColor(mat_yuv, mat_yuv, Imgproc.COLOR_BGR2YUV)
        val channels_yuv = ArrayList<Mat>()
        Core.split(mat_yuv, channels_yuv)

        /* ②-2-2：YUV空間 → グレースケール変換 → 二値化 */
        Imgproc.cvtColor(mat_yuv, mat_yuv, Imgproc.COLOR_RGB2GRAY)
        Core.subtract(channels_yuv[0], mat_yuv, channels_yuv[0])
        Core.subtract(channels_yuv[1], mat_yuv, channels_yuv[1])
        Core.subtract(channels_yuv[2], mat_yuv, channels_yuv[2])
        Imgproc.threshold(channels_yuv[0], channels_yuv[0], 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        Imgproc.threshold(channels_yuv[1], channels_yuv[1], 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
        Imgproc.threshold(channels_yuv[2], channels_yuv[2], 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)

        /* ②-2-3：YUVの輪郭を取得 */
        val contour_yuv0 = getContour(channels_yuv[0])
        val contour_yuv1 = getContour(channels_yuv[1])
        val contour_yuv2 = getContour(channels_yuv[2])


        /* ②-3：マスク画像に輪郭を合成 */
        val mat_mask = Mat(mat.size(), CvType.CV_8UC4, Scalar.all(255.0))
        val color = Scalar(0.0, 0.0, 0.0)
        Imgproc.drawContours(mat_mask, contour_rgb0, -1, color, -1)
        Imgproc.drawContours(mat_mask, contour_rgb1, -1, color, -1)
        Imgproc.drawContours(mat_mask, contour_rgb2, -1, color, -1)
        Imgproc.drawContours(mat_mask, contour_yuv0, -1, color, -1)
        Imgproc.drawContours(mat_mask, contour_yuv1, -1, color, -1)
        Imgproc.drawContours(mat_mask, contour_yuv2, -1, color, -1)

        Imgproc.cvtColor(mat_mask, mat_mask, Imgproc.COLOR_RGB2GRAY)
        Imgproc.threshold(mat_mask, mat_mask, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)

        return mat_mask
    }

    private fun getContour(mat: Mat): List<MatOfPoint> {
        val contour = ArrayList<MatOfPoint>()

        /* 二値画像中の輪郭を検出 */
        val tmp_contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat.zeros(Size(5.0, 5.0), CvType.CV_8UC1)
        Imgproc.findContours(mat, tmp_contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_TC89_L1)
        for (i in tmp_contours.indices) {
            if (Imgproc.contourArea(tmp_contours[i]) < mat.size().area() / (100 * 1)) {
                /* サイズが小さいエリアは無視 */
                continue
            }

            val ptmat2 = MatOfPoint2f(*tmp_contours[i].toArray())
            val approx = MatOfPoint2f()
            val approxf1 = MatOfPoint()

            /* 輪郭線の周囲長を取得 */
            val arclen = Imgproc.arcLength(ptmat2, true)
            /* 直線近似 */
            Imgproc.approxPolyDP(ptmat2, approx, 0.02 * arclen, true)
            approx.convertTo(approxf1, CvType.CV_32S)
            if (approxf1.size().area() != 4.0) {
                /* 四角形以外は無視 */
                continue
            }

            /* 輪郭情報を登録 */
            contour.add(approxf1)
        }

        return contour
    }

    private fun contour2point(contour: List<MatOfPoint>): List<List<Point>> {
        val points = ArrayList<List<Point>>()
        for (i in contour.indices) {
            points.add(contour[i].toList())
        }
        return points
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when(requestCode) {
            CAMERA_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    photoImageView.setImageBitmap(data.extras.get("data") as Bitmap)
                }
            }
            else -> {
                Toast.makeText(this, "Unrecognized request code.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
