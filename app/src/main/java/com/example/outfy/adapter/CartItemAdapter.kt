package com.example.outfy.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.outfy.R
import com.example.outfy.databinding.ItemCartBinding
import com.example.outfy.model.CartItem
import com.example.outfy.model.hasDiscount
import com.example.outfy.model.hasStockIssue
import com.example.outfy.model.lineTotal
import com.example.outfy.model.stockMessage
import java.text.NumberFormat
import java.util.Locale

class CartItemAdapter(
    private val onIncrease: (CartItem) -> Unit,
    private val onDecrease: (CartItem) -> Unit,
    private val onRemove: (CartItem) -> Unit,
    private val onOpen: (CartItem) -> Unit
) : ListAdapter<CartItem, CartItemAdapter.CartViewHolder>(DiffCallback()) {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CartViewHolder(private val binding: ItemCartBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItem) {
            binding.tvProductName.text = item.name
            binding.tvProductMeta.text = listOf(item.category, item.target, "Size ${item.size}")
                .filter { it.isNotBlank() }
                .joinToString(" • ")

            binding.tvProductPrice.text = currencyFormat.format(item.lineTotal())
            binding.tvUnitPrice.text = binding.root.context.getString(
                R.string.cart_unit_price,
                currencyFormat.format(item.unitPrice)
            )
            binding.tvQuantity.text = item.quantity.toString()

            if (item.hasDiscount()) {
                binding.tvOriginalPrice.isVisible = true
                binding.tvOriginalPrice.paintFlags =
                    binding.tvOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvOriginalPrice.text = currencyFormat.format(item.originalPrice)
            } else {
                binding.tvOriginalPrice.isVisible = false
            }

            binding.tvStockNote.text = item.stockMessage()
            binding.tvStockNote.setTextColor(
                binding.root.context.getColor(
                    if (item.hasStockIssue()) R.color.error_red else R.color.gray_text
                )
            )

            binding.btnIncrease.isEnabled = !item.hasStockIssue() && item.quantity < item.maxQuantity
            binding.btnDecrease.isEnabled = item.quantity > 1

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

            binding.root.setOnClickListener { onOpen(item) }
            binding.btnIncrease.setOnClickListener { onIncrease(item) }
            binding.btnDecrease.setOnClickListener { onDecrease(item) }
            binding.btnRemove.setOnClickListener { onRemove(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CartItem>() {
        override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem): Boolean =
            oldItem.cartItemId == newItem.cartItemId

        override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem): Boolean =
            oldItem == newItem
    }
}
