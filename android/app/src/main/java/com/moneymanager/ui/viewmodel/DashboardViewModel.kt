package com.moneymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.db.PeriodEntity
import com.moneymanager.data.repository.BudgetItemWithStats
import com.moneymanager.data.repository.FinanceRepository
import com.moneymanager.data.repository.PeriodStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val allPeriods: List<PeriodEntity> = emptyList(),
    val activePeriod: PeriodEntity? = null,
    val stats: PeriodStats? = null,
    val items: List<BudgetItemWithStats> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init { load(null) }

    fun selectPeriod(period: PeriodEntity) { load(period) }

    private fun load(period: PeriodEntity?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val periods = repo.getPeriods()
            val active  = period ?: repo.getActivePeriod()
            val stats   = active?.let { repo.calculateStats(it) }
            val items   = active?.let { repo.getBudgetItemsWithStats(it.id) } ?: emptyList()

            _uiState.value = DashboardUiState(
                allPeriods   = periods,
                activePeriod = active,
                stats        = stats,
                items        = items.sortedWith(
                    compareBy({ it.item.type != "IN" }, { it.category.group }, { it.category.name })
                ),
                isLoading    = false,
            )
        }
    }
}
