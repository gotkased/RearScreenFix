# RearScreenFix

An LSPosed/Xposed module that fixes rear screen theme application on **Xiaomi popsicle** (EliteGaming HyperOS 3.0).

## The Problem

When applying a theme to the rear screen via ThemeManager, the process silently fails at step 2 of the apply chain:

1. `writeConfigToFile` ✅ succeeds
2. `py.g(rightPath, dest, 511)` ❌ **crashes here** — the `.mra` rights file doesn't exist locally (theme not purchased/licensed) → returns `false`
3. `py.g(resLocalPath, ...)` — never reached (MTZ theme copy)
4. `gc3c.k(... "etc" ...)` — never reached (ETC copy)
5. `RearScreenCenterManager` — never reached (SubScreenCenter notification)
6. `return true` — never reached

The result: the rear screen shows no theme, even though the theme exists and works fine on the main screen.

## The Fix

This module hooks `py.g(String, String, int)` — the generic file copy utility inside ThemeManager.

When it is called for an `.mra` rights file containing `rearscreen` in the path that **does not exist locally**, the hook intercepts the call and returns `true` instead of attempting the copy. This causes the variable `b3 = 1`, unblocking steps 3–5 so the MTZ/ETC files are copied and SubScreenCenter receives the theme notification normally.

## Requirements

- **Device:** Xiaomi popsicle (EliteGaming HyperOS 3.0) — may work on other Xiaomi devices running HyperOS 3.0 with the same ThemeManager build
- **Framework:** [LSPosed](https://github.com/LSPosed/LSPosed) installed and active
- Android 9+ (API 28+)

## Installation

1. Download the latest `RearScreenFix.apk` from the [Releases](../../releases) page
2. Open **LSPosed Manager**
3. Go to **Modules** → tap the **+** button → install the APK
4. Enable the module and set its scope to **ThemeManager**
5. Reboot the device
6. Apply a theme to the rear screen — it should now work

## Building from Source

```bash
git clone https://github.com/kasko1111/RearScreenFix.git
cd RearScreenFix
./gradlew assembleRelease
```

The signed APK will be at `app/build/outputs/apk/release/app-release.apk`.

> **Note:** Signing requires `keystore.properties` and `rearscreenfix.jks` in the project root. These are excluded from the repository for security reasons. See the [Contributing](#contributing) section if you want to build your own signed version.

## How it Works

The module targets only the `com.android.thememanager` package. Inside that process, it hooks the obfuscated utility class `com.android.thememanager.util.py`, method `g(String, String, int)`.

The hook runs **before** the original method. It checks:
- Is the source path ending in `.mra`?
- Does the path contain `rearscreen`?
- Does the file **not** exist on disk?

If all three conditions are true, it sets the return value to `true` and skips the original method entirely. Otherwise, the original copy logic runs unchanged.

## Compatibility

| Device | HyperOS version | Status |
|---|---|---|
| Xiaomi popsicle (EliteGaming) | HyperOS 3.0 | ✅ Tested |
| Other Xiaomi devices | HyperOS 3.0 | ❓ Untested — may work |

If you test it on another device, please open an issue and let us know the result.

## Contributing

Pull requests and issue reports are welcome. If the module stops working after a ThemeManager update (the obfuscated class/method name may change), please open an issue and include:
- Device model
- HyperOS / ThemeManager version
- LSPosed log output

## License

MIT License — see [LICENSE](LICENSE) for details.
