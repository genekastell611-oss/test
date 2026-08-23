package com.stridepath.app

import android.content.Context
import java.time.LocalDate

class AppStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("stridepath_data", Context.MODE_PRIVATE)

    fun loadProfile(): UserProfile? {
        val v4 = prefs.getString("profile_v4", null)
        val raw = v4
            ?: prefs.getString("profile_v3", null)
            ?: prefs.getString("profile_v2", null)
            ?: appContext.getSharedPreferences("stridepath_profile", Context.MODE_PRIVATE).getString("profile", null)
            ?: return null
        val p = raw.split("|")
        val usesFluidOunces = v4 != null
        return runCatching {
            when {
                p.size >= 11 -> UserProfile(
                    currentWeightLb = p[0].toDouble(), loseLb = p[1].toDouble(), weeks = p[2].toInt(), baselineSteps = p[3].toInt(),
                    heightIn = p[4].toDouble(), age = p[5].toInt(), sexEstimate = SexEstimate.valueOf(p[6]),
                    activityLevel = ActivityLevel.valueOf(p[7]), startDate = p[8],
                    waterGoalFlOz = if (usesFluidOunces) p[9].toInt() else p[9].toInt() * 8,
                    displayName = p.drop(10).joinToString("|").replace("~p~", "|").take(20)
                )
                p.size >= 7 -> UserProfile(
                    p[0].toDouble(), p[1].toDouble(), p[2].toInt(), p[3].toInt(), p[4].toDouble(),
                    p[5].toInt(), SexEstimate.valueOf(p[6]), ActivityLevel.Light, LocalDate.now().toString(), 64, "Player"
                )
                p.size == 5 -> UserProfile(
                    p[0].toDouble(), p[1].toDouble(), p[2].toInt(), p[3].toInt(), p[4].toDouble(),
                    35, SexEstimate.Midpoint, ActivityLevel.Light, LocalDate.now().toString(), 64, "Player"
                )
                else -> null
            }
        }.getOrNull()
    }

    fun saveProfile(profile: UserProfile) {
        val raw = listOf(
            profile.currentWeightLb, profile.loseLb, profile.weeks, profile.baselineSteps, profile.heightIn,
            profile.age, profile.sexEstimate.name, profile.activityLevel.name, profile.startDate, profile.waterGoalFlOz,
            profile.displayName.replace("|", " ").replace(";;", " ").take(20)
        ).joinToString("|")
        prefs.edit().putString("profile_v4", raw).apply()
    }

    fun loadWeights(): List<WeightEntry> {
        var raw = prefs.getString("weights", "").orEmpty()
        if (raw.isBlank()) raw = appContext.getSharedPreferences("stridepath_profile", Context.MODE_PRIVATE).getString("weights", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { item ->
            val p = item.split("|")
            if (p.size != 2) null else p[1].toDoubleOrNull()?.let { WeightEntry(p[0], it) }
        }.sortedBy { it.date }
    }

    fun latestWeight(default: Double): Double = loadWeights().lastOrNull()?.weightLb ?: default

    fun addWeight(entry: WeightEntry) {
        val list = loadWeights().toMutableList()
        list.removeAll { it.date == entry.date }
        list.add(entry)
        prefs.edit().putString("weights", list.sortedBy { it.date }.takeLast(400).joinToString(";") { "${it.date}|${it.weightLb}" }).apply()
    }

    fun deleteWeight(date: String) {
        val list = loadWeights().filterNot { it.date == date }
        prefs.edit().putString("weights", list.joinToString(";") { "${it.date}|${it.weightLb}" }).apply()
    }

    fun loadMeasurements(): List<BodyMeasurement> {
        val raw = prefs.getString("measurements_v1", "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(";").mapNotNull { item ->
            val p = item.split("|")
            if (p.size != 2) null else p[1].toDoubleOrNull()?.let { BodyMeasurement(p[0], it) }
        }.sortedBy { it.date }
    }

    fun addMeasurement(entry: BodyMeasurement) {
        val list = loadMeasurements().filterNot { it.date == entry.date } + entry
        prefs.edit().putString("measurements_v1", list.sortedBy { it.date }.takeLast(400).joinToString(";") { "${it.date}|${it.waistIn.coerceIn(10.0, 100.0)}" }).apply()
    }

    fun deleteMeasurement(date: String) {
        prefs.edit().putString("measurements_v1", loadMeasurements().filterNot { it.date == date }.joinToString(";") { "${it.date}|${it.waistIn}" }).apply()
    }

    fun loadFood(date: String = LocalDate.now().toString()): List<FoodEntry> = allFood().filter { it.date == date }.sortedBy { it.id }

    fun loadFoodRange(start: LocalDate, end: LocalDate): List<FoodEntry> = allFood().filter {
        runCatching {
            val d = LocalDate.parse(it.date)
            !d.isBefore(start) && !d.isAfter(end)
        }.getOrDefault(false)
    }

    private fun allFood(): MutableList<FoodEntry> {
        val raw = prefs.getString("food_entries_v2", null) ?: prefs.getString("food_entries", "").orEmpty()
        if (raw.isBlank()) return mutableListOf()
        return raw.split(";;").mapNotNull { item ->
            val p = item.split("|")
            if (p.size < 6) null else runCatching {
                FoodEntry(
                    id = p[0].toLong(), date = p[1], name = p[2].replace("~p~", "|"), calories = p[3].toInt(),
                    protein = p[4].toInt(), mealType = p[5], carbs = p.getOrNull(6)?.toIntOrNull() ?: 0,
                    fat = p.getOrNull(7)?.toIntOrNull() ?: 0, fiber = p.getOrNull(8)?.toIntOrNull() ?: 0
                )
            }.getOrNull()
        }.toMutableList()
    }

    fun addFood(entry: FoodEntry) {
        val list = allFood().apply { add(entry) }
        saveFoodList(list)
    }

    fun deleteFood(id: Long) {
        val list = allFood().apply { removeAll { it.id == id } }
        saveFoodList(list)
    }

    fun totalLoggedFoodDays(): Int = allFood().map { it.date }.distinct().size

    private fun saveFoodList(list: List<FoodEntry>) {
        val trimmed = list.sortedBy { it.id }.takeLast(1600)
        val raw = trimmed.joinToString(";;") {
            "${it.id}|${it.date}|${it.name.replace(";;", " ").replace("|", "~p~")}|${it.calories.coerceIn(0, 10000)}|${it.protein.coerceIn(0, 1000)}|${it.mealType.replace("|", "")}|${it.carbs.coerceIn(0, 2000)}|${it.fat.coerceIn(0, 1000)}|${it.fiber.coerceIn(0, 500)}"
        }
        prefs.edit().putString("food_entries_v2", raw).apply()
    }

    fun loadExercise(date: String = LocalDate.now().toString()): List<ExerciseEntry> =
        allExercise().filter { it.date == date }.sortedBy { it.id }

    fun loadExerciseRange(start: LocalDate, end: LocalDate): List<ExerciseEntry> = allExercise().filter {
        runCatching {
            val date = LocalDate.parse(it.date)
            !date.isBefore(start) && !date.isAfter(end)
        }.getOrDefault(false)
    }

    private fun allExercise(): MutableList<ExerciseEntry> {
        val raw = prefs.getString("exercise_entries_v1", "").orEmpty()
        if (raw.isBlank()) return mutableListOf()
        return raw.split(";;").mapNotNull { item ->
            val p = item.split("|")
            if (p.size < 5) null else runCatching {
                ExerciseEntry(
                    id = p[0].toLong(),
                    date = p[1],
                    name = p[2].replace("~p~", "|"),
                    minutes = p[3].toInt().coerceIn(1, 1440),
                    calories = p[4].toInt().coerceIn(0, 10000),
                    notes = p.getOrNull(5)?.replace("~p~", "|").orEmpty()
                )
            }.getOrNull()
        }.toMutableList()
    }

    fun addExercise(entry: ExerciseEntry) = saveExerciseList(allExercise().apply { add(entry) })

    fun deleteExercise(id: Long) = saveExerciseList(allExercise().apply { removeAll { it.id == id } })

    fun totalExerciseSessions(): Int = allExercise().size

    fun exerciseMinutesSince(start: LocalDate): Int = allExercise().filter {
        runCatching { !LocalDate.parse(it.date).isBefore(start) }.getOrDefault(false)
    }.sumOf { it.minutes }

    fun exerciseDays(lookback: Int = 365): Int = allExercise().map { it.date }.distinct().count { date ->
        runCatching { !LocalDate.parse(date).isBefore(LocalDate.now().minusDays(lookback.toLong() - 1)) }.getOrDefault(false)
    }

    fun exerciseGoalDays(targetMinutes: Int = 20, lookback: Int = 365): Int {
        val start = LocalDate.now().minusDays(lookback.toLong() - 1)
        return allExercise().filter { runCatching { !LocalDate.parse(it.date).isBefore(start) }.getOrDefault(false) }
            .groupBy { it.date }.count { (_, entries) -> entries.sumOf { it.minutes } >= targetMinutes }
    }

    fun sleepLoggedDays(lookback: Int = 365): Int {
        val today = LocalDate.now()
        return (0 until lookback).count { sleepHours(today.minusDays(it.toLong()).toString()) > 0f }
    }

    private fun saveExerciseList(list: List<ExerciseEntry>) {
        val raw = list.sortedBy { it.id }.takeLast(1200).joinToString(";;") {
            "${it.id}|${it.date}|${it.name.cleanField()}|${it.minutes.coerceIn(1, 1440)}|${it.calories.coerceIn(0, 10000)}|${it.notes.cleanField()}"
        }
        prefs.edit().putString("exercise_entries_v1", raw).apply()
    }

    fun waterCount(date: String = LocalDate.now().toString()): Int = prefs.getInt("water_$date", 0)
    fun waterFlOz(date: String = LocalDate.now().toString()): Int = waterCount(date) * 8
    fun addWater(date: String = LocalDate.now().toString()) = prefs.edit().putInt("water_$date", waterCount(date) + 1).apply()
    fun removeWater(date: String = LocalDate.now().toString()) = prefs.edit().putInt("water_$date", (waterCount(date) - 1).coerceAtLeast(0)).apply()

    fun sleepHours(date: String = LocalDate.now().toString()): Float = prefs.getFloat("sleep_hours_$date", 0f).coerceIn(0f, 24f)
    fun sleepQuality(date: String = LocalDate.now().toString()): Int = prefs.getInt("sleep_quality_$date", 0).coerceIn(0, 5)
    fun saveSleep(hours: Float, quality: Int, date: String = LocalDate.now().toString()) {
        prefs.edit().putFloat("sleep_hours_$date", hours.coerceIn(0f, 24f)).putInt("sleep_quality_$date", quality.coerceIn(0, 5)).apply()
    }

    fun wellness(date: String = LocalDate.now().toString()): WellnessCheckIn? {
        val raw = prefs.getString("wellness_$date", null) ?: return null
        val p = raw.split("|")
        return if (p.size < 3) null else runCatching {
            WellnessCheckIn(
                date = date,
                mood = p[0].toInt().coerceIn(1, 5),
                energy = p[1].toInt().coerceIn(1, 5),
                stress = p[2].toInt().coerceIn(1, 5),
                note = p.drop(3).joinToString("|").replace("~p~", "|")
            )
        }.getOrNull()
    }

    fun saveWellness(entry: WellnessCheckIn) {
        val raw = "${entry.mood.coerceIn(1, 5)}|${entry.energy.coerceIn(1, 5)}|${entry.stress.coerceIn(1, 5)}|${entry.note.cleanField()}"
        prefs.edit().putString("wellness_${entry.date}", raw).apply()
    }

    fun wellnessLoggedDays(lookback: Int = 365): Int {
        val today = LocalDate.now()
        return (0 until lookback).count { wellness(today.minusDays(it.toLong()).toString()) != null }
    }

    fun waterReminderEnabled(): Boolean = prefs.getBoolean("water_reminder_enabled", false)
    fun setWaterReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean("water_reminder_enabled", enabled).apply()
    fun waterReminderHours(): Int = prefs.getInt("water_reminder_hours", 2).coerceIn(1, 6)
    fun setWaterReminderHours(hours: Int) = prefs.edit().putInt("water_reminder_hours", hours.coerceIn(1, 6)).apply()

    fun questReminderEnabled(): Boolean = prefs.getBoolean("quest_reminder_enabled", true)
    fun setQuestReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean("quest_reminder_enabled", enabled).apply()
    fun mealReminderEnabled(): Boolean = prefs.getBoolean("meal_reminder_enabled", false)
    fun setMealReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean("meal_reminder_enabled", enabled).apply()
    fun weighInReminderEnabled(): Boolean = prefs.getBoolean("weighin_reminder_enabled", true)
    fun setWeighInReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean("weighin_reminder_enabled", enabled).apply()

    fun saveDailySteps(steps: Int, date: String = LocalDate.now().toString()) {
        val old = prefs.getInt("steps_$date", 0)
        if (steps > old) prefs.edit().putInt("steps_$date", steps).apply()
    }

    fun setDailySteps(steps: Int, date: String = LocalDate.now().toString()) {
        prefs.edit().putInt("steps_$date", steps.coerceAtLeast(0)).apply()
    }

    fun dailySteps(date: String = LocalDate.now().toString()): Int = prefs.getInt("steps_$date", 0)

    fun walkingGoalDays(goal: Int, lookback: Int = 365): Int {
        val today = LocalDate.now()
        return (0 until lookback).count { dailySteps(today.minusDays(it.toLong()).toString()) >= goal }
    }

    fun currentWalkingStreak(goal: Int): Int {
        var streak = 0
        var day = LocalDate.now()
        if (dailySteps(day.toString()) < goal) day = day.minusDays(1)
        while (streak < 3650 && dailySteps(day.toString()) >= goal) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun perfectDays(plan: GoalPlan, profile: UserProfile, lookback: Int = 365): Int {
        val today = LocalDate.now()
        val foodsByDate = loadFoodRange(today.minusDays(lookback.toLong() - 1), today).groupBy { it.date }
        return (0 until lookback).count { offset ->
            val date = today.minusDays(offset.toLong())
            val dateString = date.toString()
            val foods = foodsByDate[dateString].orEmpty()
            val calories = foods.sumOf { it.calories }
            val stepDone = dailySteps(dateString) >= plan.balancedStepTarget
            val waterDone = waterFlOz(dateString) >= profile.waterGoalFlOz
            val foodDone = calories in (plan.mealCalorieTarget - 250)..(plan.mealCalorieTarget + 250)
            stepDone && waterDone && foodDone
        }
    }

    fun fullClearDays(plan: GoalPlan, profile: UserProfile, lookback: Int = 365): Int {
        val today = LocalDate.now()
        val start = today.minusDays(lookback.toLong() - 1)
        val foodsByDate = loadFoodRange(start, today).groupBy { it.date }
        val exerciseByDate = loadExerciseRange(start, today).groupBy { it.date }
        return (0 until lookback).count { offset ->
            val date = today.minusDays(offset.toLong())
            val key = date.toString()
            val foods = foodsByDate[key].orEmpty()
            dailySteps(key) >= plan.balancedStepTarget &&
                waterFlOz(key) >= profile.waterGoalFlOz &&
                foods.count { it.mealType != "Drink" } >= 3 &&
                foods.sumOf { it.calories } in (plan.mealCalorieTarget - 250)..(plan.mealCalorieTarget + 250) &&
                exerciseByDate[key].orEmpty().sumOf { it.minutes } >= 20 &&
                sleepHours(key) > 0f && wellness(key) != null
        }
    }


    fun maxDailySteps(lookback: Int = 365): Int {
        val today = LocalDate.now()
        return (0 until lookback).maxOfOrNull { dailySteps(today.minusDays(it.toLong()).toString()) } ?: 0
    }

    fun hydrationGoalDays(goalFlOz: Int, lookback: Int = 365): Int {
        val today = LocalDate.now()
        return (0 until lookback).count { waterFlOz(today.minusDays(it.toLong()).toString()) >= goalFlOz }
    }

    fun foodQuestDays(lookback: Int = 365): Int {
        val today = LocalDate.now()
        val foodsByDate = loadFoodRange(today.minusDays(lookback.toLong() - 1), today).groupBy { it.date }
        return (0 until lookback).count { offset ->
            foodsByDate[today.minusDays(offset.toLong()).toString()].orEmpty().count { it.mealType != "Drink" } >= 3
        }
    }

    fun nutritionGoalDays(plan: GoalPlan, lookback: Int = 365): Int {
        val today = LocalDate.now()
        val foodsByDate = loadFoodRange(today.minusDays(lookback.toLong() - 1), today).groupBy { it.date }
        return (0 until lookback).count { offset ->
            val calories = foodsByDate[today.minusDays(offset.toLong()).toString()].orEmpty().sumOf { it.calories }
            calories in (plan.mealCalorieTarget - 250)..(plan.mealCalorieTarget + 250)
        }
    }

    fun unlockedAchievementIds(): Set<String> = prefs.getStringSet("unlocked_achievements", emptySet()) ?: emptySet()
    fun unlockAchievements(ids: Set<String>) {
        if (ids.isNotEmpty()) prefs.edit().putStringSet("unlocked_achievements", unlockedAchievementIds() + ids).apply()
    }

    fun weeklyRaidKeys(): Set<String> = prefs.getStringSet("weekly_raid_keys", emptySet()) ?: emptySet()
    fun markWeeklyRaid(key: String) = prefs.edit().putStringSet("weekly_raid_keys", weeklyRaidKeys() + key).apply()

    fun selectedTheme(): String = prefs.getString("selected_theme", "pixel") ?: "pixel"
    fun setSelectedTheme(id: String) = prefs.edit().putString("selected_theme", id).apply()

    fun buddyName(): String = prefs.getString("buddy_name", "Pip") ?: "Pip"
    fun setBuddyName(name: String) = prefs.edit().putString("buddy_name", name.take(20)).apply()

    fun ownedCosmetics(): Set<String> = (prefs.getStringSet("owned_cosmetics", emptySet()) ?: emptySet()) + "none"
    fun spentCoins(): Int = prefs.getInt("spent_coins", 0).coerceAtLeast(0)
    fun equippedCosmetic(): String = prefs.getString("equipped_cosmetic", "none") ?: "none"
    fun buyCosmetic(id: String, price: Int, availableCoins: Int): Boolean {
        if (id in ownedCosmetics() || price < 0 || availableCoins < price) return false
        prefs.edit().putStringSet("owned_cosmetics", ownedCosmetics() + id).putInt("spent_coins", spentCoins() + price).apply()
        return true
    }
    fun equipCosmetic(id: String) {
        if (id in ownedCosmetics()) prefs.edit().putString("equipped_cosmetic", id).apply()
    }

    fun dismissedAchievementIds(): Set<String> = prefs.getStringSet("seen_achievements", emptySet()) ?: emptySet()
    fun markAchievementsSeen(ids: Set<String>) = prefs.edit().putStringSet("seen_achievements", dismissedAchievementIds() + ids).apply()

    fun notifiedAchievementIds(): Set<String> = prefs.getStringSet("notified_achievements", emptySet()) ?: emptySet()
    fun markAchievementsNotified(ids: Set<String>) = prefs.edit().putStringSet("notified_achievements", notifiedAchievementIds() + ids).apply()

    fun healthConnectEnabled(): Boolean = prefs.getBoolean("health_connect_enabled", false)
    fun setHealthConnectEnabled(enabled: Boolean) = prefs.edit().putBoolean("health_connect_enabled", enabled).apply()

    private fun String.cleanField(): String = replace(";;", " ").replace("|", "~p~").take(200)
}
