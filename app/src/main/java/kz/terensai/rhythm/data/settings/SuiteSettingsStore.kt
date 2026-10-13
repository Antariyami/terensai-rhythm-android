package kz.terensai.rhythm.data.settings

import android.content.Context
import org.json.JSONArray

/** Independent from TERENSAI: every parmaster maintains their own local suite names. */
object SuiteNames {
    const val MAX_COUNT = 50
    fun normalized(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(48)
    fun add(current: List<String>, input: String): List<String> {
        val name = normalized(input)
        if (name.isEmpty() || current.size >= MAX_COUNT ||
            current.any { it.equals(name, ignoreCase = true) }) return current
        return current + name
    }
    fun rename(current: List<String>, old: String, new: String): List<String> {
        val index = current.indexOf(old)
        val cleaned = normalized(new)
        if (index < 0 || cleaned.isEmpty() ||
            current.any { it != old && it.equals(cleaned,ignoreCase = true) }) return current
        return current.toMutableList().also { it[index] = cleaned }
    }
    fun remove(current: List<String>, name: String) = current.filterNot { it == name }
}

class SuiteSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("paritm_suite_settings_v1",Context.MODE_PRIVATE)
    private val key = "suite_names"
    fun load(): List<String> = try {
        val raw = JSONArray(prefs.getString(key, "[]") ?: "[]")
        var result = emptyList<String>()
        for (i in 0 until raw.length()) {
            result = SuiteNames.add(result, raw.optString(i))
        }
        result
    } catch (_: Exception) { emptyList() }

    private fun persist(names: List<String>): List<String> {
        val arr = JSONArray()
        names.forEach { arr.put(it) }
        check(prefs.edit().putString(key,arr.toString()).commit()) { "Не удалось сохранить список сьютов" }
        return names
    }
    fun add(name: String): List<String> = persist(SuiteNames.add(load(),name))
    fun rename(old: String, replacement: String): List<String> =
        persist(SuiteNames.rename(load(),old,replacement))
    fun remove(name: String): List<String> = persist(SuiteNames.remove(load(),name))
}
