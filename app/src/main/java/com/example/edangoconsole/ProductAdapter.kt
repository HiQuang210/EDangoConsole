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
    private val productList: List<Product>,
    private val onSeeDetailClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgProduct: ImageView = itemView.findViewById(R.id.img_product)
        val tvProductName: TextView = itemView.findViewById(R.id.tv_deal_product_name)
        val tvPrice: TextView = itemView.findViewById(R.id.tv_price)
        val tvQuantity: TextView = itemView.findViewById(R.id.tv_quantity)
        val btnSeeDetail: Button = itemView.findViewById(R.id.btn_see_product)

        fun bind(product: Product) {
            tvProductName.text = product.name
            tvPrice.text = "${product.price.toInt()} VND"
            tvQuantity.text = "Quantity: ${product.quantity}"
            Glide.with(itemView.context).load(product.images[0]).into(imgProduct)
            btnSeeDetail.setOnClickListener {
                onSeeDetailClick(product)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.product_rv_item, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(productList[position])
    }

    override fun getItemCount(): Int = productList.size
}