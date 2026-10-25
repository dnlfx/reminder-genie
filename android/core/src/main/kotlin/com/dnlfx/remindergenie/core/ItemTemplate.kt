package com.dnlfx.remindergenie.core

/** A ready-made starting point for common things people replace on a schedule. */
data class ItemTemplate(
    val name: String,
    val symbolName: String,
    val color: ItemColor,
    val intervalValue: Int,
    val intervalUnit: IntervalUnit,
    val category: Category,
    val leadDays: Int = 1,
    val tracksSpares: Boolean = false,
) {
    enum class Category(val title: String) {
        HEALTH("Health & Body"),
        HOME("Home"),
        KITCHEN("Kitchen"),
        CAR("Car"),
        HABITS("Habits"),
    }

    val intervalDescription: String get() = "Every $intervalValue ${intervalUnit.label(intervalValue)}"

    /** An icon offered in the editor, with what TalkBack reads for it. */
    data class Symbol(val name: String, val label: String)

    companion object {
        private val D = IntervalUnit.DAYS
        private val W = IntervalUnit.WEEKS
        private val M = IntervalUnit.MONTHS
        private val Y = IntervalUnit.YEARS

        val all: List<ItemTemplate> = listOf(
            // Health & Body
            ItemTemplate("Contact lenses (2-week)", "eye", ItemColor.BLUE, 14, D, Category.HEALTH, tracksSpares = true),
            ItemTemplate("Contact lenses (monthly)", "eye", ItemColor.INDIGO, 1, M, Category.HEALTH, leadDays = 2, tracksSpares = true),
            ItemTemplate("Contact lens case", "eye.circle", ItemColor.TEAL, 3, M, Category.HEALTH),
            ItemTemplate("Toothbrush", "mouth", ItemColor.TEAL, 3, M, Category.HEALTH, leadDays = 3, tracksSpares = true),
            ItemTemplate("Electric toothbrush head", "bolt", ItemColor.TEAL, 3, M, Category.HEALTH, leadDays = 3, tracksSpares = true),
            ItemTemplate("Razor blade", "scissors", ItemColor.GRAY, 2, W, Category.HEALTH, tracksSpares = true),
            ItemTemplate("Mascara", "paintbrush", ItemColor.PINK, 3, M, Category.HEALTH),
            ItemTemplate("Medication refill", "pills", ItemColor.RED, 30, D, Category.HEALTH, leadDays = 5),
            ItemTemplate("Running shoes", "figure.run", ItemColor.ORANGE, 6, M, Category.HEALTH, leadDays = 7),
            // Home
            ItemTemplate("HVAC / furnace filter", "wind", ItemColor.GRAY, 3, M, Category.HOME, leadDays = 7, tracksSpares = true),
            ItemTemplate("Smoke detector batteries", "battery.100", ItemColor.RED, 1, Y, Category.HOME, leadDays = 7),
            ItemTemplate("Bed sheets", "bed.double", ItemColor.PURPLE, 1, W, Category.HOME, leadDays = 0),
            ItemTemplate("Water the plants", "leaf", ItemColor.GREEN, 1, W, Category.HOME, leadDays = 0),
            // Kitchen
            ItemTemplate("Water pitcher filter", "drop", ItemColor.BLUE, 2, M, Category.KITCHEN, leadDays = 3, tracksSpares = true),
            ItemTemplate("Fridge water filter", "refrigerator", ItemColor.BLUE, 6, M, Category.KITCHEN, leadDays = 7, tracksSpares = true),
            ItemTemplate("Kitchen sponge", "sparkles", ItemColor.YELLOW, 2, W, Category.KITCHEN, leadDays = 0, tracksSpares = true),
            // Car
            ItemTemplate("Oil change", "car", ItemColor.ORANGE, 6, M, Category.CAR, leadDays = 7),
            ItemTemplate("Wiper blades", "car.rear.waves.up", ItemColor.GRAY, 1, Y, Category.CAR, leadDays = 7),
            // Habits
            ItemTemplate("Water bottle deep clean", "waterbottle", ItemColor.TEAL, 1, W, Category.HABITS, leadDays = 0),
        )

        fun templatesIn(category: Category): List<ItemTemplate> = all.filter { it.category == category }

        /** Icons offered in the editor's icon picker. The names match the iOS app's. */
        val symbols: List<Symbol> = listOf(
            Symbol("eye", "Eye"),
            Symbol("eye.circle", "Eye in circle"),
            Symbol("mouth", "Mouth"),
            Symbol("bolt", "Bolt"),
            Symbol("scissors", "Scissors"),
            Symbol("paintbrush", "Paintbrush"),
            Symbol("pills", "Pills"),
            Symbol("cross.case", "First aid kit"),
            Symbol("figure.run", "Runner"),
            Symbol("wind", "Air"),
            Symbol("battery.100", "Battery"),
            Symbol("bed.double", "Bed"),
            Symbol("leaf", "Leaf"),
            Symbol("drop", "Water drop"),
            Symbol("refrigerator", "Fridge"),
            Symbol("sparkles", "Sparkles"),
            Symbol("car", "Car"),
            Symbol("car.rear.waves.up", "Car wipers"),
            Symbol("waterbottle", "Water bottle"),
            Symbol("flame", "Flame"),
            Symbol("house", "House"),
            Symbol("tshirt", "T-shirt"),
            Symbol("pawprint", "Pet"),
            Symbol("heart", "Heart"),
            Symbol("lightbulb", "Light bulb"),
            Symbol("trash", "Trash"),
            Symbol("calendar", "Calendar"),
            Symbol("clock", "Clock"),
        )

        /** A few common picks shown on the empty list to get started quickly. */
        val starters: List<ItemTemplate> =
            listOf("Contact lenses (2-week)", "Toothbrush", "HVAC / furnace filter", "Water pitcher filter")
                .mapNotNull { name -> all.firstOrNull { it.name == name } }
    }
}
