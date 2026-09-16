package com.candlemovetracker.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.candlemovetracker.app.R
import com.candlemovetracker.app.databinding.ItemCandleHistoryBinding
import com.candlemovetracker.app.model.CandleRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var items: List<CandleRecord> = emptyList()
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())

    fun submitList(newItems: List<CandleRecord>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCandleHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], items.size - position)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemCandleHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: CandleRecord, index: Int) {
            val dateStr = timeFormat.format(Date(record.timestamp))
            binding.tvHistoryTimestamp.text = "Candle #$index • $dateStr"
            binding.tvHistoryUp.text = "UP: ${record.upCount}"
            binding.tvHistoryDown.text = "DOWN: ${record.downCount}"

            val diff = record.getDifference()
            when (record.dominantDirection) {
                CandleRecord.Direction.UP -> {
                    binding.tvWinnerBadge.text = "UP (+$diff)"
                    binding.tvWinnerBadge.setBackgroundResource(R.drawable.bg_counter_up)
                    binding.tvWinnerBadge.setTextColor(Color.parseColor("#10B981"))
                }
                CandleRecord.Direction.DOWN -> {
                    binding.tvWinnerBadge.text = "DOWN (+$diff)"
                    binding.tvWinnerBadge.setBackgroundResource(R.drawable.bg_counter_down)
                    binding.tvWinnerBadge.setTextColor(Color.parseColor("#EF4444"))
                }
                CandleRecord.Direction.TIE -> {
                    binding.tvWinnerBadge.text = "TIE (0)"
                    binding.tvWinnerBadge.setBackgroundResource(R.drawable.bg_card)
                    binding.tvWinnerBadge.setTextColor(Color.parseColor("#94A3B8"))
                }
            }
        }
    }
}
