package com.moneymanager.ui.screens.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    nav: NavController,
    transactionId: Long,
    vm: TransactionFormViewModel = hiltViewModel(),
) {
    LaunchedEffect(transactionId) { vm.load(transactionId) }

    val saved       by vm.saved.collectAsState()
    val options     by vm.budgetItemOptions.collectAsState()
    val selected    by vm.selectedBudgetItem.collectAsState()
    val date        by vm.date.collectAsState()
    val amount      by vm.amount.collectAsState()
    val description by vm.description.collectAsState()
    val notes       by vm.notes.collectAsState()

    LaunchedEffect(saved) { if (saved) nav.popBackStack() }

    var biExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (transactionId == 0L) "Nueva transacción" else "Editar transacción") },
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
            // Budget Item picker
            ExposedDropdownMenuBox(expanded = biExpanded, onExpandedChange = { biExpanded = it }) {
                OutlinedTextField(
                    value = selected?.label ?: "Sin ítem (sin categorizar)",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Ítem presupuesto") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(biExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = biExpanded, onDismissRequest = { biExpanded = false }) {
                    DropdownMenuItem(
                        text    = { Text("Sin ítem") },
                        onClick = { vm.selectedBudgetItem.value = null; biExpanded = false },
                    )
                    options.forEach { opt ->
                        DropdownMenuItem(
                            text    = { Text(opt.label) },
                            onClick = { vm.selectedBudgetItem.value = opt; biExpanded = false },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = date, onValueChange = { vm.date.value = it },
                label = { Text("Fecha (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = amount, onValueChange = { vm.amount.value = it },
                label = { Text("Monto (CLP)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description, onValueChange = { vm.description.value = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = notes, onValueChange = { vm.notes.value = it },
                label = { Text("Notas (opcional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick  = { vm.save(transactionId) },
                modifier = Modifier.fillMaxWidth(),
                enabled  = date.isNotBlank() && amount.isNotBlank() && description.isNotBlank(),
            ) { Text("Guardar") }
        }
    }
}
