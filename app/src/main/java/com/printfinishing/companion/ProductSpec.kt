package com.printfinishing.companion

import java.util.UUID

data class ProductSpec(
    val id: String = UUID.randomUUID().toString(),
    val ref: String = "",
    val finalFt: String = "",
    val openFt: String = "",
    val hTolerance: String = "",
    val wTolerance: String = "",
    val qtyPerCartridge: String = "",
    val codeRotarySide: String = ""
)
