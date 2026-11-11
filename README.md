# Space Mind

A personal self-control app for Android 11+. It blocks an app when sexual content is detected, and it cannot be turned off or uninstalled without your PIN.

## Build (GitHub Actions)
1. Create a new private repository on GitHub and upload all the project files (including the `.github` folder).
2. Go to **Actions** -> **Build APK** -> **Run workflow** (it also runs automatically on every upload).
3. When the build finishes, open the run and download **Artifacts -> StrictMode-debug-apk**. Unzip it and install `app-debug.apk` on your phone.

## Phone setup (Android 13)
1. Open the app and set a PIN or password. Kindly note it down in a safe place.
2. On the Setup screen, all permissions must be ticked (✓), otherwise the app will not work:
   Accessibility, Device Admin (uninstall protection), Battery, and Display over other apps.
   For Accessibility, first open App info -> 3 dots -> **Allow restricted settings**.
3. On Redmi/Xiaomi phones you must also tick 4 extra steps (Autostart, Battery saver, Pop-up, Recents lock).
   The app cannot verify these, so it depends on you doing them properly.
4. After tapping **Finish**, check that the Main screen shows **AI model: ready**.

## Space (like Parallel Space / Kids Place)
- Open **Space** from the Main screen, or press Home when Lock Mode is ON.
- Only the apps you add are shown in Space. Tap **+ Add app (PIN)** to pick apps from your phone. Tap **−** next to an app to remove it from Space (no PIN needed; the app stays on your phone).
- Space only controls the list. It does not stop an app from opening in other ways, for example from a notification.

## Lock Mode
Turn on **Lock Mode** on the Main screen. After that, pressing Home or Recents brings you back to the Space Mind lock screen, where all your apps can be opened.
To leave, tap **Exit Lock Mode (PIN)** and enter your PIN; you then reach the normal home screen.
Note: the real home screen may flash for a moment before the lock screen appears.

While Lock Mode is ON, Space Mind draws its own 3-button bar (Recents, Home, Back) over the phone's navigation bar:
- **Back** works as the normal Back button in whichever app is open.
- **Home** opens the Space Mind lock screen.
- **Recents** opens the app's own Recent apps list, with a **Close all** button.

For best results use 3-button navigation (Settings -> Display -> Navigation -> Buttons). With gesture navigation the bar will cover a small strip at the bottom of the screen. In full-screen videos the bar may also cover the bottom of the video.

## Testing
1. Check that WhatsApp is in the Trusted list.
2. In **Block / unblock apps**, tick any app. When you open it, you should see the block screen and go to the Home screen.
3. Open Settings -> Accessibility -> Space Mind. It should ask for your PIN.
4. Open SAFE images (food, nature) and watch **Last AI score** on the Main screen. The nsfw score should stay below 0.1.
5. If the scores look wrong, tap the **AI preprocess** button to try the other mode.

## Tuning
- `NsfwConfig` (NsfwDetector.kt): HARD and SOFT thresholds.
- `SCAN_MS` (GuardService.kt): how often the screen is checked.
- Different model: set the repository variable `NSFW_MODEL_URL` under Settings -> Secrets and variables -> Actions -> Variables.

## Updating the app
The APK is signed with the key in `app/debug.keystore`, so a new build installs over the old one without uninstalling. Do not delete that file.
