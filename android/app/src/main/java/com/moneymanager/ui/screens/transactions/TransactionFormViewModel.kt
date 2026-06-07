package com.moneymanager.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.entity.BudgetItemEntity
import com.moneymanager.data.entity.TransactionEntity
import com.moneymanager.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// Lightweight projection for the picker
data class BudgetItemOption(val id: Long, val label: String)

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    // Populate budget-item picker from active period
    val budgetItemOptions: StateFlow<List<BudgetItemOption>> =
        repo.observePeriods().flatMapLatest { periods ->
            val active = periods.firstOrNull { it.isActive } ?: periods.firstOrNull()
            if (active == null) flowOf(emptyList())
            else repo.observeBudgetItemsWithStats(active.id).map { items ->
                items.map { BudgetItemOption(it.id, "${it.type} · ${it.categoryName}") }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedBudgetItem = MutableStateFlow<BudgetItemOption?>(null)
    var date               = MutableStateFlow("")
    var amount             = MutableStateFlow("")
    var description        = MutableStateFlow("")
    var notes              = MutableStateFlow("")

    fun load(id: Long) = viewModelScope.launch {
        if (id == 0L) return@launch
        repo.getTransaction(id)?.let { tx ->
            date.value        = tx.date
            amount.value      = tx.realAmount.toLong().toString()
            description.value = tx.description
            notes.value       = tx.notes ?: ""
            // Restore budget item selection after options load
            budgetItemOptions.first { it.isNotEmpty() }.let { opts ->
                selectedBudgetItem.value = opts.firstOrNull { it.id == tx.budgetItemId }
            }
        }
    }

    fun save(existingId: Long) = viewModelScope.launch {
        repo.saveTransaction(
            TransactionEntity(
                id           = existingId,
                budgetItemId = selectedBudgetItem.value?.id,
                date         = date.value.trim(),
                realAmount   = amount.value.toDoubleOrNull() ?: 0.0,
                description  = description.value.trim(),
                notes        = notes.value.trim().takeIf { it.isNotBlank() },
            )
        )
        _saved.value = true
    }
}
