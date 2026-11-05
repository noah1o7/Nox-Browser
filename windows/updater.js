// Nox – Updates von GitHub
//
// Schaut unter github.com/noah1o7/Nox-Browser/releases nach, ob es eine neuere
// Windows-Version gibt. Ein Release muss dafür eine Datei "Nox-Setup-X.Y.Z.exe" enthalten.
// Wird ein Update gefunden, lädt Nox den Installer herunter, installiert ihn still
// und startet danach neu – Einstellungen und Logins bleiben erhalten.

const { app, dialog } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { spawn } = require('child_process');
const { Readable } = require('stream');
const { pipeline } = require('stream/promises');

const REPO = 'noah1o7/Nox-Browser';
const API = `https://api.github.com/repos/${REPO}/releases?per_page=15`;
const DATEI_MUSTER = /^Nox-Setup-(\d+(?:\.\d+){1,3})\.exe$/i;
// Nur Downloads aus den eigenen Releases werden akzeptiert
const ERLAUBT = new RegExp(`^https://github\\.com/${REPO.replace('/', '\\/')}/releases/download/`, 'i');

/** "0.12.0" ist neuer als "0.11.3" – Zahl für Zahl vergleichen */
function istNeuer(neu, alt) {
  const a = neu.split('.').map((x) => parseInt(x, 10) || 0);
  const b = alt.split('.').map((x) => parseInt(x, 10) || 0);
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const x = a[i] || 0;
    const y = b[i] || 0;
    if (x !== y) return x > y;
  }
  return false;
}

/** Sucht das neueste Release mit einer Windows-Installationsdatei */
async function neuesteVersion() {
  const antwort = await fetch(API, {
    headers: { 'User-Agent': 'Nox-Windows', Accept: 'application/vnd.github+json' },
  });
  if (!antwort.ok) throw new Error(`GitHub antwortet mit ${antwort.status}`);
  return waehleUpdate(await antwort.json());
}

/** Wählt aus der Release-Liste die neueste gültige Windows-Datei aus */
function waehleUpdate(releases) {
  let beste = null;
  for (const r of releases) {
    if (r.draft || r.prerelease) continue;
    for (const datei of r.assets || []) {
      const treffer = DATEI_MUSTER.exec(datei.name);
      if (!treffer || !ERLAUBT.test(datei.browser_download_url)) continue;
      const version = treffer[1];
      if (!beste || istNeuer(version, beste.version)) {
        beste = { version, url: datei.browser_download_url, groesse: datei.size, name: datei.name, notizen: r.body || '' };
      }
    }
  }
  return beste;
}

async function herunterladen(update, fenster) {
  const ziel = path.join(os.tmpdir(), update.name);
  const antwort = await fetch(update.url, { headers: { 'User-Agent': 'Nox-Windows' } });
  if (!antwort.ok || !antwort.body) throw new Error(`Download fehlgeschlagen (${antwort.status})`);
  const gesamt = Number(antwort.headers.get('content-length')) || update.groesse || 0;
  let geladen = 0;
  const quelle = Readable.fromWeb(antwort.body);
  quelle.on('data', (stueck) => {
    geladen += stueck.length;
    if (gesamt && fenster && !fenster.isDestroyed()) fenster.setProgressBar(geladen / gesamt);
  });
  await pipeline(quelle, fs.createWriteStream(ziel));
  if (fenster && !fenster.isDestroyed()) fenster.setProgressBar(-1);
  if (update.groesse && fs.statSync(ziel).size !== update.groesse) {
    fs.rmSync(ziel, { force: true });
    throw new Error('Die heruntergeladene Datei ist unvollständig.');
  }
  return ziel;
}

let laeuft = false;

/**
 * leise = beim Start: nur melden, wenn es wirklich etwas Neues gibt
 * fensterHolen = Funktion, die das aktuelle Fenster liefert (kann null sein)
 */
async function updateSuchen({ leise, fensterHolen }) {
  if (laeuft) return;
  laeuft = true;
  const fenster = () => fensterHolen();
  const meldung = (opts) => (fenster() ? dialog.showMessageBox(fenster(), opts) : dialog.showMessageBox(opts));
  try {
    let update;
    try {
      update = await neuesteVersion();
    } catch {
      if (!leise) await meldung({ type: 'warning', message: 'Konnte nicht nach Updates suchen.', detail: 'Bist du online?' });
      return;
    }
    if (!update || !istNeuer(update.version, app.getVersion())) {
      if (!leise) await meldung({ type: 'info', message: `Nox ${app.getVersion()} ist aktuell.` });
      return;
    }
    const { response } = await meldung({
      type: 'info',
      buttons: ['Jetzt aktualisieren', 'Später'],
      defaultId: 0,
      cancelId: 1,
      title: 'Update verfügbar',
      message: `Nox ${update.version} ist verfügbar.`,
      detail: 'Nox lädt das Update herunter, installiert es und startet danach neu. Deine Tabs werden dabei geschlossen.',
    });
    if (response !== 0) return;

    let datei;
    try {
      datei = await herunterladen(update, fenster());
    } catch (fehler) {
      const f = fenster();
      if (f && !f.isDestroyed()) f.setProgressBar(-1);
      await meldung({ type: 'error', message: 'Das Update konnte nicht geladen werden.', detail: fehler.message });
      return;
    }
    // /S = still installieren, --force-run = Nox danach wieder starten
    spawn(datei, ['/S', '--force-run'], { detached: true, stdio: 'ignore' }).unref();
    app.quit();
  } finally {
    laeuft = false;
  }
}

module.exports = { updateSuchen, istNeuer, waehleUpdate };
