package com.coy.huellasegura

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityReportarPeligroBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportarPeligroActivity : BaseAccessibleActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityReportarPeligroBinding
    private var pendingPhotoFile: File? = null
    private var pendingPhotoUri: Uri? = null
    private var photoPath: String? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && pendingPhotoUri != null) {
            PhotoUtil.loadPreview(this, pendingPhotoUri!!) { bitmap -> bitmap?.let { binding.photoPreview.setImageBitmap(it) } }
            binding.photoPreview.visibility = android.view.View.VISIBLE
            binding.photoPrompt.visibility = android.view.View.GONE
            photoPath = pendingPhotoFile?.absolutePath
            val path = photoPath
            SpeciesDetector.detectInto(this, pendingPhotoUri!!, binding.tvSpecies) { SpeciesDetector.remember(path, it) }
            Toast.makeText(this, R.string.report_photo_saved, Toast.LENGTH_SHORT).show()
        } else {
            pendingPhotoFile?.delete()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportarPeligroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnListen.visibility = if (isTtsEnabled()) android.view.View.VISIBLE else android.view.View.GONE
        if (isTtsEnabled()) tts = TextToSpeech(this, this)
        binding.btnListen.setOnClickListener { readScreenAloud() }

        binding.btnBack.setOnClickListener { finish() }

        val launchCamera = { launchCamera() }
        binding.photoPrompt.setOnClickListener { launchCamera() }
        binding.photoPreview.setOnClickListener { launchCamera() }

        binding.btnContinuar.setOnClickListener {
            val selectedChipId = binding.chipGroupType.checkedChipId
            val selectedType = findViewById<com.google.android.material.chip.Chip>(selectedChipId)?.text?.toString().orEmpty()
            startActivity(Intent(this, ReportarPeligroUbicacionActivity::class.java).apply {
                putExtra(ReportarPeligroUbicacionActivity.EXTRA_REPORT_TYPE, selectedType)
                putExtra(ReportarPeligroUbicacionActivity.EXTRA_PHOTO_PATH, photoPath)
                putExtra(LoginActivity.EXTRA_USER_NAME, intent.getStringExtra(LoginActivity.EXTRA_USER_NAME))
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
            })
        }
    }

    private fun launchCamera() {
        if (packageManager.resolveActivity(android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY) == null) {
            Toast.makeText(this, R.string.report_photo_error, Toast.LENGTH_SHORT).show()
            return
        }
        val reportsDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "reports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(reportsDir, "reporte_$timestamp.jpg")
        pendingPhotoFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingPhotoUri = uri
        takePicture.launch(uri)
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("es", "ES")
    }

    private fun readScreenAloud() {
        if (!ttsReady || !isTtsEnabled()) return
        val text = "${getString(R.string.dashboard_report_title)}. ${getString(R.string.report_question)}. " +
            "${getString(R.string.report_photo_label)}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
