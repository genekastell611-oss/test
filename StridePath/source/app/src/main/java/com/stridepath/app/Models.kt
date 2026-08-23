package com.stridepath.app

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class SexEstimate(val label: String) {
    Female("Female estimate"),
    Male("Male estimate"),
    Midpoint("Midpoint estimate")
}

enum class ActivityLevel(val label: String, val multiplier: Double) {
    Sedentary("Mostly seated", 1.20),
    Light("Lightly active", 1.35),
    Moderate("Moderately active", 1.50),
    High("Very active", 1.65)
}

data class UserProfile(
    val currentWeightLb: Double,
    val loseLb: Double,
    val weeks: Int,
    val baselineSteps: Int,
    val heightIn: Double,
    val age: Int,
    val sexEstimate: SexEstimate,
    val activityLevel: ActivityLevel = ActivityLevel.Light,
    val startDate: String = LocalDate.now().toString(),
    val waterGoalFlOz: Int = 64,
    val displayName: String = "Player"
) {
    val goalWeightLb: Double get() = (currentWeightLb - loseLb).coerceAtLeast(1.0)
}

data class GoalPlan(
    val requestedRatePerWeek: Double,
    val requestedDailyDeficit: Double,
    val balancedDailyDeficit: Double,
    val balancedExtraSteps: Int,
    val balancedStepTarget: Int,
    val allWalkingExtraSteps: Int,
    val allWalkingStepTarget: Int,
    val foodSideDeficit: Int,
    val estimatedMaintenance: Int,
    val mealCalorieTarget: Int,
    val mealTargetWasFloored: Boolean,
    val isCommonRange: Boolean,
    val minimumWeeksAtTwoPerWeek: Int,
    val kcalPerStep: Double,
    val proteinTargetG: Int,
    val carbsTargetG: Int,
    val fatTargetG: Int,
    val fiberTargetG: Int,
    val targetWeightLb: Double
)

object GoalCalculator {
    fun calculate(profile: UserProfile): GoalPlan {
        val safeWeeks = max(1, profile.weeks)
        val safeHeight = profile.heightIn.coerceIn(48.0, 84.0)
        val safeWeight = profile.currentWeightLb.coerceAtLeast(80.0)
        val strideIn = safeHeight * 0.414
        val stepsPerMile = 63360.0 / strideIn
        val kcalPerMile = 0.53 * safeWeight
        val kcalPerStep = (kcalPerMile / stepsPerMile).coerceAtLeast(0.015)

        val rate = profile.loseLb / safeWeeks
        val requestedDeficit = (profile.loseLb * 3500.0) / (safeWeeks * 7.0)
        val balancedDeficit = min(requestedDeficit, 1000.0)
        val walkingShare = 0.35
        val balancedWalkingCalories = balancedDeficit * walkingShare
        val foodSide = balancedDeficit * (1.0 - walkingShare)
        val balancedExtra = ceil(balancedWalkingCalories / kcalPerStep).toInt().coerceAtLeast(0)
        val allWalkExtra = ceil(requestedDeficit / kcalPerStep).toInt().coerceAtLeast(0)

        val weightKg = safeWeight * 0.45359237
        val heightCm = safeHeight * 2.54
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * profile.age.coerceIn(18, 90)
        val bmr = when (profile.sexEstimate) {
            SexEstimate.Male -> base + 5.0
            SexEstimate.Female -> base - 161.0
            SexEstimate.Midpoint -> base - 78.0
        }
        val maintenance = (bmr * profile.activityLevel.multiplier).roundToInt().coerceAtLeast(1400)
        val rawMealTarget = maintenance - foodSide.roundToInt()
        val mealTarget = rawMealTarget.coerceAtLeast(1200)

        val protein = max(70, (mealTarget * 0.25 / 4.0).roundToInt())
        val carbs = max(100, (mealTarget * 0.45 / 4.0).roundToInt())
        val fat = max(40, (mealTarget * 0.30 / 9.0).roundToInt())
        val fiber = if (mealTarget >= 2000) 30 else 25

        return GoalPlan(
            requestedRatePerWeek = rate,
            requestedDailyDeficit = requestedDeficit,
            balancedDailyDeficit = balancedDeficit,
            balancedExtraSteps = balancedExtra,
            balancedStepTarget = profile.baselineSteps + balancedExtra,
            allWalkingExtraSteps = allWalkExtra,
            allWalkingStepTarget = profile.baselineSteps + allWalkExtra,
            foodSideDeficit = foodSide.roundToInt(),
            estimatedMaintenance = maintenance,
            mealCalorieTarget = mealTarget,
            mealTargetWasFloored = rawMealTarget < 1200,
            isCommonRange = rate in 0.0..2.0,
            minimumWeeksAtTwoPerWeek = ceil(profile.loseLb / 2.0).toInt().coerceAtLeast(1),
            kcalPerStep = kcalPerStep,
            proteinTargetG = protein,
            carbsTargetG = carbs,
            fatTargetG = fat,
            fiberTargetG = fiber,
            targetWeightLb = profile.goalWeightLb
        )
    }

    fun expectedWeight(profile: UserProfile, date: LocalDate = LocalDate.now()): Double {
        val start = runCatching { LocalDate.parse(profile.startDate) }.getOrDefault(date)
        val elapsedDays = ChronoUnit.DAYS.between(start, date).coerceAtLeast(0)
        val totalDays = (profile.weeks * 7L).coerceAtLeast(1)
        val fraction = (elapsedDays.toDouble() / totalDays).coerceIn(0.0, 1.0)
        return profile.currentWeightLb - profile.loseLb * fraction
    }
}

data class WeightEntry(val date: String, val weightLb: Double)

data class BodyMeasurement(val date: String, val waistIn: Double)

data class FoodEntry(
    val id: Long,
    val date: String,
    val name: String,
    val calories: Int,
    val protein: Int,
    val mealType: String,
    val carbs: Int = 0,
    val fat: Int = 0,
    val fiber: Int = 0
)

data class ExerciseEntry(
    val id: Long,
    val date: String,
    val name: String,
    val minutes: Int,
    val calories: Int = 0,
    val notes: String = ""
)

data class WellnessCheckIn(
    val date: String,
    val mood: Int,
    val energy: Int,
    val stress: Int,
    val note: String = ""
)

enum class TrackingPeriod(val label: String) {
    Day("Day"), Week("Week"), Month("Month")
}

data class Meal(
    val name: String,
    val category: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val fiber: Int,
    val description: String,
    val ingredients: String
)

data class DayMealPlan(
    val day: String,
    val meals: List<Meal>,
    val totalCalories: Int,
    val totalProtein: Int,
    val totalCarbs: Int,
    val totalFat: Int,
    val totalFiber: Int
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val xp: Int,
    val unlocked: Boolean,
    val icon: String,
    val rarity: String = "Common"
)

data class DailyQuest(
    val id: String,
    val title: String,
    val description: String,
    val current: Int,
    val target: Int,
    val xp: Int,
    val icon: String
) {
    val complete: Boolean get() = current >= target
    val progress: Float get() = (current.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
}

data class GameTheme(
    val id: String,
    val name: String,
    val subtitle: String,
    val minLevel: Int,
    val emoji: String
)

data class PipCosmetic(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    val minLevel: Int,
    val icon: String
)

data class PlayerStats(
    val xp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
    val coins: Int,
    val walkingStreak: Int,
    val perfectDays: Int,
    val achievementsUnlocked: Int,
    val selectedTheme: String
)
