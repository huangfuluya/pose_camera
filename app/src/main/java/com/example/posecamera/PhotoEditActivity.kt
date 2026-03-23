package com.example.posecamera

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.posecamera.databinding.ActivityPhotoEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * PhotoEditActivity – shows the captured photo and lets the user apply an automatic
 * style-transfer to make it visually similar to the reference image.
 */
class PhotoEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CAPTURED_URI = "extra_captured_uri"
        const val EXTRA_REFERENCE_URI = "extra_reference_uri"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private lateinit var binding: ActivityPhotoEditBinding

    private var capturedBitmap: Bitmap? = null
    private var referenceBitmap: Bitmap? = null
    private var styledBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadImages()
        setupControls()
    }

    // ---- Image loading ----

    private fun loadImages() {
        val capturedUriStr = intent.getStringExtra(EXTRA_CAPTURED_URI) ?: return
        val referenceUriStr = intent.getStringExtra(EXTRA_REFERENCE_URI) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val captured = decodeBitmap(Uri.parse(capturedUriStr))
            val reference = decodeBitmap(Uri.parse(referenceUriStr))

            withContext(Dispatchers.Main) {
                capturedBitmap = captured
                referenceBitmap = reference

                binding.ivCapturedPhoto.setImageBitmap(captured)
                binding.ivReferenceThumbnail.setImageBitmap(reference)
            }
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: IOException) {
            null
        }
    }

    // ---- Controls ----

    private fun setupControls() {
        binding.btnApplyStyle.setOnClickListener { applyStyle() }
        binding.btnSavePhoto.setOnClickListener { savePhoto() }

        // Blend seekbar – mix original and styled result
        binding.seekBarBlend.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val alpha = progress / 100f
                binding.ivEditedPhoto.alpha = alpha
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    // ---- Style transfer ----

    private fun applyStyle() {
        val src = capturedBitmap ?: run {
            Toast.makeText(this, "请先等待图片加载完成", Toast.LENGTH_SHORT).show()
            return
        }
        val ref = referenceBitmap ?: run {
            Toast.makeText(this, "参考图片未加载", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.btnApplyStyle.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val result = ImageStyleUtils.applyStyleTransfer(src, ref, strength = 1f)

            withContext(Dispatchers.Main) {
                styledBitmap = result
                binding.ivEditedPhoto.setImageBitmap(result)
                // Set blend to 100% to show the styled result
                binding.seekBarBlend.progress = 100
                binding.ivEditedPhoto.alpha = 1f
                binding.progressBar.visibility = android.view.View.GONE
                binding.btnApplyStyle.isEnabled = true
                Toast.makeText(this@PhotoEditActivity, R.string.style_applied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- Save ----

    private fun savePhoto() {
        // Save the currently visible composite (original blended with styled if applied)
        val base = capturedBitmap ?: run {
            Toast.makeText(this, R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val ref = referenceBitmap ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PhotoEditActivity, R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val toSave: Bitmap = if (styledBitmap != null && binding.seekBarBlend.progress > 0) {
                val blendAlpha = binding.seekBarBlend.progress / 100f
                ImageStyleUtils.applyStyleTransfer(base, ref, strength = blendAlpha)
            } else {
                base
            }

            val saved = saveBitmapToGallery(toSave)

            withContext(Dispatchers.Main) {
                if (saved) {
                    Toast.makeText(this@PhotoEditActivity, R.string.photo_saved, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@PhotoEditActivity, R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap): Boolean {
        val name = "PoseCamera_" +
                SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) +
                ".jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PoseCamera")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false

        return try {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            true
        } catch (e: IOException) {
            false
        }
    }
}
