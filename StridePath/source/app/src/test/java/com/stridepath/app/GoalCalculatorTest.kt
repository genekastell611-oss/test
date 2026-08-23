package com.stridepath.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalCalculatorTest {
    @Test
    fun balancedPlanAddsStepsAndSplitsDeficit() {
        val profile = UserProfile(220.0, 20.0, 16, 5000, 68.0, 35, SexEstimate.Midpoint)
        val plan = GoalCalculator.calculate(profile)
        assertTrue(plan.balancedStepTarget > profile.baselineSteps)
        assertTrue(plan.foodSideDeficit > 0)
        assertTrue(plan.kcalPerStep > 0)
        assertTrue(plan.isCommonRange)
        assertTrue(plan.mealCalorieTarget >= 1200)
        assertTrue(plan.proteinTargetG > 0)
        assertTrue(plan.carbsTargetG > 0)
        assertTrue(plan.fatTargetG > 0)
    }

    @Test
    fun aggressiveGoalIsFlagged() {
        val profile = UserProfile(220.0, 40.0, 10, 5000, 68.0, 35, SexEstimate.Midpoint)
        val plan = GoalCalculator.calculate(profile)
        assertFalse(plan.isCommonRange)
        assertTrue(plan.minimumWeeksAtTwoPerWeek >= 20)
        assertTrue(plan.balancedDailyDeficit <= 1000.0)
    }

    @Test
    fun projectedWeightReachesGoalAtEndOfTimeline() {
        val profile = UserProfile(200.0, 20.0, 10, 5000, 68.0, 35, SexEstimate.Midpoint, startDate = "2026-01-01")
        val projected = GoalCalculator.expectedWeight(profile, LocalDate.parse("2026-03-12"))
        assertEquals(180.0, projected, 0.01)
    }

    @Test
    fun weeklyMealPlanStaysReasonablyCloseToTarget() {
        val week = buildWeeklyMealPlan(2000)
        assertEquals(7, week.size)
        assertTrue(week.all { it.totalCalories in 1700..2200 })
        assertTrue(week.all { it.totalProtein > 60 })
    }

    @Test
    fun projectionDoesNotMoveBeforeCampaignStart() {
        val profile = UserProfile(200.0, 20.0, 10, 5000, 68.0, 35, SexEstimate.Midpoint, startDate = "2026-06-01")
        assertEquals(200.0, GoalCalculator.expectedWeight(profile, LocalDate.parse("2026-05-15")), 0.01)
    }

    @Test
    fun extremeTimelineNeverDropsMealTargetBelowFloor() {
        val profile = UserProfile(100.0, 90.0, 1, 1000, 48.0, 90, SexEstimate.Female, ActivityLevel.Sedentary)
        val plan = GoalCalculator.calculate(profile)
        assertEquals(1200, plan.mealCalorieTarget)
        assertTrue(plan.mealTargetWasFloored)
        assertFalse(plan.isCommonRange)
    }
}
