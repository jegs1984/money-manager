package com.moneymanager.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.db.StagingTransactionEntity
import com.moneymanager.data.repository.FinanceRepository
import com.moneymanager.domain.usecase.DetectStagingDuplicatesUseCase
import com.moneymanager.domain.usecase.ParseDebitStatementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StagingUiState(
    val rows: List<StagingTransactionEntity> = emptyList(),
    val duplicateIds: Set<Long> = emptySet(),
    /** staging_id → category_id chosen by user */
    val categoryAssignments: Map<Long, Long> = emptyMap(),
    /** staging_id → "keep" | "remove" for duplicate rows */
    val duplicateActions: Map<Long, DupAction> = emptyMap(),
    val isLoading: Boolean = false,
    val importError: String? = null,
    val committedCount: Int? = null,
    val removedCount: Int? = null,
)

enum class DupAction { KEEP, REMOVE }

@HiltViewModel
class StagingViewModel @Inject constructor(
    private val repo: FinanceRepository,
    private val parser: ParseDebitStatementUseCase,
    private val detectDuplicates: DetectStagingDuplicatesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StagingUiState())
    val uiState: StateFlow<StagingUiState> = _uiState

    init {
        viewModelScope.launch {
            repo.observePendingStaging().collectLatest { rows ->
                val dupIds = detectDuplicates.forDebit(rows)
                _uiState.value = _uiState.value.copy(
                    rows         = rows,
                    duplicateIds = dupIds,
                    // Default action for duplicates: KEEP
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
                repo.insertStagingRows(result.rows)
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
            val committed = repo.processStagingBatch(entries, removeIds)
            _uiState.value = _uiState.value.copy(
                isLoading      = false,
                committedCount = committed,
                removedCount   = removeIds.size,
                categoryAssignments = emptyMap(),
                duplicateActions    = emptyMap(),
            )
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(committedCount = null, removedCount = null)
    }
}
