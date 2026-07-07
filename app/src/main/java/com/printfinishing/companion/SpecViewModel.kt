package com.printfinishing.companion

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel

class SpecViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SpecRepository(application)

    val specs = mutableStateListOf<ProductSpec>().apply { addAll(repository.load()) }

    fun get(id: String?): ProductSpec? = specs.find { it.id == id }

    fun add(spec: ProductSpec) {
        specs.add(spec)
        persist()
    }

    fun update(spec: ProductSpec) {
        val index = specs.indexOfFirst { it.id == spec.id }
        if (index >= 0) {
            specs[index] = spec
            persist()
        }
    }

    fun delete(id: String) {
        specs.removeAll { it.id == id }
        persist()
    }

    private fun persist() = repository.save(specs.toList())
}
