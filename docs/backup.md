# InsidePacer Google Drive Backups

InsidePacer can optionally back up your programs, templates, completed sessions, and settings to your Google Drive **App Data** space. The App Data space is hidden from your normal Drive files and is only accessible to InsidePacer.

## Privacy

* Backups are encrypted on-device with AES-256-GCM using a key that never leaves the device.
* We only request the Google account email so we can display which account is connected.
* No other personal identifiers or third-party servers are used.

## Managing backups

1. Open **Settings → Account & Backup**.
2. Sign in with the Google account that should store your encrypted backups.
3. Tap **Back up now** whenever you want to capture the latest data.
4. Tap **Restore** to import the most recent backup. Restoring is idempotent—running it multiple times will not duplicate data.
5. Tap **Sign out** to disconnect Google Drive access. Local data stays untouched.

## Clearing Drive App Data

If you want to delete the encrypted backups:

1. Visit [Google Drive App Management](https://drive.google.com/drive/settings/apps) in a browser while signed into the same Google account.
2. Locate **InsidePacer** in the list.
3. Choose **Delete hidden app data** to remove all backups stored in Drive App Data.

You can also remove the cached copy stored on the device by clearing the app’s storage from Android settings.
