package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.PortfolioInfo

private sealed interface PickerDialog {
    data object Create : PickerDialog
    data class Rename(val info: PortfolioInfo) : PickerDialog
    data class Delete(val info: PortfolioInfo) : PickerDialog
}

/** انتخاب پورتفو در نوار بالا. هر پورتفو اکسل، دارایی‌ها، تاریخچه و سیگنال‌های خودش را دارد. */
@Composable
fun PortfolioPicker(
    portfolios: List<PortfolioInfo>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PickerDialog?>(null) }
    val selected = portfolios.firstOrNull { it.id == selectedId }

    TextButton(onClick = { expanded = true }) {
        Text("پورتفو: ${selected?.name ?: "—"}")
        Icon(Icons.Filled.ArrowDropDown, contentDescription = "انتخاب پورتفو")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        portfolios.forEach { p ->
            DropdownMenuItem(
                text = { Text(p.name) },
                leadingIcon = { if (p.id == selectedId) Icon(Icons.Filled.Check, contentDescription = null) },
                onClick = { expanded = false; onSelect(p.id) }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("پورتفوی جدید…") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = { expanded = false; dialog = PickerDialog.Create }
        )
        if (selected != null) {
            DropdownMenuItem(
                text = { Text("تغییر نام «${selected.name}»") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { expanded = false; dialog = PickerDialog.Rename(selected) }
            )
            if (portfolios.size > 1) {
                DropdownMenuItem(
                    text = { Text("حذف «${selected.name}»") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { expanded = false; dialog = PickerDialog.Delete(selected) }
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is PickerDialog.Create -> NameDialog("پورتفوی جدید", "", "ساخت", { dialog = null }) { onCreate(it); dialog = null }
        is PickerDialog.Rename -> NameDialog("تغییر نام پورتفو", d.info.name, "ذخیره", { dialog = null }) { onRename(d.info.id, it); dialog = null }
        is PickerDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("حذف پورتفو") },
            text = { Text("پورتفوی «${d.info.name}» با همه‌ی دارایی‌ها و تاریخچه‌اش حذف می‌شود. این کار برگشت‌پذیر نیست.") },
            confirmButton = { TextButton(onClick = { onDelete(d.info.id); dialog = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("نام پورتفو") }) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
