package com.moneymanager.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneymanager.data.db.PeriodEntity
import com.moneymanager.data.repository.BudgetItemWithStats
import com.moneymanager.data.repository.PeriodStats
import com.moneymanager.ui.theme.*
import com.moneymanager.ui.viewmodel.DashboardViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToStaging: () -> Unit,
    onNavigateToCCStaging: () -> Unit,
    vm: DashboardViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Money Manager", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = Zinc900,
                    titleContentColor = Zinc200,
                ),
            )
        },
        containerColor = Zinc950,
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Emerald500)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier           = Modifier.fillMaxSize().padding(padding),
            contentPadding     = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Period selector ──────────────────────────────────────────────
            if (state.allPeriods.isNotEmpty()) {
                item {
                    PeriodSelector(
                        periods      = state.allPeriods,
                        activePeriod = state.activePeriod,
                        onSelect     = vm::selectPeriod,
                    )
                }
            }

            // ── KPI cards ────────────────────────────────────────────────────
            state.stats?.let { stats ->
                item { KpiRow(stats) }
            }

            // ── Import actions ───────────────────────────────────────────────
            item {
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick  = onNavigateToStaging,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Emerald500),
                    ) {
                        Icon(Icons.Default.AccountBalance, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Debit Import", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    OutlinedButton(
                        onClick  = onNavigateToCCStaging,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = Violet500),
                    ) {
                        Icon(Icons.Default.CreditCard, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("CC Import", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // ── Budget items ─────────────────────────────────────────────────
            if (state.items.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No budget items for this period.", color = Zinc400, fontSize = 13.sp)
                    }
                }
            } else {
                // Income
                val income = state.items.filter { it.item.type == "IN" }
                if (income.isNotEmpty()) {
                    item { SectionHeader("Income", Emerald500) }
                    items(income) { BudgetItemRow(it) }
                }
                // Expenses
                val expenses = state.items.filter { it.item.type == "OUT" }
                if (expenses.isNotEmpty()) {
                    item { SectionHeader("Expenses", Red500) }
                    items(expenses) { BudgetItemRow(it) }
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    periods: List<PeriodEntity>,
    activePeriod: PeriodEntity?,
    onSelect: (PeriodEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value            = activePeriod?.name ?: "Select period",
            onValueChange    = {},
            readOnly         = true,
            label            = { Text("Period", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
            trailingIcon     = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier         = Modifier.menuAnchor().fillMaxWidth(),
            colors           = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Emerald500,
                unfocusedBorderColor = Zinc700,
                focusedTextColor     = Zinc200,
                unfocusedTextColor   = Zinc200,
            ),
            textStyle        = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            periods.forEach { p ->
                DropdownMenuItem(
                    text    = { Text(p.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                    onClick = { onSelect(p); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun KpiRow(stats: PeriodStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KpiCard(
            label  = "Safe to Spend",
            value  = stats.safeToSpend,
            color  = if (stats.safeToSpend >= BigDecimal.ZERO) Emerald500 else Red500,
            modifier = Modifier.weight(1f),
        )
        KpiCard(
            label  = "Burn Rate",
            value  = null,
            text   = "${stats.burnRate}%",
            color  = if (stats.burnRate <= BigDecimal("80")) Emerald500 else Red500,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: BigDecimal?,
    text: String? = null,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Zinc900)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Zinc400)
            Spacer(Modifier.height(4.dp))
            Text(
                text  = text ?: formatClp(value ?: BigDecimal.ZERO),
                color = color,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text       = title.uppercase(),
        color      = color,
        fontSize   = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        modifier   = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun BudgetItemRow(item: BudgetItemWithStats) {
    val isIncome = item.item.type == "IN"
    val diffGood = item.diff >= BigDecimal.ZERO
    val diffColor = if (diffGood) Emerald500 else Red500

    Card(colors = CardDefaults.cardColors(containerColor = Zinc900)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.category.name, color = Zinc200, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                Text(item.category.group, color = Zinc400, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = formatClp(item.totalReal),
                    color      = if (isIncome) Emerald500 else Red500,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text   = "proj ${formatClp(item.item.projectedAmount.toBigDecimal())}",
                    color  = Zinc400,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text   = "${if (item.diff >= BigDecimal.ZERO) "+" else ""}${formatClp(item.diff)}",
                    color  = diffColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private fun formatClp(amount: BigDecimal): String {
    val fmt = NumberFormat.getNumberInstance(Locale("es", "CL"))
    fmt.maximumFractionDigits = 0
    return "$${fmt.format(amount)}"
}

private fun String.toBigDecimal() = java.math.BigDecimal(this)
