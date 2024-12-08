package com.example.edangoconsole.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.edangoconsole.R
import com.example.edangoconsole.databinding.FragmentMainScreenBinding

class MainScreenFragment : Fragment(R.layout.fragment_main_screen) {
    private lateinit var binding: FragmentMainScreenBinding

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentMainScreenBinding.bind(view)

        binding.btnManageProduct.setOnClickListener {
            findNavController().navigate(R.id.action_mainScreenFragment_to_productAddFragment)
        }
    }
}
