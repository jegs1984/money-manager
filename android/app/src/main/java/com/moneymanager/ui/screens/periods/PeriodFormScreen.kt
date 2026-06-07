package com.moneymanager.ui.screens.periods

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodFormScreen(
    nav: NavController,
    periodId: Long,
    vm: PeriodFormViewModel = hiltViewModel(),
) {
    LaunchedEffect(periodId) { vm.load(periodId) }

    val saved     by vm.saved.collectAsState()
    val name      by vm.name.collectAsState()
    val startDate by vm.startDate.collectAsState()
    val endDate   by vm.endDate.collectAsState()
    val isActive  by vm.isActive.collectAsState()

    LaunchedEffect(saved) { if (saved) nav.popBackStack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (periodId == 0L) "Nuevo período" else "Editar período") },
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
            OutlinedTextField(
                value = name, onValueChange = { vm.name.value = it },
                label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = startDate, onValueChange = { vm.startDate.value = it },
                label = { Text("Fecha inicio (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = endDate, onValueChange = { vm.endDate.value = it },
                label = { Text("Fecha fin (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = isActive, onCheckedChange = { vm.isActive.value = it })
                Text("Activo (período por defecto)")
            }
            Button(
                onClick = { vm.save(periodId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && startDate.isNotBlank() && endDate.isNotBlank(),
            ) { Text("Guardar") }
        }
    }
}
