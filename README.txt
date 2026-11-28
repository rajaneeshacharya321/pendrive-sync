PendriveSync v0.6
- No options inside the app. The screen is your LOGO centred on your COLOUR - the same picture Android shows
  while the app starts - and it stays that way. The app works in the background.
- Chosen at BUILD time:
    app/src/main/assets/config.json
        saveLocation: "app" (private app storage, default) or "device" (/StudyMaterials)
        masterIds:    pendrive IDs that get the full export; empty = first pendrive inserted becomes master
        logoSize:     "normal" (like Android's start-up screen) | "large" | "xlarge"
    app/src/main/res/values/colors.xml          screen colour (screen_bg)
    app/src/main/res/drawable-nodpi/splash_logo.png   the logo shown on the screen
    app/src/main/res/values/strings.xml         app name
- Do NOT delete or replace app/debug.keystore: it lets new APKs install over the old app.
- Log: log.txt inside the save location.
