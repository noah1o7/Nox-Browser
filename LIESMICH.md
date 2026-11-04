# 🦋 Nachtfalter für Android

Mein eigener Browser im Liquid-Glass-Stil: dunkles Blaugrau, Lila (#b794ff, #8f63f0), Blasen-Optik. Komplett im Dark Mode.

## Funktionen
- Tabs mit Glas-Übersicht, Suchleiste (DuckDuckGo, Brave, Ecosia, Google)
- Werbeblocker mit grünem Schild und Zähler
- Websites abdunkeln (Android 13+)
- Startseite mit Farbwolken, dunkle Fehlerseite
- Downloads, Datei-Upload, Videos im Vollbild
- Updates direkt aus der App (über die GitHub-Releases hier)

## Animationen
Tab quillt als Blase aus dem Knopf · zerplatzt beim Schließen in Glasperlen · Glasvorhang mit Kugel beim Laden ·
Menü bläst sich aus ☰ auf (☰ dreht sich zu ✕) · Knöpfe federn · Update-Blase steigt aus der Leiste auf

## Neue Version veröffentlichen
1. `versionCode` und `versionName` in `app/build.gradle.kts` hochzählen
2. APK bauen (mit dem Nachtfalter-Schlüssel, der **nicht** in diesem Repo liegt)
3. Neues Release mit Tag `vX.Y.Z` anlegen und die APK anhängen – die App findet es von selbst
