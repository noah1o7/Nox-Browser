package de.noah.nachtfalter

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

/** Ein offener Tab */
class Tab(val webView: WebView) {
    var titel = "Neuer Tab"
    var geblockt = 0
    var fortschritt = 100
    var ausstehendeUrl: String? = null
    var fehlerUrl: String? = null
    var fehlerSeiteLaedt = false
}

class MainActivity : AppCompatActivity() {

    companion object {
        private const val START_BASIS = "file:///android_asset/start.html"
        private val SUCHMASCHINEN = listOf(
            "DuckDuckGo" to "https://duckduckgo.com/?q=%s&kae=d",
            "Brave" to "https://search.brave.com/search?q=%s",
            "Ecosia" to "https://www.ecosia.org/search?q=%s",
            "Google" to "https://www.google.com/search?q=%s"
        )
    }

    // ---------- Oberfläche ----------
    private lateinit var wurzel: FrameLayout
    private lateinit var webBereich: FrameLayout
    private lateinit var ladeLinie: View
    private lateinit var adresse: EditText
    private lateinit var schild: ImageView
    private lateinit var schildZahl: TextView
    private lateinit var tabKnopf: FrameLayout
    private lateinit var tabZahl: TextView
    private lateinit var zurueckKnopf: ImageButton
    private lateinit var menueKnopf: ImageButton
    private lateinit var uebersicht: FrameLayout
    private lateinit var kartenScroll: ScrollView
    private lateinit var kartenListe: LinearLayout
    private lateinit var neuerTabKnopf: TextView
    private lateinit var fertigKnopf: TextView
    private lateinit var menueHuelle: FrameLayout
    private lateinit var menue: LinearLayout
    private lateinit var hinweisEbene: FrameLayout

    // ---------- Zustand ----------
    private val tabs = mutableListOf<Tab>()
    private var aktiv = -1
    private val aktuellerTab: Tab? get() = tabs.getOrNull(aktiv)

    private lateinit var prefs: SharedPreferences
    @Volatile private var werbeblockerAn = true
    private var dunkelAn = true
    private var suchmaschine = 0
    private var menueOffen = false

    private var vollbildView: View? = null
    private var vollbildCallback: WebChromeClient.CustomViewCallback? = null
    private var dateiCallback: ValueCallback<Array<Uri>>? = null
    private val dateiWahl = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { ergebnis ->
        dateiCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(ergebnis.resultCode, ergebnis.data))
        dateiCallback = null
    }

    private fun dp(x: Float) = x * resources.displayMetrics.density
    private fun dpi(x: Int) = (x * resources.displayMetrics.density).toInt()

    // =====================================================================
    //  Start
    // =====================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Die App zeichnet selbst hinter Status- und Navigationsleiste (alles dunkel)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        prefs = getSharedPreferences("nachtfalter", MODE_PRIVATE)
        werbeblockerAn = prefs.getBoolean("werbeblocker", true)
        dunkelAn = prefs.getBoolean("dunkel", true)
        suchmaschine = prefs.getInt("suchmaschine", 0).coerceIn(0, SUCHMASCHINEN.lastIndex)

        wurzel = findViewById(R.id.wurzel)
        webBereich = findViewById(R.id.webBereich)
        ladeLinie = findViewById(R.id.ladeLinie)
        adresse = findViewById(R.id.adresse)
        schild = findViewById(R.id.schild)
        schildZahl = findViewById(R.id.schildZahl)
        tabKnopf = findViewById(R.id.tabKnopf)
        tabZahl = findViewById(R.id.tabZahl)
        zurueckKnopf = findViewById(R.id.zurueckKnopf)
        menueKnopf = findViewById(R.id.menueKnopf)
        uebersicht = findViewById(R.id.uebersicht)
        kartenScroll = findViewById(R.id.kartenScroll)
        kartenListe = findViewById(R.id.kartenListe)
        neuerTabKnopf = findViewById(R.id.neuerTabKnopf)
        fertigKnopf = findViewById(R.id.fertigKnopf)
        menueHuelle = findViewById(R.id.menueHuelle)
        menue = findViewById(R.id.menue)
        hinweisEbene = findViewById(R.id.hinweisEbene)

        // Platz für Statusleiste, Navigationsleiste und Tastatur
        ViewCompat.setOnApplyWindowInsetsListener(wurzel) { v, insets ->
            val leisten = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val tastatur = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(leisten.left, leisten.top, leisten.right, maxOf(leisten.bottom, tastatur.bottom))
            WindowInsetsCompat.CONSUMED
        }

        richteLeisteEin()
        richteUebersichtEin()
        menueHuelle.setOnClickListener { menueZu() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = zurueckTaste()
        })

        // Tabs vom letzten Mal wiederherstellen
        val gespeichert = prefs.getString("tabs", null)
            ?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
        if (gespeichert.isEmpty()) {
            neuerTab(null, animiert = false)
        } else {
            gespeichert.forEach { neuerTab(it, animiert = false, sofortZeigen = false) }
            zeigeTab(prefs.getInt("aktiv", 0).coerceIn(0, tabs.lastIndex), animiert = false)
        }
        oeffneIntent(intent)
        updateSuchen(leise = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        oeffneIntent(intent)
    }

    /** Link aus einer anderen App öffnen (wenn Nachtfalter als Browser gewählt wurde) */
    private fun oeffneIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val url = intent.dataString ?: return
        if (url.startsWith("http://") || url.startsWith("https://")) neuerTab(url)
        intent.action = null
    }

    override fun onResume() {
        super.onResume()
        aktuellerTab?.webView?.onResume()
        // Kommt man aus "Apps installieren erlauben" zurück, geht das Update direkt weiter
        wartendeUpdateDatei?.let {
            if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) installiereUpdate(it)
        }
    }

    override fun onPause() {
        super.onPause()
        speichern()
        aktuellerTab?.webView?.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        tabs.forEach { it.webView.destroy() }
        tabs.clear()
        super.onDestroy()
    }

    private fun speichern() {
        val liste = tabs.joinToString("\n") { t ->
            val url = t.ausstehendeUrl ?: t.webView.url
            if (url == null || istStart(url) || url.startsWith("data:") || url == "start") "start" else url
        }
        prefs.edit().putString("tabs", liste).putInt("aktiv", aktiv).apply()
    }

    // =====================================================================
    //  Werkzeugleiste unten
    // =====================================================================

    private fun richteLeisteEin() {
        zurueckKnopf.setOnClickListener {
            if (menueOffen) { menueZu(); return@setOnClickListener }
            val wv = aktuellerTab?.webView ?: return@setOnClickListener
            if (wv.canGoBack()) wv.goBack()
        }
        zurueckKnopf.setOnLongClickListener {
            val wv = aktuellerTab?.webView
            if (wv != null && wv.canGoForward()) wv.goForward()
            true
        }
        tabKnopf.setOnClickListener { uebersichtAuf() }
        tabKnopf.setOnLongClickListener { neuerTab(null); true }
        // ☰ öffnet das Menü, ein zweiter Druck schließt es wieder
        menueKnopf.setOnClickListener { if (menueOffen) menueZu() else menueAuf() }
        schild.setOnClickListener {
            val n = aktuellerTab?.geblockt ?: 0
            hinweis(if (n == 1) "1 Werbe-Element blockiert" else "$n Werbe-Elemente blockiert")
        }

        adresse.setOnFocusChangeListener { _, fokus ->
            if (fokus) {
                menueZu()
                val url = aktuellerTab?.webView?.url.orEmpty()
                adresse.setText(if (istStart(url) || url.startsWith("data:")) "" else url)
                adresse.post { adresse.selectAll() }
            } else {
                aktualisiereLeiste()
            }
        }
        adresse.setOnEditorActionListener { _, aktion, ereignis ->
            val enter = ereignis?.keyCode == KeyEvent.KEYCODE_ENTER && ereignis.action == KeyEvent.ACTION_DOWN
            if (aktion == EditorInfo.IME_ACTION_GO || enter) {
                val text = adresse.text.toString().trim()
                val tab = aktuellerTab
                if (text.isNotEmpty() && tab != null) ladeUrl(tab, zuUrl(text))
                tastaturWeg()
                true
            } else false
        }
    }

    private fun tastaturWeg() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(adresse.windowToken, 0)
        adresse.clearFocus()
        wurzel.requestFocus()
    }

    private fun aktualisiereLeiste() {
        val tab = aktuellerTab ?: return
        tabZahl.text = if (tabs.size > 99) "99+" else tabs.size.toString()
        if (!adresse.hasFocus()) adresse.setText(anzeigeText(tab))
        zurueckKnopf.alpha = if (tab.webView.canGoBack()) 1f else 0.4f
        zeigeSchild()
    }

    private fun zeigeSchild() {
        val n = aktuellerTab?.geblockt ?: 0
        schild.visibility = if (werbeblockerAn) View.VISIBLE else View.GONE
        schildZahl.visibility = if (werbeblockerAn && n > 0) View.VISIBLE else View.GONE
        schildZahl.text = if (n > 999) "999+" else n.toString()
    }

    private fun zeigeFortschritt(p: Int) {
        ladeLinie.pivotX = 0f
        ladeLinie.animate().cancel()
        if (p < 100) {
            if (ladeLinie.visibility != View.VISIBLE) {
                ladeLinie.visibility = View.VISIBLE
                ladeLinie.scaleX = 0f
            }
            ladeLinie.alpha = 1f
            ladeLinie.animate().setStartDelay(0).scaleX(p / 100f).setDuration(220)
                .setInterpolator(DecelerateInterpolator()).start()
        } else if (ladeLinie.visibility == View.VISIBLE) {
            ladeLinie.animate().setStartDelay(0).scaleX(1f).alpha(0f).setDuration(320).withEndAction {
                ladeLinie.visibility = View.INVISIBLE
                ladeLinie.scaleX = 0f
            }.start()
        }
    }

    // =====================================================================
    //  Tabs
    // =====================================================================

    private fun istStart(url: String?) = url != null && url.startsWith(START_BASIS)

    private fun startUrl() = START_BASIS + "?s=" + Uri.encode(SUCHMASCHINEN[suchmaschine].second)

    private fun anzeigeText(tab: Tab): String {
        val geladen = tab.webView.url ?: tab.ausstehendeUrl ?: return ""
        // Die Fehlerseite ist eine data:-URL, angezeigt wird die Adresse, die nicht ging
        val url = if (geladen.startsWith("data:")) tab.fehlerUrl ?: return "" else geladen
        if (istStart(url) || url == "start") return ""
        return Uri.parse(url).host?.removePrefix("www.") ?: url
    }

    private fun zuUrl(eingabe: String): String {
        val t = eingabe.trim()
        if (Regex("^(https?|file|about|data):", RegexOption.IGNORE_CASE).containsMatchIn(t)) return t
        if (Regex("^localhost(:\\d+)?(/.*)?$", RegexOption.IGNORE_CASE).matches(t)) return "http://$t"
        if (!t.contains(' ') && Regex("^[^\\s/]+\\.[a-z]{2,}(:\\d+)?(/.*)?$", RegexOption.IGNORE_CASE).matches(t)) {
            return "https://$t"
        }
        return SUCHMASCHINEN[suchmaschine].second.replace("%s", Uri.encode(t))
    }

    private fun ladeUrl(tab: Tab, url: String) {
        tab.fehlerUrl = null
        tab.webView.loadUrl(if (url == "start") startUrl() else url)
    }

    private fun neuerTab(url: String?, animiert: Boolean = true, sofortZeigen: Boolean = true) {
        val tab = Tab(baueWebView())
        tab.ausstehendeUrl = url ?: "start"
        if (url != null && url != "start") tab.titel = Uri.parse(url).host?.removePrefix("www.") ?: url
        tabs.add(tab)
        if (sofortZeigen) {
            zeigeTab(tabs.lastIndex, animiert)
        }
    }

    private fun zeigeTab(index: Int, animiert: Boolean = true) {
        val alter = aktuellerTab
        aktiv = index
        val tab = tabs[index]
        if (alter != null && alter !== tab) alter.webView.onPause()

        webBereich.removeAllViews()
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        webBereich.addView(tab.webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tab.webView.onResume()

        tab.ausstehendeUrl?.let {
            tab.ausstehendeUrl = null
            ladeUrl(tab, it)
        }
        zeigeFortschritt(tab.fortschritt)
        aktualisiereLeiste()
        if (animiert) {
            webBereich.alpha = 0f
            webBereich.animate().alpha(1f).setDuration(120).start()
        }
    }

    private fun schliesseTab(tab: Tab) {
        val idx = tabs.indexOf(tab)
        if (idx < 0) return
        tabs.removeAt(idx)
        tab.webView.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        if (tabs.isEmpty()) {
            aktiv = -1
            neuerTab(null, animiert = false)
            return
        }
        val neu = when {
            idx < aktiv -> aktiv - 1
            idx == aktiv -> minOf(idx, tabs.lastIndex)
            else -> aktiv
        }
        aktiv = -1
        zeigeTab(neu, animiert = false)
        speichern()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun baueWebView(): WebView {
        val wv = WebView(this)
        wv.setBackgroundColor(ContextCompat.getColor(this, R.color.nf_bg))
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        wendeDunkelAn(wv)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = NachtfalterClient()
        wv.webChromeClient = NachtfalterChrome()
        wv.setDownloadListener { url, ua, cd, mime, _ -> herunterladen(url, ua, cd, mime) }
        return wv
    }

    /** Websites ohne eigenen Dark Mode automatisch abdunkeln (ab Android 13) */
    private fun wendeDunkelAn(wv: WebView) {
        if (Build.VERSION.SDK_INT >= 33) {
            wv.settings.setAlgorithmicDarkeningAllowed(dunkelAn)
        }
    }

    private fun tabVon(view: WebView?): Tab? = tabs.firstOrNull { it.webView === view }

    // =====================================================================
    //  WebView-Ereignisse
    // =====================================================================

    private inner class NachtfalterClient : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val schema = uri.scheme?.lowercase() ?: return false
            if (schema in setOf("http", "https", "file", "about", "data", "javascript", "blob")) return false
            // Andere Links (Telefon, Mail, WhatsApp, Play Store …) an passende Apps geben
            try {
                val i = if (schema == "intent") Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        else Intent(Intent.ACTION_VIEW, uri)
                i.addCategory(Intent.CATEGORY_BROWSABLE)
                i.component = null
                i.selector = null
                try {
                    startActivity(i)
                } catch (e: Exception) {
                    val ersatz = i.getStringExtra("browser_fallback_url")
                    if (ersatz != null) view.loadUrl(ersatz) else hinweis("Keine App für diesen Link gefunden")
                }
            } catch (e: Exception) {
                hinweis("Dieser Link lässt sich nicht öffnen")
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            val tab = tabVon(view) ?: return
            tab.geblockt = 0
            tab.fortschritt = 5
            if (tab === aktuellerTab) {
                aktualisiereLeiste()
                zeigeFortschritt(5)
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            val tab = tabVon(view) ?: return
            if (url.startsWith("data:")) tab.fehlerSeiteLaedt = false
            else if (!tab.fehlerSeiteLaedt) tab.fehlerUrl = null
            if (tab === aktuellerTab) aktualisiereLeiste()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (tabVon(view) === aktuellerTab) aktualisiereLeiste()
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            // Läuft im Hintergrund: nur Unterelemente blockieren, nie die Seite selbst
            if (!werbeblockerAn || request.isForMainFrame) return null
            if (!Werbeblocker.istWerbung(request.url.host)) return null
            runOnUiThread {
                val tab = tabVon(view) ?: return@runOnUiThread
                tab.geblockt++
                if (tab === aktuellerTab) zeigeSchild()
            }
            return Werbeblocker.leereAntwort()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val tab = tabVon(view) ?: return
            val url = request.url.toString()
            // Eigene dunkle Fehlerseite statt der weißen Standardseite
            val vorlage = try {
                assets.open("fehler.html").bufferedReader().use { it.readText() }
            } catch (e: Exception) { return }
            val html = vorlage
                .replace("{{URL}}", TextUtils.htmlEncode(url))
                .replace("{{HOST}}", TextUtils.htmlEncode(request.url.host ?: url))
                .replace("{{FEHLER}}", TextUtils.htmlEncode(error.description?.toString() ?: ""))
            tab.fehlerUrl = url
            tab.fehlerSeiteLaedt = true
            view.loadDataWithBaseURL(null, html, "text/html", "utf-8", url)
        }
    }

    private inner class NachtfalterChrome : WebChromeClient() {

        override fun onProgressChanged(view: WebView, p: Int) {
            val tab = tabVon(view) ?: return
            tab.fortschritt = p
            if (tab === aktuellerTab) zeigeFortschritt(p)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            tabVon(view)?.titel = title?.takeIf { it.isNotBlank() } ?: "Neuer Tab"
        }

        override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
            if (vollbildView != null) { callback.onCustomViewHidden(); return }
            vollbildView = view
            vollbildCallback = callback
            val decor = window.decorView as FrameLayout
            view.setBackgroundColor(Color.BLACK)
            decor.addView(view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            WindowInsetsControllerCompat(window, decor).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(250).start()
        }

        override fun onHideCustomView() = verlasseVollbild()

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: WebChromeClient.FileChooserParams
        ): Boolean {
            dateiCallback?.onReceiveValue(null)
            dateiCallback = filePathCallback
            return try {
                dateiWahl.launch(fileChooserParams.createIntent())
                true
            } catch (e: Exception) {
                dateiCallback = null
                false
            }
        }
    }

    private fun verlasseVollbild() {
        val v = vollbildView ?: return
        vollbildView = null
        (window.decorView as FrameLayout).removeView(v)
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        vollbildCallback?.onCustomViewHidden()
        vollbildCallback = null
    }

    // =====================================================================
    //  Zurück-Taste vom Handy
    // =====================================================================

    private fun zurueckTaste() {
        val wv = aktuellerTab?.webView
        when {
            vollbildView != null -> verlasseVollbild()
            menueOffen -> menueZu()
            uebersicht.visibility == View.VISIBLE -> uebersichtZu()
            adresse.hasFocus() -> tastaturWeg()
            wv != null && wv.canGoBack() -> wv.goBack()
            else -> moveTaskToBack(true)
        }
    }

    // =====================================================================
    //  Tab-Übersicht
    // =====================================================================

    private fun richteUebersichtEin() {
        fertigKnopf.setOnClickListener { uebersichtZu() }
        neuerTabKnopf.setOnClickListener {
            uebersichtZu()
            neuerTab(null)
        }
    }

    private fun baueKarten() {
        kartenListe.removeAllViews()
        tabs.forEachIndexed { i, tab ->
            val karte = layoutInflater.inflate(R.layout.tab_karte, kartenListe, false)
            val host = anzeigeText(tab)
            karte.findViewById<TextView>(R.id.karteTitel).text = tab.titel.ifBlank { "Neuer Tab" }
            karte.findViewById<TextView>(R.id.karteUrl).text = host.ifBlank { "Startseite" }
            karte.findViewById<TextView>(R.id.karteBuchstabe).text =
                (host.firstOrNull() ?: 'N').uppercaseChar().toString()
            karte.isActivated = i == aktiv
            karte.setOnClickListener {
                val idx = tabs.indexOf(tab)
                if (idx >= 0) {
                    uebersichtZu()
                    zeigeTab(idx)
                }
            }
            karte.findViewById<View>(R.id.karteZu).setOnClickListener {
                kartenListe.removeView(karte)
                schliesseTab(tab)
                // Markierung des aktiven Tabs neu setzen
                for (j in 0 until kartenListe.childCount) kartenListe.getChildAt(j).isActivated = j == aktiv
                if (kartenListe.childCount == 0) uebersichtZu()
            }
            kartenListe.addView(karte)
        }
    }

    private fun uebersichtAuf() {
        menueZu()
        tastaturWeg()
        baueKarten()
        uebersicht.animate().cancel()
        uebersicht.visibility = View.VISIBLE
        uebersicht.alpha = 0f
        uebersicht.animate().alpha(1f).setDuration(120).start()
        kartenScroll.post {
            val k = kartenListe.getChildAt(aktiv) ?: return@post
            kartenScroll.scrollTo(0, maxOf(0, k.top - dpi(120)))
        }
    }

    private fun uebersichtZu() {
        if (uebersicht.visibility != View.VISIBLE) return
        uebersicht.animate().cancel()
        uebersicht.animate().alpha(0f).setDuration(100).withEndAction {
            uebersicht.visibility = View.GONE
        }.start()
    }

    // =====================================================================
    //  Menü (☰)
    // =====================================================================

    private fun baueMenue() {
        menue.removeAllViews()
        val wv = aktuellerTab?.webView
        eintrag(R.drawable.ic_plus, "Neuer Tab") { neuerTab(null) }
        eintrag(R.drawable.ic_neuladen, "Neu laden") { neuLaden() }
        eintrag(R.drawable.ic_vor, "Vorwärts", aktiviert = wv?.canGoForward() == true) { wv?.goForward() }
        eintrag(R.drawable.ic_teilen, "Teilen") { teilen() }
        trenner()
        eintrag(R.drawable.ic_schild, "Werbeblocker", zustand = { if (werbeblockerAn) "An" else "Aus" }, schliessen = false) {
            werbeblockerAn = !werbeblockerAn
            prefs.edit().putBoolean("werbeblocker", werbeblockerAn).apply()
            zeigeSchild()
        }
        eintrag(R.drawable.ic_mond, "Websites dunkel", zustand = { if (dunkelAn) "An" else "Aus" }, schliessen = false) {
            dunkelAn = !dunkelAn
            prefs.edit().putBoolean("dunkel", dunkelAn).apply()
            tabs.forEach { wendeDunkelAn(it.webView) }
            aktuellerTab?.webView?.reload()
        }
        eintrag(R.drawable.ic_lupe, "Suche", zustand = { SUCHMASCHINEN[suchmaschine].first }, schliessen = false) {
            suchmaschine = (suchmaschine + 1) % SUCHMASCHINEN.size
            prefs.edit().putInt("suchmaschine", suchmaschine).apply()
            val tab = aktuellerTab
            if (tab != null && istStart(tab.webView.url)) ladeUrl(tab, "start")
        }
        trenner()
        eintrag(R.drawable.ic_claude, "Claude öffnen") { neuerTab("https://claude.ai") }
        eintrag(R.drawable.ic_update, "Nach Updates suchen", zustand = { "v" + eigeneVersion() }) { updateSuchen(leise = false) }
    }

    private fun eintrag(
        icon: Int,
        text: String,
        zustand: (() -> String)? = null,
        schliessen: Boolean = true,
        aktiviert: Boolean = true,
        aktion: () -> Unit
    ) {
        val zeile = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(14), 0, dpi(16), 0)
            setBackgroundResource(R.drawable.ripple_eintrag)
            isClickable = true
            alpha = if (aktiviert) 1f else 0.4f
        }
        val bild = ImageView(this).apply {
            setImageResource(icon)
            setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.nf_gedaempft))
        }
        zeile.addView(bild, LinearLayout.LayoutParams(dpi(22), dpi(22)))
        val beschriftung = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nf_text))
        }
        zeile.addView(beschriftung, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dpi(14)
        })
        var zustandText: TextView? = null
        if (zustand != null) {
            zustandText = TextView(this).apply {
                this.text = zustand()
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nf_gedaempft))
            }
            zeile.addView(zustandText)
        }
        zeile.setOnClickListener {
            if (!aktiviert) return@setOnClickListener
            aktion()
            if (schliessen) {
                menueZu()
            } else if (zustandText != null && zustand != null) {
                zustandText.text = zustand()
            }
        }
        menue.addView(zeile, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(48)))
    }

    private fun trenner() {
        val linie = View(this).apply { setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.nf_rand)) }
        menue.addView(linie, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(1)).apply {
            setMargins(0, dpi(4), 0, dpi(4))
        })
    }

    private fun menueAuf() {
        if (menueOffen) return
        menueOffen = true
        tastaturWeg()
        menueKnopf.setImageResource(R.drawable.ic_schliessen)
        baueMenue()
        menueHuelle.animate().cancel()
        menue.animate().cancel()
        menueHuelle.visibility = View.VISIBLE
        menueHuelle.alpha = 0f
        menueHuelle.animate().alpha(1f).setDuration(120).start()
        menue.alpha = 0f
        menue.translationY = dp(8f)
        menue.animate().alpha(1f).translationY(0f).setDuration(140)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun menueZu() {
        if (!menueOffen) return
        menueOffen = false
        menueKnopf.setImageResource(R.drawable.ic_menue)
        menueHuelle.animate().cancel()
        menueHuelle.animate().alpha(0f).setDuration(100).withEndAction {
            menueHuelle.visibility = View.GONE
        }.start()
    }

    private fun neuLaden() {
        val tab = aktuellerTab ?: return
        val fehler = tab.fehlerUrl
        if (fehler != null) ladeUrl(tab, fehler) else tab.webView.reload()
    }

    private fun teilen() {
        val tab = aktuellerTab ?: return
        val url = tab.webView.url
        if (url == null || istStart(url)) { hinweis("Die Startseite kann man nicht teilen"); return }
        val senden = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, tab.titel)
        }
        startActivity(Intent.createChooser(senden, "Teilen"))
    }

    // =====================================================================
    //  Downloads
    // =====================================================================

    private fun herunterladen(url: String, ua: String?, cd: String?, mime: String?) {
        if (!url.startsWith("http")) { hinweis("Diese Datei kann nicht geladen werden"); return }
        try {
            val name = URLUtil.guessFileName(url, cd, mime)
            val anfrage = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mime)
                if (ua != null) addRequestHeader("User-Agent", ua)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setTitle(name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(anfrage)
            hinweis("$name wird geladen")
        } catch (e: Exception) {
            hinweis("Download fehlgeschlagen")
        }
    }

    // =====================================================================
    //  Updates von GitHub
    // =====================================================================

    private var updateMeldung: View? = null
    private var updateDownloadId = -1L
    private var wartendeUpdateDatei: File? = null

    private fun eigeneVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
    } catch (e: Exception) { "0" }

    /** leise = beim Start, höchstens alle 6 Stunden und ohne Meldung, wenn alles aktuell ist */
    private fun updateSuchen(leise: Boolean) {
        if (leise && System.currentTimeMillis() - prefs.getLong("updatePruefung", 0L) < 6 * 60 * 60 * 1000L) return
        if (!leise) hinweis("Suche nach Updates …")
        Thread {
            val neu = Updater.pruefen(eigeneVersion())
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                prefs.edit().putLong("updatePruefung", System.currentTimeMillis()).apply()
                when {
                    neu != null -> zeigeUpdateMeldung(neu)
                    !leise -> hinweis("Nachtfalter ${eigeneVersion()} ist aktuell")
                }
            }
        }.start()
    }

    /** Bleibt über der Leiste stehen, bis man sie antippt oder schließt */
    private fun zeigeUpdateMeldung(neu: Updater.NeueVersion) {
        if (updateDownloadId != -1L) return
        updateMeldung?.let { hinweisEbene.removeView(it) }
        val meldung = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.hinweis_grund)
            setPadding(dpi(14), dpi(8), dpi(4), dpi(8))
            isClickable = true
        }
        val text = TextView(this).apply {
            this.text = "Nachtfalter ${neu.version} ist verfügbar\nTippen zum Aktualisieren"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nf_text))
            setLineSpacing(0f, 1.15f)
        }
        meldung.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val zu = ImageButton(this).apply {
            setImageResource(R.drawable.ic_schliessen)
            setBackgroundResource(R.drawable.ripple_rund)
            contentDescription = "Später"
        }
        meldung.addView(zu, LinearLayout.LayoutParams(dpi(40), dpi(40)))
        hinweisEbene.addView(meldung, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).apply { leftMargin = dpi(8); rightMargin = dpi(8); bottomMargin = dpi(64) })
        updateMeldung = meldung

        meldung.alpha = 0f
        meldung.animate().alpha(1f).setDuration(150).start()

        meldung.setOnClickListener {
            if (updateDownloadId != -1L) return@setOnClickListener
            text.text = "Lädt Nachtfalter ${neu.version} …"
            ladeUpdate(neu, text)
        }
        zu.setOnClickListener {
            if (updateDownloadId == -1L) updateMeldungWeg()
        }
    }

    private fun updateMeldungWeg() {
        val meldung = updateMeldung ?: return
        updateMeldung = null
        meldung.animate().alpha(0f).setDuration(120)
            .withEndAction { hinweisEbene.removeView(meldung) }.start()
    }

    private fun ladeUpdate(neu: Updater.NeueVersion, text: TextView) {
        try {
            val ordner = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: throw IllegalStateException()
            val datei = File(ordner, "nachtfalter-update.apk")
            if (datei.exists()) datei.delete()
            val anfrage = DownloadManager.Request(Uri.parse(neu.apkUrl)).apply {
                setTitle("Nachtfalter ${neu.version}")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, "nachtfalter-update.apk")
            }
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            updateDownloadId = dm.enqueue(anfrage)
            val pruefer = object : Runnable {
                override fun run() {
                    var status = -1
                    var geladen = 0L
                    var gesamt = -1L
                    dm.query(DownloadManager.Query().setFilterById(updateDownloadId))?.use { c ->
                        if (c.moveToFirst()) {
                            status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            geladen = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            gesamt = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        }
                    }
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            updateDownloadId = -1L
                            text.text = "Geladen – jetzt installieren"
                            installiereUpdate(datei)
                        }
                        DownloadManager.STATUS_FAILED, -1 -> {
                            updateDownloadId = -1L
                            text.text = "Download fehlgeschlagen – nochmal tippen"
                        }
                        else -> {
                            if (gesamt > 0) text.text = "Lädt Nachtfalter ${neu.version} … ${geladen * 100 / gesamt} %"
                            text.postDelayed(this, 300)
                        }
                    }
                }
            }
            text.postDelayed(pruefer, 300)
        } catch (e: Exception) {
            updateDownloadId = -1L
            text.text = "Download fehlgeschlagen – nochmal tippen"
        }
    }

    private fun installiereUpdate(datei: File) {
        // Android fragt einmalig, ob Nachtfalter Apps (also sich selbst) installieren darf
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            wartendeUpdateDatei = datei
            hinweis("Erlaube Nachtfalter einmal, Updates zu installieren")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } catch (e: Exception) { }
            return
        }
        wartendeUpdateDatei = null
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.dateien", datei)
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            updateMeldungWeg()
        } catch (e: Exception) {
            hinweis("Installation konnte nicht gestartet werden")
        }
    }

    // =====================================================================
    //  Kurze Hinweise
    // =====================================================================

    private fun hinweis(text: String) {
        val meldung = TextView(this).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.nf_text))
            setBackgroundResource(R.drawable.hinweis_grund)
            setPadding(dpi(16), dpi(10), dpi(16), dpi(10))
        }
        hinweisEbene.addView(meldung, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = dpi(68) })
        meldung.alpha = 0f
        meldung.animate().alpha(1f).setDuration(120).start()
        meldung.postDelayed({
            meldung.animate().alpha(0f).setDuration(150)
                .withEndAction { hinweisEbene.removeView(meldung) }.start()
        }, 2200)
    }
}
