// Verbindet die Oberfläche (Tableiste, Adressleiste) sicher mit dem Hauptprozess.
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('nf', {
  navigiere: (text) => ipcRenderer.send('navigiere', text),
  neuerTab: () => ipcRenderer.send('neuer-tab'),
  tabWaehlen: (id) => ipcRenderer.send('tab-waehlen', id),
  tabSchliessen: (id) => ipcRenderer.send('tab-schliessen', id),
  nav: (aktion) => ipcRenderer.send('nav', aktion),
  adblockUmschalten: () => ipcRenderer.send('adblock-umschalten'),
  menue: (x, y) => ipcRenderer.send('menue', x, y),
  seitenleisteUmschalten: () => ipcRenderer.send('seitenleiste-umschalten'),
  aufSeitenleiste: (cb) => ipcRenderer.on('seitenleiste', (_e, offen) => cb(offen)),
  aufTabs: (cb) => ipcRenderer.on('tabs', (_e, daten) => cb(daten)),
  aufEinstellungen: (cb) => ipcRenderer.on('einstellungen', (_e, daten) => cb(daten)),
  aufFokusAdresse: (cb) => ipcRenderer.on('fokus-adresse', () => cb()),
  aufSchliessAnfrage: (cb) => ipcRenderer.on('schliess-anfrage', (_e, id) => cb(id)),
});
