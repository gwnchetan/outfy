package com.example.outfy.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.outfy.R
import com.example.outfy.databinding.ItemOrderLineBinding
import com.example.outfy.model.OrderItem
import java.text.NumberFormat
import java.util.Locale

class OrderLineItemAdapter(
    private val onOpenProduct: (OrderItem) -> Unit,
    private val onReviewProduct: (OrderItem) -> Unit
) :
    ListAdapter<OrderItem, OrderLineItemAdapter.OrderLineViewHolder>(DiffCallback()) {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
    private var showReviewActions = false
    private var reviewedProductIds: Set<String> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderLineViewHolder {
        val binding = ItemOrderLineBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderLineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderLineViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderLineViewHolder(private val binding: ItemOrderLineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OrderItem) {
            binding.tvProductName.text = item.name
            binding.tvProductMeta.text = binding.root.context.getString(
                R.string.order_line_meta,
                item.size,
                item.quantity
            )
            binding.tvPrice.text = currencyFormat.format(item.lineTotal)

            if (item.imageUrl.isNotBlank()) {
                Glide.with(binding.root.context)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.logo_elem)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(binding.ivProductImage)
            } else {
                binding.ivProductImage.setImageResource(R.drawable.logo_elem)
            }

            binding.btnViewProduct.setOnClickListener { onOpenProduct(item) }
            binding.root.setOnClickListener { onOpenProduct(item) }

            val hasReview = item.productId in reviewedProductIds
            binding.btnReviewProduct.visibility =
                if (showReviewActions && item.productId.isNotBlank()) android.view.View.VISIBLE
                else android.view.View.GONE
            binding.btnReviewProduct.text = binding.root.context.getString(
                if (hasReview) R.string.edit_review else R.string.write_review
            )
            binding.btnReviewProduct.setOnClickListener { onReviewProduct(item) }
        }
    }

    fun submitReviewState(showReviewActions: Boolean, reviewedProductIds: Set<String>) {
        this.showReviewActions = showReviewActions
        this.reviewedProductIds = reviewedProductIds
        notifyDataSetChanged()
    }

    class DiffCallback : DiffUtil.ItemCallback<OrderItem>() {
        override fun areItemsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean =
            oldItem.productId == newItem.productId && oldItem.size == newItem.size

        override fun areContentsTheSame(oldItem: OrderItem, newItem: OrderItem): Boolean =
            oldItem == newItem
    }
}
