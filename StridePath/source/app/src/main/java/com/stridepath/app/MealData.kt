package com.stridepath.app

val meals = listOf(
    Meal("Greek yogurt berry bowl", "Breakfast", 340, 27, 42, 8, 8, "High-protein breakfast with fruit and fiber.", "1 cup plain Greek yogurt, 1/2 cup berries, 1/3 cup oats, 1 tbsp chia seeds, cinnamon"),
    Meal("Egg & veggie breakfast wrap", "Breakfast", 390, 28, 36, 14, 7, "Savory and portable.", "2 eggs, 1/4 cup egg whites, 1/2 cup peppers/spinach, 1 large whole-wheat tortilla, 2 tbsp salsa"),
    Meal("Overnight protein oats", "Breakfast", 410, 30, 54, 10, 9, "Prep the night before.", "1/2 cup oats, 1/2 cup milk, 1/2 cup Greek yogurt, 1/2 cup berries, 1 tbsp chia seeds"),
    Meal("Avocado egg toast", "Breakfast", 430, 24, 38, 20, 8, "Fiber, protein and healthy fat.", "2 slices whole-grain toast, 2 eggs, 1/2 small avocado, 1/2 cup tomato"),
    Meal("Cottage cheese fruit crunch", "Breakfast", 360, 29, 45, 8, 7, "Fast no-cook option.", "1 cup low-fat cottage cheese, 1/2 cup berries, 1/2 banana, 1/3 cup high-fiber cereal"),
    Meal("Banana peanut butter oatmeal", "Breakfast", 420, 19, 61, 14, 10, "Comforting whole-grain breakfast.", "1/2 cup oats, 1 small banana, 1 tbsp peanut butter, 3/4 cup milk, cinnamon"),
    Meal("Chicken crunch salad", "Lunch", 470, 43, 38, 16, 10, "Large vegetable portion with lean protein.", "4 oz cooked chicken breast, 2 cups romaine, 1/2 cup cucumber, 1/2 cup tomato, 1/3 cup chickpeas, 1 tbsp vinaigrette"),
    Meal("Turkey avocado wrap", "Lunch", 480, 36, 44, 18, 9, "Balanced, filling lunch.", "4 oz sliced turkey, 1 large whole-wheat wrap, 1/4 avocado, 1 cup lettuce/tomato, 1 tbsp mustard"),
    Meal("Mediterranean tuna bowl", "Lunch", 450, 39, 37, 15, 10, "No-cook protein-and-fiber bowl.", "1 can (5 oz) tuna, 1/2 cup white beans, 1 cup tomato/cucumber, 2 cups greens, 1 tsp olive oil, lemon"),
    Meal("Chicken burrito bowl", "Lunch", 520, 44, 59, 13, 12, "Meal-prep friendly.", "4 oz cooked chicken, 1/2 cup black beans, 1/2 cup cooked rice, 1 cup lettuce, 1/4 cup salsa, 1/3 cup corn"),
    Meal("Lentil soup & turkey sandwich", "Lunch", 510, 38, 60, 12, 13, "Warm, high-fiber lunch.", "1 1/2 cups lentil soup, 2 slices whole-grain bread, 3 oz turkey, 1/2 cup greens, mustard"),
    Meal("Tofu sesame veggie bowl", "Lunch", 500, 28, 58, 18, 11, "Plant-forward bowl with protein and crunch.", "4 oz tofu, 1/2 cup shelled edamame, 1/2 cup cooked brown rice, 1 cup cabbage/cucumber, 1 tbsp sesame dressing"),
    Meal("Sheet-pan chicken & vegetables", "Dinner", 540, 48, 48, 16, 10, "Simple dinner with lots of vegetables.", "5 oz chicken, 1 1/2 cups broccoli/peppers, 6 oz potato, 2 tsp olive oil, herbs"),
    Meal("Turkey taco bowl", "Dinner", 560, 45, 58, 17, 13, "Comfort-food flavor with easy portions.", "5 oz lean ground turkey, 1/2 cup black beans, 1/2 cup cooked rice, 1 cup lettuce, 1/4 cup salsa/corn, 2 tbsp Greek yogurt"),
    Meal("Salmon, potato & green beans", "Dinner", 590, 42, 52, 23, 9, "Protein-rich dinner with unsaturated fat.", "5 oz salmon, 7 oz roasted potato, 1 1/2 cups green beans, lemon and herbs"),
    Meal("Beef & broccoli rice bowl", "Dinner", 610, 46, 67, 18, 8, "Lean beef with vegetables and rice.", "5 oz lean beef, 2 cups broccoli, 3/4 cup cooked rice, 1 tbsp low-sodium soy sauce, ginger"),
    Meal("Chicken pasta primavera", "Dinner", 620, 47, 72, 17, 11, "Pasta with protein and a large vegetable serving.", "4 oz cooked chicken, 1 1/2 cups cooked whole-wheat pasta, 1 1/2 cups vegetables, 2 tbsp parmesan"),
    Meal("Shrimp fajita plate", "Dinner", 560, 40, 55, 18, 11, "Fast skillet dinner.", "6 oz shrimp, 1 1/2 cups peppers/onions, 2 small tortillas, 1/4 cup salsa, 1/4 avocado"),
    Meal("Black bean sweet potato tacos", "Dinner", 550, 23, 82, 15, 18, "High-fiber plant-based dinner.", "3/4 cup black beans, 6 oz sweet potato, 3 small corn tortillas, 1 cup cabbage, 1/4 cup salsa, 1/4 avocado"),
    Meal("Apple + peanut butter", "Snack", 220, 7, 28, 11, 6, "Fruit plus fat/protein for staying power.", "1 medium apple, 1 1/2 tbsp peanut butter"),
    Meal("Cottage cheese snack bowl", "Snack", 200, 24, 18, 4, 3, "Quick high-protein snack.", "3/4 cup low-fat cottage cheese, 1/2 cup pineapple or berries, cinnamon"),
    Meal("Hummus crunch plate", "Snack", 210, 8, 28, 8, 7, "High-volume savory snack.", "1/4 cup hummus, 1 1/2 cups carrots/cucumber/pepper, 1 oz whole-grain crackers"),
    Meal("Protein shake + banana", "Snack", 250, 27, 32, 3, 4, "Useful when you need something fast.", "1 scoop protein powder, 1 cup milk or unsweetened alternative, 1 small banana"),
    Meal("Popcorn + string cheese", "Snack", 190, 10, 24, 6, 4, "Crunchy snack with a protein side.", "3 cups air-popped popcorn, 1 oz string cheese"),
    Meal("Edamame + orange", "Snack", 210, 14, 28, 6, 9, "Plant protein plus fruit.", "1 cup shelled edamame, 1 medium orange, pinch of salt"),
    Meal("Greek yogurt cocoa cup", "Snack", 180, 20, 19, 3, 3, "Dessert-like high-protein snack.", "3/4 cup Greek yogurt, 1 tsp cocoa powder, 1/2 cup berries, 1 tsp honey")
)

fun buildWeeklyMealPlan(targetCalories: Int): List<DayMealPlan> {
    val breakfasts = meals.filter { it.category == "Breakfast" }
    val lunches = meals.filter { it.category == "Lunch" }
    val dinners = meals.filter { it.category == "Dinner" }
    val snacks = meals.filter { it.category == "Snack" }
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    return days.mapIndexed { index, day ->
        val chosen = mutableListOf(
            breakfasts[index % breakfasts.size],
            lunches[(index + 1) % lunches.size],
            dinners[(index + 2) % dinners.size]
        )
        var total = chosen.sumOf { it.calories }
        var snackIndex = index
        while (total < targetCalories - 120 && chosen.size < 7) {
            val snack = snacks[snackIndex % snacks.size]
            if (total + snack.calories <= targetCalories + 180) {
                chosen += snack
                total += snack.calories
            }
            snackIndex++
            if (snackIndex > index + snacks.size * 3) break
        }
        DayMealPlan(
            day = day,
            meals = chosen,
            totalCalories = total,
            totalProtein = chosen.sumOf { it.protein },
            totalCarbs = chosen.sumOf { it.carbs },
            totalFat = chosen.sumOf { it.fat },
            totalFiber = chosen.sumOf { it.fiber }
        )
    }
}
