package com.shieldvpn.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Server(val id: String, val code: String, val country: String, val config: String) {
    val flag: String get() = Countries.flagOf(code)
}

object Countries {
    val list: List<Pair<String, String>> = listOf(
        "US" to "United States", "CA" to "Canada", "MX" to "Mexico", "BR" to "Brazil",
        "AR" to "Argentina", "GB" to "United Kingdom", "IE" to "Ireland", "DE" to "Germany",
        "FR" to "France", "NL" to "Netherlands", "BE" to "Belgium", "CH" to "Switzerland",
        "AT" to "Austria", "IT" to "Italy", "ES" to "Spain", "PT" to "Portugal",
        "SE" to "Sweden", "NO" to "Norway", "DK" to "Denmark", "FI" to "Finland",
        "PL" to "Poland", "CZ" to "Czechia", "RO" to "Romania", "HU" to "Hungary",
        "GR" to "Greece", "TR" to "Turkey", "IL" to "Israel", "SA" to "Saudi Arabia",
        "QA" to "Qatar", "BH" to "Bahrain", "OM" to "Oman", "EG" to "Egypt",
        "ZA" to "South Africa", "NG" to "Nigeria", "IN" to "India", "PK" to "Pakistan",
        "BD" to "Bangladesh", "SG" to "Singapore", "MY" to "Malaysia", "TH" to "Thailand",
        "ID" to "Indonesia", "PH" to "Philippines", "VN" to "Vietnam", "HK" to "Hong Kong",
        "TW" to "Taiwan", "JP" to "Japan", "KR" to "South Korea", "AU" to "Australia",
        "NZ" to "New Zealand"
    )

    fun flagOf(code: String): String =
        code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")

    /** Guess the country from a file name like "NL-FREE-12.conf" or "us-nyc-wg-301.conf". */
    fun guessIndex(fileName: String): Int {
        var prefix = fileName.uppercase().takeWhile { it.isLetter() }.take(2)
        if (prefix == "UK") prefix = "GB"
        return list.indexOfFirst { it.first == prefix }
    }
}

class ServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("servers", Context.MODE_PRIVATE)

    fun load(): MutableList<Server> {
        val arr = JSONArray(prefs.getString("list", "[]"))
        return MutableList(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Server(o.getString("id"), o.getString("code"), o.getString("country"), o.getString("config"))
        }
    }

    fun save(list: List<Server>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("code", it.code)
                    .put("country", it.country).put("config", it.config)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    var selectedId: String?
        get() = prefs.getString("selected", null)
        set(v) {
            prefs.edit().putString("selected", v).apply()
        }
}
