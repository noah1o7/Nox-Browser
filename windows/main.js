// Nox – Hauptprozess
// Hier passiert alles "hinter den Kulissen": Fenster, Tabs, Werbeblocker,
// Erweiterungen, eigene Skripte und Einstellungen.

const {
  app, BaseWindow, WebContentsView, ipcMain, session,
  nativeTheme, Menu, shell, dialog,
} = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const { pathToFileURL } = require('url');
const updater = require('./updater');

// ---------------------------------------------------------------------------
// Einstellungen
// ---------------------------------------------------------------------------

// Nox hieß früher "Nachtfalter". Damit Einstellungen, Logins und Erweiterungen erhalten
// bleiben, nutzt Nox weiter den alten Datenordner (Windows: %APPDATA%\Nachtfalter).
app.setPath('userData', path.join(app.getPath('appData'), 'Nachtfalter'));
const DATEN = app.getPath('userData');
const EINSTELLUNGEN_DATEI = path.join(DATEN, 'einstellungen.json');
const ERWEITERUNGEN_ORDNER = path.join(DATEN, 'erweiterungen');
const SKRIPTE_ORDNER = path.join(DATEN, 'eigene-skripte');

const STANDARD = {
  suchmaschine: 'duckduckgo',
  adblock: true,
  darkErzwingen: true, // Chromiums automatischer Dark Mode für alle Websites
};

function einstellungenLaden() {
  try {
    return { ...STANDARD, ...JSON.parse(fs.readFileSync(EINSTELLUNGEN_DATEI, 'utf8')) };
  } catch {
    return { ...STANDARD };
  }
}

let einstellungen = einstellungenLaden();

function einstellungenSpeichern() {
  fs.mkdirSync(DATEN, { recursive: true });
  fs.writeFileSync(EINSTELLUNGEN_DATEI, JSON.stringify(einstellungen, null, 2));
}

// Muss VOR app.ready passieren: macht helle Websites automatisch dunkel.
if (einstellungen.darkErzwingen) {
  app.commandLine.appendSwitch('enable-features', 'WebContentsForceDark');
}

const SUCHMASCHINEN = {
  duckduckgo: { name: 'DuckDuckGo', url: 'https://duckduckgo.com/?q=%s&kae=d' },
  brave: { name: 'Brave Search', url: 'https://search.brave.com/search?q=%s' },
  ecosia: { name: 'Ecosia', url: 'https://www.ecosia.org/search?q=%s' },
  google: { name: 'Google', url: 'https://www.google.com/search?q=%s' },
};

function suchUrl() {
  return (SUCHMASCHINEN[einstellungen.suchmaschine] || SUCHMASCHINEN.duckduckgo).url;
}

// Entscheidet: Ist die Eingabe eine Adresse oder eine Suche?
function eingabeZuUrl(text) {
  const t = (text || '').trim();
  if (!t) return null;
  if (/^(https?|file):\/\//i.test(t) || /^about:/i.test(t)) return t;
  if (/^localhost(:\d+)?(\/.*)?$/i.test(t)) return 'http://' + t;
  if (/^\d{1,3}(\.\d{1,3}){3}(:\d+)?(\/.*)?$/.test(t)) return 'http://' + t;
  if (!/\s/.test(t) && /^[^\s/]+\.[a-z]{2,}(:\d+)?(\/.*)?$/i.test(t)) return 'https://' + t;
  return suchUrl().replace('%s', encodeURIComponent(t));
}

// ---------------------------------------------------------------------------
// Geheimer privater Tab
//   - Öffnen: den eigenen Geheimcode (nur Ziffern) in die Adressleiste tippen
//   - Code festlegen: "nox:code 1234"   ändern: "nox:code ALT NEU"
//   - Der Code steht nirgends im Code, sondern nur als Hash in einstellungen.json
//   - Eigene, dauerhafte Sitzung: Logins bleiben, aber getrennt von den normalen Tabs
// ---------------------------------------------------------------------------

const NUR_ZIFFERN = /^\d{4,16}$/;
const PRIVAT_PARTITION = 'persist:nox-privat';
const PRIVAT_AUSZEIT = 60 * 1000; // so lange darf das Fenster minimiert sein

function codeHash(code, salz) {
  return crypto.pbkdf2Sync(code, salz, 100000, 32, 'sha256');
}

function istGeheimcode(text) {
  if (!NUR_ZIFFERN.test(text) || !einstellungen.geheimSalz || !einstellungen.geheimHash) return false;
  const versuch = codeHash(text, Buffer.from(einstellungen.geheimSalz, 'base64'));
  const gespeichert = Buffer.from(einstellungen.geheimHash, 'base64');
  return versuch.length === gespeichert.length && crypto.timingSafeEqual(versuch, gespeichert);
}

function geheimcodeSpeichern(code) {
  const salz = crypto.randomBytes(16);
  einstellungen.geheimSalz = salz.toString('base64');
  einstellungen.geheimHash = codeHash(code, salz).toString('base64');
  einstellungenSpeichern();
}

// Gibt eine Meldung zurück, wenn die Eingabe ein "nox:code"-Befehl war, sonst null
function codeBefehl(text) {
  const teile = text.trim().split(/\s+/);
  if ((teile[0] || '').toLowerCase() !== 'nox:code') return null;
  const gesetzt = !!einstellungen.geheimHash;
  if (!gesetzt && teile.length === 2) {
    if (!NUR_ZIFFERN.test(teile[1])) return 'Der Code muss aus 4 bis 16 Ziffern bestehen.';
    geheimcodeSpeichern(teile[1]);
    return 'Geheimcode gespeichert.';
  }
  if (gesetzt && teile.length === 3 && istGeheimcode(teile[1])) {
    if (!NUR_ZIFFERN.test(teile[2])) return 'Der Code muss aus 4 bis 16 Ziffern bestehen.';
    geheimcodeSpeichern(teile[2]);
    return 'Geheimcode geändert.';
  }
  if (!gesetzt) return 'Der Code muss aus 4 bis 16 Ziffern bestehen.';
  return 'Code nicht geändert.'; // falscher alter Code: bewusst nichts verraten
}

let privatSitzung = null;
function privatSession() {
  if (privatSitzung) return privatSitzung;
  privatSitzung = session.fromPartition(PRIVAT_PARTITION);
  privatSitzung.setUserAgent(sauberenUserAgent(privatSitzung.getUserAgent()));
  berechtigungenEinrichten(privatSitzung);
  if (einstellungen.adblock) blockerEin(privatSitzung);
  return privatSitzung;
}

// ---------------------------------------------------------------------------
// Interne Seiten (Startseite, Fehlerseite)
// ---------------------------------------------------------------------------

const START_DATEI = path.join(__dirname, 'ui', 'start.html');
const FEHLER_DATEI = path.join(__dirname, 'ui', 'fehler.html');
const START_URL = pathToFileURL(START_DATEI).href;
const FEHLER_URL = pathToFileURL(FEHLER_DATEI).href;

function startseiteLaden(wc) {
  wc.loadFile(START_DATEI, { query: { s: suchUrl() } });
}

// Was soll in der Adressleiste stehen?
function anzeigeUrl(u) {
  if (!u) return '';
  if (u.startsWith(START_URL)) return '';
  if (u.startsWith(FEHLER_URL)) {
    try { return new URL(u).searchParams.get('u') || ''; } catch { return ''; }
  }
  return u;
}

// ---------------------------------------------------------------------------
// Fenster und Tabs
// ---------------------------------------------------------------------------

const UI_HOEHE = 82; // Höhe von Tableiste + Werkzeugleiste in Pixeln
const HINTERGRUND = '#131416';

const LEISTE_BREITE = 420; // Breite des Claude-Fensters
const CLAUDE_URL = 'https://claude.ai/new';

let fenster = null;
let ui = null;
let seitenleiste = null;   // WebContentsView mit claude.ai (wird erst beim ersten Öffnen erstellt)
let leisteOffen = false;
const tabs = new Map();
let aktiverTab = null;
let naechsteId = 1;

function fensterErstellen() {
  fenster = new BaseWindow({
    width: 1280,
    height: 820,
    minWidth: 640,
    minHeight: 420,
    title: 'Nox',
    icon: path.join(__dirname, 'build', 'icon.png'),
    backgroundColor: HINTERGRUND,
    autoHideMenuBar: true,
  });

  ui = new WebContentsView({
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true,
    },
  });
  ui.setBackgroundColor(HINTERGRUND);
  fenster.contentView.addChildView(ui);
  ui.webContents.loadFile(path.join(__dirname, 'ui', 'index.html'));
  ui.webContents.once('did-finish-load', () => {
    einstellungenSenden();
    neuerTab();
  });

  fenster.on('resize', anordnen);
  // War das Fenster länger als eine Minute minimiert, geht der private Tab zu
  let minimiertSeit = 0;
  fenster.on('minimize', () => { minimiertSeit = Date.now(); });
  fenster.on('restore', () => {
    if (minimiertSeit && Date.now() - minimiertSeit > PRIVAT_AUSZEIT) schliessePrivat();
    minimiertSeit = 0;
  });
  fenster.on('closed', () => {
    for (const t of tabs.values()) {
      if (!t.view.webContents.isDestroyed()) t.view.webContents.close();
    }
    tabs.clear();
    if (seitenleiste && !seitenleiste.webContents.isDestroyed()) seitenleiste.webContents.close();
    seitenleiste = null;
    leisteOffen = false;
    fenster = null;
    ui = null;
  });
  anordnen();
}

// Wo das Claude-Fenster liegt, wenn es offen ist
function leistenRahmen() {
  const { width, height } = fenster.getContentBounds();
  // Bei schmalen Fenstern nimmt die Seitenleiste höchstens die Hälfte ein.
  const breite = Math.min(LEISTE_BREITE, Math.floor(width / 2));
  return {
    x: width - breite,
    y: UI_HOEHE,
    width: breite,
    height: Math.max(0, height - UI_HOEHE),
  };
}

function anordnen() {
  if (!fenster) return;
  const { width, height } = fenster.getContentBounds();
  ui.setBounds({ x: 0, y: 0, width, height: UI_HOEHE });
  const rahmen = leistenRahmen();
  const bereich = {
    x: 0,
    y: UI_HOEHE,
    width: leisteOffen ? rahmen.x : width,
    height: Math.max(0, height - UI_HOEHE),
  };
  for (const t of tabs.values()) t.view.setBounds(bereich);
  if (seitenleiste) seitenleiste.setBounds(rahmen);
}

// ---------------------------------------------------------------------------
// Claude-Seitenleiste
// ---------------------------------------------------------------------------

function seitenleisteErstellen() {
  // Eigene, dauerhafte Sitzung: Login bleibt gespeichert, und der Werbeblocker
  // der normalen Tabs mischt sich hier nicht ein.
  const ses = session.fromPartition('persist:claude');
  ses.setUserAgent(sauberenUserAgent(ses.getUserAgent()));
  berechtigungenEinrichten(ses);

  seitenleiste = new WebContentsView({
    webPreferences: { contextIsolation: true, sandbox: true, nodeIntegration: false, session: ses },
  });
  seitenleiste.setBackgroundColor('#16191f'); // passt zum angepassten Claude-Hintergrund
  seitenleiste.setVisible(false);
  fenster.contentView.addChildView(seitenleiste);

  const wc = seitenleiste.webContents;
  // Links aus dem Chat (z. B. Quellen) öffnen sich als normaler Tab.
  wc.setWindowOpenHandler(({ url: ziel }) => {
    if (/^https?:/i.test(ziel)) neuerTab(ziel);
    return { action: 'deny' };
  });
  wc.on('did-fail-load', (_e, code, beschreibung, u, istHauptframe) => {
    if (!istHauptframe || code === -3 || u.startsWith(FEHLER_URL)) return;
    wc.loadFile(FEHLER_DATEI, { query: { u, d: beschreibung, c: String(code) } });
  });
  // Nox-Design einschleusen (+ eigene "claude.ai.css" aus dem Skripte-Ordner)
  wc.on('dom-ready', () => {
    let host = '';
    try { host = new URL(wc.getURL()).hostname; } catch {}
    if (host === 'claude.ai' || host.endsWith('.claude.ai')) {
      try {
        const css = fs.readFileSync(path.join(__dirname, 'ui', 'claude.css'), 'utf8');
        wc.insertCSS(css, { cssOrigin: 'user' });
      } catch (fehler) {
        console.error('Claude-Design konnte nicht geladen werden:', fehler.message);
      }
    }
    eigeneSkripteEinfuegen(wc);
  });
  wc.loadURL(CLAUDE_URL);
}

function seitenleisteUmschalten() {
  if (!fenster) return;
  leisteOffen = !leisteOffen;
  if (leisteOffen && !seitenleiste) seitenleisteErstellen();
  if (seitenleiste) seitenleiste.setVisible(leisteOffen);
  anordnen();
  if (leisteOffen) {
    seitenleiste.webContents.focus();
  } else {
    const wc = aktivesWc();
    if (wc) wc.focus();
  }
  if (ui) ui.webContents.send('seitenleiste', leisteOffen);
  menueAktualisieren(); // Häkchen im Menü aktuell halten
}

function seitenleisteNeuLaden() {
  if (seitenleiste) seitenleiste.webContents.loadURL(CLAUDE_URL);
}

function neuerTab(url, { aktivieren = true, privat = false } = {}) {
  if (!fenster) return;
  const id = naechsteId++;
  const view = new WebContentsView({
    webPreferences: {
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
      ...(privat ? { session: privatSession() } : {}),
    },
  });
  view.setBackgroundColor(HINTERGRUND);

  const tab = { id, view, titel: privat ? 'Privat' : 'Neuer Tab', url: '', laedt: false, favicon: null, geblockt: 0, privat };
  tabs.set(id, tab);
  fenster.contentView.addChildView(view);
  view.setVisible(false);
  anordnen();

  const wc = view.webContents;
  wc.on('page-title-updated', (_e, titel) => {
    tab.titel = privat && titel === 'Neuer Tab' ? 'Privat' : titel;
    tabsSenden();
  });
  wc.on('did-start-loading', () => { tab.laedt = true; tabsSenden(); });
  wc.on('did-stop-loading', () => { tab.laedt = false; tabsSenden(); });
  wc.on('did-navigate', (_e, u) => {
    tab.url = u;
    tab.favicon = null;
    tab.geblockt = 0;
    tabsSenden();
  });
  wc.on('did-navigate-in-page', (_e, u, istHauptframe) => {
    if (istHauptframe) { tab.url = u; tabsSenden(); }
  });
  wc.on('page-favicon-updated', (_e, icons) => { tab.favicon = icons[0] || null; tabsSenden(); });
  wc.on('dom-ready', () => eigeneSkripteEinfuegen(wc));

  wc.on('did-fail-load', (_e, code, beschreibung, u, istHauptframe) => {
    // -3 = Abgebrochen (z. B. weil man schnell weitergeklickt hat) -> ignorieren
    if (!istHauptframe || code === -3 || u.startsWith(FEHLER_URL)) return;
    wc.loadFile(FEHLER_DATEI, { query: { u, d: beschreibung, c: String(code) } });
  });

  // Links, die ein neues Fenster öffnen wollen, landen in einem neuen Tab.
  // Im privaten Tab bleiben sie im privaten Tab – sonst würden sie in der normalen Sitzung landen.
  wc.setWindowOpenHandler(({ url: ziel, disposition }) => {
    if (/^https?:|^about:/i.test(ziel)) {
      if (privat) wc.loadURL(ziel);
      else neuerTab(ziel, { aktivieren: disposition !== 'background-tab' });
    }
    return { action: 'deny' };
  });

  if (url) wc.loadURL(url);
  else startseiteLaden(wc);

  if (aktivieren) tabAktivieren(id);
  else tabsSenden();
  return id;
}

function tabAktivieren(id) {
  const tab = tabs.get(id);
  if (!tab) return;
  aktiverTab = id;
  for (const t of tabs.values()) t.view.setVisible(t.id === id);
  // Im privaten Tab: keine Screenshots / Bildschirmaufnahmen des Fensters
  if (typeof fenster.setContentProtection === 'function') fenster.setContentProtection(!!tab.privat);
  menueAktualisieren();
  tabsSenden();
  if (!tab.url || tab.url.startsWith(START_URL)) adressleisteFokussieren();
  else tab.view.webContents.focus();
}

function tabSchliessen(id) {
  const tab = tabs.get(id);
  if (!tab || !fenster) return;
  const ids = [...tabs.keys()];
  const index = ids.indexOf(id);
  fenster.contentView.removeChildView(tab.view);
  tab.view.webContents.close();
  tabs.delete(id);
  if (tab.privat && privatSitzung) {
    // Logins (Cookies) bleiben, zwischengespeicherte Seiten nicht
    privatSitzung.cookies.flushStore().catch(() => {});
    privatSitzung.clearCache().catch(() => {});
  }

  if (tabs.size === 0) {
    fenster.close();
    return;
  }
  if (aktiverTab === id) {
    const rest = [...tabs.keys()];
    tabAktivieren(rest[Math.min(index, rest.length - 1)]);
  } else {
    tabsSenden();
  }
}

// Schließen über Tastenkürzel: Oberfläche spielt erst die Animation ab und meldet sich dann.
function tabSchliessenMitAnimation(id) {
  if (ui && !ui.webContents.isDestroyed()) ui.webContents.send('schliess-anfrage', id);
  else tabSchliessen(id);
}

function tabWechseln(richtung) {
  const ids = [...tabs.keys()];
  if (ids.length < 2) return;
  const i = ids.indexOf(aktiverTab);
  tabAktivieren(ids[(i + richtung + ids.length) % ids.length]);
}

function aktivesWc() {
  const t = tabs.get(aktiverTab);
  return t ? t.view.webContents : null;
}

function privaterTab() {
  for (const t of tabs.values()) if (t.privat) return t;
  return null;
}

function oeffnePrivat() {
  const vorhanden = privaterTab();
  if (vorhanden) tabAktivieren(vorhanden.id);
  else neuerTab(null, { privat: true });
}

function schliessePrivat() {
  const t = privaterTab();
  if (t) tabSchliessen(t.id);
}

function privatAllesLoeschen() {
  if (!fenster) return;
  dialog.showMessageBox(fenster, {
    type: 'warning',
    buttons: ['Löschen', 'Abbrechen'],
    defaultId: 1,
    cancelId: 1,
    title: 'Privaten Bereich löschen',
    message: 'Privaten Bereich löschen?',
    detail: 'Alle Logins, Cookies und Website-Daten im privaten Tab werden gelöscht. Das lässt sich nicht rückgängig machen.',
  }).then(async ({ response }) => {
    if (response !== 0) return;
    schliessePrivat();
    const ses = privatSession();
    await ses.clearStorageData().catch(() => {});
    await ses.clearCache().catch(() => {});
    if (fenster) dialog.showMessageBox(fenster, { type: 'info', message: 'Privater Bereich gelöscht.' });
  });
}

function verlauf(wc) {
  const h = wc.navigationHistory;
  return {
    zurueck: h ? h.canGoBack() : wc.canGoBack(),
    vor: h ? h.canGoForward() : wc.canGoForward(),
  };
}

// Schickt den aktuellen Zustand aller Tabs an die Oberfläche (gebündelt).
let sendenGeplant = null;
function tabsSenden() {
  if (sendenGeplant) return;
  sendenGeplant = setTimeout(() => {
    sendenGeplant = null;
    if (!ui || ui.webContents.isDestroyed()) return;
    const liste = [...tabs.values()].map((t) => {
      const wc = t.view.webContents;
      const v = wc.isDestroyed() ? { zurueck: false, vor: false } : verlauf(wc);
      return {
        id: t.id,
        titel: t.titel,
        url: anzeigeUrl(t.url),
        laedt: t.laedt,
        favicon: t.favicon,
        geblockt: t.geblockt,
        kannZurueck: v.zurueck,
        kannVor: v.vor,
        privat: !!t.privat,
      };
    });
    ui.webContents.send('tabs', { aktiv: aktiverTab, tabs: liste });
  }, 30);
}

function einstellungenSenden() {
  if (!ui) return;
  ui.webContents.send('einstellungen', {
    adblock: einstellungen.adblock,
    adblockBereit: !!blocker,
    suchmaschine: (SUCHMASCHINEN[einstellungen.suchmaschine] || SUCHMASCHINEN.duckduckgo).name,
  });
}

function adressleisteFokussieren() {
  if (!ui) return;
  ui.webContents.focus();
  ui.webContents.send('fokus-adresse');
}

// ---------------------------------------------------------------------------
// Werbeblocker (Ghostery-Engine mit EasyList, EasyPrivacy & Co.)
// ---------------------------------------------------------------------------

let blocker = null;

// Der Werbeblocker meldet beim Einschalten zwei globale Kanäle an. Bei mehreren Sitzungen
// (normal + privat) würde das doppelt passieren und abstürzen – darum diese Helfer.
const ADBLOCK_KANAELE = ['@ghostery/adblocker/inject-cosmetic-filters', '@ghostery/adblocker/is-mutation-observer-enabled'];

function blockerEin(ses) {
  if (!blocker || !ses || blocker.isBlockingEnabled(ses)) return;
  for (const k of ADBLOCK_KANAELE) ipcMain.removeHandler(k);
  blocker.enableBlockingInSession(ses);
}

function blockerAus(ses) {
  if (!blocker || !ses || !blocker.isBlockingEnabled(ses)) return;
  blocker.disableBlockingInSession(ses); // entfernt dabei auch die globalen Kanäle
  // Läuft er in einer anderen Sitzung weiter, die Kanäle wieder anmelden
  const andere = [session.defaultSession, privatSitzung].some((x) => x && x !== ses && blocker.isBlockingEnabled(x));
  if (andere) {
    ipcMain.handle(ADBLOCK_KANAELE[0], (e, url, msg) => blocker.onInjectCosmeticFilters(e, url, msg));
    ipcMain.handle(ADBLOCK_KANAELE[1], (e) => blocker.onIsMutationObserverEnabled(e));
  }
}

async function werbeblockerStarten() {
  try {
    const modul = await import('@ghostery/adblocker-electron');
    const ElectronBlocker = modul.ElectronBlocker || (modul.default && modul.default.ElectronBlocker);
    blocker = await ElectronBlocker.fromPrebuiltAdsAndTracking(fetch, {
      path: path.join(DATEN, 'werbeblocker-cache.bin'),
      read: fs.promises.readFile,
      write: fs.promises.writeFile,
    });
    blocker.on('request-blocked', (anfrage) => {
      for (const t of tabs.values()) {
        if (!t.view.webContents.isDestroyed() && t.view.webContents.id === anfrage.tabId) {
          t.geblockt++;
          tabsSenden();
          break;
        }
      }
    });
    if (einstellungen.adblock) {
      blockerEin(session.defaultSession);
      blockerEin(privatSitzung);
    }
    console.log('Werbeblocker aktiv.');
  } catch (fehler) {
    console.error('Werbeblocker konnte nicht geladen werden:', fehler);
  }
  einstellungenSenden();
}

function werbeblockerUmschalten() {
  einstellungen.adblock = !einstellungen.adblock;
  einstellungenSpeichern();
  if (blocker) {
    for (const ses of [session.defaultSession, privatSitzung]) {
      if (einstellungen.adblock) blockerEin(ses);
      else blockerAus(ses);
    }
  }
  einstellungenSenden();
  menueAktualisieren();
  const wc = aktivesWc();
  if (wc) wc.reload();
}

// ---------------------------------------------------------------------------
// Chrome-Erweiterungen (entpackt) und eigene Skripte
// ---------------------------------------------------------------------------

const geladeneErweiterungen = [];

async function erweiterungenLaden() {
  fs.mkdirSync(ERWEITERUNGEN_ORDNER, { recursive: true });
  const ses = session.defaultSession;
  const api = ses.extensions || ses; // neuere Electron-Versionen: ses.extensions
  for (const name of fs.readdirSync(ERWEITERUNGEN_ORDNER)) {
    const ordner = path.join(ERWEITERUNGEN_ORDNER, name);
    try {
      if (!fs.statSync(ordner).isDirectory()) continue;
      if (!fs.existsSync(path.join(ordner, 'manifest.json'))) continue;
      const erw = await api.loadExtension(ordner, { allowFileAccess: false });
      geladeneErweiterungen.push(erw.name);
      console.log('Erweiterung geladen:', erw.name);
    } catch (fehler) {
      console.error(`Erweiterung "${name}" konnte nicht geladen werden:`, fehler.message);
    }
  }
}

function skripteOrdnerVorbereiten() {
  fs.mkdirSync(SKRIPTE_ORDNER, { recursive: true });
  const liesmich = path.join(SKRIPTE_ORDNER, 'LIESMICH.txt');
  if (!fs.existsSync(liesmich)) {
    fs.writeFileSync(liesmich, [
      'Eigene Skripte für Nox',
      '======================',
      '',
      'Lege hier .js- oder .css-Dateien ab. Sie werden beim Laden jeder Seite eingefügt.',
      '',
      'Dateiname ohne Punkt (z. B. "alle.css")      -> gilt für ALLE Seiten',
      'Dateiname mit Domain (z. B. "youtube.com.css") -> gilt nur für diese Seite',
      '',
      'Änderungen wirken sofort beim nächsten Neuladen (F5), kein Neustart nötig.',
      '',
      'Beispiel "wikipedia.org.css":',
      '  body { font-size: 18px !important; }',
      '',
    ].join('\n'));
  }
}

function eigeneSkripteEinfuegen(wc) {
  let host = '';
  try { host = new URL(wc.getURL()).hostname; } catch { return; }
  if (!host) return;
  let dateien = [];
  try { dateien = fs.readdirSync(SKRIPTE_ORDNER); } catch { return; }
  for (const datei of dateien) {
    const endung = path.extname(datei).toLowerCase();
    if (endung !== '.js' && endung !== '.css') continue;
    const basis = path.basename(datei, endung);
    const gilt = !basis.includes('.') || host === basis || host.endsWith('.' + basis);
    if (!gilt) continue;
    try {
      const inhalt = fs.readFileSync(path.join(SKRIPTE_ORDNER, datei), 'utf8');
      if (endung === '.css') wc.insertCSS(inhalt);
      else wc.executeJavaScript(inhalt).catch((f) => console.error(`Fehler in ${datei}:`, f.message));
    } catch (fehler) {
      console.error(`Skript ${datei} konnte nicht gelesen werden:`, fehler.message);
    }
  }
}

// ---------------------------------------------------------------------------
// Berechtigungen (Kamera, Mikrofon, Standort ...) – erst nachfragen!
// ---------------------------------------------------------------------------

const erlaubteBerechtigungen = new Map(); // "host|berechtigung" -> true/false

function berechtigungenEinrichten(ses = session.defaultSession) {
  const harmlos = new Set(['fullscreen', 'clipboard-sanitized-write', 'pointerLock']);
  const namen = {
    media: 'Kamera / Mikrofon',
    geolocation: 'deinen Standort',
    notifications: 'Benachrichtigungen',
    'clipboard-read': 'die Zwischenablage',
    midi: 'MIDI-Geräte',
    midiSysex: 'MIDI-Geräte',
  };
  ses.setPermissionRequestHandler((wc, berechtigung, antwort, details) => {
    if (harmlos.has(berechtigung)) return antwort(true);
    let host = 'Diese Seite';
    try { host = new URL(details.requestingUrl || wc.getURL()).hostname; } catch {}
    const schluessel = `${host}|${berechtigung}`;
    if (erlaubteBerechtigungen.has(schluessel)) return antwort(erlaubteBerechtigungen.get(schluessel));
    if (!fenster) return antwort(false);
    dialog.showMessageBox(fenster, {
      type: 'question',
      buttons: ['Erlauben', 'Blockieren'],
      defaultId: 1,
      cancelId: 1,
      title: 'Berechtigung',
      message: `${host} möchte Zugriff auf ${namen[berechtigung] || berechtigung}.`,
      detail: 'Die Entscheidung gilt, bis du Nox schließt.',
    }).then(({ response }) => {
      const ok = response === 0;
      erlaubteBerechtigungen.set(schluessel, ok);
      antwort(ok);
    });
  });
}

// ---------------------------------------------------------------------------
// Menü (Tastenkürzel + ☰-Menü)
// ---------------------------------------------------------------------------

function neustartFragen() {
  if (!fenster) return;
  dialog.showMessageBox(fenster, {
    type: 'info',
    buttons: ['Jetzt neu starten', 'Später'],
    defaultId: 0,
    message: 'Diese Änderung wird nach einem Neustart von Nox aktiv.',
  }).then(({ response }) => {
    if (response === 0) { app.relaunch(); app.exit(0); }
  });
}

function einstellungsMenue() {
  const imPrivaten = !!(tabs.get(aktiverTab) || {}).privat;
  return [
    { label: 'Neuer Tab', accelerator: 'CmdOrCtrl+T', click: () => neuerTab() },
    ...(imPrivaten ? [
      { type: 'separator' },
      { label: 'Privaten Tab schließen', click: schliessePrivat },
      { label: 'Privaten Bereich löschen …', click: privatAllesLoeschen },
      { type: 'separator' },
    ] : []),
    { label: 'Claude-Seitenleiste', accelerator: 'CmdOrCtrl+Shift+C', type: 'checkbox', checked: leisteOffen, click: seitenleisteUmschalten },
    { label: 'Claude neu laden', enabled: !!seitenleiste, click: seitenleisteNeuLaden },
    { type: 'separator' },
    {
      label: 'Suchmaschine',
      submenu: Object.entries(SUCHMASCHINEN).map(([schluessel, s]) => ({
        label: s.name,
        type: 'radio',
        checked: einstellungen.suchmaschine === schluessel,
        click: () => {
          einstellungen.suchmaschine = schluessel;
          einstellungenSpeichern();
          einstellungenSenden();
          menueAktualisieren();
        },
      })),
    },
    {
      label: 'Werbeblocker',
      type: 'checkbox',
      checked: einstellungen.adblock,
      click: werbeblockerUmschalten,
    },
    {
      label: 'Dark Mode für alle Websites erzwingen',
      type: 'checkbox',
      checked: einstellungen.darkErzwingen,
      click: () => {
        einstellungen.darkErzwingen = !einstellungen.darkErzwingen;
        einstellungenSpeichern();
        menueAktualisieren();
        neustartFragen();
      },
    },
    { type: 'separator' },
    {
      label: geladeneErweiterungen.length
        ? `Erweiterungen (${geladeneErweiterungen.length} geladen)`
        : 'Erweiterungen (keine)',
      submenu: [
        ...geladeneErweiterungen.map((n) => ({ label: n, enabled: false })),
        ...(geladeneErweiterungen.length ? [{ type: 'separator' }] : []),
        { label: 'Erweiterungs-Ordner öffnen', click: () => shell.openPath(ERWEITERUNGEN_ORDNER) },
        { label: 'Neu starten, um Erweiterungen zu laden', click: () => { app.relaunch(); app.exit(0); } },
      ],
    },
    { label: 'Eigene Skripte-Ordner öffnen', click: () => shell.openPath(SKRIPTE_ORDNER) },
    { type: 'separator' },
    { label: 'Nach Updates suchen', click: () => updatesPruefen(false) },
    {
      label: 'Über Nox',
      click: () => fenster && dialog.showMessageBox(fenster, {
        type: 'info',
        message: `Nox ${app.getVersion()}`,
        detail: `Gebaut mit Electron ${process.versions.electron} (Chromium ${process.versions.chrome}).`,
      }),
    },
  ];
}

function updatesPruefen(leise) {
  // Updates gibt es nur für die installierte Version (nicht bei "npm start")
  if (!app.isPackaged) {
    if (!leise && fenster) dialog.showMessageBox(fenster, { type: 'info', message: 'Updates gibt es nur in der installierten Version von Nox.' });
    return;
  }
  updater.updateSuchen({ leise, fensterHolen: () => fenster });
}

function zoom(delta) {
  const wc = aktivesWc();
  if (!wc) return;
  wc.setZoomLevel(delta === 0 ? 0 : wc.getZoomLevel() + delta);
}

function menueAktualisieren() {
  const istMac = process.platform === 'darwin';
  const vorlage = [
    ...(istMac ? [{ role: 'appMenu' }] : []),
    { label: 'Nox', submenu: einstellungsMenue() },
    {
      label: 'Navigation',
      submenu: [
        { label: 'Tab schließen', accelerator: 'CmdOrCtrl+W', click: () => tabSchliessenMitAnimation(aktiverTab) },
        { label: 'Nächster Tab', accelerator: 'Ctrl+Tab', click: () => tabWechseln(1) },
        { label: 'Vorheriger Tab', accelerator: 'Ctrl+Shift+Tab', click: () => tabWechseln(-1) },
        { type: 'separator' },
        { label: 'Adressleiste', accelerator: 'CmdOrCtrl+L', click: adressleisteFokussieren },
        { label: 'Zurück', accelerator: istMac ? 'Cmd+[' : 'Alt+Left', click: () => navigation('zurueck') },
        { label: 'Vor', accelerator: istMac ? 'Cmd+]' : 'Alt+Right', click: () => navigation('vor') },
        { label: 'Neu laden', accelerator: 'CmdOrCtrl+R', click: () => navigation('neu') },
        { label: 'Neu laden', accelerator: 'F5', visible: false, click: () => navigation('neu') },
        { type: 'separator' },
        { label: 'Vergrößern', accelerator: 'CmdOrCtrl+Plus', click: () => zoom(0.5) },
        { label: 'Vergrößern', accelerator: 'CmdOrCtrl+=', visible: false, click: () => zoom(0.5) },
        { label: 'Verkleinern', accelerator: 'CmdOrCtrl+-', click: () => zoom(-0.5) },
        { label: 'Originalgröße', accelerator: 'CmdOrCtrl+0', click: () => zoom(0) },
        { type: 'separator' },
        { label: 'Entwicklertools', accelerator: 'F12', click: () => { const wc = aktivesWc(); if (wc) wc.toggleDevTools(); } },
      ],
    },
    { role: 'editMenu', label: 'Bearbeiten' },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(vorlage));
}

function navigation(aktion) {
  const wc = aktivesWc();
  if (!wc) return;
  const h = wc.navigationHistory;
  if (aktion === 'zurueck') h ? h.goBack() : wc.goBack();
  else if (aktion === 'vor') h ? h.goForward() : wc.goForward();
  else if (aktion === 'neu') wc.reload();
  else if (aktion === 'stopp') wc.stop();
}

// ---------------------------------------------------------------------------
// Nachrichten von der Oberfläche
// ---------------------------------------------------------------------------

ipcMain.on('navigiere', (_e, text) => {
  // Geheimcode oder "nox:code …"? Dann wird NICHT gesucht.
  const eingabe = (text || '').trim();
  const meldung = codeBefehl(eingabe);
  if (meldung) {
    if (ui) ui.webContents.send('adresse-leeren');
    if (fenster) dialog.showMessageBox(fenster, { type: 'info', message: meldung });
    return;
  }
  if (istGeheimcode(eingabe)) {
    if (ui) ui.webContents.send('adresse-leeren');
    oeffnePrivat();
    return;
  }
  const ziel = eingabeZuUrl(text);
  const wc = aktivesWc();
  if (!ziel || !wc) return;
  wc.loadURL(ziel);
  wc.focus();
});
ipcMain.on('neuer-tab', () => neuerTab());
ipcMain.on('tab-waehlen', (_e, id) => tabAktivieren(id));
ipcMain.on('tab-schliessen', (_e, id) => tabSchliessen(id));
ipcMain.on('nav', (_e, aktion) => navigation(aktion));
ipcMain.on('adblock-umschalten', werbeblockerUmschalten);
ipcMain.on('menue', (_e, x, y) => {
  if (!fenster) return;
  Menu.buildFromTemplate(einstellungsMenue()).popup({ window: fenster, x: Math.round(x), y: Math.round(y) });
});

ipcMain.on('seitenleiste-umschalten', seitenleisteUmschalten);

function sauberenUserAgent(ua) {
  return ua.split(' ').filter((teil) => !/^(Electron|nachtfalter|Nachtfalter|nox-browser|Nox)\//.test(teil)).join(' ');
}

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------

app.whenReady().then(async () => {
  nativeTheme.themeSource = 'dark'; // Fenster, Menüs und Websites bekommen "dunkel" gemeldet

  // "Electron" aus der Browser-Kennung entfernen, sonst zicken manche Seiten.
  const ses = session.defaultSession;
  ses.setUserAgent(sauberenUserAgent(ses.getUserAgent()));

  berechtigungenEinrichten();
  skripteOrdnerVorbereiten();
  await erweiterungenLaden();
  menueAktualisieren();
  fensterErstellen();
  werbeblockerStarten(); // läuft im Hintergrund, Fenster ist schon offen
  setTimeout(() => updatesPruefen(true), 8000); // kurz nach dem Start leise nach Updates schauen

  app.on('activate', () => { if (!fenster) fensterErstellen(); });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
