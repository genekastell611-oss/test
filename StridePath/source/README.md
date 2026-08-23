# StridePath RPG 4.0.1 — gamified Android weight-loss planner (U.S. units)

See `AUDIT-4.0.0.md` for the game-system, widget, health-tracking, privacy, and reliability review. Version 4.0.1 updates the Android 16 build toolchain required by stable Health Connect.

## What changed in 4.0

- Added a native, resizable Android home-screen widget with Pip, live steps, step progress, Calories, hydration, level, and XP.
- Added an in-app **Add Widget** request plus manual launcher instructions when pinning is unsupported.
- Rebuilt Pip as a full-bodied game character with ears, armor, belt, boots, expressions, sword, cape, crown, victory particles, and level evolution.
- Added a Pip equipment shop so earned coins can buy and equip shields, headphones, a wizard hat, or a victory trail.
- Expanded to seven daily quests and five independent weekly raids across steps, meals, nutrition, hydration, workouts, sleep, and wellness check-ins.
- Added full-clear bonuses plus 10%, 25%, 50%, 75%, and 100% weight-campaign milestone rewards.
- Added optional mood, energy, and stress check-ins with non-diagnostic Pip guidance and trend summaries.
- Added optional waist measurements, history, deletion, and CSV export.
- Expanded Pip guidance to use wellness check-ins while encouraging smaller, sustainable actions on low-energy or high-stress days.
- Optimized long-term quest calculations to avoid repeatedly parsing the complete food and workout history.

### Add the widget

Open **Today → Pip Home-Screen Widget → Add Widget**. If the launcher does not support direct pinning, long-press an empty area of the Android home screen, choose **Widgets**, find **StridePath RPG**, and drag Pip onto the home screen.

## What changed in 3.0

- Five clear destinations: **Today, Food, Move, Awards, and Progress**.
- Pip now lives on Today and uses steps, food, water, workouts, sleep, weight progress, and streaks to provide rotating guidance.
- Day/week/month nutrition and movement views with historical navigation.
- Workout logging with duration, optional active Calories, notes, and deletion confirmation.
- Optional sleep duration and quality check-ins plus recovery summaries.
- Health Connect can backfill up to 31 days of step history after permission is granted.
- Weight forecast includes the original plan date and an actual-trend estimate when enough weigh-ins exist.
- CSV export for up to one year of locally stored health data.
- Safer deletion, historical food editing, manual missing-step correction, and stronger persistence validation.
- Modernized card shapes, clearer labels, improved system-inset behavior, and a less crowded five-item navigation bar.

StridePath RPG turns ordinary wellness tracking into a game campaign while keeping the underlying numbers visible. It is designed as an offline-first Android app with a local profile, food log, weight history, hydration habit tracker, walking goals, meal ideas, notifications, and game progression.

## What changed in 2.0

### Full game layer

- **XP, levels and coins** earned from long-term achievements, daily quest completion, walking-goal days, food-log consistency and perfect days.
- **Daily quests**: Step Boss, Hydration Potion Run, Food Inventory Check and Green-Zone nutrition quest.
- **Weekly raid**: complete the walking target on 5 days in the current week.
- **Step Boss HP** is directly tied to how many steps remain today.
- **Companion**: interactive Pip-style character, tappable dialogue, reactive mood, level cosmetics and a custom name.
- **Achievement cabinet** with common through legendary milestones and an unlock pop-up.
- **Unlockable worlds/themes** by level: Pixel Quest, Neon Arcade, Fantasy Guild, Cozy Adventure, Cyber Runner, Star Voyage and Creature Trainer.
- Themes are genre/era inspired rather than copies of copyrighted games, characters or logos.
- **Retro game presentation**: monospace HUD labels, quest cards, XP bars, boss language, checkpoint language and a new pixel-heart/boot launcher icon.

### Steps and movement

- Phone hardware `TYPE_STEP_COUNTER` fallback with Android activity-recognition permission.
- **Health Connect read-only step sync** using `androidx.health.connect:connect-client:1.1.0` and aggregated `StepsRecord.COUNT_TOTAL` so compatible Android/fitness sources can contribute to the day's total.
- App takes the highest available total from locally saved steps, the live phone sensor and Health Connect rather than adding sources together.
- Daily target, remaining steps, boss HP, 7-day average and goal-day count.
- Gradual walking progression suggestions instead of forcing the full target immediately.

### Food and nutrition

- Food/drink logging with calories, protein, carbohydrates, fat and fiber.
- Daily calorie and macro HUDs.
- Quick-log meal ideas.
- 7-day calorie scorecard with actual average vs planning target.
- Estimated maintenance, food-side deficit, calorie target and macro planning targets.
- Expanded meal library with breakfasts, lunches, dinners and snacks.
- Generated 7-day meal plan near the calculated calorie target.
- One-tap logging from a suggested meal into today's food inventory.
- Eating-strategy guidance focused on sustainable portions and consistency rather than forbidden foods.

### Weight planning and tracking

- Starting weight, goal amount, timeline and goal weight.
- **Actual vs projected weight** for the current date.
- Weight checkpoint logging.
- Weight map with actual checkpoints overlaid on the projected goal path.
- Goal progress bar and actual-vs-plan delta.
- Requests above about 2 lb/week are flagged as aggressive.
- Planning deficit is capped at 1,000 Calories/day.
- Estimated meal target is never shown below the app's 1,200 Calories/day floor.

### Reminders

- Hydration side-quest notifications every 1–4 hours when enabled.
- Morning quest-board reminder.
- Optional evening food-inventory reminder.
- Optional Sunday weigh-in/checkpoint reminder.
- Reminder schedules restore after device reboot.
- Android 13+ notification permission is requested only when notifications are enabled.

### Persistence and privacy

- Existing 1.x profiles, weight entries and old six-field food entries are migrated/read where possible.
- No account, analytics SDK, ad SDK or StridePath cloud server.
- Health Connect access is read-only and optional.
- A Health Connect permission-rationale activity explains local data usage.

## Safety design

StridePath is a general adult wellness/planning app, not medical care. Calorie burn, maintenance, macros, expected weight and meal values are estimates. The app deliberately avoids punishment mechanics: missed quests do not deduct XP, foods are not labeled good/bad, and streak loss does not erase campaign progress.

Pregnancy, a history of an eating disorder, major medical conditions, or medications that materially affect weight are situations where individualized clinical guidance is more appropriate than an app-generated deficit.

## Build on GitHub — easiest path

1. Create a GitHub repository.
2. Upload the **contents** of this `StridePath` folder so `.github`, `app`, `build.gradle.kts` and `settings.gradle.kts` are at the repository root.
3. Commit to `main` or `master`.
4. Open **Actions → Build StridePath APK**.
5. Run the workflow if it did not start automatically.
6. Download the `StridePath-debug-apk` artifact from the completed workflow.
7. Extract it and install `app-debug.apk` on the Android phone.

The included workflow uses Java 17, Android SDK 36 and Gradle 8.11.1, runs unit tests, builds the APK, and uploads the APK as an Actions artifact.

## Local build

Open the `StridePath` folder in Android Studio and allow Gradle sync, then choose **Build → Build APK(s)**. Or with Gradle 8.11.1 available:

```bash
gradle testDebugUnitTest assembleDebug
```

Expected APK path:

`app/build/outputs/apk/debug/app-debug.apk`

## Main source files

- `MainActivity.kt` — Compose UI for home HUD, food log, game room, nutrition plan and progress stats.
- `Models.kt` — profile, goal calculator, nutrition targets and core models.
- `GameData.kt` — daily quests, weekly raid, achievement rules, XP/levels and unlockable themes.
- `MealData.kt` — expanded meal library and 7-day planner.
- `AppStore.kt` — local persistence and migration.
- `StepCounter.kt` — direct Android phone sensor fallback.
- `HealthConnectSteps.kt` — Health Connect availability, permission and aggregated step reads.
- `WaterReminder.kt` — hydration/quest/meal/weigh-in alarm scheduling and notification receivers.
- `PermissionsRationaleActivity.kt` — Health Connect privacy/permission explanation.

## Important step-count note

A raw Android `TYPE_STEP_COUNTER` sensor reports steps since reboot while the sensor is activated and behavior varies by manufacturer. Health Connect is the preferred full-day source when available and permission is granted; direct phone-sensor counting remains as a fallback.

## Current limitations / sensible next upgrades

2.0 intentionally does not pretend to have food-label data that is not bundled with the app. Useful future additions would be a real food database/barcode source, recipe builder, portion units, favorites, export/backup, home-screen widget, Wear OS companion, and optional Health Connect weight/nutrition write support. Those should be added with explicit privacy and data-source handling rather than fake data.

## U.S. units

StridePath is standardized for U.S. customary display units:
- Body weight: pounds (lb).
- Height: feet and inches (ft/in).
- Walking distance calculations: miles.
- Hydration: fluid ounces (fl oz); one in-app potion adds 8 fl oz (1 U.S. cup).
- Meal portions: ounces, cups, tablespoons and teaspoons where quantities are shown.
- Calories: displayed as Calories, matching U.S. food-label convention.
- Protein, carbohydrate, fat and fiber remain in grams because U.S. Nutrition Facts labels report these nutrients in grams.
- Any future temperature UI should use degrees Fahrenheit (°F).

Existing 2.0 profiles migrate hydration goals from cups to fluid ounces automatically; existing hydration logs are interpreted as 8-fl-oz servings.
