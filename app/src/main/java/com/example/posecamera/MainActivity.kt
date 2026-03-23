package com.example.posecamera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.posecamera.databinding.ActivityMainBinding

/**
 * MainActivity – entry point.
 * Lets the user pick a reference image from the gallery and then open the camera.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var referenceImageUri: Uri? = null

    // ---- permission launcher ----
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            openCamera()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- image picker launcher ----
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            referenceImageUri = uri
            binding.ivReferencePreview.setImageURI(uri)
            binding.tvHint.setText(R.string.open_camera)
            binding.btnOpenCamera.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivReferencePreview.setOnClickListener { pickImage() }
        binding.btnSelectImage.setOnClickListener { pickImage() }
        binding.btnOpenCamera.setOnClickListener { checkPermissionsAndOpenCamera() }
    }

    private fun pickImage() {
        pickImageLauncher.launch("image/*")
    }

    private fun checkPermissionsAndOpenCamera() {
        val required = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            required += Manifest.permission.READ_MEDIA_IMAGES
        } else {
            required += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            openCamera()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun openCamera() {
        val uri = referenceImageUri ?: return
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra(CameraActivity.EXTRA_REFERENCE_URI, uri.toString())
        }
        startActivity(intent)
    }
}
