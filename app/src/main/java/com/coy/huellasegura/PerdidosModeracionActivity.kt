package com.coy.huellasegura

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityPerdidosModeracionBinding
import com.coy.huellasegura.databinding.DialogTextoBinding
import com.coy.huellasegura.databinding.ItemPerdidoModeracionBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

class PerdidosModeracionActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityPerdidosModeracionBinding
    private var listener: ListenerRegistration? = null
    private var funcionarioEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerdidosModeracionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        funcionarioEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty()
        binding.btnBack.setOnClickListener { finish() }

        binding.listContainer.showStatusMessage(getString(R.string.list_loading))
        listener = Firebase.firestore.collection("perdidos")
            .whereEqualTo("estado", "pendiente")
            .addSnapshotListener { snapshot, error ->
                if (error != null && snapshot == null) {
                    binding.listContainer.showStatusMessage(getString(R.string.list_error))
                    return@addSnapshotListener
                }
                render(snapshot?.documents.orEmpty().sortedBy { it.createdAtMillis() })
            }
    }

    private fun render(docs: List<DocumentSnapshot>) {
        binding.listContainer.removeAllViews()
        if (docs.isEmpty()) {
            binding.listContainer.addView(TextView(this).apply {
                text = getString(R.string.perdidos_mod_empty)
                setTextColor(ContextCompat.getColor(this@PerdidosModeracionActivity, R.color.text_secondary))
                textSize = 14f
                setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            return
        }
        docs.forEach { addCard(it) }
    }

    private fun addCard(doc: DocumentSnapshot) {
        val item = ItemPerdidoModeracionBinding.inflate(layoutInflater, binding.listContainer, false)
        val por = doc.getString("reportanteNombre").orEmpty().ifBlank { doc.getString("reportanteCorreo").orEmpty() }

        item.tvNombre.text = doc.getString("nombre").orEmpty()
        item.tvPor.text = getString(R.string.perdidos_mod_by, por)
        item.tvDesc.text = doc.getString("descripcion").orEmpty()
        item.ivFoto.setImageBitmap(PhotoUtil.decode(doc.getString("fotoBase64"), doc.id))

        val original = doc.getString("descripcionOriginal")
        if (doc.getBoolean("editadoPorFuncionario") == true && !original.isNullOrBlank()) {
            item.tvOriginal.visibility = View.VISIBLE
            item.tvOriginal.text = getString(R.string.perdidos_mod_original, original)
        }

        item.btnAceptar.setOnClickListener {
            doc.reference.update(
                mapOf(
                    "estado" to "publicado",
                    "revisadoPor" to funcionarioEmail,
                    "fechaPublicacion" to FieldValue.serverTimestamp()
                )
            )
        }
        item.btnEditar.setOnClickListener { showEditDialog(doc) }
        item.btnRechazar.setOnClickListener { showRejectDialog(doc) }
        binding.listContainer.addView(item.root)
    }

    private fun showEditDialog(doc: DocumentSnapshot) {
        val form = DialogTextoBinding.inflate(layoutInflater)
        form.tvNote.visibility = View.VISIBLE
        form.tvNote.setText(R.string.perdidos_mod_edit_note)
        form.tilTexto.hint = getString(R.string.perdidos_hint_desc)
        val current = doc.getString("descripcion").orEmpty()
        form.etTexto.setText(current)
        form.etTexto.setSelection(current.length)

        val dialog = styledDialog()
            .setTitle(R.string.perdidos_mod_edit_title)
            .setView(form.root)
            .setPositiveButton(R.string.btn_guardar, null)
            .setNegativeButton(R.string.btn_cancelar, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nueva = form.etTexto.text.toString().trim()
                if (nueva.isBlank()) {
                    form.tilTexto.error = getString(R.string.perdidos_mod_desc_error)
                    return@setOnClickListener
                }
                if (nueva != current) {
                    val updates = mutableMapOf<String, Any>(
                        "descripcion" to nueva,
                        "editadoPorFuncionario" to true,
                        "editadoPor" to funcionarioEmail
                    )
                    if (doc.getString("descripcionOriginal").isNullOrBlank()) updates["descripcionOriginal"] = current
                    doc.reference.update(updates)
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showRejectDialog(doc: DocumentSnapshot) {
        val form = DialogTextoBinding.inflate(layoutInflater)
        form.tvNote.visibility = View.VISIBLE
        form.tvNote.setText(R.string.perdidos_mod_reject_note)
        form.tilTexto.hint = getString(R.string.perdidos_mod_reject_hint)

        val dialog = styledDialog()
            .setTitle(R.string.perdidos_mod_reject_title)
            .setView(form.root)
            .setPositiveButton(R.string.perdidos_mod_reject, null)
            .setNegativeButton(R.string.btn_cancelar, null)
            .create()

        dialog.setOnShowListener {
            val confirm = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            confirm.setTextColor(ContextCompat.getColor(this, R.color.danger_red))
            confirm.setOnClickListener {
                val motivo = form.etTexto.text.toString().trim()
                if (motivo.isBlank()) {
                    form.tilTexto.error = getString(R.string.perdidos_mod_reject_error)
                    return@setOnClickListener
                }
                doc.reference.update(
                    mapOf(
                        "estado" to "rechazado",
                        "motivoRechazo" to motivo,
                        "revisadoPor" to funcionarioEmail
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}
