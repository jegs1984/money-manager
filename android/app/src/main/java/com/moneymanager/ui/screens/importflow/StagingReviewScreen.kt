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
fun StagingReviewScreen(
    nav: NavController,
    periodId: Long,
    vm: StagingReviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(periodId) { vm.setPeriod(periodId) }

    val state  by vm.uiState.collectAsState()
    val clpFmt = remember { NumberFormat.getNumberInstance(Locale("es", "CL")) }

    // Navigate to dashboard after commit
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
                title = { Text("Revisar transacciones (${state.rows.size})") },
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
                Text("No hay transacciones pendientes.")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.rows, key = { _, row -> row.staging.id }) { _, row ->
                StagingRowCard(
                    staging     = row,
                    categories  = state.categories,
                    clpFmt      = clpFmt,
                    onAssign    = { catId -> vm.assign(row.staging.id, catId) },
                )
            }

            item {
                Button(
                    onClick  = { vm.commit() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled  = state.rows.any { it.assignedCategoryId != null },
                ) {
                    val assignedCount = state.rows.count { it.assignedCategoryId != null }
                    Text("Confirmar $assignedCount transacciones al ledger")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StagingRowCard(
    staging: StagingRowUiState,
    categories: List<CategoryEntity>,
    clpFmt: NumberFormat,
    onAssign: (Long) -> Unit,
) {
    val s = staging.staging
    var expanded by remember { mutableStateOf(false) }
    val selectedCat = categories.firstOrNull { it.id == staging.assignedCategoryId }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(s.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${s.originalDate}  ·  ${s.type}  ·  $ ${clpFmt.format(s.amount.toLong())}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            // Category picker
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
