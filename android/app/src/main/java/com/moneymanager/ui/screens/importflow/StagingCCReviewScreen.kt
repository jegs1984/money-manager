package com.moneymanager.ui.screens.importflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.moneymanager.data.entity.CategoryEntity
import com.moneymanager.ui.navigation.Routes
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StagingCCReviewScreen(
    nav: NavController,
    periodId: Long,
    vm: StagingCCReviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(periodId) { vm.setPeriod(periodId) }

    val state  by vm.uiState.collectAsState()
    val clpFmt = remember { NumberFormat.getNumberInstance(Locale("es", "CL")) }

    LaunchedEffect(state.committed) {
        if (state.committed != null) {
            nav.navigate(Routes.DASHBOARD) {
                popUpTo(Routes.DASHBOARD) { inclusive = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Revisar TC (${state.rows.size})")
                        if (state.cardHolder != null)
                            Text("${state.cardHolder} · ${state.cardNumber}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.commit() },
                        enabled = state.rows.any { it.assignedCategoryId != null },
                    ) {
                        Icon(Icons.Default.Check, "Confirmar")
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No hay transacciones TC pendientes.")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.rows, key = { _, row -> row.staging.id }) { _, row ->
                CCRowCard(
                    row        = row,
                    categories = state.categories,
                    clpFmt     = clpFmt,
                    onAssign   = { catId -> vm.assign(row.staging.id, catId) },
                )
            }

            item {
                Button(
                    onClick  = { vm.commit() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled  = state.rows.any { it.assignedCategoryId != null },
                ) {
                    val n = state.rows.count { it.assignedCategoryId != null }
                    Text("Confirmar $n transacciones al ledger")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CCRowCard(
    row: StagingCCRowUiState,
    categories: List<CategoryEntity>,
    clpFmt: NumberFormat,
    onAssign: (Long) -> Unit,
) {
    val s = row.staging
    var expanded by remember { mutableStateOf(false) }
    val selectedCat = categories.firstOrNull { it.id == row.assignedCategoryId }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.description, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.originalDate, style = MaterialTheme.typography.labelSmall)
                Text(s.type, style = MaterialTheme.typography.labelSmall)
                Text("$ ${clpFmt.format(s.amount.toLong())}", style = MaterialTheme.typography.labelSmall)
                if (s.installmentCurrent != null)
                    Text("cuota ${s.installmentCurrent}/${s.installmentTotal}", style = MaterialTheme.typography.labelSmall)
            }
            s.location?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedCat?.name ?: "— Omitir —",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Categoría") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text    = { Text("${cat.group} / ${cat.name}") },
                            onClick = { onAssign(cat.id); expanded = false },
                        )
                    }
                }
            }
        }
    }
}
