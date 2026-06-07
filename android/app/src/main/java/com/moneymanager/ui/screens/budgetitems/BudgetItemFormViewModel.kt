package com.moneymanager.ui.screens.budgetitems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.entity.BudgetItemEntity
import com.moneymanager.data.entity.CategoryEntity
import com.moneymanager.data.entity.PeriodEntity
import com.moneymanager.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BudgetItemFormViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    val periods    = repo.observePeriods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedPeriod   = MutableStateFlow<PeriodEntity?>(null)
    var selectedCategory = MutableStateFlow<CategoryEntity?>(null)
    var type             = MutableStateFlow("OUT")   // "IN" | "OUT"
    var projectedAmount  = MutableStateFlow("0")

    fun load(id: Long) = viewModelScope.launch {
        if (id == 0L) {
            // Default to active period
            selectedPeriod.value = repo.getActivePeriod()
            return@launch
        }
        repo.getBudgetItem(id)?.let { bi ->
            val allPeriods = repo.getPeriods()
            val allCats    = repo.getCategories()
            selectedPeriod.value   = allPeriods.firstOrNull { it.id == bi.periodId }
            selectedCategory.value = allCats.firstOrNull { it.id == bi.categoryId }
            type.value             = bi.type
            projectedAmount.value  = bi.projectedAmount.toLong().toString()
        }
    }

    fun save(existingId: Long) = viewModelScope.launch {
        val period   = selectedPeriod.value   ?: return@launch
        val category = selectedCategory.value ?: return@launch
        repo.saveBudgetItem(
            BudgetItemEntity(
                id              = existingId,
                periodId        = period.id,
                categoryId      = category.id,
                type            = type.value,
                projectedAmount = projectedAmount.value.toDoubleOrNull() ?: 0.0,
            )
        )
        _saved.value = true
    }
}
