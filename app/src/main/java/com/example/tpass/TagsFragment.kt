package com.example.tpass

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tpass.databinding.FragmentTagsBinding
import com.google.android.material.chip.Chip

class TagsFragment : Fragment() {
    private var _binding: FragmentTagsBinding? = null
    private val binding get() = _binding!!

    private val passwordViewModel: PasswordViewModel by viewModels(ownerProducer = { requireActivity() }) {
        PasswordViewModelFactory(requireActivity().application, KeePassManager(requireContext()))
    }
    private lateinit var passwordAdapter: PasswordAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTagsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupTagsChips()
    }

    private fun setupRecyclerView() {
        passwordAdapter = PasswordAdapter(
            onItemClick = { entry ->
                // Можно реализовать просмотр/редактирование записи по тегу
                Toast.makeText(requireContext(), entry.title, Toast.LENGTH_SHORT).show()
            },
            onCopyClick = { entry ->
                // Можно реализовать копирование пароля
                Toast.makeText(requireContext(), "Скопировано: ${entry.title}", Toast.LENGTH_SHORT).show()
            }
        )
        binding.taggedRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = passwordAdapter
        }
    }

    private fun setupTagsChips() {
        val chipGroup = binding.tagsChipGroup
        chipGroup.removeAllViews()
        val tags = passwordViewModel.getAllTags()
        tags.forEach { tag ->
            val chip = Chip(requireContext())
            chip.text = tag
            chip.isCheckable = true
            chip.setOnClickListener {
                showEntriesByTag(tag)
            }
            chipGroup.addView(chip)
        }
    }

    private fun showEntriesByTag(tag: String) {
        val entries = passwordViewModel.getEntriesByTag(tag)
        passwordAdapter.updateList(entries)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 