package com.example.edangoconsole.fragments

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.edangoconsole.Product
import com.example.edangoconsole.R
import com.example.edangoconsole.databinding.FragmentAddProductBinding
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

class ProductAddFragment : Fragment(R.layout.fragment_add_product) {
    private lateinit var binding: FragmentAddProductBinding
    private var selectedImages = mutableListOf<Uri>()
    private val selectedColors = mutableListOf<Int>()
    private val productsStorage = Firebase.storage.reference
    private val fireStore = Firebase.firestore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentAddProductBinding.bind(view)

        val categories = listOf("Accessories", "Cosmetics", "Entertainment", "Technology", "Furniture")
        val spinnerCategory: Spinner = binding.spinnerCategory
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = adapter

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

        binding.btnSaveProduct.setOnClickListener {
            if (validateInformation()) {
                saveProduct()
            } else {
                Toast.makeText(requireContext(), "Check your inputs", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveProduct() {
        val name = binding.edName.text.toString().trim()
        val category = binding.spinnerCategory.selectedItem.toString()
        val price = binding.edPrice.text.toString().trim()
        val discountPercentage = binding.discountPercentage.text.toString().trim()
        val description = binding.edDescription.text.toString().trim()
        val sizes = getSelectedSizes()
        val imagesByteArrays = getImagesByteArrays()
        val images = mutableListOf<String>()

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

                val product = Product(
                    UUID.randomUUID().toString(),
                    name,
                    category,
                    price.toFloat(),
                    if (discountPercentage.isEmpty()) null else discountPercentage.toFloat(),
                    description.ifEmpty { null },
                    if (selectedColors.isEmpty()) null else selectedColors,
                    sizes,
                    images
                )

                fireStore.collection("Products").add(product).await()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Product uploaded successfully", Toast.LENGTH_SHORT).show()
                    binding.btnSaveProduct.revertAnimation()
                    resetInputs()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to upload product", Toast.LENGTH_SHORT).show()
                    binding.btnSaveProduct.revertAnimation()
                }
            }
        }
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

    private fun updateImages() {
        binding.tvSelectedImages.text = "Selected Images: ${selectedImages.size}"
        val imagePreviewContainer = binding.imagePreviewContainer
        imagePreviewContainer.removeAllViews() // Clear previous previews

        selectedImages.forEach { uri ->
            val imageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.image_preview_size),
                    resources.getDimensionPixelSize(R.dimen.image_preview_size)
                ).apply {
                    setMargins(0, 0, 5, 0)
                }
                setImageURI(uri)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            imagePreviewContainer.addView(imageView)
        }
    }

    private fun updateColors() {
        val colorBlocksContainer = binding.colorBlocksContainer
        colorBlocksContainer.removeAllViews()

        selectedColors.forEach { color ->
            val colorBlock = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.color_block_size),
                    resources.getDimensionPixelSize(R.dimen.color_block_size)
                ).apply {
                    setMargins(10, 0, 10, 0)
                }
                setBackgroundColor(color)
            }
            colorBlocksContainer.addView(colorBlock)
        }
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

    private fun validateInformation(): Boolean {
        if (binding.edPrice.text.toString().trim().isEmpty()) return false
        if (binding.edName.text.toString().trim().isEmpty()) return false
        if (binding.spinnerCategory.selectedItem == null) return false
        if (selectedImages.isEmpty()) return false
        return true
    }

    private fun resetInputs() {
        binding.edName.text.clear()
        binding.edPrice.text.clear()
        binding.discountPercentage.text.clear()
        binding.edDescription.text.clear()
        binding.spinnerCategory.setSelection(0)
        selectedColors.clear()
        updateColors()
        selectedImages.clear()
        updateImages()
        val chipGroup = binding.chipGroupSizes
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? com.google.android.material.chip.Chip
            chip?.isChecked = false
        }
    }
}
