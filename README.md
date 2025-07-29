# Cager

Cager is a powerful and flexible lockdown tool for Android, designed to help you minimize distractions and take control of your device. Whether you're a developer, a privacy enthusiast, or just someone who needs a "kill switch" for their phone, Cager provides a simple and effective way to "cage" your device's radios, notifications, and other system services.

## Key Features

*   **Root and Non-Root Modes:** Cager is designed to work on both rooted and non-rooted devices, providing a different set of features for each.
*   **Radio Control:**
    *   **Non-Root:** Programmatically disable Wi-Fi and Bluetooth. For other radios like NFC and Airplane Mode, Cager will open the relevant settings panels for you to toggle them manually.
    *   **Root:** Cager can directly control a wide range of system services, including networking, radios, USB debugging, and more, by executing shell commands.
*   **Notification Management:**
    *   **Three Styles:** Choose from three notification styles: "full" (all notifications are shown), "icon" (only the notification icon is shown), and "silent" (all notifications are suppressed).
    *   **Whitelisting:** You can whitelist specific apps to allow their notifications to come through even when the cage is enabled.
*   **Do Not Disturb Integration:** When the notification style is set to "silent," Cager will automatically enable "Do Not Disturb" mode.
*   **Battery Guard:** To prevent your device from becoming unresponsive, Cager can be configured to avoid performing certain actions when the battery is low.
*   **Housekeeping Reminders:** Cager uses `WorkManager` to schedule periodic reminders to reboot your device or clear your caches.
*   **Customizable Settings:** Cager provides a settings screen where you can configure its behavior, including which subsystems to control, the notification style, and more.
*   **Emergency Kill-Switch:** A broadcast receiver is available to disable the cage in case of an emergency, which can be triggered via `adb`.

## How it Works

Cager's functionality is centered around the `MainActivity`, which serves as the main entry point for the app. The app's behavior changes depending on whether the device is rooted or not.

### Non-Root Mode

In non-root mode, Cager uses a combination of Android's standard APIs and accessibility services to control the device.

*   **Wi-Fi and Bluetooth:** Cager can directly enable or disable Wi-Fi and Bluetooth using the `WifiManager` and `BluetoothAdapter` APIs.
*   **NFC and Airplane Mode:** For NFC and Airplane Mode, Cager opens the corresponding settings screens for the user to manually toggle them. The `CagerAccessibilityService` can then be used to automate this process.
*   **Notification Handling:** The `NotificationHandlerService` is a `NotificationListenerService` that intercepts incoming notifications and suppresses them based on the user's selected notification style.

### Root Mode

In root mode, Cager can execute shell commands with `su` to directly control a wide range of system settings. This allows for a much more powerful and flexible "cage." The commands to be executed are stored in a JSON file in the app's assets.

## Installation and Setup

1.  **Build the App:** Clone this repository and build the app using Android Studio or by running `./gradlew assembleDebug` in the project's root directory.
2.  **Install the APK:** Install the generated APK file (`app/build/outputs/apk/debug/app-debug.apk`) on your Android device.
3.  **Grant Permissions:** For Cager to function correctly, you will need to grant it several permissions:
    *   **Notification Listener:** This allows Cager to manage and suppress notifications. You can grant this permission from the app's settings menu.
    *   **Accessibility Service:** In non-root mode, Cager requires the Accessibility Service permission to automate the toggling of system settings. You will be prompted to enable this service when you first launch the app.
    *   **Bluetooth and Wi-Fi Control (optional):** For non-root mode, you may be prompted to grant permissions to control Bluetooth and Wi-Fi.

## Usage

*   **Main Screen:** The main screen of the app features a single button to "Enable Cage" or "Disable Cage."
*   **Advanced Settings:** The overflow menu in the toolbar provides access to advanced settings.
*   **Emergency Kill-Switch:** To disable the cage in an emergency, use the following `adb` command:

    ```
    adb shell am broadcast -a com.example.cager.EMERGENCY_CAGE
    ```

## Contributing

Contributions are welcome! If you have an idea for a new feature or have found a bug, please open an issue or submit a pull request.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details.

## Disclaimer

This application can modify system-level settings, especially when used with root access. Use this application at your own risk. The developers are not responsible for any damage to your device or loss of data that may result from its use.
