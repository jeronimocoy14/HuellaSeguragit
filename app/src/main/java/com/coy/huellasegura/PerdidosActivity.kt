package com.coy.huellasegura

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.coy.huellasegura.databinding.ActivityPerdidosBinding
import com.coy.huellasegura.databinding.ItemPerdidoCardBinding
import com.coy.huellasegura.databinding.ItemPerdidoSolicitudBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore

class PerdidosActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityPerdidosBinding
    private val catalogAdapter = CatalogAdapter()
    private var catalogListener: ListenerRegistration? = null
    private var mineListener: ListenerRegistration? = null
    private var userEmail = ""
    private var userName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPerdidosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        userEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty().trim().lowercase()
        userName = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()

        binding.btnBack.setOnClickListener { finish() }
        binding.segFound.setOnClickListener { selectSegment(found = true) }
        binding.segLost.setOnClickListener { selectSegment(found = false) }
        binding.btnReportar.enablePressScale()
        binding.btnReportar.setOnClickListener {
            startActivity(Intent(this, ReportarPerdidoActivity::class.java).apply {
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
                putExtra(LoginActivity.EXTRA_USER_NAME, userName)
            })
        }

        binding.rvCatalogo.layoutManager = GridLayoutManager(this, 2)
        binding.rvCatalogo.adapter = catalogAdapter

        listenCatalog()
        listenMine()
    }

    private fun selectSegment(found: Boolean) {
        binding.panelFound.visibility = if (found) View.VISIBLE else View.GONE
        binding.panelLost.visibility = if (found) View.GONE else View.VISIBLE
        styleSegment(binding.segFound, found)
        styleSegment(binding.segLost, !found)
    }

    private fun styleSegment(view: TextView, selected: Boolean) {
        view.setBackgroundResource(if (selected) R.drawable.bg_seg_selected else 0)
        view.setTextColor(ContextCompat.getColor(this, if (selected) android.R.color.white else R.color.text_secondary))
    }

    private fun listenCatalog() {
        catalogListener = Firebase.firestore.collection("perdidos")
            .whereEqualTo("estado", "publicado")
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents.orEmpty().sortedByDescending { it.createdAtMillis() }
                binding.tvCatalogEmpty.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
                catalogAdapter.submitList(docs)
            }
    }

    private fun listenMine() {
        if (userEmail.isBlank()) {
            renderMine(emptyList())
            return
        }
        mineListener = Firebase.firestore.collection("perdidos")
            .whereEqualTo("reportanteCorreo", userEmail)
            .addSnapshotListener { snapshot, _ ->
                renderMine(snapshot?.documents.orEmpty().sortedByDescending { it.createdAtMillis() })
            }
    }

    private fun renderMine(docs: List<DocumentSnapshot>) {
        binding.solicitudesContainer.removeAllViews()
        if (docs.isEmpty()) {
            binding.solicitudesContainer.addView(TextView(this).apply {
                text = getString(R.string.perdidos_mine_empty)
                setTextColor(ContextCompat.getColor(this@PerdidosActivity, R.color.text_secondary))
                textSize = 14f
                setPadding(0, dp(16f), 0, 0)
            })
            return
        }
        docs.forEach { addSolicitud(it) }
    }

    private fun addSolicitud(doc: DocumentSnapshot) {
        val item = ItemPerdidoSolicitudBinding.inflate(layoutInflater, binding.solicitudesContainer, false)
        val estado = doc.getString("estado").orEmpty()

        item.tvNombre.text = doc.getString("nombre").orEmpty()
        item.tvDesc.text = doc.getString("descripcion").orEmpty()
        item.ivFoto.setImageBitmap(PhotoUtil.decode(doc.getString("fotoBase64"), doc.id))

        val (label, bg, color) = when (estado) {
            "publicado" -> Triple(R.string.perdidos_status_published, R.drawable.bg_tag_pill, R.color.brand_dark)
            "rechazado" -> Triple(R.string.perdidos_status_rejected, R.drawable.bg_pill_danger, R.color.danger_red_dark_text)
            else -> Triple(R.string.perdidos_status_review, R.drawable.bg_pill_pending, R.color.offline_notice_text)
        }
        item.tvPill.setText(label)
        item.tvPill.setBackgroundResource(bg)
        item.tvPill.setTextColor(ContextCompat.getColor(this, color))

        val nota = when {
            estado == "rechazado" -> getString(R.string.perdidos_note_reason, doc.getString("motivoRechazo").orEmpty())
            doc.getBoolean("editadoPorFuncionario") == true -> getString(R.string.perdidos_note_edited)
            else -> null
        }
        if (nota != null) {
            item.tvNota.visibility = View.VISIBLE
            item.tvNota.text = nota
            if (estado != "rechazado") item.tvNota.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
        item.btnYaLoEncontre.setOnClickListener { confirmAlreadyFound(doc) }
        binding.solicitudesContainer.addView(item.root)
    }

    private fun confirmAlreadyFound(doc: DocumentSnapshot) {
        styledDialog()
            .setTitle(getString(R.string.perdidos_already_title, doc.getString("nombre").orEmpty()))
            .setMessage(R.string.perdidos_already_msg)
            .setPositiveButton(R.string.perdidos_already_confirm) { _, _ -> removeEverywhere(doc) }
            .setNegativeButton(R.string.btn_cancelar, null)
            .show()
    }

    private fun removeEverywhere(doc: DocumentSnapshot) {
        val db = Firebase.firestore
        db.collection("reportes")
            .whereEqualTo("perdidoId", doc.id)
            .whereEqualTo("perdidoDuenoCorreo", userEmail)
            .get()
            .addOnSuccessListener { avisos ->
                val batch = db.batch()
                avisos.documents.forEach { batch.delete(it.reference) }
                batch.delete(doc.reference)
                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.perdidos_already_done, Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, R.string.perdidos_send_error, Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, R.string.perdidos_send_error, Toast.LENGTH_SHORT).show()
            }
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        catalogListener?.remove()
        mineListener?.remove()
        super.onDestroy()
    }

    private inner class CatalogAdapter : ListAdapter<DocumentSnapshot, CatalogHolder>(Diff) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            CatalogHolder(ItemPerdidoCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: CatalogHolder, position: Int) = holder.bind(getItem(position))
    }

    private inner class CatalogHolder(private val item: ItemPerdidoCardBinding) : RecyclerView.ViewHolder(item.root) {
        fun bind(doc: DocumentSnapshot) {
            item.tvNombre.text = doc.getString("nombre").orEmpty()
            item.tvDesc.text = doc.getString("descripcion").orEmpty()
            item.ivFoto.setImageBitmap(PhotoUtil.decode(doc.getString("fotoBase64"), doc.id))
            item.root.enablePressScale()
            item.root.setOnClickListener {
                startActivity(Intent(this@PerdidosActivity, PerdidoDetalleActivity::class.java).apply {
                    putExtra(PerdidoDetalleActivity.EXTRA_PERDIDO_ID, doc.id)
                    putExtra(ProfileActivity.EXTRA_USER_EMAIL, userEmail)
                    putExtra(LoginActivity.EXTRA_USER_NAME, userName)
                })
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<DocumentSnapshot>() {
        override fun areItemsTheSame(oldItem: DocumentSnapshot, newItem: DocumentSnapshot) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: DocumentSnapshot, newItem: DocumentSnapshot) = oldItem.data == newItem.data
    }
}
