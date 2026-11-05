# Nachtfalter für Android

Ein eigener, durchgehend dunkler Browser für Android.

## Funktionen
- Tabs mit Übersicht, Suchleiste (DuckDuckGo, Brave, Ecosia, Google)
- Werbeblocker mit Schild und Zähler
- Websites abdunkeln (Android 13+)
- Eigene Startseite und Fehlerseite
- Downloads, Datei-Upload, Videos im Vollbild
- Updates direkt aus der App (über den Ordner `updates` hier)

## Gestaltung
Flache Flächen in Graphit (`#131416`, `#1b1c1f`), feine Linien (`#2f3034`), eine Akzentfarbe (`#e8a33d`).
Der Akzent markiert nur Fokus, Fortschritt und die wichtigste Aktion. Übergänge sind kurze Überblendungen.

## Neue Version veröffentlichen
1. `versionCode` und `versionName` in `app/build.gradle.kts` hochzählen
2. `./gradlew assembleRelease` ausführen (signiert mit dem Nachtfalter-Schlüssel, der **nicht** in diesem Repo liegt)
3. APK als `updates/Nachtfalter.apk` ablegen und in `updates/version.json` die neue Version eintragen – die App findet es von selbst
