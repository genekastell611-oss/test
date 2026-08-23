# StridePath 3.0.0 product and code audit

## Interface and navigation

- Replaced overlapping Game/Plan/Stats navigation with five purpose-specific destinations: Today, Food, Move, Awards, and Progress.
- Kept Pip visible on the main summary and preserved the full companion/reward experience on Awards.
- Added reusable day/week/month controls and historical period navigation.
- Removed duplicate safe-area padding while preserving protection from Android status and navigation bars.
- Increased panel corner radii, improved metric wrapping, clarified headings, and retained accessible text labels on navigation and destructive actions.
- Added confirmation dialogs before deleting food, workouts, or weigh-ins.

## Health tracking

- Added workout sessions with activity name, duration, optional active Calories, notes, history, and deletion.
- Added estimated walking distance, daily averages, goal-day counts, and compact movement charts.
- Added optional sleep duration and quality tracking with day/week/month recovery summaries.
- Added hydration goal-day reporting and workout totals to Progress.
- Added historical Health Connect backfill for the most recent 31 days.
- Improved the phone step-counter accumulator so permission approval works immediately and steps persist across app restarts.
- Added manual step corrections for missing/imported data.
- Added CSV export for one year of steps, nutrition totals, hydration, sleep, workouts, and weight.

## Food, weight, and forecast

- Food logs now support day/week/month totals and browsing earlier periods.
- Macro and calorie targets scale to the selected period; weekly/monthly views show daily averages.
- Preserved quick logging and restored expandable seven-day meal ideas inside the Food page.
- Added deletion for mistaken weigh-ins.
- Added original goal-date and actual weigh-in trend forecasts, with clear uncertainty language.

## Pip and achievements

- Pip rotates through live summaries of movement, hydration, nutrition, workouts, sleep, and weight-path data.
- Added achievements for the first workout, a 150-minute active week, and seven sleep logs.
- Achievement notifications no longer mark rewards as notified when Android notification permission is missing.

## Data and privacy

- Existing profile, food, weight, hydration, achievement, reminder, theme, and step data remain compatible.
- New workout and sleep records are stored locally in the same private app preferences.
- CSV export uses Android's system document picker; the user chooses the destination and no broad storage permission is requested.
- No network permission, advertising library, account requirement, or analytics SDK is present.

## Verification status

- Source structure, delimiter balance, manifest permissions, navigation branches, persistence migrations, and generated archive integrity were checked locally.
- Pure model behavior remains covered by the existing JUnit tests.
- The included GitHub Actions workflow is the authoritative full Android compile/test path because the local work environment does not contain Gradle or an Android SDK.
