package com.example.photoserverxkotlin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
// Your IDE likely can auto-import these classes, but there are several
// different implementations so we list them here to disambiguate.
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.util.Size
import android.graphics.Matrix
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import android.os.Handler
import java.nio.ByteBuffer
import android.view.WindowManager
import android.widget.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap


// This is an arbitrary number we are using to keep track of the permission
// request. Where an app has multiple context for requesting permission,
// this can help differentiate the different contexts.
private const val REQUEST_CODE_PERMISSIONS = 10
lateinit var napis: String
private var checked = 0
// This is an array of all the permission specified in the manifest.
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
class MainActivity : AppCompatActivity(), LifecycleOwner {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Add this at the end of onCreate function
        setContentView(R.layout.activity_main)
        viewFinder = findViewById(R.id.view_finder)

        var lensFacing = CameraX.LensFacing.FRONT
        // Request camera permissions
        if (allPermissionsGranted()) {
            viewFinder.post { startCamera(lensFacing) }
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Every time the provided texture view changes, recompute layout
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }

        lum = findViewById(R.id.lum)
        napis = " "
        var lumStr: String
        val handler = Handler()
        val r = object : Runnable {
            override fun run() {
                // DO WORK
                if (checked == 0) {
                    lumStr = "Średnia jasność obrazu: $napis"
                    Log.d("CameraXApp", lumStr)
                    lum.text = lumStr
                    checked = 1
                }
                // Call function.
                handler.postDelayed(this, 10)
            }
        }
        r.run()

        checkCameraProperties()
        aparat = findViewById(R.id.aparat)
        if (lensFacing == CameraX.LensFacing.BACK) {
            aparat.text = "Tylni"
            if (backOK == 0)
                aparat.text = "Tylni - Zła jakość"
        }
        else {
            aparat.text = "Przedni"
            if (frontOK == 0)
                aparat.text = "Przedni - Zła jakość"
        }

        aparat.setOnCheckedChangeListener{ _, b: Boolean ->
            if (b){
                aparat.text = "Tylni"
                startCamera(CameraX.LensFacing.BACK)
                if (backOK == 0)
                    aparat.text = "Tylni - Zła jakość"
            }
            else {
                aparat.text = "Przedni"
                startCamera(CameraX.LensFacing.FRONT)
                if (frontOK == 0)
                    aparat.text = "Przedni - Zła jakość"
            }
        }




    }

    private fun checkCameraProperties(){
        var manager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {

            for (cameraId in manager.cameraIdList) {
                var chars = manager.getCameraCharacteristics(cameraId)
                // Do something with the characteristics
                var map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) as StreamConfigurationMap
                //var formats = map.outputFormats
                var max = 0
                var maxH = 0
                var maxW = 0
                var maxFormat = 0
                var maxSizeIdx = 0
                var format = ImageFormat.JPEG
                var sizes = map.getOutputSizes(format)
                var i = 0
                for (size in sizes) {
                    Log.i("Characteristics", "Resolution: " + size.width + "x" + size.height)
                    if (max < size.height * size.width) {
                        maxH = size.height
                        maxW = size.width
                        maxFormat = format
                        maxSizeIdx = i
                        max = size.height * size.width
                    }
                    i++
                }

                Log.i("Characteristics", "Format: $maxFormat")
                Log.i("Characteristics", "SizeIdx: $maxSizeIdx")
                Log.i("Characteristics", "MaxResolution: $maxW" + "x" + "$maxH")

                if ( chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK ) {
                    Log.i("Characteristics", "Lens Facing Back")
                    if (maxW * maxH < 4*1000000){
                        Log.i("Characteristics", "Too low resolution")
                        backOK = 0
                    }
                    else
                        backOK = 1

                }
                if ( chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT ) {
                    Log.i("Characteristics", "Lens Facing Front")
                    if (maxW * maxH < 4*1000000){
                        Log.i("Characteristics", "Too low resolution")
                        frontOK = 0
                    }
                    else
                        frontOK = 1
                }
            }

        }
        catch (e: CameraAccessException) {
            Log.e("Camera", "CameraAccessException")
            e.printStackTrace()
        }
    }

    // Add this after onCreate
    private var frontOK = 0
    private var backOK = 0
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var viewFinder: TextureView
    private lateinit var lum: TextView
    private  lateinit var aparat: Switch
    private fun startCamera(lensFacing: CameraX.LensFacing) {
        // TODO: Implement CameraX operations
        CameraX.unbindAll()
        //val lensFacing = CameraX.LensFacing.BACK // FRONT
        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetResolution(Size(694, 483))
            setLensFacing(lensFacing)
            setTargetRotation(windowManager.defaultDisplay.rotation)
        }.build()

        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener {

            // To update the SurfaceTexture, we have to remove it and re-add it
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)

            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        // Add this before CameraX.bindToLifecycle

        // Create configuration object for the image capture use case
        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .apply {
                // We don't set a resolution for image capture; instead, we
                // select a capture mode which will infer the appropriate
                // resolution based on aspect ration and requested mode
                setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                setLensFacing(lensFacing)
            }.build()
        // Build the image capture use case and attach button click listener
        val imageCapture = ImageCapture(imageCaptureConfig)
        findViewById<ImageButton>(R.id.capture_button).setOnClickListener {
            val file = File(externalMediaDirs.first(),
                "${System.currentTimeMillis()}.jpg")

            imageCapture.takePicture(file, executor,
                object : ImageCapture.OnImageSavedListener {
                    override fun onError(
                        imageCaptureError: ImageCapture.ImageCaptureError,
                        message: String,
                        exc: Throwable?
                    ) {
                        val msg = "Photo capture failed: $message"
                        Log.e("CameraXApp", msg, exc)
                        viewFinder.post {
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onImageSaved(file: File) {
                        val msg = "Photo capture succeeded: ${file.absolutePath}"
                        Log.d("CameraXApp", msg)
                        viewFinder.post {
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                })
        }

        // Add this before CameraX.bindToLifecycle

        // Setup image analysis pipeline that computes average pixel luminance
        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            // In our analysis, we care more about the latest image than
            // analyzing *every* image
            setLensFacing(lensFacing)
            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()

        // Build the image analysis use case and instantiate our analyzer
        val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(executor, LuminosityAnalyzer())
        }

        // Bind use cases to lifecycle
        // If Android Studio complains about "this" being not a LifecycleOwner
        // try rebuilding the project or updating the appcompat dependency to
        // version 1.1.0 or higher.
        CameraX.bindToLifecycle(
            this, preview, imageCapture, analyzerUseCase)
    }

    private fun updateTransform() {
        // TODO: Implement camera viewfinder transformations
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when(viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix)
    }

    private class LuminosityAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L

        /**
         * Helper extension function used to extract a byte array from an
         * image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy, rotationDegrees: Int) {
            val currentTimestamp = System.currentTimeMillis()
            // Calculate the average luma no more often than every second
            if (currentTimestamp - lastAnalyzedTimestamp >=
                //TimeUnit.SECONDS.toMillis(1)) {
                50){
                // Since format in ImageAnalysis is YUV, image.planes[0]
                // contains the Y (luminance) plane
                val buffer = image.planes[0].buffer
                // Extract image data from callback object
                val data = buffer.toByteArray()
                // Convert the data into an array of pixel values
                val pixels = data.map { it.toInt() and 0xFF }
                // Compute average luminance for the image
                val luma = pixels.average()
                // Log the new luma value
                Log.d("CameraXApp", "Average luminosity: $luma")
                napis = "%.2f".format(luma)
                checked = 0
                // Update timestamp of last analyzed frame
                lastAnalyzedTimestamp = currentTimestamp
            }
        }
    }



    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                viewFinder.post { startCamera(CameraX.LensFacing.FRONT) }
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
}