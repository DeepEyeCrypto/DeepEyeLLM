# DeepEyeLLM Auto-Update System

This document outlines how the in-app auto-update system works and how to manage GitHub releases to trigger updates.

## How It Works
DeepEyeLLM uses a completely custom, self-hosted update mechanism that checks the public GitHub API on launch. 
1. `UpdateChecker` hits `https://api.github.com/repos/DeepEyeCrypto/DeepEyeLLM/releases/latest`.
2. It compares the semantic `tag_name` (e.g. `v2027.2.1`) against the local `BuildConfig.VERSION_NAME` (`2027.2.0`).
3. If the tag is newer, it parses the release `body` (Changelog) and `assets` (APK).
4. `UpdateDialog` displays a glassmorphic popup with the changelog.
5. If the user accepts, `UpdateDownloader` streams the APK to the external cache directory with a live progress bar.
6. `UpdateInstaller` uses a `FileProvider` to launch the Android Package Installer securely.

## How to Publish an Update

To trigger this auto-update on user devices, follow these steps:

1. **Build the APK**:
   ```bash
   ./android/gradlew -p android assembleRelease
   ```
2. **Create a GitHub Release**:
   - Go to https://github.com/DeepEyeCrypto/DeepEyeLLM/releases/new
   - Set the **Tag Name** to a strictly higher semantic version (e.g., `v2027.2.1` if current is `2027.2.0`).
   - Add your changelog in the **Description** box.
   - Attach your signed `app-release.apk` to the **Assets** section.
   - Click **Publish release**.

> [!IMPORTANT]
> The auto-updater specifically looks for an asset name ending in `.apk`. Ensure your asset does not have a misleading extension.

## Testing Locally
If you want to test the UI without publishing a new release:
1. Open `UpdateChecker.kt`.
2. Temporarily hardcode `isNewer` to `true`:
   ```kotlin
   // Replace this:
   val isNewer = isVersionNewer(currentVersion, tagName)
   // With this:
   val isNewer = true 
   ```
3. Launch the app. You will immediately see the glassmorphic changelog popup (if an APK exists on the latest release to parse).

## Permissions Used
- `INTERNET`: To fetch the release data and APK.
- `REQUEST_INSTALL_PACKAGES`: To launch the package installer for the downloaded APK.

## Troubleshooting
- **No popup on launch**: The user may have clicked "Later" previously, which dismisses the specific version tag permanently. You can clear the app's SharedPreferences or push a *newer* version tag to bypass the debounce.
- **Parse Error**: Ensure the GitHub Release is marked as the "Latest Release" (not a pre-release), and that it contains an asset ending in `.apk`.
