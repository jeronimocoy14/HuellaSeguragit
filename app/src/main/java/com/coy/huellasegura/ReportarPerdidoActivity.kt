package com.coy.huellasegura

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.coy.huellasegura.databinding.ActivityReportarPerdidoBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportarPerdidoActivity : BaseAccessibleActivity() {

    companion object {
        const val EXTRA_PREFILL_NAME = "extra_prefill_name"
        const val EXTRA_PREFILL_DESC = "extra_prefill_desc"
    }

    private lateinit var binding: ActivityReportarPerdidoBinding
    private var photoBase64: String? = null
    private var pendingUri: Uri? = null
    private var pendingFile: File? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingUri
        if (ok && uri != null) loadPhoto(uri) else pendingFile?.delete()
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportarPerdidoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        intent.getStringExtra(EXTRA_PREFILL_NAME)?.let { binding.etNombre.setText(it) }
        intent.getStringExtra(EXTRA_PREFILL_DESC)?.let { binding.etDesc.setText(it) }

        binding.btnBack.setOnClickListener { finish() }
        binding.photoBox.setOnClickListener { chooseSource() }
        binding.btnEnviar.enablePressScale()
        binding.btnEnviar.setOnClickListener { submit() }
        binding.etNombre.doAfterTextChanged { binding.tilNombre.error = null }
        binding.etDesc.doAfterTextChanged { binding.tilDesc.error = null }
    }

    private fun chooseSource() {
        val options = arrayOf(getString(R.string.perdidos_photo_take), getString(R.string.perdidos_photo_gallery))
        styledDialog()
            .setTitle(R.string.perdidos_photo_prompt)
            .setItems(options) { _, which ->
                if (which == 0) launchCamera() else pickImage.launch("image/*")
            }
            .show()
    }

    private fun launchCamera() {
        if (packageManager.resolveActivity(android.content.Intent(MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY) == null) {
            Toast.makeText(this, R.string.perdidos_photo_error, Toast.LENGTH_SHORT).show()
            return
        }
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "perdido_$stamp.jpg")
        pendingFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        pendingUri = uri
        takePicture.launch(uri)
    }

    private fun loadPhoto(uri: Uri) {
        binding.btnEnviar.isEnabled = false
        Thread {
            val encoded = PhotoUtil.encode(this, uri)
            runOnUiThread {
                binding.btnEnviar.isEnabled = true
                if (encoded == null) {
                    Toast.makeText(this, R.string.perdidos_photo_error, Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                photoBase64 = encoded
                binding.ivFoto.setImageBitmap(PhotoUtil.decode(encoded, "preview"))
                binding.ivFoto.visibility = View.VISIBLE
                binding.photoPrompt.visibility = View.GONE
                binding.tvFotoError.visibility = View.GONE
            }
        }.start()
    }

    private fun submit() {
        val nombre = binding.etNombre.text.toString().trim()
        val desc = binding.etDesc.text.toString().trim()
        val email = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase()

        binding.tilNombre.error = null
        binding.tilDesc.error = null
        var valid = true
        if (photoBase64 == null) {
            binding.tvFotoError.visibility = View.VISIBLE
            valid = false
        }
        if (nombre.isBlank()) {
            binding.tilNombre.error = getString(R.string.perdidos_error_name)
            valid = false
        }
        if (desc.isBlank()) {
            binding.tilDesc.error = getString(R.string.perdidos_error_desc)
            valid = false
        }
        if (!valid) return
        if (email.isBlank()) {
            Toast.makeText(this, R.string.chat_error_no_account, Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnEnviar.isEnabled = false
        val data = mapOf(
            "nombre" to nombre,
            "descripcion" to desc,
            "fotoBase64" to photoBase64!!,
            "estado" to "pendiente",
            "reportanteCorreo" to email,
            "reportanteNombre" to intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty(),
            "fechaCreacion" to FieldValue.serverTimestamp()
        )
        Firebase.firestore.collection("perdidos").add(data)
            .addOnSuccessListener {
                Toast.makeText(this, R.string.perdidos_sent, Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                binding.btnEnviar.isEnabled = true
                Toast.makeText(this, R.string.perdidos_send_error, Toast.LENGTH_SHORT).show()
            }
    }
}
