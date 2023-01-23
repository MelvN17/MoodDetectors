package com.example.mooddetectors

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.opencv.android.*
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel


class CameraActivity : AppCompatActivity(), CvCameraViewListener2 {
    companion object {
        const val SCREEN_WIDTH = 1920
        const val SCREEN_HEIGHT = 1080
    }
    var cascadefile: File? = null
    var model: ByteBuffer? = null
    var interpreter: Interpreter? = null
    //var face_haar_cascade = CascadeClassifier("haarcascade_frontalface_default.xml")
    var faceDetector: CascadeClassifier? = null
    var cameraBridgeViewBase: CameraBridgeViewBase? = null
    var quit: Boolean = false
    var mRGBA: Mat? = null
    var mGrey: Mat? = null


    //FPS log variables
    private var frameCount = 0
    private var lastLogTime = System.currentTimeMillis()

//    private val loader = object : BaseLoaderCallback(this) {
//        override fun onManagerConnected(status: Int) {
//            when (status) {
//                LoaderCallbackInterface.SUCCESS -> {
//                    var IS: InputStream = resources.openRawResource(R.raw.haarcascade_frontalface_default)
//                    var cascadeDir: File = getDir("cascade", Context.MODE_PRIVATE)
//                    cascadefile = File(cascadeDir, "haarcascade_frontalface_dafault.xml")
//
//                    var fos: FileOutputStream = FileOutputStream(cascadefile)
//
//                    var buffer = ByteArray(4096)
//                    var bytesRead: Int = IS.read(buffer)
//
//                   while (bytesRead != -1){
//                       fos.write(buffer, 0, bytesRead)
//                       bytesRead = IS.read(buffer)
//
//                   }
//
//                    IS.close()
//                    fos.close()
//
//                    faceDetector = CascadeClassifier(cascadefile?.absolutePath)
//
//                    if(faceDetector!!.empty()){
//                        faceDetector = null
//                    }else{
//                        cascadeDir.delete()
//                    }
//
//                    System.loadLibrary("opencv_java4")
//                    cameraBridgeViewBase?.enableView()
//                }
//                else -> super.onManagerConnected(status)
//            }
//        }
//    }

    //With this, we do not need to close the InputStream and FileOutputStream explicitly, the resources will be closed automatically when the try block is exited. This can help to avoid resource leaks and make your code easier to read and maintain.
    private val loader = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    try {
                        val IS: InputStream = resources.openRawResource(R.raw.haarcascade_frontalface_default)
                        val cascadeDir: File = getDir("cascade", Context.MODE_PRIVATE)
                        cascadefile = File(cascadeDir, "haarcascade_frontalface_alt_tree.xml")
                        Log.d("onManagerConnected", "Cascade file loaded successfully")
                        val fos: FileOutputStream = FileOutputStream(cascadefile)
                        val buffer = ByteArray(4096)
                        var bytesRead: Int = IS.read(buffer)
                        while (bytesRead != -1) {
                            fos.write(buffer, 0, bytesRead)
                            bytesRead = IS.read(buffer)
                        }
                        faceDetector = CascadeClassifier(cascadefile?.absolutePath)
                        if (faceDetector!!.empty()) {
                            faceDetector = null
                        } else {
                            cascadeDir.delete()
                        }
                        System.loadLibrary("opencv_java4")
                        cameraBridgeViewBase?.enableView()

                        //load model
                        val fileDescriptor = assets.openFd("model.tflite")
                        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
                        val fileChannel = inputStream.channel
                        val startOffset = fileDescriptor.startOffset
                        val declaredLength = fileDescriptor.declaredLength
                        model = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
                        interpreter = Interpreter(model!!)
                        Log.d("onManagerConnected", "Cascade file loaded successfully")
                    } catch (e: Exception) {
                        // handle exception here
                        Log.e("onManagerConnected", "Error loading cascade file: ${e.message}")
                        Toast.makeText(this@CameraActivity, "Error loading cascade file", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> super.onManagerConnected(status)
            }
        }
    }





    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera).apply{
            cameraBridgeViewBase = findViewById(R.id.cameraView)
        }




        getPermission()
        cameraBridgeViewBase?.setCvCameraViewListener(this)

        if (OpenCVLoader.initDebug()) {
            initView()
            Toast.makeText(this, "Starting", Toast.LENGTH_SHORT).show()
        }
//
//        initView()
//        initListener()



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
        mGrey = Mat(height, width, CvType.CV_8UC1)
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

        mGrey = inputFrame.gray()
        Core.flip(mRGBA!!.t(),mRGBA,1);
        Core.flip(mGrey!!.t(),mGrey,1);
        //detect faces and predict
        var faceDetections: MatOfRect = MatOfRect()
        faceDetector!!.detectMultiScale(mGrey, faceDetections)

        try {
            for (rect:Rect in faceDetections.toArray()) {
                Imgproc.rectangle(mRGBA, Point(rect.x.toDouble(), rect.y.toDouble()), Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()), Scalar(255.0, 0.0, 0.0), 2)
                val roiGray = mGrey!!.submat(rect.y - 5, rect.y + rect.height + 5, rect.x - 5, rect.x + rect.width + 5)
//                Imgproc.resize(roiGray, roiGray, Size(48.0, 48.0))
                val imagePixels = roiGray
                val bmp = Bitmap.createBitmap(imagePixels.cols(), imagePixels.rows(), Bitmap.Config.ARGB_8888)

                Utils.matToBitmap(imagePixels, bmp)
                val scaledBitmap = Bitmap.createScaledBitmap(bmp, 48, 48, false)
                //convert bitmap to byte array*
                val byteBuffer = ByteBuffer.allocateDirect(4*1*48*48*1)
                byteBuffer.order(ByteOrder.nativeOrder())
                val intValue = IntArray(48*48)
                scaledBitmap.getPixels(intValue, 0, scaledBitmap.width, 0, 0, scaledBitmap.width,scaledBitmap.height)
                var pixel = 0
                for (i in 0 until 48) {
                    for (j in 0 until 48) {
                        val value: Int = intValue[pixel++]
                        //normalize image
//                        byteBuffer.putFloat((value shr 16 and 0xFF) / 255.0f)
//                        byteBuffer.putFloat((value shr 8 and 0xFF) / 255.0f)
                        byteBuffer.putFloat((value and 0xFF) / 255.0f)
                    }
                }

//                    bmp.copyPixelsToBuffer(byteBuffer)
//                    val byteArray = byteBuffer.array()
//
////pass byte array to the TensorFlow Lite interpreter
//                    val roiGreyUint8 = Mat()
//                    Core.normalize(roiGray, roiGreyUint8, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8UC1)
//                    val input = Array(1) { ByteArray(48 * 48) }
//                    roiGreyUint8.get(0, 0, input[0])
//
//
                val output = Array(1) { FloatArray(7) }
                interpreter!!.run(byteBuffer, output)
                val maxIndex = output[0].indices.maxBy { output[0][it] }!!
                val emotionLabels = arrayOf("angry", "disgust", "fear", "happy", "sad", "surprise", "neutral")
                val emotion = emotionLabels[maxIndex]
                Imgproc.putText(mRGBA, "Sentiment: $emotion", Point(rect.x.toDouble(), (rect.y + rect.height + 40).toDouble()), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, Scalar(255.0, 255.0, 0.0))

            }
        } catch (e: Exception) {
            Log.e("ERRORRR: ", e.stackTraceToString())
        }


//        try {
//            for(rect:Rect in faceDetections.toArray()){
//                Imgproc.rectangle(mRGBA, Point(rect.x.toDouble(), rect.y.toDouble()), Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()), Scalar(255.0, 255.0,0.0))
//                // crop the face detected and resize to 48 x 48(the input of the model)
//                var roiGrey = mGrey!!.submat(rect.y - 5, rect.y + rect.height + 5, rect.x - 5, rect.x + rect.width + 5)
//                Imgproc.resize(roiGrey, roiGrey, Size(48.0, 48.0))
//                //converting the image to bitmaps
//                var imagePixels = roiGrey
//
//                if(!imagePixels.empty()){
//                    val bmp = Bitmap.createBitmap(imagePixels.cols(), imagePixels.rows(), Bitmap.Config.ARGB_8888)
//                    Utils.matToBitmap(imagePixels, bmp)
//
//                    //convert bitmap to byte array
//                    val byteBuffer = ByteBuffer.allocate(bmp.byteCount)
//                    bmp.copyPixelsToBuffer(byteBuffer)
////                    val byteArray = byteBuffer.array()
////
////                    //pass byte array to the TensorFlow Lite interpreter
////                    val roiGreyUint8 = Mat()
////                    Core.normalize(roiGrey, roiGreyUint8, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8UC1)
////                    val input = Array(1) { ByteArray(48 * 48) }
////                    roiGreyUint8.get(0, 0, input[0])
////
////                    var output = Array(1) { FloatArray(7) }
////                    interpreter?.run(input, output)
//
//                    //pass byte buffer to the TensorFlow Lite interpreter
//                    val input = Array(1) { ByteArray(48 * 48) }
//                    val roiGreyUint8 = Mat()
//                    Core.normalize(roiGrey, roiGreyUint8, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8UC1)
//                    roiGreyUint8.get(0, 0, input[0])
//                    val output = Array(1) { IntArray(7) }
//                    interpreter?.run(input, output)
//
//                    //get the index of the highest prediction
//                    val maxIndex = output[0].indices.maxBy { output[0][it] }!!
//
//                    // Map the index to the corresponding emotion
//                    val emotionLabels = arrayOf("angry", "disgust", "fear", "happy", "sad", "surprise", "neutral")
//                    val emotion = emotionLabels[maxIndex]
//
//                    //display the emotion prediction on the screen
//                    Imgproc.putText(mRGBA, "Sentiment: $emotion", Point(rect.x.toDouble(), (rect.y + rect.height + 40).toDouble()), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, Scalar(255.0, 255.0, 0.0))
////                    Toast.makeText(this@CameraActivity, "Sentiment: $emotion", Toast.LENGTH_SHORT).show()
//
//
//                }else{
//                    Log.d("EMPTY", "EMPTY")
//                }
//
//            }
//
//        }catch(e: Exception){
//            Log.e("ERRORRR: ", ""+ frameCount + " " + e.message.toString());
//        }

        //var frameT = mRGBA!!.t()
//        Core.flip(mRGBA!!.t(),mRGBA,0);


        //FPS LOGGING
        frameCount++
        logFPS()



        return mRGBA!!
    }

//    //optimized version
//    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
//        //Convert the RGBA image to grayscale for faster processing
//        mGrey = inputFrame.gray()
//        val inputFrameSize = inputFrame.rgba().size()
//        val frameWidth = inputFrameSize.width
//        val frameHeight = inputFrameSize.height
//        //Resize the image to reduce processing time
//        Imgproc.resize(mGrey, mGrey, Size(frameWidth/2, frameHeight/2))
//
//
//
//        //detect faces using grayscale image and smaller scale factor
//        var faceDetections: MatOfRect = MatOfRect()
//        faceDetector!!.detectMultiScale(mGrey, faceDetections, 1.1, 3, 0, Size(30.0, 30.0))
//
//
//
//        //Draw rectangles on the faces
//        for(rect:Rect in faceDetections.toArray()){
//            Imgproc.rectangle(mGrey, Point(rect.x.toDouble(), rect.y.toDouble()), Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()), Scalar(255.0, 255.0,0.0))
//        }
//
//
//
//        Imgproc.resize(mGrey, mGrey, Size(frameWidth, frameHeight))
//        //Convert grayscale image to RGB
//        mRGBA = Mat()
//        Imgproc.cvtColor(mGrey, mRGBA, Imgproc.COLOR_GRAY2RGBA)
//
//        //FPS LOGGING
//        frameCount++
//        logFPS()
//
//        return mRGBA!!
//    }

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


    // logic for logging the FPS
    private fun logFPS() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime >= 1000) {
            Log.d("FPS", "FPS: " + frameCount)
            frameCount = 0
            lastLogTime = currentTime
        }
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
