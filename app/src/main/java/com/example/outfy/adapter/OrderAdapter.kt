package com.example.outfy.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.outfy.R
import com.example.outfy.databinding.ItemOrderBinding
import com.example.outfy.model.OrderRecord
import com.example.outfy.model.orderStatusLabel
import com.example.outfy.model.previewText
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class OrderAdapter(
    private val onOpen: (OrderRecord) -> Unit
) : ListAdapter<OrderRecord, OrderAdapter.OrderViewHolder>(DiffCallback()) {
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(private val binding: ItemOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: OrderRecord) {
            binding.tvOrderId.text = "#${order.orderId.takeLast(6).uppercase()}"
            binding.tvStatus.text = orderStatusLabel(order.status)
            binding.tvStatus.setTextColor(binding.root.context.getColor(statusTextColor(order.status)))
            binding.tvPlacedAt.text = order.createdAt?.toDate()?.let(dateFormat::format) ?: "Just now"
            binding.tvOrderItems.text = order.previewText()
            binding.tvOrderMeta.text = "${order.itemCount} items"
            binding.tvOrderTotal.text = currencyFormat.format(order.pricing.totalAmount)
            binding.tvOrderAction.text = actionLabel(order.status)
            binding.root.setOnClickListener { onOpen(order) }
        }

        private fun statusTextColor(status: String): Int = when (status) {
            "order_placed" -> R.color.order_status_placed
            "departure" -> R.color.order_status_departure
            "arrived_city" -> R.color.order_status_city
            "out_for_delivery" -> R.color.order_status_out
            "delivered" -> R.color.order_status_delivered
            "cancelled" -> R.color.order_status_cancelled
            else -> R.color.black
        }

        private fun actionLabel(status: String): String = when (status) {
            "delivered", "cancelled" -> binding.root.context.getString(R.string.order_again)
            else -> binding.root.context.getString(R.string.track_order_cta)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<OrderRecord>() {
        override fun areItemsTheSame(oldItem: OrderRecord, newItem: OrderRecord): Boolean =
            oldItem.orderId == newItem.orderId

        override fun areContentsTheSame(oldItem: OrderRecord, newItem: OrderRecord): Boolean =
            oldItem == newItem
    }
}
