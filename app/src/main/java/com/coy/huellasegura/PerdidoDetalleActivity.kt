package com.coy.huellasegura

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityPerdidoDetalleBinding
import com.coy.huellasegura.databinding.DialogTextoBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

class PerdidoDetalleActivity : BaseAccessibleActivity() {

    companion object {
        const val EXTRA_PERDIDO_ID = "extra_perdido_id"
    }

    private lateinit var binding: ActivityPerdidoDetalleBinding
    private lateinit var perdidoId: String
    private var perdido: DocumentSnapshot? = null
    private var alreadyReported = false
    private var docListener: ListenerRegistration? = null
    private var reportsListener: ListenerRegistration? = null
    private var userEmail = ""
    private var userName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerdidoDetalleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            (binding.btnBack.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin =
                bars.top + (20 * resources.displayMetrics.density).toInt()
            insets
        }

        perdidoId = intent.getStringExtra(EXTRA_PERDIDO_ID) ?: run { finish(); return }
        userEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase()
        userName = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnEncontre.enablePressScale()
        binding.btnEncontre.setOnClickListener { showFoundDialog() }

        docListener = Firebase.firestore.collection("perdidos").document(perdidoId)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists() || doc.getString("estado") != "publicado") {
                    finish()
                    return@addSnapshotListener
                }
                perdido = doc
                render(doc)
            }

        reportsListener = Firebase.firestore.collection("reportes")
            .whereEqualTo("perdidoId", perdidoId)
            .whereEqualTo("reportanteCorreo", userEmail)
            .addSnapshotListener { snapshot, _ ->
                alreadyReported = !snapshot?.documents.isNullOrEmpty()
                perdido?.let { render(it) }
            }
    }

    private fun render(doc: DocumentSnapshot) {
        binding.tvNombre.text = doc.getString("nombre").orEmpty()
        val especie = doc.getString("especie").orEmpty()
        binding.tvDesc.text = listOf(
            doc.getString("descripcion").orEmpty(),
            if (especie.isBlank()) "" else getString(R.string.species_card, especie),
        ).filter { it.isNotBlank() }.joinToString("\n\n")
        binding.ivFoto.setImageBitmap(PhotoUtil.decode(doc.getString("fotoBase64"), doc.id))

        val mine = userEmail.isNotBlank() && doc.getString("reportanteCorreo") == userEmail
        val aviso = when {
            mine -> R.string.perdidos_found_mine
            alreadyReported -> R.string.perdidos_found_already
            else -> null
        }
        binding.btnEncontre.visibility = if (aviso == null) View.VISIBLE else View.GONE
        binding.tvAviso.visibility = if (aviso == null) View.GONE else View.VISIBLE
        aviso?.let { binding.tvAviso.setText(it) }
    }

    private fun showFoundDialog() {
        val doc = perdido ?: return
        val form = DialogTextoBinding.inflate(layoutInflater)
        form.tvNote.visibility = View.VISIBLE
        form.tvNote.setText(R.string.perdidos_found_dialog_note)
        form.tilTexto.hint = getString(R.string.perdidos_found_hint)

        val dialog = styledDialog()
            .setTitle(R.string.perdidos_found_dialog_title)
            .setView(form.root)
            .setPositiveButton(R.string.perdidos_found_confirm, null)
            .setNegativeButton(R.string.btn_cancelar, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                sendFoundReport(doc, form.etTexto.text.toString().trim())
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun sendFoundReport(doc: DocumentSnapshot, donde: String) {
        if (userEmail.isBlank()) {
            Toast.makeText(this, R.string.chat_error_no_account, Toast.LENGTH_SHORT).show()
            return
        }
        val nombre = doc.getString("nombre").orEmpty()
        val referencia = buildString {
            append(getString(R.string.perdidos_report_reference, nombre))
            if (donde.isNotBlank()) append(" · ").append(donde)
        }
        val report = mapOf(
            "tipo" to getString(R.string.perdidos_report_type),
            "referencia" to referencia,
            "estado" to "pendiente",
            "perdidoId" to doc.id,
            "perdidoNombre" to nombre,
            "perdidoDuenoCorreo" to doc.getString("reportanteCorreo").orEmpty(),
            "reportanteCorreo" to userEmail,
            "reportanteNombre" to userName,
            "fechaCreacion" to FieldValue.serverTimestamp()
        )
        OfflineWrite.run(
            this,
            Firebase.firestore.collection("reportes").document().set(report),
            onDone = { queued ->
                Toast.makeText(this, if (queued) R.string.perdidos_found_queued else R.string.perdidos_found_sent, Toast.LENGTH_LONG).show()
            },
            onError = {
                Toast.makeText(this, R.string.perdidos_send_error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onDestroy() {
        docListener?.remove()
        reportsListener?.remove()
        super.onDestroy()
    }
}
