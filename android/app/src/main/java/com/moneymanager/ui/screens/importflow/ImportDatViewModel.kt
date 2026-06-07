package com.moneymanager.ui.screens.importflow

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.entity.PeriodEntity
import com.moneymanager.data.entity.StagingTransactionEntity
import com.moneymanager.data.parser.DatParser
import com.moneymanager.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class ImportDatState {
    object Idle : ImportDatState()
    object Parsing : ImportDatState()
    data class Staged(val count: Int, val skipped: Int, val accountNumber: String?) : ImportDatState()
    data class Error(val message: String) : ImportDatState()
}

@HiltViewModel
class ImportDatViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportDatState>(ImportDatState.Idle)
    val state = _state.asStateFlow()

    val periods = repo.observePeriods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedPeriod = MutableStateFlow<PeriodEntity?>(null)

    init {
        viewModelScope.launch {
            selectedPeriod.value = repo.getActivePeriod()
        }
    }

    fun parse(contentResolver: android.content.ContentResolver, uri: Uri, filename: String?) {
        viewModelScope.launch {
            _state.value = ImportDatState.Parsing
            try {
                val result = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)!!.use { stream ->
                        DatParser.parse(stream, filename)
                    }
                }
                repo.insertStagingRows(result.rows)
                _state.value = ImportDatState.Staged(result.rows.size, result.skipped, result.accountNumber)
            } catch (e: Exception) {
                _state.value = ImportDatState.Error(e.message ?: "Error desconocido")
            }
        }
    }
}
