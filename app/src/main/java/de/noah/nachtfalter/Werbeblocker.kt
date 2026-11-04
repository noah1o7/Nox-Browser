package de.noah.nachtfalter

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Einfacher Werbeblocker: blockiert Anfragen an bekannte Werbe- und Tracking-Server.
 * Geprüft wird der Hostname samt allen übergeordneten Domains
 * (z. B. "pagead2.googlesyndication.com" -> "googlesyndication.com").
 */
object Werbeblocker {

    private val gesperrt = hashSetOf(
        // Google-Werbung & -Tracking
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "adservice.google.com", "adservice.google.de", "imasdk.googleapis.com",
        // Werbenetzwerke
        "adnxs.com", "adsrvr.org", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
        "amazon-adsystem.com", "pubmatic.com", "rubiconproject.com", "openx.net",
        "casalemedia.com", "advertising.com", "adform.net", "smartadserver.com",
        "yieldlab.net", "teads.tv", "media.net", "zemanta.com", "bidswitch.net",
        "3lift.com", "sharethrough.com", "spotxchange.com", "adcolony.com",
        "serving-sys.com", "adition.com", "popads.net", "propellerads.com",
        "exoclick.com", "trafficjunky.net", "revcontent.com", "mgid.com",
        "ads.yahoo.com", "adtech.de", "stroeerdigitalgroup.de", "adscale.de",
        "contextweb.com", "lijit.com", "sovrn.com", "indexww.com", "33across.com",
        "gumgum.com", "undertone.com", "adsafeprotected.com", "doubleverify.com",
        "moatads.com", "adroll.com", "quantcount.com", "ad.360yield.com",
        "ads-twitter.com", "ads.linkedin.com", "an.facebook.com",
        // Tracking & Statistik
        "scorecardresearch.com", "quantserve.com", "hotjar.com", "mouseflow.com",
        "clarity.ms", "mixpanel.com", "chartbeat.com", "chartbeat.net", "ioam.de",
        "xiti.com", "omtrdc.net", "demdex.net", "krxd.net", "bluekai.com",
        "agkn.com", "rlcdn.com", "everesttech.net", "tapad.com", "mathtag.com",
        "crwdcntrl.net", "exelator.com", "eyeota.net", "addthis.com", "onesignal.com"
    )

    fun istWerbung(host: String?): Boolean {
        var h = host?.lowercase() ?: return false
        while (true) {
            if (h in gesperrt) return true
            val punkt = h.indexOf('.')
            if (punkt < 0) return false
            h = h.substring(punkt + 1)
        }
    }

    /** Leere Antwort statt der Werbung */
    fun leereAntwort() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
