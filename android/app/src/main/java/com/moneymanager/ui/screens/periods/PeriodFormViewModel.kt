package com.moneymanager.ui.screens.periods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.entity.PeriodEntity
import com.moneymanager.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeriodFormViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    var name      = MutableStateFlow("")
    var startDate = MutableStateFlow("")
    var endDate   = MutableStateFlow("")
    var isActive  = MutableStateFlow(false)

    fun load(id: Long) = viewModelScope.launch {
        if (id == 0L) return@launch
        repo.getPeriods().firstOrNull { it.id == id }?.let { p ->
            name.value      = p.name
            startDate.value = p.startDate
            endDate.value   = p.endDate
            isActive.value  = p.isActive
        }
    }

    fun save(existingId: Long) = viewModelScope.launch {
        repo.savePeriod(
            PeriodEntity(
                id        = existingId,
                name      = name.value.trim(),
                startDate = startDate.value.trim(),
                endDate   = endDate.value.trim(),
                isActive  = isActive.value,
            )
        )
        _saved.value = true
    }
}
