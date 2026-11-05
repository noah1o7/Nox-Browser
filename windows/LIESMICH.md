# Nox für Windows 🌙

Dein eigener kleiner Browser – komplett im Dark Mode. (Früher hieß er Nachtfalter.)

## Was er kann

- **Tabs** – öffnen, schließen (auch per Mittelklick), wechseln
- **Suchleiste** – Adresse eintippen oder einfach suchen (DuckDuckGo, Brave Search, Ecosia oder Google, im ☰-Menü wählbar)
- **Werbeblocker** – mit den gleichen Filterlisten wie gängige Adblocker (EasyList, EasyPrivacy). Das Schild zeigt, wie viel auf der Seite blockiert wurde; ein Klick schaltet ihn an/aus.
- **Dark Mode überall** – Oberfläche, Startseite und Menüs sind dunkel, helle Websites werden automatisch abgedunkelt (abschaltbar im ☰-Menü)
- **Berechtigungen** – Seiten müssen fragen, bevor sie Kamera, Mikrofon oder Standort bekommen
- **Chrome-Erweiterungen** (entpackt) und **eigene Skripte**, siehe unten

## Installationsdatei bauen

```
npm install
npm run dist
```
Die fertige `Nox-Setup-X.Y.Z.exe` liegt danach im Ordner `fertig`.

## Zum Ausprobieren starten (ohne Installation)

1. **Node.js** installieren, falls noch nicht vorhanden: https://nodejs.org → die „LTS“-Version.
2. Diesen Ordner irgendwo entpacken, z. B. auf den Desktop.
3. Im Ordner ein Terminal öffnen:
   - **Windows:** Im Explorer in den Ordner gehen, oben in die Adresszeile `cmd` tippen, Enter.
   - **Mac:** Rechtsklick auf den Ordner → „Neues Terminal beim Ordner“.
4. Einmalig eingeben:
   ```
   npm install
   ```
   (lädt Electron und den Werbeblocker herunter, dauert kurz)
5. Starten mit:
   ```
   npm start
   ```

## Tastenkürzel

| Kürzel | Funktion |
|---|---|
| Strg+T | Neuer Tab |
| Strg+W | Tab schließen |
| Strg+Tab / Strg+Umschalt+Tab | Tab wechseln |
| Strg+L | In die Adressleiste |
| F5 / Strg+R | Neu laden |
| Alt+← / Alt+→ | Zurück / Vor |
| Strg+Plus / Strg+Minus / Strg+0 | Zoom |
| F12 | Entwicklertools |

(Auf dem Mac jeweils Cmd statt Strg.)

## Chrome-Erweiterungen

1. Im ☰-Menü → *Erweiterungen* → *Erweiterungs-Ordner öffnen*.
2. Die Erweiterung **entpackt** als eigenen Ordner dort hineinlegen (der Ordner muss eine `manifest.json` enthalten).
3. Nox neu starten.

So kommst du an den entpackten Ordner: In Chrome `chrome://extensions` öffnen, oben rechts „Entwicklermodus“ einschalten, dann zeigt jede Erweiterung ihre ID. Die Dateien liegen unter
- Windows: `%LOCALAPPDATA%\Google\Chrome\User Data\Default\Extensions\<ID>\<Version>`
- Mac: `~/Library/Application Support/Google/Chrome/Default/Extensions/<ID>/<Version>`

Den `<Version>`-Ordner in den Erweiterungs-Ordner kopieren.

**Wichtig:** Electron unterstützt nur einen Teil der Chrome-Schnittstellen. Erweiterungen, die nur Seiten verändern, laufen meist. Knöpfe in der Leiste und Popups (z. B. bei Passwortmanagern) gibt es nicht.

## Eigene Skripte

Im ☰-Menü → *Eigene Skripte-Ordner öffnen*. Dort `.css`- oder `.js`-Dateien ablegen:

- `alle.css` → gilt für alle Seiten
- `youtube.com.css` → gilt nur für YouTube

Neu laden (F5) reicht, kein Neustart nötig. Eine Kurzanleitung liegt als `LIESMICH.txt` im Ordner.

## NordVPN & Co.

- **NordVPN:** Einfach die normale NordVPN-App laufen lassen, sie schützt den ganzen Computer, also auch Nox.
- **NordLocker:** Läuft unabhängig vom Browser weiter wie gewohnt.
- **NordPass:** Die Desktop-App nutzen und Passwörter von dort kopieren.

## Gut zu wissen

- Unter der Haube steckt Chromium (über Electron). Sicherheitsupdates bekommst du, indem du ab und zu `npm update` im Ordner ausführst.
- Google-Anmeldungen blockt Google bei selbstgebauten Browsern manchmal („Dieser Browser ist möglicherweise nicht sicher“). Dann am besten im Hauptbrowser anmelden.
- Einstellungen, Cache und Erweiterungen liegen im Benutzerordner (Windows: `%APPDATA%\Nachtfalter`, Mac: `~/Library/Application Support/Nachtfalter`). Der Ordner heißt absichtlich noch wie früher, damit nach der Umbenennung nichts verloren geht.
- Updates: Nox schaut nach dem Start selbst unter den GitHub-Releases nach einer neueren `Nox-Setup-X.Y.Z.exe`, von Hand über ☰ → *Nach Updates suchen*.
