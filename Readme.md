# BluetoothChat

A simple Android chat app that sends text messages between two phones over Bluetooth Classic (RFCOMM). No internet or Wi-Fi is needed.

## Requirements

- Two Android phones running Android 8.0 (API 26) or newer
- Bluetooth turned on
- The two phones paired in Android **Settings → Bluetooth**

## Install

1. Go to the **Actions** tab of this repo and open the latest successful **Build APK** run.
2. Download the `app-debug` artifact and unzip it.
3. Install `app-debug.apk` on both phones. You may need to allow installs from your browser or file app.

## How to use

1. Pair the two phones in Android's Bluetooth settings.
2. Open BluetoothChat on both phones and allow the Bluetooth permission.
3. On phone A, tap **Host: wait for the other phone**.
4. On phone B, tap phone A's name in the paired devices list.
5. When both screens show "Connected," type a message and tap **Send**.

## Build from source

The project uses Kotlin and Gradle. The included GitHub Actions workflow (`.github/workflows/build.yml`) builds a debug APK on every push. To build locally, run:

```bash
gradle assembleDebug
