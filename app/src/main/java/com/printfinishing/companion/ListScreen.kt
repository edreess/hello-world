package com.printfinishing.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text

@Composable
fun ListScreen(
    specs: List<ProductSpec>,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit
) {
    if (specs.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No data")
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onAdd) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add entry")
            }
        }
    } else {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListHeader {
                    Text("Product specs")
                }
            }
            items(specs, key = { it.id }) { spec ->
                Chip(
                    onClick = { onOpen(spec.id) },
                    label = { Text(spec.ref.ifEmpty { "(no ref)" }) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(onClick = onAdd) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add entry")
                }
            }
        }
    }
}
