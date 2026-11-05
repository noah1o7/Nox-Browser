// Oberfläche: Tableiste, Adressleiste, Knöpfe
const $ = (id) => document.getElementById(id);

const tabsEl = $('tabs');
const adresse = $('adresse');
const knopfZurueck = $('zurueck');
const knopfVor = $('vor');
const knopfNeu = $('neu-laden');
const iconNeu = $('icon-neu');
const iconStopp = $('icon-stopp');
const schild = $('schild');
const zaehler = $('zaehler');
const ladebalken = $('ladebalken');

let zustand = { aktiv: null, tabs: [] };
let adblockAn = true;
let ladeteAktiv = false;

function svgSchliessen() {
  const ns = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(ns, 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  const p = document.createElementNS(ns, 'path');
  p.setAttribute('d', 'M6 6l12 12M18 6L6 18');
  svg.appendChild(p);
  return svg;
}

// Strg+W kommt aus dem Menü des Hauptprozesses
nf.aufSchliessAnfrage((id) => nf.tabSchliessen(id));

function tabsZeichnen() {
  tabsEl.replaceChildren();
  for (const t of zustand.tabs) {
    const el = document.createElement('div');
    el.className = 'tab' + (t.id === zustand.aktiv ? ' aktiv' : '');
    el.dataset.id = t.id;
    el.title = t.titel;

    let icon;
    if (t.laedt) {
      icon = document.createElement('div');
      icon.className = 'kreisel';
    } else if (t.favicon) {
      icon = document.createElement('img');
      icon.className = 'favicon';
      icon.src = t.favicon;
      icon.onerror = () => { icon.replaceWith(Object.assign(document.createElement('div'), { className: 'platzhalter' })); };
    } else {
      icon = document.createElement('div');
      icon.className = 'platzhalter';
    }

    const titel = document.createElement('span');
    titel.className = 'titel';
    titel.textContent = t.titel || t.url || 'Neuer Tab';

    const zu = document.createElement('button');
    zu.className = 'schliessen';
    zu.title = 'Tab schließen (Strg+W)';
    zu.appendChild(svgSchliessen());
    zu.addEventListener('click', (e) => { e.stopPropagation(); nf.tabSchliessen(t.id); });

    el.append(icon, titel, zu);
    el.addEventListener('mousedown', (e) => {
      // Klick auf das ✕ nicht als "Tab auswählen" werten – sonst wird die Leiste neu
      // gezeichnet, bevor der Klick ankommt, und das Schließen geht verloren.
      if (e.target.closest('.schliessen')) return;
      if (e.button === 1) { e.preventDefault(); nf.tabSchliessen(t.id); } // Mittelklick
      else if (e.button === 0) nf.tabWaehlen(t.id);
    });
    tabsEl.appendChild(el);
  }
}

function leisteAktualisieren() {
  const t = zustand.tabs.find((x) => x.id === zustand.aktiv);
  if (!t) return;
  if (document.activeElement !== adresse) adresse.value = t.url;
  knopfZurueck.disabled = !t.kannZurueck;
  knopfVor.disabled = !t.kannVor;
  iconNeu.hidden = t.laedt;
  iconStopp.hidden = !t.laedt;
  knopfNeu.title = t.laedt ? 'Laden abbrechen' : 'Neu laden (F5)';
  zaehler.textContent = adblockAn ? String(t.geblockt) : 'aus';
  schild.title = adblockAn
    ? `Werbeblocker an – ${t.geblockt} Elemente auf dieser Seite blockiert. Klicken zum Ausschalten.`
    : 'Werbeblocker aus. Klicken zum Einschalten.';

  if (t.laedt && !ladeteAktiv) {
    ladebalken.className = '';
    void ladebalken.offsetWidth; // Animation neu starten
    ladebalken.className = 'aktiv';
  } else if (!t.laedt && ladeteAktiv) {
    ladebalken.className = 'fertig';
  }
  ladeteAktiv = t.laedt;
}

nf.aufTabs((daten) => {
  zustand = daten;
  tabsZeichnen();
  leisteAktualisieren();
});

nf.aufEinstellungen((e) => {
  adblockAn = e.adblock;
  schild.classList.toggle('aus', !e.adblock);
  adresse.placeholder = `Mit ${e.suchmaschine} suchen oder Adresse eingeben`;
  leisteAktualisieren();
});

nf.aufFokusAdresse(() => { adresse.focus(); adresse.select(); });

$('adressformular').addEventListener('submit', (e) => {
  e.preventDefault();
  if (adresse.value.trim()) { nf.navigiere(adresse.value); adresse.blur(); }
});
adresse.addEventListener('focus', () => adresse.select());
adresse.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    const t = zustand.tabs.find((x) => x.id === zustand.aktiv);
    adresse.value = t ? t.url : '';
    adresse.blur();
  }
});

$('neuer-tab').addEventListener('click', () => nf.neuerTab());
knopfZurueck.addEventListener('click', () => nf.nav('zurueck'));
knopfVor.addEventListener('click', () => nf.nav('vor'));
knopfNeu.addEventListener('click', () => nf.nav(iconNeu.hidden ? 'stopp' : 'neu'));
schild.addEventListener('click', () => nf.adblockUmschalten());
$('claude').addEventListener('click', () => nf.seitenleisteUmschalten());
nf.aufSeitenleiste((offen) => $('claude').classList.toggle('aktiv', offen));
$('menue').addEventListener('click', (e) => {
  const r = e.currentTarget.getBoundingClientRect();
  nf.menue(r.left, r.bottom + 4);
});

// Doppelklick auf leere Tableiste = neuer Tab
$('tableiste').addEventListener('dblclick', (e) => {
  if (e.target.id === 'tableiste' || e.target.id === 'tabs') nf.neuerTab();
});
