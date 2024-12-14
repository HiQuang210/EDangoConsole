package com.example.edangoconsole.fragments

import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.edangoconsole.Product
import com.example.edangoconsole.R
import com.example.edangoconsole.databinding.FragmentEditProductBinding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class EditProductFragment : Fragment(R.layout.fragment_edit_product) {
    private lateinit var binding: FragmentEditProductBinding
    private lateinit var product: Product
    private val productsStorage = Firebase.storage.reference
    private val fireStore = Firebase.firestore
    private val selectedImages = mutableListOf<Uri>()
    private val selectedColors = mutableListOf<Int>()
    private var currentImageIndexToEdit: Int? = null
    private val editImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleEditedImage(it)
        } ?: run {
            Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentEditProductBinding.bind(view)

        binding.buttonColorPicker.setOnClickListener {
            ColorPickerDialog.Builder(requireContext())
                .setTitle("Product color")
                .setPositiveButton("Select", object : ColorEnvelopeListener {
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.let {
                            selectedColors.add(it.color)
                            updateColors()
                        }
                    }
                })
                .setNegativeButton("Cancel") { colorPicker, _ ->
                    colorPicker.dismiss()
                }.show()
        }

        val selectedImageActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data
                if (intent?.clipData != null) {
                    val count = intent.clipData?.itemCount ?: 0
                    (0 until count).forEach {
                        val imageUri = intent.clipData?.getItemAt(it)?.uri
                        imageUri?.let {
                            selectedImages.add(it)
                        }
                    }
                } else {
                    val imageUri = intent?.data
                    imageUri?.let {
                        selectedImages.add(it)
                    }
                }
                updateImages()
            }
        }

        binding.buttonImagesPicker.setOnClickListener {
            val intent = Intent(ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.type = "image/*"
            selectedImageActivityResult.launch(intent)
        }


        binding.returnIconBtn.setOnClickListener {
            findNavController().navigate(R.id.action_editProductFragment_to_manageProductFragment)
        }
        arguments?.let {
            val args = EditProductFragmentArgs.fromBundle(it)
            product = args.product
        } ?: run {
            throw IllegalStateException("Product argument is missing")
        }

        binding.edName.setText(product.name)
        binding.edDescription.setText(product.description)
        binding.edPrice.setText(product.price.toString())
        binding.discountPercentage.setText((product.discountPercentage?.times(100)?.toInt())?.toString() ?: "")
        binding.edQuantity.setText(product.quantity.toString())
        fetchProductSizes()

        val categories = listOf("Accessories", "Cosmetics", "Entertainment", "Technology", "Furniture")
        val spinnerCategory: Spinner = binding.spinnerCategory
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter
        val selectedCategoryPosition = categories.indexOf(product.category)
        spinnerCategory.setSelection(selectedCategoryPosition)
        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCategory = categories[position]
                if (selectedCategory == "Accessories" || selectedCategory == "Furniture") {
                    binding.tvSelectSizes.visibility = View.VISIBLE
                    binding.chipGroupSizes.visibility = View.VISIBLE
                } else {
                    binding.tvSelectSizes.visibility = View.GONE
                    binding.chipGroupSizes.visibility = View.GONE
                    val chipGroup = binding.chipGroupSizes
                    for (i in 0 until chipGroup.childCount) {
                        val chip = chipGroup.getChildAt(i) as? com.google.android.material.chip.Chip
                        chip?.isChecked = false
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        product.colors?.forEach { color ->
            selectedColors.add(color)
        }
        updateColors()

        product.images.forEach { imageUrl ->
            selectedImages.add(Uri.parse(imageUrl))
        }
        updateImages()

        binding.btnSaveProduct.setOnClickListener {
            if (validateInformation()) {
                saveProduct()
            } else {
                Toast.makeText(requireContext(), "Check your inputs", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchProductSizes() {
        val productRef = fireStore.collection("Products").document(product.id)

        productRef.get().addOnSuccessListener { documentSnapshot ->
            val sizesFromDb = documentSnapshot.get("sizes")?.let {
                when (it) {
                    is List<*> -> it.filterIsInstance<String>()
                    else -> emptyList()
                }
            } ?: emptyList()
            updateSizeChipGroup(sizesFromDb)
        }.addOnFailureListener { exception ->
            Toast.makeText(requireContext(), "Failed to load sizes: ${exception.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSizeChipGroup(sizesFromDb: List<String>) {
        val chipGroup = binding.chipGroupSizes
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? com.google.android.material.chip.Chip
            chip?.let {
                chip.isChecked = sizesFromDb.contains(chip.text.toString())
            }
        }
    }

    private fun updateColors() {
        val colorBlocksContainer = binding.colorBlocksContainer
        colorBlocksContainer.removeAllViews()

        selectedColors.forEachIndexed { index, color ->
            val colorBlock = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.color_block_size),
                    resources.getDimensionPixelSize(R.dimen.color_block_size)
                ).apply {
                    setMargins(10, 0, 10, 0)
                }
                setBackgroundColor(color)
                setOnClickListener {
                    showColorOptionsDialog(index)
                }
            }
            colorBlocksContainer.addView(colorBlock)
        }
    }

    private fun showColorOptionsDialog(colorIndex: Int) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Choose an Action")
        builder.setItems(arrayOf("Edit Color", "Delete Color")) { dialog, which ->
            when (which) {
                0 -> showColorEditDialog(colorIndex)
                1 -> {
                    selectedColors.removeAt(colorIndex)
                    updateColors()
                    Toast.makeText(requireContext(), "Color deleted", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }
        builder.create().show()
    }

    private fun showColorEditDialog(colorIndex: Int) {
        ColorPickerDialog.Builder(requireContext())
            .setTitle("Edit Color")
            .setPositiveButton("Select", object : ColorEnvelopeListener {
                override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                    envelope?.let {
                        selectedColors[colorIndex] = it.color
                        updateColors()
                    }
                }
            })
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateImages() {
        val imagePreviewContainer = binding.imagePreviewContainer
        imagePreviewContainer.removeAllViews()

        selectedImages.forEachIndexed { index, uri ->
            val imageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.image_preview_size),
                    resources.getDimensionPixelSize(R.dimen.image_preview_size)
                ).apply {
                    setMargins(0, 0, 5, 0)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP

                Glide.with(context)
                    .load(uri)
                    .into(this)

                setOnClickListener {
                    showImageOptionsDialog(index)
                }
            }
            imagePreviewContainer.addView(imageView)
        }
    }


    private fun showImageOptionsDialog(imageIndex: Int) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Choose an Action")
        builder.setItems(arrayOf("Edit Image", "Delete Image")) { dialog, which ->
            when (which) {
                0 -> editImage(imageIndex)
                1 -> {
                    selectedImages.removeAt(imageIndex)
                    updateImages()
                    Toast.makeText(requireContext(), "Image deleted", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }
        builder.create().show()
    }

    private fun editImage(imageIndex: Int) {
        currentImageIndexToEdit = imageIndex
        editImageLauncher.launch("image/*")
    }

    private fun handleEditedImage(newImageUri: Uri) {
        currentImageIndexToEdit?.let { index ->
            selectedImages[index] = newImageUri
            updateImages()
            Toast.makeText(requireContext(), "Image updated successfully", Toast.LENGTH_SHORT).show()
            currentImageIndexToEdit = null
        }
    }

    private fun validateInformation(): Boolean {
        if (binding.edPrice.text.toString().trim().isEmpty()) return false
        if (binding.edName.text.toString().trim().isEmpty()) return false
        if (binding.edQuantity.text.toString().trim().isEmpty()) return false
        if (binding.spinnerCategory.selectedItem == null) return false
        if (selectedImages.isEmpty()) return false
        val discountText = binding.discountPercentage.text.toString().trim()
        if (discountText.isNotEmpty()) {
            val discount = discountText.toInt()
            if (discount !in 1..99) return false
        }
        return true
    }

    private fun getSelectedSizes(): List<String>? {
        val selectedSizes = mutableListOf<String>()
        val chipGroup = binding.chipGroupSizes
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? com.google.android.material.chip.Chip
            if (chip?.isChecked == true) {
                selectedSizes.add(chip.text.toString())
            }
        }
        return if (selectedSizes.isEmpty()) null else selectedSizes
    }

    private fun getImagesByteArrays(): List<ByteArray> {
        val imagesByteArray = mutableListOf<ByteArray>()
        selectedImages.forEach { uri ->
            try {
                val stream = ByteArrayOutputStream()
                val imageBmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(requireContext().contentResolver, uri)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                }
                if (imageBmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                    imagesByteArray.add(stream.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return imagesByteArray
    }

    private fun saveProduct() {
        val name = binding.edName.text.toString().trim()
        val category = binding.spinnerCategory.selectedItem.toString()
        val price = binding.edPrice.text.toString().trim()
        val discountText = binding.discountPercentage.text.toString().trim()
        val discountPercentage = if (discountText.isEmpty()) null else discountText.toInt().toFloat() / 100
        val description = binding.edDescription.text.toString().trim()
        val sizes = getSelectedSizes()
        val imagesByteArrays = getImagesByteArrays()
        val images = mutableListOf<String>()
        val quantity = binding.edQuantity.text.toString().toInt()

        binding.btnSaveProduct.startAnimation()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                async {
                    imagesByteArrays.forEach {
                        val id = UUID.randomUUID().toString()
                        launch {
                            val imageStorage = productsStorage.child("products/images/$id")
                            val result = imageStorage.putBytes(it).await()
                            val downloadUrl = result.storage.downloadUrl.await().toString()
                            images.add(downloadUrl)
                        }
                    }
                }.await()

                val updatedProduct = Product(
                    product.id,
                    name,
                    category,
                    price.toFloat(),
                    discountPercentage,
                    description.ifEmpty { null },
                    if (selectedColors.isEmpty()) null else selectedColors,
                    sizes,
                    images.ifEmpty { product.images },
                    quantity,
                    uploadedAt = product.uploadedAt
                )

                fireStore.collection("Products").document(product.id).set(updatedProduct).await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Product updated successfully", Toast.LENGTH_SHORT).show()
                    binding.btnSaveProduct.revertAnimation()
                    findNavController().navigate(R.id.action_editProductFragment_to_manageProductFragment)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to update product", Toast.LENGTH_SHORT).show()
                    binding.btnSaveProduct.revertAnimation()
                }
            }
        }
    }
}

