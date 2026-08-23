# StridePath 2.3.0 audit

## Corrected

- Activity Recognition permission now restarts the step sensor immediately after approval.
- Pip is present on the main daily summary and responds from live steps, food, hydration, and streak data.
- Newly unlocked achievements can generate Android notifications, with duplicate-notification protection.
- Food entries can be reviewed, added, and removed on earlier dates using an accessible day picker.
- Food/profile persistence now sanitizes record delimiters and clamps stored nutrition values.
- Version metadata advanced to 2.3.0 (`versionCode` 7).

## Verified by inspection

- Every bottom-navigation destination is independent and respects safe drawing/navigation-bar insets.
- Health Connect permission, availability, and aggregate reads fail safely.
- Notification receivers are non-exported; reboot rescheduling is supported.
- Input bounds protect profile, calorie, and weigh-in calculations.
- No network permission or analytics SDK is present.
- Weight-loss projections remain estimates and aggressive goals are visibly flagged.

## Build path

The included GitHub Actions workflow installs Java 17, Android SDK 35, and Gradle 8.9, runs unit tests, builds the debug APK, and uploads it as an artifact. The source archive intentionally contains no generated build directories.
