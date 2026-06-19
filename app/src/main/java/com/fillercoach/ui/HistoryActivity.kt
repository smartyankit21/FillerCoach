package com.fillercoach.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fillercoach.data.AppDatabase
import com.fillercoach.data.Session
import com.fillercoach.databinding.ActivityHistoryBinding
import com.fillercoach.databinding.ItemSessionBinding
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getDatabase(this)
        val adapter = SessionAdapter { session ->
            val intent = Intent(this, PlaybackActivity::class.java)
            intent.putExtra("session_id", session.id)
            startActivity(intent)
        }

        binding.rvSessions.layoutManager = LinearLayoutManager(this)
        binding.rvSessions.adapter = adapter

        db.sessionDao().getAllSessions().observe(this) { sessions ->
            adapter.submitList(sessions)
            binding.tvEmpty.visibility = if (sessions.isEmpty())
                android.view.View.VISIBLE else android.view.View.GONE
        }

        binding.btnBack.setOnClickListener { finish() }
    }
}

class SessionAdapter(private val onClick: (Session) -> Unit) :
    RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    private var sessions: List<Session> = emptyList()

    fun submitList(list: List<Session>) {
        sessions = list
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        holder.binding.tvDate.text = sdf.format(Date(session.timestamp))
        holder.binding.tvFillers.text = "Fillers: ${session.fillerCount}"
        holder.binding.tvWpm.text = "${session.wpm} WPM"
        holder.binding.tvDuration.text = "${session.durationSeconds}s"

        val score = when {
            session.fillerCount == 0 -> "🏆"
            session.fillerCount <= 3 -> "✅"
            session.fillerCount <= 8 -> "👍"
            else -> "💪"
        }
        holder.binding.tvScore.text = score

        holder.itemView.setOnClickListener { onClick(session) }
    }

    override fun getItemCount() = sessions.size
}
