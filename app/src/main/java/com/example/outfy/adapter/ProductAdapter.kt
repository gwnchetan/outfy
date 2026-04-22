package com.example.outfy.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.outfy.R
import com.example.outfy.databinding.ItemProductBinding
import com.example.outfy.model.Product
import com.example.outfy.model.displayOriginalPrice
import com.example.outfy.model.hasRatings
import com.example.outfy.model.hasDiscount
import com.example.outfy.model.imageGallery
import com.example.outfy.model.isInStock
import java.text.NumberFormat
import java.util.Locale

class ProductAdapter(
    private val onClick: (Product) -> Unit
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(DiffCallback()) {

    // Move expensive NumberFormat out of bind() to avoid re-instantiating on every scroll
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ProductViewHolder(private val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(product: Product) {
            binding.tvProductName.text = product.name
            
            // Format price to Rupees
            binding.tvProductPrice.text = currencyFormat.format(product.price)

            // Strikethrough original price if discount exists
            if (product.hasDiscount()) {
                binding.tvProductPriceOld.visibility = View.VISIBLE
                binding.tvProductPriceOld.paintFlags =
                    binding.tvProductPriceOld.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvProductPriceOld.text = currencyFormat.format(product.displayOriginalPrice())
            } else {
                binding.tvProductPriceOld.visibility = View.GONE
            }

            if (product.hasRatings()) {
                val countText = binding.root.resources.getQuantityString(
                    R.plurals.reviews_count,
                    product.ratingCount,
                    product.ratingCount
                )
                binding.tvProductRating.visibility = View.VISIBLE
                binding.tvProductRating.text = binding.root.context.getString(
                    R.string.review_summary_with_count,
                    product.ratingAverage,
                    countText
                )
            } else {
                binding.tvProductRating.visibility = View.GONE
            }

            // Optimize image loading: disk caching and centerCrop
            val imageUrl = product.imageGallery().firstOrNull()
            if (imageUrl != null) {
                Glide.with(binding.root.context)
                    .load(imageUrl)
                    .placeholder(R.drawable.logo_elem)
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both original and resized images
                    .centerCrop()
                    .into(binding.ivProductImage)
            } else {
                binding.ivProductImage.setImageResource(R.drawable.logo_elem)
            }

            val inStock = product.isInStock()
            binding.ivProductImage.alpha = if (inStock) 1f else 0.55f
            binding.tvStockBadge.visibility = if (inStock) View.GONE else View.VISIBLE

            // Scale effect on touch
            binding.root.setOnClickListener { 
                it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    onClick(product)
                }.start()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(old: Product, new: Product) =
            old.productId == new.productId
        override fun areContentsTheSame(old: Product, new: Product) =
            old == new
    }
}
