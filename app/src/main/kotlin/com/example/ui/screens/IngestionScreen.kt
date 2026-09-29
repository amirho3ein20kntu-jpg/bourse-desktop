package com.example.ui.screens

import com.example.platform.Uri
import com.example.platform.rememberLauncherForActivityResult
import com.example.platform.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AssetCategory
import com.example.data.model.PortfolioEntity
import com.example.data.parser.ExcelAndCsvParser
import com.example.ui.components.getCategoryColor
import com.example.ui.util.Formatters
import com.example.ui.viewmodel.PcmrUiState

@Composable
fun IngestionScreen(
    uiState: PcmrUiState,
    onImportPortfolio: (Uri) -> Unit,
    onAddOrUpdateItem: (Long, String, AssetCategory, Double, Long, Double) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    onExportBackup: (Uri) -> Unit = {},
    onRestoreBackup: (Uri) -> Unit = {},
    backupFileName: () -> String = { "pcmr-backup.json" }
) {
    var showEditDialog by remember { mutableStateOf<PortfolioEntity?>(null) }
    var isCreatingNew by rememberSaveable { mutableStateOf(false) }

    // File picker for brokerage portfolio excel
    val portfolioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onImportPortfolio(it) }
    }

    val backupSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { onExportBackup(it) }
    }

    val backupOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { onRestoreBackup(it) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. File Ingestion Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "بارگذاری فایل‌های اکسل و داده‌ها",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "سیستم به طور خودکار ستون‌های «نماد» و «ارزش روز» را شناسایی کرده و اعداد فارسی یا متن‌های کارگزاری را پاکسازی می‌کند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (uiState.isImporting) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("در حال پردازش و استخراج اطلاعات...")
                        }
                    } else {
                        // Single active upload: Brokerage Portfolio Excel
                        Button(
                            onClick = {
                                portfolioLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("آپلود فایل اکسل پورتفوی کارگزاری (XLSX / CSV)")
                        }
                    }
                }
            }
        }

        // 1-ب. پشتیبان‌گیری و بازیابی
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    0.8.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "پشتیبان‌گیری از داده‌ها",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "سبد، دسته‌بندی‌ها و تنظیمات شما فقط روی همین گوشی ذخیره می‌شوند. " +
                                "با پاک شدن اپ یا عوض کردن گوشی از بین می‌روند — یک فایل پشتیبان بگیرید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { backupSaveLauncher.launch(backupFileName()) },
                            enabled = !uiState.isImporting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ذخیره پشتیبان")
                        }

                        OutlinedButton(
                            onClick = { backupOpenLauncher.launch(arrayOf("application/json", "*/*")) },
                            enabled = !uiState.isImporting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بازیابی")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "توجه: بازیابی، سبد فعلی را کامل جایگزین می‌کند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // 2. Table of Holdings Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "دارایی‌های ثبت‌شده (${uiState.portfolioItems.size} نماد)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "مجموع ارزش: ${Formatters.formatToman(uiState.calculationResult.totalPv)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row {
                    Button(
                        onClick = { isCreatingNew = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نماد جدید")
                    }
                }
            }
        }

        // 3. هشدار دسته‌بندی حدسی
        if (uiState.guessedCategorySymbols.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "دستهٔ ${uiState.guessedCategorySymbols.size} نماد حدس زده شده",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "دستهٔ اشتباه، وزن هدف طبقات و در نتیجه سفارش‌های خرید و فروش را " +
                                        "جابه‌جا می‌کند. روی هر نماد نشان‌دار «ویرایش» را بزنید و دسته را " +
                                        "تأیید کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3-ب. هشدار نماد تکراری
        if (uiState.duplicateSymbols.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "نماد تکراری: ${uiState.duplicateSymbols.joinToString("، ")}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "این نماد(ها) بیش از یک ردیف دارند. محاسبات درست است چون " +
                                        "ارزش‌ها جمع می‌شوند، ولی معمولاً یعنی یک‌بار دستی و یک‌بار از " +
                                        "اکسل وارد شده‌اند. بهتر است ردیف اضافی را حذف کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 4. Items List
        items(uiState.portfolioItems, key = { it.id }) { item ->
            PortfolioItemRow(
                item = item,
                isCategoryGuessed = item.symbol.trim() in uiState.guessedCategorySymbols,
                onEdit = { showEditDialog = item },
                onDelete = { onDeleteItem(item.id) }
            )
        }
    }

    // Edit/Add Dialog
    if (showEditDialog != null || isCreatingNew) {
        val editingItem = showEditDialog
        PortfolioItemEditDialog(
            initialItem = editingItem,
            onDismiss = {
                showEditDialog = null
                isCreatingNew = false
            },
            onSave = { id, symbol, category, valueRial, quantity, price ->
                onAddOrUpdateItem(id, symbol, category, valueRial, quantity, price)
                showEditDialog = null
                isCreatingNew = false
            }
        )
    }
}

@Composable
fun PortfolioItemRow(
    item: PortfolioEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    /** دستهٔ این نماد حدس زده شده و کاربر تأییدش نکرده. */
    isCategoryGuessed: Boolean = false
) {
    val catColor = getCategoryColor(item.assetCategory)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(catColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.assetCategory.code,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = catColor
                        )
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.symbol,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        if (isCategoryGuessed) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "دستهٔ حدسی — تأیید کنید",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = if (isCategoryGuessed) {
                            "${item.assetCategory.persianName} (حدسی — تأیید کنید)"
                        } else {
                            "${item.assetCategory.persianName} • ${if (item.quantity > 0) "${Formatters.formatNumber(item.quantity)} واحد" else ""}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCategoryGuessed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatters.formatToman(item.currentValue),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (item.quantity > 0) {
                            "${Formatters.formatNumber(item.quantity)} واحد"
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "ویرایش", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioItemEditDialog(
    initialItem: PortfolioEntity?,
    onDismiss: () -> Unit,
    onSave: (Long, String, AssetCategory, Double, Long, Double) -> Unit
) {
    var symbol by rememberSaveable { mutableStateOf(initialItem?.symbol ?: "") }
    var category by rememberSaveable { mutableStateOf(initialItem?.assetCategory ?: AssetCategory.STOCK) }
    var valueTomanText by rememberSaveable {
        mutableStateOf(if (initialItem != null) (initialItem.currentValue / 10.0).toLong().toString() else "")
    }
    var quantityText by rememberSaveable {
        mutableStateOf(if (initialItem != null && initialItem.quantity > 0) initialItem.quantity.toString() else "")
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "افزودن نماد جدید" else "ویرایش نماد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text("نام نماد (مثلاً اهرم، طلا، کمند)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category Selector
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "${category.persianName} (${category.code})",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("نوع صندوق") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        for (cat in AssetCategory.entries) {
                            DropdownMenuItem(
                                text = { Text("${cat.persianName} (${cat.code})") },
                                onClick = {
                                    category = cat
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = valueTomanText,
                    onValueChange = { valueTomanText = it },
                    label = { Text("ارزش روز به تومان") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("تعداد واحد (اختیاری)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (symbol.isNotBlank()) {
                        val valueToman = ExcelAndCsvParser.parseNumber(valueTomanText)
                        val valueRial = valueToman * 10.0 // Store in Rial
                        val quantity = ExcelAndCsvParser.parseLong(quantityText)
                        val priceRial = if (quantity > 0) valueRial / quantity else 0.0
                        onSave(initialItem?.id ?: 0L, symbol, category, valueRial, quantity, priceRial)
                    }
                }
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}
