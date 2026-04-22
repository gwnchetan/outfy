package com.example.outfy.adapter

import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.outfy.R
import com.bumptech.glide.Glide
import com.example.outfy.databinding.ItemSimilarProductBinding
import com.example.outfy.model.Product
import com.example.outfy.model.hasRatings
import com.example.outfy.model.imageGallery
import com.example.outfy.model.isInStock
import java.text.NumberFormat
import java.util.Locale

class SimilarProductAdapter(
    private val products: List<Product>,
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<SimilarProductAdapter.SimilarProductViewHolder>() {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimilarProductViewHolder {
        val binding = ItemSimilarProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SimilarProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SimilarProductViewHolder, position: Int) {
        val product = products[position]
        holder.binding.tvProductName.text = product.name
        holder.binding.tvProductPrice.text = currencyFormat.format(product.price)

        if (product.hasRatings()) {
            val countText = holder.binding.root.resources.getQuantityString(
                R.plurals.reviews_count,
                product.ratingCount,
                product.ratingCount
            )
            holder.binding.tvProductRating.visibility = View.VISIBLE
            holder.binding.tvProductRating.text = holder.binding.root.context.getString(
                R.string.review_summary_with_count,
                product.ratingAverage,
                countText
            )
        } else {
            holder.binding.tvProductRating.visibility = View.GONE
        }

        val imageUrl = product.imageGallery().firstOrNull()
        if (imageUrl != null) {
            Glide.with(holder.itemView.context)
                .load(imageUrl)
                .into(holder.binding.ivProductImage)
        }

        val inStock = product.isInStock()
        holder.binding.ivProductImage.alpha = if (inStock) 1f else 0.55f
        holder.binding.tvStockBadge.visibility =
            if (inStock) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener { onClick(product) }
    }

    override fun getItemCount(): Int = products.size

    class SimilarProductViewHolder(val binding: ItemSimilarProductBinding) :
        RecyclerView.ViewHolder(binding.root)
}
