package com.example.tpass

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tpass.databinding.FragmentHistoryBinding
import androidx.fragment.app.viewModels
import android.app.AlertDialog

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyAdapter: HistoryAdapter
    private val historyViewModel: HistoryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeHistory()
        setupClearButton()
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onItemClick = { historyItem ->
                val dialog = HistoryDetailsDialog(historyItem)
                dialog.show(parentFragmentManager, "HistoryDetailsDialog")
            },
            onDeleteClick = { historyItem ->
                showDeleteConfirmationDialog(historyItem)
            }
        )
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }
    }

    private fun observeHistory() {
        historyViewModel.historyItems.observe(viewLifecycleOwner) { items ->
            historyAdapter.submitList(items)
            binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupClearButton() {
        binding.clearButton.setOnClickListener {
            showClearConfirmationDialog()
        }
    }

    private fun showDeleteConfirmationDialog(historyItem: HistoryItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удаление записи")
            .setMessage("Вы уверены, что хотите удалить эту запись из истории?")
            .setPositiveButton("Удалить") { _, _ ->
                historyViewModel.deleteHistoryItem(historyItem.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showClearConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Очистка истории")
            .setMessage("Вы уверены, что хотите очистить всю историю изменений?")
            .setPositiveButton("Очистить") { _, _ ->
                historyViewModel.clearHistory()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 