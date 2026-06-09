package com.moneymanager.ui.screens.importflow

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.entity.PeriodEntity
import com.moneymanager.data.parser.XlsParser
import com.moneymanager.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class ImportCCState {
    object Idle : ImportCCState()
    object Parsing : ImportCCState()
    data class Staged(val count: Int, val skipped: Int, val cardHolder: String?, val cardNumber: String?) : ImportCCState()
    data class Error(val message: String) : ImportCCState()
}

@HiltViewModel
class ImportCCViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportCCState>(ImportCCState.Idle)
    val state = _state.asStateFlow()

    val periods = repo.observePeriods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedPeriod = MutableStateFlow<PeriodEntity?>(null)

    init {
        viewModelScope.launch { selectedPeriod.value = repo.getActivePeriod() }
    }

    fun parse(contentResolver: android.content.ContentResolver, uri: Uri, filename: String?) {
        viewModelScope.launch {
            _state.value = ImportCCState.Parsing
            try {
                val result = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)!!.use { stream ->
                        XlsParser.parse(stream, filename)
                    }
                }
                repo.insertStagingCCRows(result.rows)
                _state.value = ImportCCState.Staged(
                    result.rows.size, result.skipped, result.cardHolder, result.cardNumber
                )
            } catch (e: Exception) {
                _state.value = ImportCCState.Error(e.message ?: "Error desconocido")
            }
        }
    }
}
