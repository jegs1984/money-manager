package com.moneymanager.ui.screens.categories

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
fun CategoryFormScreen(
    nav: NavController,
    categoryId: Long,
    vm: CategoryFormViewModel = hiltViewModel(),
) {
    LaunchedEffect(categoryId) { vm.load(categoryId) }

    val saved by vm.saved.collectAsState()
    val name  by vm.name.collectAsState()
    val group by vm.group.collectAsState()

    LaunchedEffect(saved) { if (saved) nav.popBackStack() }

    var groupExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (categoryId == 0L) "Nueva categoría" else "Editar categoría") },
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

            ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = it }) {
                OutlinedTextField(
                    value = group, onValueChange = {},
                    readOnly = true,
                    label = { Text("Grupo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(groupExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                    CATEGORY_GROUPS.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g) },
                            onClick = { vm.group.value = g; groupExpanded = false },
                        )
                    }
                }
            }

            Button(
                onClick = { vm.save(categoryId) },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank(),
            ) { Text("Guardar") }
        }
    }
}
