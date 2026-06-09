package com.moneymanager.ui.screens.budgetitems

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.moneymanager.data.db.dao.BudgetItemWithStats
import com.moneymanager.data.entity.BudgetItemEntity
import com.moneymanager.ui.navigation.Routes
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetItemsScreen(
    nav: NavController,
    periodId: Long,
    vm: BudgetItemsViewModel = hiltViewModel(),
) {
    LaunchedEffect(periodId) { vm.setPeriod(periodId) }

    val state by vm.uiState.collectAsState()
    val clpFmt = remember { NumberFormat.getNumberInstance(Locale("es", "CL")) }
    var toDelete by remember { mutableStateOf<BudgetItemWithStats?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.period?.name ?: "Ítems presupuesto") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.budgetItemForm()) }) {
                Icon(Icons.Default.Add, "Nuevo ítem")
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val income   = state.items.filter { it.type == "IN" }
            val expenses = state.items.filter { it.type == "OUT" }

            if (income.isNotEmpty()) {
                item { SectionHeader("Ingresos") }
                items(income, key = { it.id }) { item ->
                    BudgetItemCard(item, clpFmt,
                        onEdit   = { nav.navigate(Routes.budgetItemForm(item.id)) },
                        onDelete = { toDelete = item },
                    )
                }
            }
            if (expenses.isNotEmpty()) {
                item { SectionHeader("Gastos") }
                items(expenses, key = { it.id }) { item ->
                    BudgetItemCard(item, clpFmt,
                        onEdit   = { nav.navigate(Routes.budgetItemForm(item.id)) },
                        onDelete = { toDelete = item },
                    )
                }
            }
        }
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title   = { Text("Eliminar ítem") },
            text    = { Text("¿Eliminar \"${item.categoryName}\"? Se eliminarán sus transacciones.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(BudgetItemEntity(
                        id = item.id, periodId = item.periodId,
                        categoryId = item.categoryId, type = item.type,
                        projectedAmount = item.projectedAmount,
                    ))
                    toDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    HorizontalDivider()
}

@Composable
private fun BudgetItemCard(
    item: BudgetItemWithStats,
    fmt: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.categoryName, style = MaterialTheme.typography.bodyMedium)
                Text(item.categoryGroup, style = MaterialTheme.typography.labelSmall)
                Text(
                    "Real: $ ${fmt.format(item.totalReal.toLong())}  /  Presupuesto: $ ${fmt.format(item.projectedAmount.toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit,   "Editar") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar") }
        }
    }
}
