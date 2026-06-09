package com.moneymanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.db.StagingCCTransactionEntity
import com.moneymanager.data.repository.FinanceRepository
import com.moneymanager.domain.usecase.DetectStagingDuplicatesUseCase
import com.moneymanager.domain.usecase.ParseCCStatementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CCStagingUiState(
    val rows: List<StagingCCTransactionEntity> = emptyList(),
    val duplicateIds: Set<Long> = emptySet(),
    val categoryAssignments: Map<Long, Long> = emptyMap(),
    val duplicateActions: Map<Long, DupAction> = emptyMap(),
    val isLoading: Boolean = false,
    val importError: String? = null,
    val committedCount: Int? = null,
    val removedCount: Int? = null,
)

@HiltViewModel
class CCStagingViewModel @Inject constructor(
    private val repo: FinanceRepository,
    private val parser: ParseCCStatementUseCase,
    private val detectDuplicates: DetectStagingDuplicatesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CCStagingUiState())
    val uiState: StateFlow<CCStagingUiState> = _uiState

    init {
        viewModelScope.launch {
            repo.observePendingCCStaging().collectLatest { rows ->
                val dupIds = detectDuplicates.forCC(rows)
                _uiState.value = _uiState.value.copy(
                    rows         = rows,
                    duplicateIds = dupIds,
                    duplicateActions = dupIds.associateWith { id ->
                        _uiState.value.duplicateActions[id] ?: DupAction.KEEP
                    },
                )
            }
        }
    }

    fun setCategory(stagingId: Long, categoryId: Long) {
        _uiState.value = _uiState.value.copy(
            categoryAssignments = _uiState.value.categoryAssignments + (stagingId to categoryId)
        )
    }

    fun setDupAction(stagingId: Long, action: DupAction) {
        _uiState.value = _uiState.value.copy(
            duplicateActions = _uiState.value.duplicateActions + (stagingId to action)
        )
    }

    fun importFile(stream: java.io.InputStream, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, importError = null)
            try {
                val result = parser.parse(stream, fileName)
                repo.insertCCStagingRows(result.rows)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(importError = e.message, isLoading = false)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun commit() {
        viewModelScope.launch {
            val state = _uiState.value
            val removeIds = state.duplicateIds
                .filter { state.duplicateActions[it] == DupAction.REMOVE }
                .toSet()

            val entries = state.rows
                .filter { it.id !in removeIds }
                .mapNotNull { row ->
                    val catId = state.categoryAssignments[row.id] ?: return@mapNotNull null
                    row.id to catId
                }

            _uiState.value = _uiState.value.copy(isLoading = true)
            val committed = repo.processCCStagingBatch(entries, removeIds)
            _uiState.value = _uiState.value.copy(
                isLoading           = false,
                committedCount      = committed,
                removedCount        = removeIds.size,
                categoryAssignments = emptyMap(),
                duplicateActions    = emptyMap(),
            )
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(committedCount = null, removedCount = null)
    }
}
