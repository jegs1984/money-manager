package com.moneymanager.ui.screens.importflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.db.dao.BudgetItemWithStats
import com.moneymanager.data.entity.CategoryEntity
import com.moneymanager.data.entity.StagingCCTransactionEntity
import com.moneymanager.data.repository.FinanceRepository
import com.moneymanager.data.repository.StagingCommitEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StagingCCRowUiState(
    val staging:            StagingCCTransactionEntity,
    val assignedCategoryId: Long? = null,
)

data class StagingCCReviewUiState(
    val rows:        List<StagingCCRowUiState>  = emptyList(),
    val categories:  List<CategoryEntity>       = emptyList(),
    val budgetItems: List<BudgetItemWithStats>  = emptyList(),
    val cardHolder:  String?                    = null,
    val cardNumber:  String?                    = null,
    val committed:   Int?                       = null,
    val loading:     Boolean                    = true,
)

@HiltViewModel
class StagingCCReviewViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _periodId    = MutableStateFlow<Long?>(null)
    private val _assignments = MutableStateFlow<Map<Long, Long>>(emptyMap())
    private val _committed   = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<StagingCCReviewUiState> = combine(
        _periodId.filterNotNull().flatMapLatest { pid ->
            combine(
                repo.observePendingStagingCC(),
                repo.observeCategories(),
                repo.observeBudgetItemsWithStats(pid),
            ) { staging, cats, budgetItems -> Triple(staging, cats, budgetItems) }
        },
        _assignments,
        _committed,
    ) { (staging, cats, budgetItems), assignments, committed ->
        val first = staging.firstOrNull()
        StagingCCReviewUiState(
            rows        = staging.map { s -> StagingCCRowUiState(s, assignments[s.id]) },
            categories  = cats,
            budgetItems = budgetItems,
            cardHolder  = first?.cardHolder,
            cardNumber  = first?.cardNumber,
            committed   = committed,
            loading     = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StagingCCReviewUiState())

    fun setPeriod(id: Long) { _periodId.value = id }

    fun assign(stagingId: Long, categoryId: Long) {
        _assignments.value = _assignments.value.toMutableMap().also { it[stagingId] = categoryId }
    }

    fun commit() = viewModelScope.launch {
        val currentState = uiState.value
        val entries = currentState.rows.mapNotNull { row ->
            val catId = row.assignedCategoryId ?: return@mapNotNull null
            StagingCommitEntry(row.staging.id, catId)
        }
        val n = repo.commitCCStagingBatch(entries, currentState.budgetItems)
        _committed.value = n
    }
}
