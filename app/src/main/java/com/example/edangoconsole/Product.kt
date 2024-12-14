package com.example.edangoconsole

import android.os.Parcel
import android.os.Parcelable
import com.google.firebase.Timestamp

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
    val quantity: Int = 0,
    val uploadedAt: Timestamp? = null
) : Parcelable {

    constructor() : this("0", "", "", 0f, images = emptyList())

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        name = parcel.readString() ?: "",
        category = parcel.readString() ?: "",
        price = parcel.readFloat(),
        discountPercentage = parcel.readValue(Float::class.java.classLoader) as? Float,
        description = parcel.readString(),
        colors = parcel.createIntArray()?.toList(),
        sizes = parcel.createStringArrayList(),
        images = parcel.createStringArrayList() ?: emptyList(),
        quantity = parcel.readInt(),
        uploadedAt = parcel.readParcelable(Timestamp::class.java.classLoader)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(category)
        parcel.writeFloat(price)
        parcel.writeValue(discountPercentage)
        parcel.writeString(description)
        parcel.writeList(colors)
        parcel.writeStringList(sizes)
        parcel.writeStringList(images)
        parcel.writeInt(quantity)
        parcel.writeParcelable(uploadedAt, flags)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Product> {
        override fun createFromParcel(parcel: Parcel): Product {
            return Product(parcel)
        }

        override fun newArray(size: Int): Array<Product?> {
            return arrayOfNulls(size)
        }
    }
}
