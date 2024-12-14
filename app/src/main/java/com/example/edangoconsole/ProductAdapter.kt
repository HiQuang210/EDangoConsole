package com.example.edangoconsole

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ProductAdapter(
    private val productList: List<Pair<Product, String>>,
    private val onSeeDetailClick: (Product, String) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgProduct: ImageView = itemView.findViewById(R.id.img_product)
        private val tvProductName: TextView = itemView.findViewById(R.id.tv_deal_product_name)
        private val tvPrice: TextView = itemView.findViewById(R.id.tv_price)
        private val tvDiscount: TextView = itemView.findViewById(R.id.tv_discount)
        private val btnSeeDetail: Button = itemView.findViewById(R.id.btn_see_product)

        fun bind(product: Product, documentId: String) {
            tvProductName.text = product.name
            tvPrice.text = "${product.price.toInt()} VND"
            tvDiscount.text = "Discounted: ${product.discountPercentage ?: "0"}"
            Glide.with(itemView.context).load(product.images.firstOrNull()).into(imgProduct)

            btnSeeDetail.setOnClickListener {
                onSeeDetailClick(product, documentId)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.product_rv_item, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val (product, documentId) = productList[position]
        holder.bind(product, documentId)
    }

    override fun getItemCount(): Int = productList.size
}
