package com.moneymanager.ui.screens.periods

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
import com.moneymanager.data.entity.PeriodEntity
import com.moneymanager.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodsScreen(nav: NavController, vm: PeriodsViewModel = hiltViewModel()) {
    val periods by vm.periods.collectAsState()
    var toDelete by remember { mutableStateOf<PeriodEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Períodos") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.periodForm()) }) {
                Icon(Icons.Default.Add, "Nuevo período")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(periods, key = { it.id }) { p ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                if (p.isActive) Badge { Text("activo") }
                            }
                            Text("${p.startDate}  →  ${p.endDate}", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { nav.navigate(Routes.periodForm(p.id)) }) {
                            Icon(Icons.Default.Edit, "Editar")
                        }
                        IconButton(onClick = { toDelete = p }) {
                            Icon(Icons.Default.Delete, "Eliminar")
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title   = { Text("Eliminar período") },
            text    = { Text("¿Eliminar \"${p.name}\"? Se eliminarán todos sus ítems y transacciones.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(p); toDelete = null }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancelar") }
            },
        )
    }
}
