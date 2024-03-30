package com.example.outfitoftheday

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.gson.GsonBuilder
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayOutputStream
import android.util.Base64
import android.widget.TextView

class AddOutfitFragment : Fragment() {
    private lateinit var imageView: ImageView
    private lateinit var takePictureLauncher: ActivityResultLauncher<Intent>
    private lateinit var tvLabels: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize the ActivityResultLauncher
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageBitmap = result.data?.extras?.get("data") as Bitmap?
                imageBitmap?.let {
                    imageView.setImageBitmap(it)
                    analyzeImage(imageBitmap)
                } ?: Toast.makeText(requireContext(), "Failed to capture image!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_add_outfit, container, false)
        imageView = view.findViewById(R.id.imageViewCaptured)
        val button: Button = view.findViewById(R.id.buttonCapture)
        tvLabels = view.findViewById(R.id.tvLabels)

        button.setOnClickListener {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            try {
                takePictureLauncher.launch(takePictureIntent)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Camera not available.", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun setupRetrofit(): VisionService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://vision.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()

        return retrofit.create(VisionService::class.java)
    }
    fun analyzeImage(imageBitmap: Bitmap) {
        val visionService = setupRetrofit()
        val byteArrayOutputStream = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
        val base64Image = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)

        val request = VisionModel.VisionRequest(
            requests = listOf(
                VisionModel.AnnotateImageRequest(
                    image = VisionModel.Image(content = base64Image),
                    features = listOf(VisionModel.Feature())
                )
            )
        )

        visionService.annotateImage(request).enqueue(object : retrofit2.Callback<VisionModel.VisionResponse> {
            override fun onResponse(call: retrofit2.Call<VisionModel.VisionResponse>, response: retrofit2.Response<VisionModel.VisionResponse>) {
                if (response.isSuccessful) {
                    val labels = response.body()?.responses?.firstOrNull()?.labelAnnotations
                    val descriptions = labels?.joinToString(separator = "\n") { it.description }
                    activity?.runOnUiThread {
                        tvLabels.text = descriptions ?: "No labels found"
                    }
                } else {
                    Toast.makeText(context, "API request failed with code: ${response.code()}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: retrofit2.Call<VisionModel.VisionResponse>, t: Throwable) {
                Toast.makeText(context, "API request failed: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }
}
