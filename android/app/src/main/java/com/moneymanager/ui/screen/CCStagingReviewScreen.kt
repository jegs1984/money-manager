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
import com.moneymanager.data.db.StagingCCTransactionEntity
import com.moneymanager.ui.theme.*
import com.moneymanager.ui.viewmodel.CCStagingViewModel
import com.moneymanager.ui.viewmodel.CategoryViewModel
import com.moneymanager.ui.viewmodel.DupAction
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

// Zinc500 is not in the shared theme file — defined locally
private val Zinc500 = Color(0xFF71717A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CCStagingReviewScreen(
    onBack: () -> Unit,
    vm: CCStagingViewModel = hiltViewModel(),
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
                val fileName = uri.lastPathSegment ?: "statement.xls"
                vm.importFile(stream, fileName)
            }
        }
    }

    LaunchedEffect(state.committedCount) {
        state.committedCount?.let { count ->
            val removed = state.removedCount ?: 0
            val parts = buildList {
                if (count > 0)   add("$count CC transaction${if (count != 1) "s" else ""} committed")
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
                            "CC Staging Review",
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
                        fileLauncher.launch(Intent.createChooser(intent, "Select .xls CC statement"))
                    }) {
                        Icon(Icons.Default.Upload, "Import CC", tint = Violet500)
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
                                containerColor = Violet500,
                                contentColor   = Color.White,
                            ),
                        ) {
                            Text(
                                "Commit CC to Ledger",
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
                    CircularProgressIndicator(color = Violet500)
                }
                return@Scaffold
            }

            state.importError != null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Import error: ${state.importError}",
                        color      = Red500,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                return@Scaffold
            }

            state.rows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            "No pending CC transactions",
                            color      = Zinc400,
                            fontFamily = FontFamily.Monospace,
                        )
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                                fileLauncher.launch(Intent.createChooser(intent, "Select .xls CC statement"))
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Violet500,
                                contentColor   = Color.White,
                            ),
                        ) {
                            Icon(Icons.Default.Upload, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import CC Statement", fontFamily = FontFamily.Monospace)
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
                CCRowCard(
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
private fun CCRowCard(
    row: StagingCCTransactionEntity,
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

            // Row 1: date + amount + type badge
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
                    Surface(
                        color = if (row.type == "IN") Emerald900.copy(alpha = 0.6f) else Red900.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            row.type,
                            color      = amountColor,
                            fontSize   = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        "${if (row.type == "OUT") "-" else ""}${fmtClpCC(BigDecimal(row.amount))}",
                        color      = amountColor,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Text(row.description, color = Zinc200, fontSize = 12.sp, maxLines = 2)

            // Location + ref
            val hasMeta = !row.location.isNullOrBlank() || !row.refCode.isNullOrBlank()
            if (hasMeta) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.location?.let {
                        Text(it, color = Zinc500, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                    row.refCode?.let {
                        Text("Ref: $it", color = Zinc700, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Installment badge
            if ((row.installmentTotal ?: 0) > 1) {
                Surface(color = Zinc800, shape = MaterialTheme.shapes.small) {
                    Text(
                        "Cuota ${row.installmentCurrent}/${row.installmentTotal}" +
                            (row.installmentValue?.let { " · ${fmtClpCC(BigDecimal(it))}" } ?: ""),
                        color      = Violet500,
                        fontSize   = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
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

            // Category dropdown
            if (!isDuplicate || dupAction == DupAction.KEEP) {
                CCCategoryDropdown(
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
private fun CCCategoryDropdown(
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
                focusedBorderColor   = Violet500,
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

private fun fmtClpCC(amount: BigDecimal): String {
    val fmt = NumberFormat.getNumberInstance(Locale("es", "CL"))
    fmt.maximumFractionDigits = 0
    return "$${fmt.format(amount)}"
}
