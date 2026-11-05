<p align="center">
  <img src="docs/banner.png" alt="Nox – der Browser, der nie hell wird" width="100%">
</p>

<p align="center">
  <a href="https://raw.githubusercontent.com/noah1o7/nachtfalter-android/main/updates/Nachtfalter.apk">
    <img src="docs/herunterladen.svg" alt="Nox für Android herunterladen" height="52">
  </a>
</p>

<p align="center">
  <sub>Android 8.0 oder neuer · kostenlos · ohne Konto · ohne Tracking</sub>
</p>

---

**Nox** ist ein schlanker Browser für Android, der von der ersten bis zur letzten Sekunde dunkel bleibt. Kein weißes Aufblitzen beim Start, keine grellen Menüs, und Websites ohne eigenen Dark Mode werden auf Wunsch automatisch abgedunkelt.

## ✦ Funktionen

| | |
|---|---|
| 🌙 **Immer dunkel** | Oberfläche, Startseite, Fehlerseiten und Menüs sind komplett dunkel – auch wenn dein Handy im hellen Modus ist. |
| 🌗 **Websites abdunkeln** | Seiten ohne eigenen Dark Mode werden automatisch dunkel dargestellt (ab Android 13). |
| 🛡️ **Werbeblocker** | Blockiert Werbung und Tracker schon beim Laden. Das Schild in der Suchleiste zeigt, wie viel es auf der aktuellen Seite war. |
| 🗂️ **Tabs** | Beliebig viele Tabs mit übersichtlicher Tab-Ansicht. Beim nächsten Start ist alles wieder da. |
| 🔎 **Deine Suchmaschine** | DuckDuckGo, Brave, Ecosia oder Google – mit einem Tipp im Menü umgestellt. |
| ⬇️ **Downloads & mehr** | Dateien herunterladen, Dateien hochladen, Videos im Vollbild, Links teilen. |
| 🔄 **Updates aus der App** | Nox schaut selbst nach neuen Versionen und installiert sie – ohne Play Store, ohne Neuinstallation. |

## 📲 Installieren

1. Tippe oben auf **„Nox für Android herunterladen“** – am besten direkt auf deinem Handy.
2. Öffne die heruntergeladene Datei `Nachtfalter.apk`.
3. Android fragt beim ersten Mal, ob dein Browser **Apps aus unbekannten Quellen** installieren darf. Erlaube das einmal.
4. Auf **Installieren** tippen – fertig. Nox findet sich danach in deiner App-Liste.

> **Tipp:** Unter *Einstellungen → Apps → Standard-Apps → Browser* kannst du Nox als Standardbrowser festlegen. Dann öffnen sich Links aus anderen Apps direkt in Nox.

## 🔄 Updates

Du musst Nox nur **ein einziges Mal** installieren. Danach meldet sich die App von selbst, sobald es eine neue Version gibt – einmal antippen, und das Update wird geladen und installiert. Von Hand geht's über **☰ → Nach Updates suchen**.

Alle bisherigen Versionen findest du unter [Releases](https://github.com/noah1o7/nachtfalter-android/releases).

## 🔒 Datenschutz

- Kein Konto, keine Anmeldung, keine Werbung.
- Nox sammelt **keine** Daten und schickt nichts an mich oder Dritte.
- Die einzige Verbindung, die Nox von sich aus aufbaut, ist der Update-Check hier auf GitHub.

## 🎨 Gestaltung

Flache Flächen in Graphit (`#131416`, `#1b1c1f`), feine Linien (`#2f3034`) und genau eine Akzentfarbe: Bernstein (`#e8a33d`). Der Akzent markiert nur Fokus, Fortschritt und die wichtigste Aktion. Übergänge sind kurze, ruhige Überblendungen.

---

<details>
<summary><b>🛠️ Für Entwickler: selbst bauen & neue Version veröffentlichen</b></summary>

<br>

Nox ist eine native Android-App in **Kotlin** auf Basis von Android-WebView. Öffnen lässt sich das Projekt direkt in Android Studio.

**Neue Version veröffentlichen**

1. `versionCode` und `versionName` in `app/build.gradle.kts` hochzählen
2. `./gradlew assembleRelease` ausführen (signiert mit dem Nox-Schlüssel, der **nicht** in diesem Repo liegt)
3. APK als `updates/Nachtfalter.apk` ablegen und in `updates/version.json` die neue Version eintragen – die App findet das Update von selbst
4. Auf GitHub ein Release `vX.Y.Z` anlegen und die APK als `Nox-X.Y.Z.apk` anhängen

Die interne App-Kennung bleibt `de.noah.nachtfalter`, damit Updates über bestehende Installationen funktionieren.

</details>

<p align="center"><sub>Gebaut von <a href="https://github.com/noah1o7">noah1o7</a> 🌙</sub></p>
