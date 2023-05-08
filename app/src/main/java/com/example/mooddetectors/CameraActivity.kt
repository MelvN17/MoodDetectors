package com.example.mooddetectors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.SurfaceView
import android.view.View
import android.widget.ImageView
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.opencv.android.*
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*


class CameraActivity : AppCompatActivity(), CvCameraViewListener2,  TextToSpeech.OnInitListener {
    companion object {
        const val SCREEN_WIDTH = 1920
        const val SCREEN_HEIGHT = 1080
    }
    var cascadefile: File? = null
    var model: ByteBuffer? = null
    var interpreter: Interpreter? = null
    var gpuDelegate: GpuDelegate? = null
    //var face_haar_cascade = CascadeClassifier("haarcascade_frontalface_default.xml")
    var faceDetector: CascadeClassifier? = null
    var cameraBridgeViewBase: CameraBridgeViewBase? = null
    var quit: Boolean = false
    var mRGBA: Mat? = null
    var mGrey: Mat? = null
    var height: Int? = null
    var width: Int? = null
    var emotion: String = "No face detected"
    var tts: TextToSpeech? = null
    var btnEva: ImageView? = null
    var ttsIsOn: Boolean = true
    var isAutoEvaluate: Boolean = true


    // auto run for x seconds function
    val handler = Handler(Looper.getMainLooper())
    val autoTriggerInSecs: Long = 5
    var isRecognize: Boolean = false

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
                SUCCESS -> {
                    try {
                        val IS: InputStream = resources.openRawResource(R.raw.haarcascade_frontalface_default)
                        val cascadeDir: File = getDir("cascade", MODE_PRIVATE)
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
                        //TODO: add gpu delegate
                        var options: Interpreter.Options = Interpreter.Options()
                        gpuDelegate = GpuDelegate()
                        options.addDelegate(gpuDelegate)
                        options.setNumThreads(4)
                        interpreter = Interpreter(model!!, options)


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
        tts= TextToSpeech(this,this);

        btnEva = findViewById(R.id.imageView2)

        //AUTO EVALUATE
        val autoToggle = findViewById<Switch>(R.id.autoswitch)
        autoToggle.setOnCheckedChangeListener { _, isChecked ->
            run {
                isAutoEvaluate = isChecked
                if (isAutoEvaluate) {
                    startAutoRun()
                    Toast.makeText(this, "Auto Evaluate turned on", Toast.LENGTH_SHORT).show()
                } else {
                    stopAutoRun()
                    Toast.makeText(this, "Auto Evaluate turned off", Toast.LENGTH_SHORT).show()
                }
            }
        }

        //TTS SWITCH
        val ttsToggle = findViewById<Switch>(R.id.tts_switch)
        val ttsIcon = findViewById<ImageView>(R.id.tts_icon)
        ttsToggle.setOnCheckedChangeListener { _, isChecked ->
            run {
                ttsIsOn = isChecked
                if (ttsIsOn) {
                    ttsIcon.visibility = View.VISIBLE
                    Toast.makeText(this, "TTS turned on", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "TTS turned off", Toast.LENGTH_SHORT).show()
                    ttsIcon.visibility = View.INVISIBLE
                }
            }
        }

        getPermission()
        cameraBridgeViewBase?.setCvCameraViewListener(this)

        if (OpenCVLoader.initDebug()) {
            initView()
            Toast.makeText(this, "Starting", Toast.LENGTH_SHORT).show()
        }

        btnEva!!.setOnClickListener{
            isRecognize = true
            //tts!!.speak(emotion,TextToSpeech.QUEUE_FLUSH,null,"")
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
        mRGBA = Mat(height, width, CvType.CV_8UC4)
        mGrey = Mat(height, width, CvType.CV_8UC1)

        if(isAutoEvaluate) {
            startAutoRun()
        }
//        frame = Mat(height, width, CvType.CV_8UC4)
//        cameraBridgeViewBase!!.enableFpsMeter()
//        dnnHelper.onCameraViewStarted(this)

    }

    override fun onCameraViewStopped() {
        mRGBA?.release()
        mGrey?.release()

        if(isAutoEvaluate) {
            stopAutoRun()
        }

    }

    fun recognizeFacialExpression(image: Mat): Mat {
        //we flip the image by 90 degrees for proper alignment
        Core.flip(image.t(),image,1)

        //convert to grey
        var greyImage: Mat = Mat()
        Imgproc.cvtColor(image, greyImage, Imgproc.COLOR_RGBA2GRAY)

        //set height and width of the image
        height = greyImage.height()
        width = greyImage.width()

        var minFaceSize: Double = height!!*0.1
        val faces: MatOfRect = MatOfRect()
        if(faceDetector != null){
            //detect the face in the current frame

            faceDetector!!.detectMultiScale(greyImage, faces, 1.1, 2, 2, Size(minFaceSize, minFaceSize), Size())
        }

        // now convert it to array
        // now convert it to array
        if (!faces.empty()) {
            val faceDetections = faces.toArray()
            try {
                //optimization step, using a reusable scalar instead of creating it everytime we draw a rectangle
                val rectangleColor = Scalar(27.0, 171.0, 33.0)
                val textColor = Scalar(247.0, 251.0, 55.0)
                val rectangleThickness = 2

                val emotionLabels = arrayOf("angry", "disgust", "fear", "happy", "sad", "surprise", "neutral")

                for (rect:Rect in faceDetections) {
                    //draw rectangle
                    Imgproc.rectangle(image, Point(rect.x.toDouble(), rect.y.toDouble()), Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()), rectangleColor, rectangleThickness)

                    //crop the images
                    val roiGray = greyImage!!.submat(rect.y  , rect.y + rect.height , rect.x , rect.x + rect.width )

                    //convert to bitmap
                    val bmp = Bitmap.createBitmap(
                        roiGray.cols(),
                        roiGray.rows(),
                        Bitmap.Config.ARGB_8888
                    )
                    if (bmp.width != roiGray.width() || bmp.height != roiGray.height()) {
                        throw RuntimeException("Bitmap dimensions do not match matrix dimensions")
                    }
                    Utils.matToBitmap(roiGray, bmp)

                    //resize bmp to 48 by 48 (our model's input size)
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

                    val output = Array(1) {
                        FloatArray(
                            7
                        )
                    }

                    //run the model
                    interpreter!!.run(byteBuffer, output)

                    val maxIndex = output[0].indices.maxBy { output[0][it] }!!
                     emotion = emotionLabels[maxIndex]
                    Imgproc.putText(image, "Mood: $emotion", Point(rect.x.toDouble(), (rect.y + rect.height + 40).toDouble()), Imgproc.FONT_HERSHEY_SIMPLEX, 2.0, textColor,2)

                }


            } catch (e: Exception) {
                Log.e("ERRORRR: ", e.stackTraceToString())
            }

        }
        else{
            emotion = "No face detected"
        }



        //tts!!.speak(emotion,TextToSpeech.QUEUE_FLUSH,null,"")
        Core.flip(image.t(),image,0)
        return image;

    }

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        mRGBA = inputFrame.rgba()

        mGrey = inputFrame.gray()

        mRGBA = recognizeFacialExpression(mRGBA!!)
        //when recognize triggers once every 5 seconds or when button touched
        if(isRecognize){
            if(ttsIsOn) {
                tts!!.speak(emotion, TextToSpeech.QUEUE_FLUSH, null, "")
            }else{
                Toast.makeText(this@CameraActivity, emotion, Toast.LENGTH_SHORT).show()
            }
            isRecognize = false;
        }

//        Core.flip(mRGBA!!.t(),mRGBA,1);
//        Core.flip(mGrey!!.t(),mGrey,1);
//        //detect faces and predict
//        var faceDetections: MatOfRect = MatOfRect()
//        faceDetector!!.detectMultiScale(mGrey, faceDetections)
//
//        try {
//            for (rect:Rect in faceDetections.toArray()) {
//                Imgproc.rectangle(mRGBA, Point(rect.x.toDouble(), rect.y.toDouble()), Point((rect.x + rect.width).toDouble(), (rect.y + rect.height).toDouble()), Scalar(255.0, 0.0, 0.0), 2)
//                val roiGray = mGrey!!.submat(rect.y - 5, rect.y + rect.height + 5, rect.x - 5, rect.x + rect.width + 5)
////                Imgproc.resize(roiGray, roiGray, Size(48.0, 48.0))
//                val bmp = Bitmap.createBitmap(
//                    roiGray.cols(),
//                    roiGray.rows(),
//                    Bitmap.Config.ARGB_8888
//                )
//
//                Utils.matToBitmap(roiGray, bmp)
//                val scaledBitmap = Bitmap.createScaledBitmap(bmp, 48, 48, false)
//                //convert bitmap to byte array*
//                val byteBuffer = ByteBuffer.allocateDirect(4*1*48*48*1)
//                byteBuffer.order(ByteOrder.nativeOrder())
//                val intValue = IntArray(48*48)
//                scaledBitmap.getPixels(intValue, 0, scaledBitmap.width, 0, 0, scaledBitmap.width,scaledBitmap.height)
//                var pixel = 0
//                for (i in 0 until 48) {
//                    for (j in 0 until 48) {
//                        val value: Int = intValue[pixel++]
//                        byteBuffer.putFloat((value and 0xFF) / 255.0f)
//                    }
//                }
//
//
//                val output = Array(1) { FloatArray(7) }
//                interpreter!!.run(byteBuffer, output)
//                val maxIndex = output[0].indices.maxBy { output[0][it] }!!
//                val emotionLabels = arrayOf("angry", "disgust", "fear", "happy", "sad", "surprise", "neutral")
//                val emotion = emotionLabels[maxIndex]
//                Imgproc.putText(mRGBA, "Mood: $emotion", Point(rect.x.toDouble(), (rect.y + rect.height + 40).toDouble()), Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, Scalar(255.0, 255.0, 0.0))
//
//            }
//        } catch (e: Exception) {
//            Log.e("ERRORRR: ", e.stackTraceToString())
//        }



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


    //LOGIC for AUTO RUN
    private val runnable = object : Runnable {
        override fun run() {
            //recognize face
            isRecognize = true
            Log.d("MyApp", "This code is executed every $autoTriggerInSecs seconds")
            handler.postDelayed(this, (autoTriggerInSecs*1000))
        }
    }

    private fun startAutoRun() {
        runnable.run()
    }

    private fun stopAutoRun() {
        handler.removeCallbacks(runnable)
    }

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
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
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

    override fun onInit(status: Int) {
        if(status== TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.US)
            if(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED ){
                Log.e("TTS","The Langause specified is not supported")
            }

        }else{
            Log.e("TTS","Initializing failed")
        }
    }
}
