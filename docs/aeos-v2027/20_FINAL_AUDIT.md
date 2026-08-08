# 20_FINAL_AUDIT

## ACCEPTANCE CRITERIA
1. **Manifest Sync**: App successfully fetches and applies an upstream manifest without breaking offline operation.
2. **Model Management**: UI accurately displays Installed, Available, Unsupported, and Failed models.
3. **Download Integrity**: `.tmp` downloads verify checksums and rename atomically. Corrupt downloads fail safely.
4. **Rescan**: App detects manually pushed ADB models and maps them correctly.
5. **Truthful UI**: Chat header accurately reflects the engine's loading/loaded state.
6. **Vibe Coding**: Roo Code IDE interface can display local files, receive generated patches, and apply them securely.
7. **Offline-First**: All network traffic originates only from the explicit "Check for Updates" action or explicit model/skill downloads.

## ADB VERIFICATION SEQUENCE (Must Pass Before Merge)
1. `adb shell am instrument -w <package>/.ModelManagerTest`
2. Push a ghost model: `adb shell run-as <package> cp /sdcard/model.bin files/models/`
3. Trigger Rescan: `adb shell am start -n <package>/.MainActivity --ez "FORCE_RESCAN" true`
4. Assert model appears as `Installed`.
5. Trigger Corrupt Download Test.
6. Assert download is deleted and marked `Failed`.
