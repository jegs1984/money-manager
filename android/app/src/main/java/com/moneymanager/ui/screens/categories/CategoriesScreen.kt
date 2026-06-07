package com.moneymanager.ui.screens.categories

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
import com.moneymanager.data.entity.CategoryEntity
import com.moneymanager.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(nav: NavController, vm: CategoriesViewModel = hiltViewModel()) {
    val categories by vm.categories.collectAsState()
    var toDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    // Group for display
    val grouped = remember(categories) { categories.groupBy { it.group } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorías") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(Routes.categoryForm()) }) {
                Icon(Icons.Default.Add, "Nueva categoría")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            grouped.forEach { (group, cats) ->
                item(key = "header_$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    HorizontalDivider()
                }
                items(cats, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { nav.navigate(Routes.categoryForm(c.id)) }) {
                            Icon(Icons.Default.Edit, "Editar", Modifier.size(18.dp))
                        }
                        IconButton(onClick = { toDelete = c }) {
                            Icon(Icons.Default.Delete, "Eliminar", Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title   = { Text("Eliminar categoría") },
            text    = { Text("¿Eliminar \"${c.name}\"?") },
            confirmButton = {
                TextButton(onClick = { vm.delete(c); toDelete = null }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancelar") }
            },
        )
    }
}
