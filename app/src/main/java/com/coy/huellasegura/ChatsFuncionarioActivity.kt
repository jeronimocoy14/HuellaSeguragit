package com.coy.huellasegura

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.coy.huellasegura.databinding.ActivityChatsFuncionarioBinding
import com.coy.huellasegura.databinding.ItemChatConversationBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore

class ChatsFuncionarioActivity : BaseAccessibleActivity() {

    private lateinit var binding: ActivityChatsFuncionarioBinding
    private var listener: ListenerRegistration? = null
    private var animated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatsFuncionarioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.listContainer.showStatusMessage(getString(R.string.list_loading))
        listener = Firebase.firestore.collection("chats")
            .orderBy("ultimoMensajeFecha", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null && snapshot == null) {
                    binding.listContainer.showStatusMessage(getString(R.string.list_error))
                    return@addSnapshotListener
                }
                render(snapshot?.documents.orEmpty())
            }
    }

    private fun render(docs: List<DocumentSnapshot>) {
        binding.listContainer.removeAllViews()
        if (docs.isEmpty()) {
            binding.listContainer.addView(TextView(this).apply {
                text = getString(R.string.chat_list_empty)
                setTextColor(ContextCompat.getColor(this@ChatsFuncionarioActivity, R.color.text_secondary))
                textSize = 14f
                setPadding(0, (24 * resources.displayMetrics.density).toInt(), 0, 0)
            })
            return
        }

        val cards = docs.map { addConversation(it) }
        if (!animated) {
            animated = true
            val distance = 8 * resources.displayMetrics.density
            cards.forEachIndexed { i, card -> card.fadeSlideIn(i, distance) }
        }
    }

    private fun addConversation(doc: DocumentSnapshot): View {
        val item = ItemChatConversationBinding.inflate(layoutInflater, binding.listContainer, false)
        val correo = doc.getString("ciudadanoCorreo").orEmpty().ifBlank { doc.id }
        val nombre = doc.getString("ciudadanoNombre").orEmpty().ifBlank { correo }
        val unread = doc.getBoolean("noLeidoFuncionario") == true
        val mine = doc.getString("ultimoRemitente") == ChatActivity.ROLE_FUNCIONARIO
        val ultimo = doc.getString("ultimoMensaje").orEmpty()

        item.tvInitial.text = nombre.trim().firstOrNull()?.uppercase() ?: "?"
        item.tvName.text = nombre
        item.tvPreview.text = if (mine) getString(R.string.chat_preview_you, ultimo) else ultimo
        item.tvTime.text = doc.getTimestamp("ultimoMensajeFecha")?.let { relativeTime(it.toDate().time) }.orEmpty()

        if (unread) {
            item.dotUnread.visibility = View.VISIBLE
            item.tvName.setTypeface(item.tvName.typeface, android.graphics.Typeface.BOLD)
            item.tvPreview.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }

        item.root.enablePressScale()
        item.root.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, doc.id)
                putExtra(ChatActivity.EXTRA_ROLE, ChatActivity.ROLE_FUNCIONARIO)
                putExtra(ChatActivity.EXTRA_PEER_NAME, nombre)
                putExtra(ProfileActivity.EXTRA_USER_EMAIL, intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL))
            })
        }
        binding.listContainer.addView(item.root)
        return item.root
    }

    private fun relativeTime(epochMillis: Long): String {
        val minutes = (System.currentTimeMillis() - epochMillis) / 60000
        return when {
            minutes < 1 -> getString(R.string.time_just_now)
            minutes < 60 -> getString(R.string.time_minutes_ago, minutes.toInt())
            minutes < 60 * 24 -> getString(R.string.time_hours_ago, (minutes / 60).toInt())
            else -> getString(R.string.time_days_ago, (minutes / (60 * 24)).toInt())
        }
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}
