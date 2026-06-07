package com.moneymanager.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneymanager.data.entity.CategoryEntity
import com.moneymanager.data.repository.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

val CATEGORY_GROUPS = listOf(
    "AhorroeInversion", "Alimentación", "Cuenta", "Deuda", "Gastos",
    "GastosExtraordinarios", "IngresoFijo", "IngresoVariableExtra",
    "Lujo", "Pension", "Personal", "Transporte", "ViviendaHogar",
)

@HiltViewModel
class CategoryFormViewModel @Inject constructor(
    private val repo: FinanceRepository,
) : ViewModel() {

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    var name  = MutableStateFlow("")
    var group = MutableStateFlow(CATEGORY_GROUPS.first())

    fun load(id: Long) = viewModelScope.launch {
        if (id == 0L) return@launch
        repo.getCategories().firstOrNull { it.id == id }?.let { c ->
            name.value  = c.name
            group.value = c.group
        }
    }

    fun save(existingId: Long) = viewModelScope.launch {
        repo.saveCategory(
            CategoryEntity(id = existingId, name = name.value.trim(), group = group.value)
        )
        _saved.value = true
    }
}
