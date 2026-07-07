package com.printfinishing.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

@Composable
fun EditScreen(
    initial: ProductSpec?,
    onSave: (ProductSpec) -> Unit
) {
    var ref by remember { mutableStateOf(initial?.ref ?: "") }
    var finalFt by remember { mutableStateOf(initial?.finalFt ?: "") }
    var openFt by remember { mutableStateOf(initial?.openFt ?: "") }
    var hTolerance by remember { mutableStateOf(initial?.hTolerance ?: "") }
    var wTolerance by remember { mutableStateOf(initial?.wTolerance ?: "") }
    var qtyPerCartridge by remember { mutableStateOf(initial?.qtyPerCartridge ?: "") }
    var codeRotarySide by remember { mutableStateOf(initial?.codeRotarySide ?: "") }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ListHeader {
                Text(if (initial == null) "New entry" else "Edit entry")
            }
        }
        item { LabeledField("Ref", ref) { ref = it } }
        item { LabeledField("Final Ft", finalFt) { finalFt = it } }
        item { LabeledField("Open Ft", openFt) { openFt = it } }
        item { LabeledField("H Tolerance", hTolerance) { hTolerance = it } }
        item { LabeledField("W Tolerance", wTolerance) { wTolerance = it } }
        item { LabeledField("Qty per cartridge", qtyPerCartridge) { qtyPerCartridge = it } }
        item { LabeledField("Code rotary side", codeRotarySide) { codeRotarySide = it } }
        item {
            Button(
                onClick = {
                    onSave(
                        ProductSpec(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            ref = ref,
                            finalFt = finalFt,
                            openFt = openFt,
                            hTolerance = hTolerance,
                            wTolerance = wTolerance,
                            qtyPerCartridge = qtyPerCartridge,
                            codeRotarySide = codeRotarySide
                        )
                    )
                    if (initial == null) {
                        ref = ""
                        finalFt = ""
                        openFt = ""
                        hTolerance = ""
                        wTolerance = ""
                        qtyPerCartridge = ""
                        codeRotarySide = ""
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = "Save entry")
            }
        }
    }
}

@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
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
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.body2.copy(
                color = MaterialTheme.colors.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colors.primary),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}
