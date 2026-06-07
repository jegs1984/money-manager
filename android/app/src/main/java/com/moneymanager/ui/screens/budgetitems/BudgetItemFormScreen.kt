package com.moneymanager.ui.screens.budgetitems

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.moneymanager.data.entity.CategoryEntity
import com.moneymanager.data.entity.PeriodEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetItemFormScreen(
    nav: NavController,
    budgetItemId: Long,
    vm: BudgetItemFormViewModel = hiltViewModel(),
) {
    LaunchedEffect(budgetItemId) { vm.load(budgetItemId) }

    val saved           by vm.saved.collectAsState()
    val periods         by vm.periods.collectAsState()
    val categories      by vm.categories.collectAsState()
    val selectedPeriod  by vm.selectedPeriod.collectAsState()
    val selectedCat     by vm.selectedCategory.collectAsState()
    val type            by vm.type.collectAsState()
    val amount          by vm.projectedAmount.collectAsState()

    LaunchedEffect(saved) { if (saved) nav.popBackStack() }

    var periodExpanded   by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (budgetItemId == 0L) "Nuevo ítem" else "Editar ítem") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Period picker
            DropdownField(
                label    = "Período",
                value    = selectedPeriod?.name ?: "Seleccionar",
                expanded = periodExpanded,
                onToggle = { periodExpanded = it },
                items    = periods,
                itemLabel = { it.name },
                onSelect = { vm.selectedPeriod.value = it; periodExpanded = false },
            )

            // Category picker
            DropdownField(
                label    = "Categoría",
                value    = selectedCat?.name ?: "Seleccionar",
                expanded = categoryExpanded,
                onToggle = { categoryExpanded = it },
                items    = categories,
                itemLabel = { it.name },
                onSelect = { vm.selectedCategory.value = it; categoryExpanded = false },
            )

            // Type toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == "IN",
                    onClick  = { vm.type.value = "IN" },
                    label    = { Text("Ingreso") },
                )
                FilterChip(
                    selected = type == "OUT",
                    onClick  = { vm.type.value = "OUT" },
                    label    = { Text("Gasto") },
                )
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { vm.projectedAmount.value = it },
                label = { Text("Monto proyectado (CLP)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick  = { vm.save(budgetItemId) },
                modifier = Modifier.fillMaxWidth(),
                enabled  = selectedPeriod != null && selectedCat != null,
            ) { Text("Guardar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onToggle) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onToggle(false) }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text    = { Text(itemLabel(item)) },
                    onClick = { onSelect(item) },
                )
            }
        }
    }
}
