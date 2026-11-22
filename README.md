# Shield VPN

A simple Android VPN app with **no ads, no tracking and no analytics**.

- Big power button + ON/OFF switch
- Country dropdown with your saved servers
- Connection time, download and upload counters
- Uses WireGuard, a fast open-source VPN protocol

## 1. Build the APK (free, works from your phone)

1. Make a free account at github.com and create a **private** repository.
2. Upload every file in this folder to it (keep the folder structure, including `.github/workflows/build.yml`).
3. Open the **Actions** tab. The "Build APK" job runs on its own (about 5 minutes).
4. Open **Releases** and download `ShieldVPN.apk`.
5. Open the APK on your phone and allow "Install unknown apps" when Android asks.

Every time you change a file, GitHub builds a new version that installs as an update.

## 2. Get servers (WireGuard config files)

The app connects to WireGuard servers. Each server is one `.conf` file. Some options:

| Source | Cost | Countries |
|---|---|---|
| Proton VPN (free plan) | Free, no ads | A few countries |
| Proton VPN Plus / Mullvad / Surfshark / NordVPN (WireGuard configs) | Paid | Many countries |
| Your own server (e.g. a $5/month cloud server running WireGuard) | Cheap | Wherever you rent it |

For Proton: sign in at account.protonvpn.com → **Downloads** → **WireGuard configuration** → pick a platform "Android", choose a server and download the `.conf` file.

## 3. Add servers in the app

Tap **+ Add server** → **Import .conf file** → pick the file. The country is picked automatically when the file name starts with a country code (like `NL-FREE-12.conf`); you can change it. Tap **Save**. Repeat for each country.

Then pick a country from the dropdown and tap the power button.

## Notes

- Server keys are stored only inside the app on your phone (backups disabled).
- The signing key (`app/shield.jks`) is in this project so updates install over the old app. Keep the repository private.
- Follow local laws about VPN use where you live.
