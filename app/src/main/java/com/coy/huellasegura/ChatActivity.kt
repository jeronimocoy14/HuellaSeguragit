package com.coy.huellasegura

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.coy.huellasegura.databinding.ActivityChatBinding
import com.coy.huellasegura.databinding.ItemChatDayBinding
import com.coy.huellasegura.databinding.ItemChatMessageBinding
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private sealed class ChatRow {
    data class Day(val key: String, val label: String) : ChatRow()
    data class Msg(
        val id: String,
        val texto: String,
        val mine: Boolean,
        val hora: String,
        val lat: Double? = null,
        val lon: Double? = null,
        val direccion: String = ""
    ) : ChatRow()
}

class ChatActivity : BaseAccessibleActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_ROLE = "extra_role"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_MY_NAME = "extra_my_name"
        const val ROLE_CIUDADANO = "ciudadano"
        const val ROLE_FUNCIONARIO = "funcionario"
    }

    private lateinit var binding: ActivityChatBinding
    private lateinit var chatId: String
    private lateinit var role: String
    private val chatRef by lazy { Firebase.firestore.collection("chats").document(chatId) }
    private val adapter = ChatAdapter()
    private var messagesListener: ListenerRegistration? = null
    private var chatListener: ListenerRegistration? = null
    private var firstRender = true

    private val isCitizen get() = role == ROLE_CIUDADANO

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) confirmShareLocation()
            else Toast.makeText(this, R.string.chat_location_no_permission, Toast.LENGTH_LONG).show()
        }
    private val unreadField get() = if (isCitizen) "noLeidoCiudadano" else "noLeidoFuncionario"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty().trim().lowercase()
        role = intent.getStringExtra(EXTRA_ROLE) ?: ROLE_CIUDADANO
        if (chatId.isBlank()) {
            Toast.makeText(this, R.string.chat_error_no_account, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        val peerName = intent.getStringExtra(EXTRA_PEER_NAME)
        binding.tvTitle.text = if (isCitizen) getString(R.string.chat_citizen_title) else peerName ?: chatId
        binding.tvSubtitle.text = if (isCitizen) getString(R.string.chat_citizen_subtitle) else chatId
        binding.tvEmpty.text = getString(if (isCitizen) R.string.chat_empty_citizen else R.string.chat_empty_funcionario)

        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvMessages.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSend.enablePressScale(0.92f)
        binding.btnSend.setOnClickListener { send() }
        binding.btnLocation.setOnClickListener {
            if (LocationUtil.hasPermission(this)) confirmShareLocation()
            else requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = updateSendState()
        })
        updateSendState()

        listenMessages()
        listenChatDoc()
    }

    private fun updateSendState() {
        val enabled = binding.etMessage.text.isNotBlank()
        binding.btnSend.isEnabled = enabled
        binding.btnSend.animate().alpha(if (enabled) 1f else 0.4f).setDuration(120).start()
    }

    private fun listenMessages() {
        messagesListener = chatRef.collection("mensajes")
            .orderBy("fechaCreacion", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                renderMessages(snapshot?.documents.orEmpty())
            }
    }

    private fun listenChatDoc() {
        chatListener = chatRef.addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists() && doc.getBoolean(unreadField) == true) {
                chatRef.update(unreadField, false)
            }
        }
    }

    private fun renderMessages(docs: List<DocumentSnapshot>) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dayKeyFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val rows = mutableListOf<ChatRow>()
        var lastDay: String? = null

        docs.forEach { doc ->
            val date = doc.getTimestamp("fechaCreacion", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()
                ?: Date()
            val dayKey = dayKeyFormat.format(date)
            if (dayKey != lastDay) {
                rows.add(ChatRow.Day(dayKey, dayLabel(date)))
                lastDay = dayKey
            }
            val isLocation = doc.getString("tipo") == "ubicacion"
            rows.add(
                ChatRow.Msg(
                    id = doc.id,
                    texto = doc.getString("texto").orEmpty(),
                    mine = doc.getString("remitente") == role,
                    hora = timeFormat.format(date),
                    lat = if (isLocation) doc.getDouble("lat") else null,
                    lon = if (isLocation) doc.getDouble("lon") else null,
                    direccion = if (isLocation) doc.getString("direccion").orEmpty() else ""
                )
            )
        }

        binding.tvEmpty.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
        val wasFirst = firstRender
        firstRender = false
        adapter.submitList(rows) {
            if (rows.isNotEmpty()) {
                if (wasFirst) binding.rvMessages.scrollToPosition(rows.size - 1)
                else binding.rvMessages.smoothScrollToPosition(rows.size - 1)
            }
        }
    }

    private fun dayLabel(date: Date): String {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        val sameDay = { a: Calendar, b: Calendar ->
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        }
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(today, target) -> getString(R.string.chat_day_today)
            sameDay(yesterday, target) -> getString(R.string.chat_day_yesterday)
            else -> SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("es-ES")).format(date)
        }
    }

    private fun send() {
        val texto = binding.etMessage.text.toString().trim()
        if (texto.isEmpty()) return
        binding.etMessage.setText("")
        sendMessage(texto)
    }

    /** Pide confirmación: la ubicación es un dato sensible. */
    private fun confirmShareLocation() {
        styledDialog()
            .setTitle(R.string.chat_location_confirm_title)
            .setMessage(R.string.chat_location_confirm_message)
            .setNegativeButton(R.string.btn_cancelar, null)
            .setPositiveButton(R.string.chat_location_confirm_send) { _, _ -> shareLocation() }
            .show()
    }

    private fun shareLocation() {
        binding.btnLocation.isEnabled = false
        Toast.makeText(this, R.string.chat_location_getting, Toast.LENGTH_SHORT).show()
        LocationUtil.getCurrent(this) { location ->
            if (location == null) {
                binding.btnLocation.isEnabled = true
                Toast.makeText(this, R.string.chat_location_error, Toast.LENGTH_LONG).show()
                return@getCurrent
            }
            // La dirección es opcional: si no llega rápido se envían solo las coordenadas.
            Thread {
                val address = GeoUtil.reverseGeocode(location.latitude, location.longitude, 4000).orEmpty()
                runOnUiThread {
                    binding.btnLocation.isEnabled = true
                    sendMessage(
                        texto = getString(R.string.chat_location_title),
                        extra = mapOf(
                            "tipo" to "ubicacion",
                            "lat" to location.latitude,
                            "lon" to location.longitude,
                            "direccion" to address
                        )
                    )
                }
            }.start()
        }
    }

    private fun sendMessage(texto: String, extra: Map<String, Any> = emptyMap()) {
        val myEmail = intent.getStringExtra(ProfileActivity.EXTRA_USER_EMAIL).orEmpty()
        val chatData = mutableMapOf<String, Any>(
            "ultimoMensaje" to texto,
            "ultimoRemitente" to role,
            "ultimoMensajeFecha" to FieldValue.serverTimestamp()
        )
        if (isCitizen) {
            chatData["ciudadanoCorreo"] = chatId
            chatData["ciudadanoNombre"] = intent.getStringExtra(EXTRA_MY_NAME) ?: getString(R.string.chat_citizen_fallback_name)
            chatData["noLeidoFuncionario"] = true
        } else {
            chatData["ciudadanoCorreo"] = chatId
            intent.getStringExtra(EXTRA_PEER_NAME)?.takeIf { it.isNotBlank() }?.let { chatData["ciudadanoNombre"] = it }
            chatData["noLeidoCiudadano"] = true
        }

        val message = mutableMapOf<String, Any>(
            "texto" to texto,
            "remitente" to role,
            "remitenteCorreo" to myEmail,
            "fechaCreacion" to FieldValue.serverTimestamp()
        )
        message.putAll(extra)

        val batch = Firebase.firestore.batch()
        batch.set(chatRef, chatData, SetOptions.merge())
        batch.set(chatRef.collection("mensajes").document(), message)
        batch.commit().addOnFailureListener {
            Toast.makeText(this, R.string.chat_error_send, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        messagesListener?.remove()
        chatListener?.remove()
        super.onDestroy()
    }

    private inner class ChatAdapter : ListAdapter<ChatRow, RecyclerView.ViewHolder>(Diff) {

        override fun getItemViewType(position: Int) = if (getItem(position) is ChatRow.Day) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) DayHolder(ItemChatDayBinding.inflate(inflater, parent, false))
            else MsgHolder(ItemChatMessageBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = getItem(position)) {
                is ChatRow.Day -> (holder as DayHolder).binding.tvDay.text = row.label
                is ChatRow.Msg -> (holder as MsgHolder).bind(row)
            }
        }
    }

    private inner class DayHolder(val binding: ItemChatDayBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class MsgHolder(val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: ChatRow.Msg) {
            val maxTextWidth = (resources.displayMetrics.widthPixels * 0.68f).toInt()
            binding.tvTexto.maxWidth = maxTextWidth
            binding.tvTexto.text = row.texto
            binding.tvHora.text = row.hora

            binding.bubble.setBackgroundResource(if (row.mine) R.drawable.bg_bubble_mine else R.drawable.bg_bubble_theirs)
            binding.tvTexto.setTextColor(
                ContextCompat.getColor(this@ChatActivity, if (row.mine) android.R.color.white else R.color.text_primary)
            )
            binding.tvHora.setTextColor(
                ContextCompat.getColor(this@ChatActivity, if (row.mine) R.color.splash_subtitle else R.color.text_secondary)
            )

            val textColor = ContextCompat.getColor(this@ChatActivity, if (row.mine) android.R.color.white else R.color.text_primary)
            val hasLocation = row.lat != null && row.lon != null
            binding.tvDireccion.visibility = if (hasLocation) View.VISIBLE else View.GONE
            binding.tvMapa.visibility = if (hasLocation) View.VISIBLE else View.GONE
            if (hasLocation) {
                val lat = row.lat!!
                val lon = row.lon!!
                binding.tvDireccion.maxWidth = maxTextWidth
                binding.tvDireccion.text = row.direccion.ifBlank { GeoUtil.formatCoordinates(lat, lon) }
                binding.tvDireccion.setTextColor(textColor)
                binding.tvMapa.setTextColor(textColor)
                binding.tvMapa.paintFlags = binding.tvMapa.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                binding.bubble.setOnClickListener {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(GeoUtil.mapsUrl(lat, lon))))
                    }
                }
            } else {
                binding.bubble.setOnClickListener(null)
                binding.bubble.isClickable = false
            }

            val params = binding.bubble.layoutParams as LinearLayout.LayoutParams
            params.gravity = if (row.mine) android.view.Gravity.END else android.view.Gravity.START
            binding.bubble.layoutParams = params
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatRow>() {
        override fun areItemsTheSame(oldItem: ChatRow, newItem: ChatRow) = when {
            oldItem is ChatRow.Day && newItem is ChatRow.Day -> oldItem.key == newItem.key
            oldItem is ChatRow.Msg && newItem is ChatRow.Msg -> oldItem.id == newItem.id
            else -> false
        }

        override fun areContentsTheSame(oldItem: ChatRow, newItem: ChatRow) = oldItem == newItem
    }
}
