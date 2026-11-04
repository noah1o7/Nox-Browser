package de.noah.nachtfalter

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Schaut auf GitHub nach, ob es eine neuere Nachtfalter-Version gibt.
 * Neue Versionen liegen als "Release" auf github.com/noah1o7/nachtfalter-android
 * (Tag z. B. "v0.2.0", mit der APK als angehängter Datei).
 */
object Updater {

    private const val API = "https://api.github.com/repos/noah1o7/nachtfalter-android/releases/latest"

    data class NeueVersion(val version: String, val apkUrl: String, val notizen: String)

    /** Läuft im Hintergrund. Gibt null zurück, wenn es nichts Neues gibt (oder kein Internet da ist). */
    fun pruefen(aktuell: String): NeueVersion? {
        return try {
            val verbindung = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Nachtfalter-Android")
            }
            try {
                if (verbindung.responseCode != 200) return null
                val json = JSONObject(verbindung.inputStream.bufferedReader().use { it.readText() })
                val version = json.optString("tag_name").removePrefix("v").removePrefix("V")
                if (!istNeuer(version, aktuell)) return null
                val dateien = json.optJSONArray("assets") ?: return null
                for (i in 0 until dateien.length()) {
                    val datei = dateien.getJSONObject(i)
                    if (datei.optString("name").endsWith(".apk", ignoreCase = true)) {
                        return NeueVersion(version, datei.optString("browser_download_url"), json.optString("body"))
                    }
                }
                null
            } finally {
                verbindung.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** "0.10.0" ist neuer als "0.9.2" – Zahl für Zahl vergleichen */
    fun istNeuer(neu: String, alt: String): Boolean {
        val a = neu.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val b = alt.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
