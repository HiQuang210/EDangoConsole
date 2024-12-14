package com.example.edangoconsole

import com.google.firebase.Timestamp
import java.io.Serializable

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Float,
    val discountPercentage: Float? = null,
    val description: String? = null,
    val colors: List<Int>? = null,
    val sizes: List<String>? = null,
    val images: List<String>,
    val uploadedAt: Timestamp? = null
) : Serializable {
    constructor() : this("0", "", "", 0f, images = emptyList())
}
