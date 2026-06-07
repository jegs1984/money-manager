package com.moneymanager.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.db.dao.BudgetItemWithStats
import com.moneymanager.data.entity.PeriodEntity
import com.moneymanager.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val periods:      List<PeriodEntity>          = emptyList(),
    val activePeriod: PeriodEntity?               = null,
    val stats:        FinanceRepository.DashboardStats? = null,
    val items:        List<BudgetItemWithStats>   = emptyList(),
    val loading:      Boolean                     = true,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _selectedPeriodId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<DashboardUiState> = combine(
        repo.observePeriods(),
        _selectedPeriodId,
    ) { periods, selectedId ->
        val active = when {
            selectedId != null -> periods.firstOrNull { it.id == selectedId }
            else -> periods.firstOrNull { it.isActive } ?: periods.firstOrNull()
        }
        Triple(periods, active, selectedId)
    }.flatMapLatest { (periods, active, _) ->
        if (active == null) {
            flowOf(DashboardUiState(periods = periods, loading = false))
        } else {
            repo.observeBudgetItemsWithStats(active.id).map { items ->
                val stats = repo.getDashboardStats(active.id)
                DashboardUiState(
                    periods      = periods,
                    activePeriod = active,
                    stats        = stats,
                    items        = items,
                    loading      = false,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun selectPeriod(id: Long) { _selectedPeriodId.value = id }
}
