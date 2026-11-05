package de.noah.nachtfalter

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Schaut auf GitHub nach, ob es eine neuere Nox-Version gibt.
 * Neue Versionen liegen im Ordner "updates" von github.com/noah1o7/Nox-Browser:
 *   updates/version.json  -> {"version": "0.2.0", "apk": "<Link zur APK>", "notizen": "..."}
 *   updates/Nachtfalter.apk
 */
object Updater {

    private const val ADRESSE = "https://raw.githubusercontent.com/noah1o7/Nox-Browser/main/updates/version.json"

    data class NeueVersion(val version: String, val apkUrl: String, val notizen: String)

    /**
     * Läuft im Hintergrund. Gibt null zurück, wenn es nichts Neues gibt.
     * Wirft einen Fehler, wenn GitHub nicht erreichbar ist – damit die App nicht fälschlich "aktuell" meldet.
     */
    fun pruefen(aktuell: String): NeueVersion? {
        // Zeitstempel anhängen, damit keine alte, zwischengespeicherte Antwort kommt
        val verbindung = (URL(ADRESSE + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            useCaches = false
            setRequestProperty("User-Agent", "Nox-Android")
        }
        try {
            if (verbindung.responseCode != 200) throw IllegalStateException("GitHub antwortet mit ${verbindung.responseCode}")
            val json = JSONObject(verbindung.inputStream.bufferedReader().use { it.readText() })
            val version = json.optString("version").removePrefix("v")
            val apk = json.optString("apk")
            if (apk.isBlank() || !istNeuer(version, aktuell)) return null
            return NeueVersion(version, apk, json.optString("notizen"))
        } finally {
            verbindung.disconnect()
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
