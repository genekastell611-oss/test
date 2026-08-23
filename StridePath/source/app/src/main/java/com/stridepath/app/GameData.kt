package com.stridepath.app

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

val gameThemes = listOf(
    GameTheme("pixel", "Pixel Quest", "8-bit overworld • bright and chunky", 1, "🟩"),
    GameTheme("arcade", "Neon Arcade", "80s arcade glow • score-chasing energy", 3, "🕹️"),
    GameTheme("fantasy", "Fantasy Guild", "Quest boards • potions • boss battles", 5, "🛡️"),
    GameTheme("cozy", "Cozy Adventure", "Soft village vibes • gentle streaks", 7, "🍄"),
    GameTheme("cyber", "Cyber Runner", "Futuristic city • combo meters", 10, "🌃"),
    GameTheme("space", "Star Voyage", "Galaxy missions • expedition progress", 15, "🚀"),
    GameTheme("monster", "Creature Trainer", "Companion evolution • badge hunt", 20, "🥚")
)

val pipCosmetics = listOf(
    PipCosmetic("shield", "Forest Shield", "A bright quest shield for Pip.", 75, 2, "🛡️"),
    PipCosmetic("headphones", "Focus Headset", "Headphones for training sessions.", 125, 4, "🎧"),
    PipCosmetic("wizard", "Wellness Wizard Hat", "A legendary-looking thinking cap.", 225, 7, "🧙"),
    PipCosmetic("spark", "Victory Trail", "Extra spark effects around Pip.", 350, 10, "✨")
)

object GameEngine {
    fun dailyQuests(store: AppStore, profile: UserProfile, plan: GoalPlan, date: LocalDate = LocalDate.now()): List<DailyQuest> {
        val dateString = date.toString()
        val foods = store.loadFood(dateString)
        val calories = foods.sumOf { it.calories }
        val mealsLogged = foods.count { it.mealType != "Drink" }.coerceAtMost(3)
        val calorieBandHit = if (calories in (plan.mealCalorieTarget - 250)..(plan.mealCalorieTarget + 250)) 1 else 0
        val exerciseMinutes = store.loadExercise(dateString).sumOf { it.minutes }
        val sleepLogged = if (store.sleepHours(dateString) > 0f) 1 else 0
        val checkInLogged = if (store.wellness(dateString) != null) 1 else 0
        return listOf(
            DailyQuest("steps", "Defeat the Step Boss", "Reach today’s walking target", store.dailySteps(dateString), plan.balancedStepTarget, 120, "⚔️"),
            DailyQuest("water", "Potion Run", "Log ${profile.waterGoalFlOz} fl oz of water", store.waterFlOz(dateString), profile.waterGoalFlOz, 60, "🧪"),
            DailyQuest("food", "Inventory Check", "Log 3 meals/snacks", mealsLogged, 3, 50, "🎒"),
            DailyQuest("calories", "Land in the Green Zone", "Finish within ±250 Calories of the planning target", calorieBandHit, 1, 80, "🎯"),
            DailyQuest("exercise", "Training Session", "Log 20 minutes of intentional movement", exerciseMinutes.coerceAtMost(20), 20, 80, "🏋️"),
            DailyQuest("sleep", "Recovery Report", "Log last night’s sleep", sleepLogged, 1, 50, "🌙"),
            DailyQuest("checkin", "Status Check", "Log mood, energy, and stress", checkInLogged, 1, 40, "🧠")
        )
    }

    fun achievements(store: AppStore, profile: UserProfile, plan: GoalPlan): List<Achievement> {
        val today = LocalDate.now()
        val walkDays = store.walkingGoalDays(plan.balancedStepTarget)
        val streak = store.currentWalkingStreak(plan.balancedStepTarget)
        val foodDays = store.totalLoggedFoodDays()
        val weights = store.loadWeights().size
        val perfect = store.perfectDays(plan, profile)
        val fullClears = store.fullClearDays(plan, profile)
        val totalSteps7 = (0..6).sumOf { store.dailySteps(today.minusDays(it.toLong()).toString()) }
        val maxSteps = store.maxDailySteps()
        val latestWeight = store.latestWeight(profile.currentWeightLb)
        val poundsLost = (profile.currentWeightLb - latestWeight).coerceAtLeast(0.0)
        val exerciseSessions = store.totalExerciseSessions()
        val exerciseMinutes7 = store.exerciseMinutesSince(today.minusDays(6))
        val sleepDays = store.sleepLoggedDays()
        val goalFraction = if (profile.loseLb <= 0.0) 0.0 else (poundsLost / profile.loseLb).coerceIn(0.0, 1.0)
        val permanent = store.unlockedAchievementIds()
        val candidates = listOf(
            Achievement("spawn", "Spawn Point", "Set up your first StridePath campaign.", 100, true, "🗺️"),
            Achievement("first1k", "Boots Equipped", "Walk 1,000 steps in a day.", 50, maxSteps >= 1000, "🥾"),
            Achievement("first5k", "Trail Scout", "Walk 5,000 steps in a day.", 75, maxSteps >= 5000, "🧭"),
            Achievement("goal1", "Daily Boss Defeated", "Clear your walking target once.", 125, walkDays >= 1, "👾", "Uncommon"),
            Achievement("streak3", "Combo x3", "Clear the walking quest 3 days in a row.", 150, streak >= 3, "🔥", "Uncommon"),
            Achievement("streak7", "Seven-Day Saga", "Clear the walking quest 7 days in a row.", 300, streak >= 7, "📜", "Rare"),
            Achievement("streak30", "Legendary Campaign", "Clear the walking quest 30 days in a row.", 1000, streak >= 30, "👑", "Legendary"),
            Achievement("water", "Potion Collector", "Hit your hydration goal in a day.", 75, store.hydrationGoalDays(profile.waterGoalFlOz) >= 1, "🧪"),
            Achievement("food3", "Inventory Master", "Log food on 3 different days.", 100, foodDays >= 3, "🎒"),
            Achievement("food14", "Master Provisioner", "Log food on 14 different days.", 350, foodDays >= 14, "🍱", "Rare"),
            Achievement("weight1", "Checkpoint Saved", "Log your first weigh-in.", 75, weights >= 1, "💾"),
            Achievement("workout1", "Training Started", "Log your first workout session.", 75, exerciseSessions >= 1, "🏋️"),
            Achievement("active150", "Active Week", "Log 150 workout minutes across the last 7 days.", 250, exerciseMinutes7 >= 150, "⚡", "Rare"),
            Achievement("sleep7", "Recovery Scout", "Log sleep on 7 different days.", 150, sleepDays >= 7, "🌙", "Uncommon"),
            Achievement("lost1", "First Pound", "Record at least 1 lb of progress from your start.", 150, poundsLost >= 1.0, "⭐", "Uncommon"),
            Achievement("lost5", "Five-Pound Dungeon", "Record 5 lb of progress from your start.", 400, poundsLost >= 5.0, "🏰", "Rare"),
            Achievement("goal10", "First Map Fragment", "Complete 10% of your weight quest.", 150, goalFraction >= 0.10, "🧩", "Uncommon"),
            Achievement("goal25", "Quarter Quest", "Complete 25% of your weight quest.", 300, goalFraction >= 0.25, "🗝️", "Rare"),
            Achievement("goal50", "Halfway Hero", "Complete 50% of your weight quest.", 600, goalFraction >= 0.50, "⚔️", "Epic"),
            Achievement("goal75", "Final Region", "Complete 75% of your weight quest.", 900, goalFraction >= 0.75, "🏔️", "Epic"),
            Achievement("goal100", "Campaign Complete", "Reach the campaign goal weight.", 2000, goalFraction >= 1.0, "🏆", "Legendary"),
            Achievement("week35", "World Map Explorer", "Walk 35,000 steps across 7 days.", 250, totalSteps7 >= 35000, "🌍"),
            Achievement("week70", "Fast Travel Unlocked", "Walk 70,000 steps across 7 days.", 500, totalSteps7 >= 70000, "✨", "Epic"),
            Achievement("perfect", "Perfect Run", "Complete steps, hydration and calorie-zone quests on the same day.", 300, perfect >= 1, "💎", "Rare"),
            Achievement("perfect7", "Platinum Week", "Complete 7 perfect days.", 1200, perfect >= 7, "🏆", "Legendary"),
            Achievement("fullclear", "Full Quest Clear", "Complete every daily quest on the same day.", 500, fullClears >= 1, "🌟", "Epic"),
            Achievement("fullclear7", "Seven Perfect Saves", "Complete every daily quest on 7 days.", 1800, fullClears >= 7, "💫", "Legendary")
        )
        val unlockedNow = candidates.filter { it.unlocked }.map { it.id }.toSet()
        store.unlockAchievements(unlockedNow)
        val allPermanent = permanent + unlockedNow
        return candidates.map { it.copy(unlocked = it.id in allPermanent) }
    }

    fun playerStats(store: AppStore, profile: UserProfile, plan: GoalPlan): PlayerStats {
        weeklyQuests(store, profile, plan)
        val unlocked = achievements(store, profile, plan).filter { it.unlocked }
        val walkDays = store.walkingGoalDays(plan.balancedStepTarget)
        val waterDays = store.hydrationGoalDays(profile.waterGoalFlOz)
        val foodQuestDays = store.foodQuestDays()
        val nutritionDays = store.nutritionGoalDays(plan)
        val perfect = store.perfectDays(plan, profile)
        val fullClears = store.fullClearDays(plan, profile)
        val exerciseDays = store.exerciseGoalDays()
        val sleepDays = store.sleepLoggedDays()
        val checkInDays = store.wellnessLoggedDays()
        val raidXp: Int = store.weeklyRaidKeys().fold(0) { total: Int, key: String ->
            total + when {
                key.endsWith("weekly_food") || key.endsWith("weekly_water") -> 350
                key.endsWith("weekly_sleep") -> 300
                else -> 500
            }
        }
        val behaviorXp: Int = walkDays * 120 + waterDays * 60 + foodQuestDays * 50 + nutritionDays * 80 +
            exerciseDays * 80 + sleepDays * 50 + checkInDays * 40 + perfect * 100 + fullClears * 200 + raidXp
        val xp: Int = unlocked.fold(0) { total, achievement -> total + achievement.xp } + behaviorXp
        val level = 1 + levelFromXp(xp)
        val prior = xpForLevel(level - 1)
        val next = xpForLevel(level)
        return PlayerStats(
            xp = xp,
            level = level,
            xpIntoLevel = (xp - prior).coerceAtLeast(0),
            xpForNextLevel = max(1, next - prior),
            coins = (xp / 10 - store.spentCoins()).coerceAtLeast(0),
            walkingStreak = store.currentWalkingStreak(plan.balancedStepTarget),
            perfectDays = perfect,
            achievementsUnlocked = unlocked.size,
            selectedTheme = store.selectedTheme()
        )
    }

    private fun levelFromXp(xp: Int): Int {
        var level = 0
        while (xp >= xpForLevel(level + 1) && level < 99) level++
        return level
    }

    private fun xpForLevel(level: Int): Int = if (level <= 0) 0 else 200 * level * level

    fun bossHpRemaining(steps: Int, target: Int): Int = ((1f - steps.toFloat() / target.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)

    fun weeklyQuests(store: AppStore, profile: UserProfile, plan: GoalPlan): List<DailyQuest> {
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        val daysSoFar = (0..6).map { monday.plusDays(it.toLong()) }.filter { !it.isAfter(today) }
        val stepDays = daysSoFar.count { store.dailySteps(it.toString()) >= plan.balancedStepTarget }
        val foodDays = daysSoFar.count { store.loadFood(it.toString()).isNotEmpty() }
        val waterDays = daysSoFar.count { store.waterFlOz(it.toString()) >= profile.waterGoalFlOz }
        val sleepDays = daysSoFar.count { store.sleepHours(it.toString()) > 0f }
        val workoutMinutes = store.loadExerciseRange(monday, today).sumOf { it.minutes }
        val quests = listOf(
            DailyQuest("weekly_steps", "Step Dragon", "Clear your step goal on 5 days", stepDays, 5, 500, "🐉"),
            DailyQuest("weekly_food", "Inventory Raid", "Log food on 5 days", foodDays, 5, 350, "🎒"),
            DailyQuest("weekly_water", "Potion Mastery", "Hit hydration on 5 days", waterDays, 5, 350, "🧪"),
            DailyQuest("weekly_sleep", "Dream Temple", "Log sleep on 5 days", sleepDays, 5, 300, "🌙"),
            DailyQuest("weekly_active", "Training Grounds", "Log 150 workout minutes", workoutMinutes.coerceAtMost(150), 150, 500, "⚡")
        )
        quests.filter { it.complete }.forEach { store.markWeeklyRaid("$monday-${it.id}") }
        return quests
    }

    fun buddyMood(store: AppStore, profile: UserProfile, plan: GoalPlan): String {
        val steps = store.dailySteps()
        val boss = bossHpRemaining(steps, plan.balancedStepTarget)
        val walkingStreak = store.currentWalkingStreak(plan.balancedStepTarget)
        return when {
            boss == 0 -> "Boss down! That was a clean clear. Loot secured."
            walkingStreak >= 7 -> "Your streak aura is ridiculous right now. Keep the combo alive!"
            boss <= 25 -> "Boss is in the red! One more short walk could finish it."
            store.waterFlOz() < max(8, profile.waterGoalFlOz / 2) -> "I found a potion. Hydration side quest?"
            store.loadFood().isEmpty() -> "Your food inventory is empty. Log your next meal so we can see the full map."
            else -> "Quest board is active. We only need the next small win—not a perfect day."
        }
    }

    fun buddyMessages(store: AppStore, profile: UserProfile, plan: GoalPlan): List<String> {
        val steps = store.dailySteps()
        val stepLeft = (plan.balancedStepTarget - steps).coerceAtLeast(0)
        val water = store.waterFlOz()
        val waterLeft = (profile.waterGoalFlOz - water).coerceAtLeast(0)
        val calories = store.loadFood().sumOf { it.calories }
        val calorieDifference = plan.mealCalorieTarget - calories
        val workoutMinutes = store.loadExercise().sumOf { it.minutes }
        val sleep = store.sleepHours()
        val wellness = store.wellness()
        val latest = store.latestWeight(profile.currentWeightLb)
        val expected = GoalCalculator.expectedWeight(profile)
        return listOf(
            buddyMood(store, profile, plan),
            if (stepLeft == 0) "You cleared today’s step target with ${steps.formatForPip()} steps. That absolutely counts." else "${stepLeft.formatForPip()} steps remain. A few short walks can be easier than one giant quest.",
            if (waterLeft == 0) "Hydration goal logged. Potion inventory is full." else "$waterLeft fl oz remains on your hydration tracker. Sip at a pace that feels comfortable.",
            when {
                calories == 0 -> "No food is logged yet. Your next meal is enough to start—there’s no need to reconstruct a perfect day."
                calorieDifference >= 0 -> "You’ve logged ${calories.formatForPip()} Calories, about ${calorieDifference.formatForPip()} below today’s planning target so far."
                else -> "You’re about ${(-calorieDifference).formatForPip()} Calories above the estimate. One day never ruins the campaign; keep logging honestly."
            },
            if (workoutMinutes > 0) "$workoutMinutes workout minutes logged today. Nice work showing up." else "Walking counts, and so do strength, chores, sports, and mobility. Log a workout only when it helps you see the pattern.",
            if (sleep > 0f) "You logged ${"%.1f".format(sleep)} hours of sleep. Recovery is part of the campaign too." else "Want a fuller picture? Add last night’s sleep from Today. It can help explain hunger and energy patterns.",
            when {
                wellness == null -> "A quick mood, energy, and stress check-in can help us connect the dots without grading your day."
                wellness.stress >= 4 -> "Stress is logged high today. Consider making the next quest smaller and kinder, not abandoning the campaign."
                wellness.energy <= 2 -> "Energy is low today. Recovery and a gentle walk may fit better than forcing a maximum-effort session."
                wellness.mood >= 4 -> "Mood is looking strong today. Notice which repeatable choices helped—you may want them in tomorrow’s loadout."
                else -> "Check-in saved. Mood ${wellness.mood}/5, energy ${wellness.energy}/5, stress ${wellness.stress}/5. Patterns matter more than one score."
            },
            if (latest <= expected) "Your latest checkpoint is on or ahead of the projected path. Keep the habits sustainable." else "Your latest checkpoint is above the simple projected line. Trends need time—focus on the next repeatable action."
        )
    }

    private fun Int.formatForPip(): String = "%,d".format(this)
}
