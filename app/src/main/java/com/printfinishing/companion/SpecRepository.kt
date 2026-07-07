package com.printfinishing.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SpecRepository(context: Context) {

    private val prefs =
        context.getSharedPreferences("product_specs", Context.MODE_PRIVATE)

    fun load(): List<ProductSpec> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ProductSpec(
                    id = o.getString("id"),
                    ref = o.optString("ref"),
                    finalFt = o.optString("finalFt"),
                    openFt = o.optString("openFt"),
                    hTolerance = o.optString("hTolerance"),
                    wTolerance = o.optString("wTolerance"),
                    qtyPerCartridge = o.optString("qtyPerCartridge"),
                    codeRotarySide = o.optString("codeRotarySide")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(specs: List<ProductSpec>) {
        val array = JSONArray()
        specs.forEach { spec ->
            array.put(
                JSONObject()
                    .put("id", spec.id)
                    .put("ref", spec.ref)
                    .put("finalFt", spec.finalFt)
                    .put("openFt", spec.openFt)
                    .put("hTolerance", spec.hTolerance)
                    .put("wTolerance", spec.wTolerance)
                    .put("qtyPerCartridge", spec.qtyPerCartridge)
                    .put("codeRotarySide", spec.codeRotarySide)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "specs_json"
    }
}
