PendriveSync v0.4
- No options inside the app. It shows either a loading spinner, or - if you chose one in the builder -
  ONLY your picture, full screen, permanently (assets/screen_image). It works in the background.
- Everything is chosen at BUILD time in app/src/main/assets/config.json
    saveLocation: "app" (private app storage, default) or "device" (/StudyMaterials, visible in a file manager)
    masterIds:    pendrive IDs that get the full export; empty = first pendrive inserted becomes master
    screenFit:    "fit" (whole picture) or "fill" (cover the screen, edges cropped)
- App name: app/src/main/res/values/strings.xml    Logo: app/src/main/res/mipmap-*/ic_launcher.png
- Do NOT delete or replace app/debug.keystore: it lets new APKs install over the old app.
- Log: log.txt inside the save location.
