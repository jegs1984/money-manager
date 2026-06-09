package com.moneymanager.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneymanager.data.db.CategoryEntity
import com.moneymanager.data.db.StagingTransactionEntity
import com.moneymanager.ui.theme.*
import com.moneymanager.ui.viewmodel.CategoryViewModel
import com.moneymanager.ui.viewmodel.DupAction
import com.moneymanager.ui.viewmodel.StagingViewModel
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StagingReviewScreen(
    onBack: () -> Unit,
    vm: StagingViewModel = hiltViewModel(),
    categoryVm: CategoryViewModel = hiltViewModel(),
) {
    val state      by vm.uiState.collectAsStateWithLifecycle()
    val categories by categoryVm.categories.collectAsStateWithLifecycle()
    val context    = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val stream   = context.contentResolver.openInputStream(uri) ?: return@let
                val fileName = uri.lastPathSegment ?: "statement.dat"
                vm.importFile(stream, fileName)
            }
        }
    }

    LaunchedEffect(state.committedCount) {
        state.committedCount?.let { count ->
            val removed = state.removedCount ?: 0
            val parts = buildList {
                if (count > 0)   add("$count transaction${if (count != 1) "s" else ""} committed")
                if (removed > 0) add("$removed duplicate${if (removed != 1) "s" else ""} removed")
            }
            snackbarHostState.showSnackbar(parts.joinToString(", ") + ".")
            vm.clearResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Staging Review",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${state.rows.size} pending${if (state.duplicateIds.isNotEmpty()) " · ${state.duplicateIds.size} duplicates" else ""}",
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 11.sp,
                            color      = if (state.duplicateIds.isNotEmpty()) Amber400 else Zinc400,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Zinc400)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                        fileLauncher.launch(Intent.createChooser(intent, "Select .dat statement"))
                    }) {
                        Icon(Icons.Default.Upload, "Import", tint = Emerald500)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = Zinc900,
                    titleContentColor = Zinc200,
                ),
            )
        },
        snackbarHost   = { SnackbarHost(snackbarHostState) },
        containerColor = Zinc950,
        bottomBar = {
            if (state.rows.isNotEmpty()) {
                Surface(color = Zinc900, tonalElevation = 4.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Rows without category are skipped",
                            fontSize   = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color      = Zinc700,
                        )
                        Button(
                            onClick = vm::commit,
                            enabled = !state.isLoading,
                            colors  = ButtonDefaults.buttonColors(
                                containerColor = Emerald500,
                                contentColor   = Color.Black,
                            ),
                        ) {
                            Text(
                                "Commit to Ledger",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Emerald500)
                }
                return@Scaffold
            }

            state.importError != null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Import error: ${state.importError}", color = Red500, fontFamily = FontFamily.Monospace)
                }
                return@Scaffold
            }

            state.rows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("No pending debit transactions", color = Zinc400, fontFamily = FontFamily.Monospace)
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                fileLauncher.launch(Intent.createChooser(intent, "Select .dat statement"))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald500,
                                contentColor   = Color.Black,
                            ),
                        ) {
                            Icon(Icons.Default.Upload, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import Statement", fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                return@Scaffold
            }
        }

        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            if (state.duplicateIds.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .background(Amber950.copy(alpha = 0.3f))
                            .border(1.dp, Amber400.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Amber400, modifier = Modifier.size(16.dp))
                        Text(
                            "${state.duplicateIds.size} row${if (state.duplicateIds.size != 1) "s" else ""} already exist in the active period ledger.",
                            color      = Amber400,
                            fontSize   = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            items(state.rows, key = { it.id }) { row ->
                val isDuplicate = row.id in state.duplicateIds
                StagingRow(
                    row                = row,
                    isDuplicate        = isDuplicate,
                    dupAction          = state.duplicateActions[row.id] ?: DupAction.KEEP,
                    selectedCatId      = state.categoryAssignments[row.id],
                    categories         = categories,
                    onDupAction        = { vm.setDupAction(row.id, it) },
                    onCategorySelected = { vm.setCategory(row.id, it) },
                )
            }
        }
    }
}

@Composable
private fun StagingRow(
    row: StagingTransactionEntity,
    isDuplicate: Boolean,
    dupAction: DupAction,
    selectedCatId: Long?,
    categories: List<CategoryEntity>,
    onDupAction: (DupAction) -> Unit,
    onCategorySelected: (Long) -> Unit,
) {
    val amountColor = if (row.type == "IN") Emerald500 else Red500
    val dateFmt     = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(
                1.dp,
                if (isDuplicate) Amber400.copy(alpha = 0.5f) else Zinc800,
                MaterialTheme.shapes.medium,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isDuplicate) Amber950.copy(alpha = 0.12f) else Zinc900,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

            // Header: date + amount + type badge
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    row.originalDate.format(dateFmt),
                    color      = Zinc400,
                    fontSize   = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    TypeBadge(row.type)
                    Text(
                        "${if (row.type == "OUT") "-" else ""}${fmtClp(BigDecimal(row.amount))}",
                        color      = amountColor,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Text(row.description, color = Zinc200, fontSize = 12.sp, maxLines = 2)

            row.docNumber?.let {
                Text("Doc: $it", color = Zinc700, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }

            // Duplicate controls
            if (isDuplicate) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Default.Warning, null, tint = Amber400, modifier = Modifier.size(12.dp))
                    Text("Already in ledger", color = Amber400, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = dupAction == DupAction.KEEP,
                            onClick  = { onDupAction(DupAction.KEEP) },
                            colors   = RadioButtonDefaults.colors(selectedColor = Emerald500),
                        )
                        Text("Keep", color = Zinc200, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = dupAction == DupAction.REMOVE,
                            onClick  = { onDupAction(DupAction.REMOVE) },
                            colors   = RadioButtonDefaults.colors(selectedColor = Red500),
                        )
                        Text(
                            "Remove from staging",
                            color      = Zinc200,
                            fontSize   = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            // Category dropdown — shown for normal rows and "keep" duplicates
            if (!isDuplicate || dupAction == DupAction.KEEP) {
                DebitCategoryDropdown(
                    categories = categories,
                    selectedId = selectedCatId,
                    onSelected = onCategorySelected,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebitCategoryDropdown(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = categories.firstOrNull { it.id == selectedId }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value         = selected?.name ?: "Select category…",
            onValueChange = {},
            readOnly      = true,
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier      = Modifier.menuAnchor().fillMaxWidth(),
            textStyle     = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Emerald500,
                unfocusedBorderColor = Zinc700,
                focusedTextColor     = Zinc200,
                unfocusedTextColor   = if (selected == null) Zinc700 else Zinc200,
            ),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (categories.isEmpty()) {
                DropdownMenuItem(
                    text    = { Text("No categories found", color = Zinc400, fontSize = 12.sp) },
                    onClick = {},
                    enabled = false,
                )
            }
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(cat.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text(cat.group, fontSize = 10.sp, color = Zinc400, fontFamily = FontFamily.Monospace)
                        }
                    },
                    onClick = { onSelected(cat.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: String) {
    val bg    = if (type == "IN") Emerald900.copy(alpha = 0.6f) else Red900.copy(alpha = 0.6f)
    val color = if (type == "IN") Emerald500 else Red500
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(
            type,
            color      = color,
            fontSize   = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun fmtClp(amount: BigDecimal): String {
    val fmt = NumberFormat.getNumberInstance(Locale("es", "CL"))
    fmt.maximumFractionDigits = 0
    return "$${fmt.format(amount)}"
}
