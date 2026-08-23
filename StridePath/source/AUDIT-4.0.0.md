# StridePath 4.0.0 game, widget, and health audit

## Home-screen widget

- Added a native `AppWidgetProvider`, provider metadata, resizable RemoteViews layout, Pip vector character, progress drawable, and launcher pin request.
- Widget displays current steps/goal, progress, logged Calories, hydration, XP, level, and a Pip message.
- Tapping the widget opens StridePath.
- Widget refreshes periodically and whenever tracked data causes the app state to refresh.
- Widget receiver declaration, provider metadata, initial layout, resize attributes, and RemoteViews constraints follow current Android widget architecture.

## Game systems

- Expanded the daily quest board to seven independent quests: steps, hydration, meal logging, calorie zone, workout minutes, sleep logging, and wellness check-in.
- Added a visible full-clear meter and 200 XP daily full-clear bonus.
- Added five weekly raids: Step Dragon, Inventory Raid, Potion Mastery, Dream Temple, and Training Grounds.
- Each weekly raid rewards independently; missing one does not erase another reward.
- Added permanent weight-campaign milestones at 10%, 25%, 50%, 75%, and 100% progress.
- Added first/full-clear and seven-full-clear achievements.
- Coins now have purpose through a Pip cosmetic shop, with safe non-negative accounting and permanent ownership.

## Pip redesign

- Replaced the simple circular avatar with a full-body game character rendered natively in Compose Canvas.
- Added armor, belt, boots, pointed ears, animated bounce/blink expressions, level-five sword, level-ten cape, level-twenty crown, and boss-clear particles.
- Added equippable forest shield, focus headset, wizard hat, and victory trail.
- Added semantic accessibility description for the companion canvas.
- Widget includes a matching vector version of Pip.

## Expanded health tracking

- Added optional daily mood, energy, stress, and note check-ins.
- Added period averages for mood, energy, and stress without creating a diagnostic health score.
- Pip can suggest gentler goals when energy is low or stress is high; wording remains supportive and non-medical.
- Added optional waist measurements in U.S. inches, recent history, safe deletion, and change from first measurement.
- CSV export now includes mood, energy, stress, and waist measurements alongside steps, nutrition, hydration, sleep, workouts, and weight.

## Safety and privacy

- Health behaviors earn rewards but incomplete quests never remove XP, coins, streak history, or prior achievements.
- Pip equipment is cosmetic and cannot change calorie, step, or weight goals.
- Check-ins are optional and explicitly described as non-diagnostic.
- New records remain local in private app preferences; widget data is rendered by Android without adding network access.
- No broad storage permission, advertising SDK, analytics SDK, account, or internet permission was added.

## Verification status

- XML resources and manifest parse checks, Kotlin delimiter checks, resource-reference checks, navigation branches, data persistence, and archive integrity are validated locally.
- The source includes calculation unit tests and a GitHub Actions Android build/test workflow.
- A full APK compile still requires that workflow because this work environment has no Gradle or Android SDK installation.
