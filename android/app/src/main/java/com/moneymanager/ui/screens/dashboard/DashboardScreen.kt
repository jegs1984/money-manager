package com.moneymanager.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.moneymanager.data.db.dao.BudgetItemWithStats
import com.moneymanager.ui.navigation.Routes
import com.moneymanager.ui.theme.BudgetAmber
import com.moneymanager.ui.theme.BudgetGreen
import com.moneymanager.ui.theme.BudgetRed
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(nav: NavController, vm: DashboardViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsState()
    val clpFmt = remember { NumberFormat.getNumberInstance(Locale("es", "CL")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Money Manager") },
                actions = {
                    IconButton(onClick = { nav.navigate(Routes.PERIODS) }) {
                        Icon(Icons.Default.DateRange, "Períodos")
                    }
                    IconButton(onClick = { nav.navigate(Routes.CATEGORIES) }) {
                        Icon(Icons.Default.List, "Categorías")
                    }
                    IconButton(onClick = { nav.navigate(Routes.TRANSACTIONS) }) {
                        Icon(Icons.Default.Receipt, "Transacciones")
                    }
                }
            )
        },
        floatingActionButton = {
            state.activePeriod?.let { period ->
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFloatingActionButton(onClick = { nav.navigate(Routes.IMPORT_CC) }) {
                        Icon(Icons.Default.CreditCard, "Importar TC")
                    }
                    SmallFloatingActionButton(onClick = { nav.navigate(Routes.IMPORT_DAT) }) {
                        Icon(Icons.Default.FileUpload, "Importar DAT")
                    }
                    FloatingActionButton(onClick = { nav.navigate(Routes.budgetItems(period.id)) }) {
                        Icon(Icons.Default.Add, "Nuevo ítem")
                    }
                }
            }
        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Period selector
            item {
                PeriodSelector(
                    periods  = state.periods,
                    selected = state.activePeriod,
                    onSelect = { vm.selectPeriod(it.id) },
                )
            }

            val stats = state.stats
            if (stats != null && state.activePeriod != null) {
                // Summary cards
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryCard(
                            label = "Safe to Spend",
                            value = "$ ${clpFmt.format(stats.safeToSpend.toLong())}",
                            color = if (stats.safeToSpend >= 0) BudgetGreen else BudgetRed,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryCard(
                            label = "Burn Rate",
                            value = "${"%.0f".format(stats.burnRate * 100)} %",
                            color = when {
                                stats.burnRate < 0.8  -> BudgetGreen
                                stats.burnRate < 1.0  -> BudgetAmber
                                else                  -> BudgetRed
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryCard(
                            label    = "Ingresos",
                            value    = "$ ${clpFmt.format(stats.totalIncome.toLong())}",
                            subLabel = "/ $ ${clpFmt.format(stats.projectedIn.toLong())}",
                            modifier = Modifier.weight(1f),
                        )
                        SummaryCard(
                            label    = "Gastos",
                            value    = "$ ${clpFmt.format(stats.totalExpenses.toLong())}",
                            subLabel = "/ $ ${clpFmt.format(stats.projectedOut.toLong())}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Section header: Income
                item { SectionHeader("Ingresos") }
                items(state.items.filter { it.type == "IN" }) { item ->
                    BudgetItemRow(item, clpFmt)
                }

                // Section header: Expenses
                item { SectionHeader("Gastos") }
                items(state.items.filter { it.type == "OUT" }) { item ->
                    BudgetItemRow(item, clpFmt)
                }
            } else {
                item {
                    Text(
                        "Sin período activo. Crea uno en Períodos →",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    periods: List<com.moneymanager.data.entity.PeriodEntity>,
    selected: com.moneymanager.data.entity.PeriodEntity?,
    onSelect: (com.moneymanager.data.entity.PeriodEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: "Sin período",
            onValueChange = {},
            readOnly = true,
            label = { Text("Período") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            periods.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.name) },
                    onClick = { onSelect(p); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    subLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            subLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    HorizontalDivider()
}

@Composable
private fun BudgetItemRow(item: BudgetItemWithStats, fmt: NumberFormat) {
    val ratio = if (item.projectedAmount > 0) item.totalReal / item.projectedAmount else 0.0
    val barColor = when {
        ratio < 0.8  -> BudgetGreen
        ratio < 1.0  -> BudgetAmber
        else         -> BudgetRed
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.categoryName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "$ ${fmt.format(item.totalReal.toLong())} / $ ${fmt.format(item.projectedAmount.toLong())}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LinearProgressIndicator(
                progress = { ratio.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color    = barColor,
            )
        }
    }
}
