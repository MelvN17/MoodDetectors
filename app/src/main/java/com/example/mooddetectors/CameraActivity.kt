package com.example.mooddetectors

import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.android.JavaCameraView
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class CameraActivity : AppCompatActivity(), CvCameraViewListener2 {
    companion object {
        const val SCREEN_WIDTH = 1920
        const val SCREEN_HEIGHT = 1080
    }
    var cascadefile: File? = null
    //var face_haar_cascade = CascadeClassifier("haarcascade_frontalface_default.xml")
    var faceDetector: CascadeClassifier? = null
    var javaCameraView: JavaCameraView? = null
    var cameraBridgeViewBase: CameraBridgeViewBase? = null
    var quit: Boolean = false
    var mRGBA: Mat? = null
    var mGrey: Mat? = null
    private val loader = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    var IS: InputStream = resources.openRawResource(R.raw.haarcascade_frontalface_default)
                    var cascadeDir: File = getDir("cascade", Context.MODE_PRIVATE)
                    cascadefile = File(cascadeDir, "haarcascade_frontalface_dafault.xml")

                    var fos: FileOutputStream = FileOutputStream(cascadefile)

                    var buffer = ByteArray(4096)
                    var bytesRead: Int = IS.read(buffer)

                   while (bytesRead != -1){
                       fos.write(buffer, 0, bytesRead)
                       bytesRead = IS.read(buffer)

                   }

                    IS.close()
                    fos.close()

                    faceDetector = CascadeClassifier(cascadefile?.absolutePath)

                    if(faceDetector!!.empty()){
                        faceDetector = null
                    }else{
                        cascadeDir.delete()
                    }

                    System.loadLibrary("opencv_java4")
                    cameraBridgeViewBase?.enableView()
                }
                else -> super.onManagerConnected(status)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)



        cameraBridgeViewBase = findViewById(R.id.cameraView)
//        javaCameraView = findViewById(R.id.cameraView)
        getPermission()
        cameraBridgeViewBase?.setCvCameraViewListener(this)

        if (OpenCVLoader.initDebug()) {
            initView()
            Toast.makeText(this, "Starting", Toast.LENGTH_SHORT).show()
        }
//
//        initView()
//        initListener()

//        while (!quit) {
//
//        }

    }

    override fun onDestroy() {
        super.onDestroy()
        quit = true;
        if (cameraBridgeViewBase != null)
            cameraBridgeViewBase!!.disableView()
    }

    override fun onPause() {
        super.onPause()
        quit = true
        if(cameraBridgeViewBase != null){
            cameraBridgeViewBase!!.disableView()
        }
    }
    override fun onResume() {
        super.onResume()
        quit = false
        if (OpenCVLoader.initDebug()) {
            Toast.makeText(this, "OpenCV successfully loaded", Toast.LENGTH_SHORT).show()
            Log.d(CameraActivity::class.java.simpleName, "OpenCV successfully loaded")
            loader.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        } else {
            Log.d(CameraActivity::class.java.simpleName, "OpenCV load failed")
            Toast.makeText(this, "OpenCV load failed", Toast.LENGTH_SHORT).show()

        }

//        mCameraFrameManager = CameraFrameManager(this, mFeatureString, dnnHelper)
//        mCameraFrameManager.start()
    }

    private fun initView() {
        cameraBridgeViewBase!!.enableView()
        cameraBridgeViewBase!!.visibility = SurfaceView.VISIBLE
        cameraBridgeViewBase!!.setMaxFrameSize(SCREEN_WIDTH, SCREEN_HEIGHT)
    }

    private fun initListener() {
        cameraBridgeViewBase!!.setCvCameraViewListener(this)
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        mRGBA = Mat()
        mGrey = Mat()
//        frame = Mat(height, width, CvType.CV_8UC4)
//        cameraBridgeViewBase!!.enableFpsMeter()
//        dnnHelper.onCameraViewStarted(this)

    }

    override fun onCameraViewStopped() {
        mRGBA?.release()
        mGrey?.release()

    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        mRGBA = inputFrame.rgba()
//        frameT = frame!!.t()
//        Core.flip(frame!!.t(), frameT, 1)
//        Imgproc.resize(frameT, frameT, frame!!.size())

        mGrey = inputFrame.gray()

        //detect faces
        var faceDetections: MatOfRect = MatOfRect()
        faceDetector!!.detectMultiScale(mRGBA, faceDetections)

        for(rect:Rect in faceDetections.toArray()){
            Imgproc.rectangle(mRGBA, Point(rect.x.toDouble(), rect.y.toDouble()), Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()), Scalar(255.0, 255.0,0.0))
        }
        var frameT = mRGBA!!.t()
        Core.flip(mRGBA!!.t(), frameT, 1);



        return frameT
//        mCurrentFrame = inputFrame.rgba()
//        val image = mCurrentFrame.clone()
//        Imgproc.cvtColor(image, image, Imgproc.COLOR_RGB2BGR)
//
//        try {
//            val frameInfo = mCameraFrameManager.getFrameInfo()
//            Core.addWeighted(image, 1.0, frameInfo, 0.7, 0.0, image)
//        } catch (e: NoCameraFrameInfoAvailableException) {
//        }
//
//        if (mShowDebug)
//            setImage(createDebugImage(image))
//        else
//            setImage(image)
//
//        setFps()
//
//        if (mFeatureString == Feature.OVERTAKING) {
//            getCurrentLane()
//            setCurrentSpeed()
//        }
//        return mCurrentFrame
    }

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }else{
            cameraBridgeViewBase?.setCameraPermissionGranted()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraBridgeViewBase?.setCameraPermissionGranted()

        }else{

            getPermission()
        }
    }
}
