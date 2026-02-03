package com.simplstudios.simplstream.presentation.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.simplstudios.simplstream.R
import com.simplstudios.simplstream.data.remote.dto.VideoStream

/**
 * Adapter for displaying video streams in the native player sidebar
 */
class StreamAdapter(
    private val onStreamSelected: (VideoStream) -> Unit
) : ListAdapter<StreamAdapter.StreamItem, StreamAdapter.StreamViewHolder>(StreamDiffCallback()) {

    data class StreamItem(
        val stream: VideoStream,
        val isCurrentStream: Boolean
    )

    private var focusedPosition: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StreamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stream, parent, false)
        return StreamViewHolder(view)
    }

    override fun onBindViewHolder(holder: StreamViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setFocusedPosition(position: Int) {
        focusedPosition = position
    }

    inner class StreamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val streamTitle: TextView = itemView.findViewById(R.id.stream_title)
        private val streamQuality: TextView = itemView.findViewById(R.id.stream_quality)
        private val streamProvider: TextView = itemView.findViewById(R.id.stream_provider)
        private val currentIndicator: View = itemView.findViewById(R.id.current_indicator)
        private val qualityBadge: TextView = itemView.findViewById(R.id.quality_badge)

        fun bind(item: StreamItem) {
            val stream = item.stream
            
            // Set title (provider name + quality)
            streamTitle.text = stream.getDisplayName()
            
            // Set quality badge with color coding
            val quality = stream.quality
            qualityBadge.text = when {
                quality.contains("2160") || quality.contains("4k", ignoreCase = true) -> "4K"
                quality.contains("1080") -> "FHD"
                quality.contains("720") -> "HD"
                quality.contains("480") -> "SD"
                else -> quality.take(5)
            }
            
            // Color code by quality
            val badgeColor = when {
                quality.contains("2160") || quality.contains("4k", ignoreCase = true) -> 
                    ContextCompat.getColor(itemView.context, R.color.quality_4k)
                quality.contains("1080") -> 
                    ContextCompat.getColor(itemView.context, R.color.quality_1080p)
                quality.contains("720") -> 
                    ContextCompat.getColor(itemView.context, R.color.quality_720p)
                else -> 
                    ContextCompat.getColor(itemView.context, R.color.text_secondary)
            }
            qualityBadge.setTextColor(badgeColor)
            
            // Provider name
            streamProvider.text = stream.provider.uppercase()
            
            // Stream type indicator (HLS or MP4)
            streamQuality.text = if (stream.isHls) "HLS Stream" else "Direct Link"
            
            // Show current stream indicator
            currentIndicator.isVisible = item.isCurrentStream
            
            // Highlight current stream
            if (item.isCurrentStream) {
                itemView.setBackgroundResource(R.drawable.bg_stream_item_selected)
            } else {
                itemView.setBackgroundResource(R.drawable.bg_stream_item)
            }
            
            // Click listener
            itemView.setOnClickListener {
                onStreamSelected(stream)
            }
            
            // D-pad focus handling
            itemView.setOnFocusChangeListener { _, hasFocus ->
                itemView.isSelected = hasFocus
                if (hasFocus) {
                    focusedPosition = adapterPosition
                }
            }
            
            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = true
        }
    }

    private class StreamDiffCallback : DiffUtil.ItemCallback<StreamItem>() {
        override fun areItemsTheSame(oldItem: StreamItem, newItem: StreamItem): Boolean {
            return oldItem.stream.id == newItem.stream.id
        }

        override fun areContentsTheSame(oldItem: StreamItem, newItem: StreamItem): Boolean {
            return oldItem == newItem
        }
    }
}
