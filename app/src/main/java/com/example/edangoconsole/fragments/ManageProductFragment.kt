package com.example.edangoconsole.fragments

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.edangoconsole.Product
import com.example.edangoconsole.R
import com.example.edangoconsole.ProductAdapter
import com.example.edangoconsole.databinding.FragmentManageProductBinding
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ManageProductFragment : Fragment(R.layout.fragment_manage_product) {
    private lateinit var binding: FragmentManageProductBinding
    private val fireStore = Firebase.firestore
    private val productList = mutableListOf<Pair<Product, String>>()
    private lateinit var productAdapter: ProductAdapter
    private var lastVisible: DocumentSnapshot? = null
    private var isLoading = false
    private var hasMoreProducts = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentManageProductBinding.bind(view)

        val onSeeDetailClick: (Product, String) -> Unit = { product, documentId ->
            val action = ManageProductFragmentDirections
                .actionManageProductFragmentToEditProductFragment(product, documentId)
            findNavController().navigate(action)
        }

        productAdapter = ProductAdapter(productList, onSeeDetailClick)

        setupRecyclerView()

        binding.addIconBtn.setOnClickListener {
            findNavController().navigate(R.id.action_manageProductFragment_to_productAddFragment)
        }

        binding.returnIconBtn.setOnClickListener {
            findNavController().navigate(R.id.action_manageProductFragment_to_mainScreenFragment)
        }

        fetchProducts()
    }

    private fun setupRecyclerView() {
        binding.rvProductsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = productAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (!isLoading && hasMoreProducts && !recyclerView.canScrollVertically(1)) {
                        fetchProducts()
                    }
                }
            })
        }
    }

    private fun fetchProducts() {
        if (isLoading || !hasMoreProducts) return

        isLoading = true
        val query = fireStore.collection("Products")
            .orderBy("uploadedAt", Query.Direction.DESCENDING)
            .limit(15)

        lastVisible?.let {
            query.startAfter(it)
        }

        lifecycleScope.launch {
            try {
                val result = query.get().await()

                if (result.isEmpty) {
                    hasMoreProducts = false
                    isLoading = false
                    return@launch
                }

                val newProducts = result.documents.mapNotNull { doc ->
                    val product = doc.toObject(Product::class.java)
                    product?.let { Pair(it, doc.id) }
                }

                // Add new products to the list and notify only the inserted range
                val startPosition = productList.size
                productList.addAll(newProducts)
                productAdapter.notifyItemRangeInserted(startPosition, newProducts.size)

                lastVisible = result.documents[result.size() - 1]

                if (newProducts.size < 15) {
                    hasMoreProducts = false
                }

                isLoading = false

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to fetch products", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
                isLoading = false
            }
        }
    }
}

