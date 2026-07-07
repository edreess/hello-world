package com.printfinishing.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog

@Composable
fun DetailScreen(
    spec: ProductSpec,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ListHeader {
                Text(spec.ref.ifEmpty { "(no ref)" })
            }
        }
        item { LabeledValue("Ref", spec.ref) }
        item { LabeledValue("Final Ft", spec.finalFt) }
        item { LabeledValue("Open Ft", spec.openFt) }
        item { LabeledValue("H Tolerance", spec.hTolerance) }
        item { LabeledValue("W Tolerance", spec.wTolerance) }
        item { LabeledValue("Qty per cartridge", spec.qtyPerCartridge) }
        item { LabeledValue("Code rotary side", spec.codeRotarySide) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit entry")
                }
                Button(
                    onClick = { showDeleteConfirmation = true },
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete entry")
                }
            }
        }
    }

    Dialog(
        showDialog = showDeleteConfirmation,
        onDismissRequest = { showDeleteConfirmation = false }
    ) {
        Alert(
            title = {
                Text("Delete ${spec.ref.ifEmpty { "this entry" }}?")
            },
            negativeButton = {
                Button(
                    onClick = { showDeleteConfirmation = false },
                    colors = ButtonDefaults.secondaryButtonColors()
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Cancel")
                }
            },
            positiveButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = "Confirm delete")
                }
            }
        ) {
            Text("This cannot be undone.")
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.primary
        )
        Text(
            text = value.ifEmpty { "—" },
            style = MaterialTheme.typography.body2
        )
    }
}
