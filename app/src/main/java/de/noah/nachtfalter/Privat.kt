package de.noah.nachtfalter

import android.content.SharedPreferences
import android.util.Base64
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Der geheime private Tab.
 *
 * - Geöffnet wird er, indem man den eigenen Geheimcode (nur Ziffern) in die Suchleiste tippt.
 * - Der Code steht NICHT im Code hier (das Projekt ist öffentlich auf GitHub), sondern wird
 *   auf dem Handy festgelegt und nur verschlüsselt (als Hash) gespeichert:
 *     nox:code 1234         -> Code zum ersten Mal festlegen
 *     nox:code 1234 5678    -> Code von 1234 auf 5678 ändern
 * - Der private Tab hat einen eigenen Speicher (WebView-Profil): Logins bleiben dort erhalten,
 *   sind aber von den normalen Tabs komplett getrennt.
 */
object Privat {

    private const val PROFIL = "nox-privat"
    private const val SALZ = "geheim_salz"
    private const val HASH = "geheim_hash"
    private const val RUNDEN = 20_000

    private val nurZiffern = Regex("^\\d{4,16}$")

    /** Kann dieses Handy (bzw. seine WebView-Version) getrennte Speicher? */
    fun wirdUnterstuetzt(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    fun istCodeGesetzt(prefs: SharedPreferences) = prefs.getString(HASH, null) != null

    /** Prüft, ob die Eingabe in der Suchleiste der Geheimcode ist */
    fun istGeheimcode(prefs: SharedPreferences, eingabe: String): Boolean {
        if (!nurZiffern.matches(eingabe)) return false
        val salz = prefs.getString(SALZ, null) ?: return false
        val gespeichert = prefs.getString(HASH, null) ?: return false
        val versuch = hash(eingabe, Base64.decode(salz, Base64.NO_WRAP))
        return MessageDigest.isEqual(versuch, Base64.decode(gespeichert, Base64.NO_WRAP))
    }

    sealed class CodeErgebnis {
        object Gespeichert : CodeErgebnis()
        object Geaendert : CodeErgebnis()
        object Ungueltig : CodeErgebnis()
        object NichtGeaendert : CodeErgebnis()
    }

    /** Behandelt "nox:code …". Gibt null zurück, wenn die Eingabe gar kein Code-Befehl ist. */
    fun codeBefehl(prefs: SharedPreferences, eingabe: String): CodeErgebnis? {
        val teile = eingabe.trim().split(Regex("\\s+"))
        if (teile.firstOrNull()?.lowercase() != "nox:code") return null
        return when {
            // Noch kein Code: "nox:code NEU"
            !istCodeGesetzt(prefs) && teile.size == 2 ->
                if (nurZiffern.matches(teile[1])) { speichere(prefs, teile[1]); CodeErgebnis.Gespeichert }
                else CodeErgebnis.Ungueltig
            // Code ändern geht nur mit dem alten Code: "nox:code ALT NEU"
            istCodeGesetzt(prefs) && teile.size == 3 && istGeheimcode(prefs, teile[1]) ->
                if (nurZiffern.matches(teile[2])) { speichere(prefs, teile[2]); CodeErgebnis.Geaendert }
                else CodeErgebnis.Ungueltig
            !istCodeGesetzt(prefs) -> CodeErgebnis.Ungueltig
            // Falscher alter Code: bewusst nichts verraten
            else -> CodeErgebnis.NichtGeaendert
        }
    }

    private fun speichere(prefs: SharedPreferences, code: String) {
        val salz = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(SALZ, Base64.encodeToString(salz, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(hash(code, salz), Base64.NO_WRAP))
            .apply()
    }

    private fun hash(code: String, salz: ByteArray): ByteArray {
        val spec = PBEKeySpec(code.toCharArray(), salz, RUNDEN, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Der getrennte Speicher (Cookies, Logins, Website-Daten) des privaten Tabs */
    fun profil(): Profile = ProfileStore.getInstance().getOrCreateProfile(PROFIL)

    /** Muss direkt nach dem Erstellen der WebView aufgerufen werden, vor allem anderen */
    fun verbinde(wv: WebView) {
        profil()
        WebViewCompat.setProfile(wv, PROFIL)
    }

    /** Löscht alles im privaten Bereich: Logins, Cookies, gespeicherte Website-Daten */
    fun allesLoeschen() {
        val p = profil()
        p.cookieManager.removeAllCookies(null)
        p.cookieManager.flush()
        p.webStorage.deleteAllData()
    }
}
