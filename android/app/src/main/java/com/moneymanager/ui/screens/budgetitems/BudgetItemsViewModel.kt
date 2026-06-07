package com.moneymanager.ui.screens.budgetitems

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.db.dao.BudgetItemWithStats
import com.moneymanager.data.entity.BudgetItemEntity
import com.moneymanager.data.entity.CategoryEntity
import com.moneymanager.data.entity.PeriodEntity
import com.moneymanager.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetItemsUiState(
    val period:     PeriodEntity?             = null,
    val items:      List<BudgetItemWithStats> = emptyList(),
    val categories: List<CategoryEntity>      = emptyList(),
    val loading:    Boolean                   = true,
)

@HiltViewModel
class BudgetItemsViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _periodId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<BudgetItemsUiState> = _periodId.filterNotNull().flatMapLatest { pid ->
        combine(
            repo.observeBudgetItemsWithStats(pid),
            repo.observeCategories(),
        ) { items, cats ->
            val period = repo.getPeriods().firstOrNull { it.id == pid }
            BudgetItemsUiState(period = period, items = items, categories = cats, loading = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetItemsUiState())

    fun setPeriod(id: Long) { _periodId.value = id }

    fun delete(item: BudgetItemEntity) = viewModelScope.launch { repo.deleteBudgetItem(item) }
}
