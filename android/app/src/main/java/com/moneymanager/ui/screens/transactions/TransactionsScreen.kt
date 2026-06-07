package com.moneymanager.ui.screens.transactions

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
import com.moneymanager.data.entity.TransactionEntity
import com.moneymanager.ui.navigation.Routes
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(nav: NavController, vm: TransactionsViewModel = hiltViewModel()) {
    val transactions by vm.transactions.collectAsState()
    val clpFmt = remember { NumberFormat.getNumberInstance(Locale("es", "CL")) }
    var toDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transacciones") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.transactionForm()) }) {
                Icon(Icons.Default.Add, "Nueva transacción")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(transactions, key = { it.id }) { tx ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tx.description, style = MaterialTheme.typography.bodyMedium)
                            Text(tx.date, style = MaterialTheme.typography.labelSmall)
                            tx.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        Text(
                            "$ ${clpFmt.format(tx.realAmount.toLong())}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { nav.navigate(Routes.transactionForm(tx.id)) }) {
                            Icon(Icons.Default.Edit, "Editar")
                        }
                        IconButton(onClick = { toDelete = tx }) {
                            Icon(Icons.Default.Delete, "Eliminar")
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title   = { Text("Eliminar transacción") },
            text    = { Text("¿Eliminar \"${tx.description}\"?") },
            confirmButton = {
                TextButton(onClick = { vm.delete(tx); toDelete = null }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}
