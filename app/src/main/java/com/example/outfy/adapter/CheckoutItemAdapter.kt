package com.example.outfy.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.outfy.R
import com.example.outfy.databinding.ItemCheckoutProductBinding
import com.example.outfy.model.CartItem
import com.example.outfy.model.lineTotal
import java.text.NumberFormat
import java.util.Locale

class CheckoutItemAdapter :
    ListAdapter<CartItem, CheckoutItemAdapter.CheckoutItemViewHolder>(DiffCallback()) {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CheckoutItemViewHolder {
        val binding = ItemCheckoutProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CheckoutItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CheckoutItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CheckoutItemViewHolder(private val binding: ItemCheckoutProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItem) {
            binding.tvProductName.text = item.name
            binding.tvProductMeta.text = binding.root.context.getString(
                R.string.checkout_item_meta,
                item.category.ifBlank { "Product" },
                item.size,
                item.quantity
            )
            binding.tvProductPrice.text = currencyFormat.format(item.lineTotal())

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
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CartItem>() {
        override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem): Boolean =
            oldItem.cartItemId == newItem.cartItemId

        override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem): Boolean =
            oldItem == newItem
    }
}
