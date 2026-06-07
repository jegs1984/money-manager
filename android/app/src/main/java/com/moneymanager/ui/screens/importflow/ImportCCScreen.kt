package com.moneymanager.ui.screens.importflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.moneymanager.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportCCScreen(nav: NavController, vm: ImportCCViewModel = hiltViewModel()) {
    val context        = LocalContext.current
    val state          by vm.state.collectAsState()
    val periods        by vm.periods.collectAsState()
    val selectedPeriod by vm.selectedPeriod.collectAsState()
    var periodExpanded by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        vm.parse(context.contentResolver, uri, uri.lastPathSegment)
    }

    LaunchedEffect(state) {
        if (state is ImportCCState.Staged && selectedPeriod != null) {
            nav.navigate(Routes.stagingCCReview(selectedPeriod!!.id)) {
                popUpTo(Routes.IMPORT_CC) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar Estado de Cuenta TC") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Selecciona el período y luego abre el archivo .xls del Estado de Cuenta " +
                "Nacional de Tarjeta de Crédito Scotiabank.",
                style = MaterialTheme.typography.bodyMedium,
            )

            ExposedDropdownMenuBox(expanded = periodExpanded, onExpandedChange = { periodExpanded = it }) {
                OutlinedTextField(
                    value = selectedPeriod?.name ?: "Sin período",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Período destino") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(periodExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = periodExpanded, onDismissRequest = { periodExpanded = false }) {
                    periods.forEach { p ->
                        DropdownMenuItem(
                            text    = { Text(p.name) },
                            onClick = { vm.selectedPeriod.value = p; periodExpanded = false },
                        )
                    }
                }
            }

            when (val s = state) {
                is ImportCCState.Idle -> {
                    Button(
                        onClick  = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled  = selectedPeriod != null,
                    ) {
                        Icon(Icons.Default.CreditCard, null, Modifier.padding(end = 8.dp))
                        Text("Abrir archivo .xls")
                    }
                }
                is ImportCCState.Parsing -> {
                    CircularProgressIndicator()
                    Text("Procesando estado de cuenta…")
                }
                is ImportCCState.Error -> {
                    Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { filePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Reintentar")
                    }
                }
                is ImportCCState.Staged -> CircularProgressIndicator()
            }
        }
    }
}
