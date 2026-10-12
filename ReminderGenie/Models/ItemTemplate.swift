import Foundation

/// A ready-made starting point for common things people replace on a schedule.
struct ItemTemplate: Identifiable, Hashable {
    enum Category: String, CaseIterable, Identifiable {
        case health = "Health & Body"
        case home = "Home"
        case kitchen = "Kitchen"
        case car = "Car"
        case habits = "Habits"
        var id: String { rawValue }
    }

    var name: String
    var symbolName: String
    var color: ItemColor
    var intervalValue: Int
    var intervalUnit: IntervalUnit
    var category: Category
    var leadDays: Int = 1
    var tracksSpares: Bool = false

    var id: String { name }

    var intervalDescription: String {
        "Every \(intervalValue) \(intervalUnit.label(for: intervalValue))"
    }

    static let all: [ItemTemplate] = [
        // Health & Body
        ItemTemplate(name: "Contact lenses (2-week)", symbolName: "eye", color: .blue, intervalValue: 14, intervalUnit: .days, category: .health, tracksSpares: true),
        ItemTemplate(name: "Contact lenses (monthly)", symbolName: "eye", color: .indigo, intervalValue: 1, intervalUnit: .months, category: .health, leadDays: 2, tracksSpares: true),
        ItemTemplate(name: "Contact lens case", symbolName: "eye.circle", color: .teal, intervalValue: 3, intervalUnit: .months, category: .health),
        ItemTemplate(name: "Toothbrush", symbolName: "mouth", color: .teal, intervalValue: 3, intervalUnit: .months, category: .health, leadDays: 3, tracksSpares: true),
        ItemTemplate(name: "Electric toothbrush head", symbolName: "bolt", color: .teal, intervalValue: 3, intervalUnit: .months, category: .health, leadDays: 3, tracksSpares: true),
        ItemTemplate(name: "Razor blade", symbolName: "scissors", color: .gray, intervalValue: 2, intervalUnit: .weeks, category: .health, tracksSpares: true),
        ItemTemplate(name: "Mascara", symbolName: "paintbrush", color: .pink, intervalValue: 3, intervalUnit: .months, category: .health),
        ItemTemplate(name: "Medication refill", symbolName: "pills", color: .red, intervalValue: 30, intervalUnit: .days, category: .health, leadDays: 5),
        ItemTemplate(name: "Running shoes", symbolName: "figure.run", color: .orange, intervalValue: 6, intervalUnit: .months, category: .health, leadDays: 7),
        // Home
        ItemTemplate(name: "HVAC / furnace filter", symbolName: "wind", color: .gray, intervalValue: 3, intervalUnit: .months, category: .home, leadDays: 7, tracksSpares: true),
        ItemTemplate(name: "Smoke detector batteries", symbolName: "battery.100", color: .red, intervalValue: 1, intervalUnit: .years, category: .home, leadDays: 7),
        ItemTemplate(name: "Bed sheets", symbolName: "bed.double", color: .purple, intervalValue: 1, intervalUnit: .weeks, category: .home, leadDays: 0),
        ItemTemplate(name: "Water the plants", symbolName: "leaf", color: .green, intervalValue: 1, intervalUnit: .weeks, category: .home, leadDays: 0),
        // Kitchen
        ItemTemplate(name: "Water pitcher filter", symbolName: "drop", color: .blue, intervalValue: 2, intervalUnit: .months, category: .kitchen, leadDays: 3, tracksSpares: true),
        ItemTemplate(name: "Fridge water filter", symbolName: "refrigerator", color: .blue, intervalValue: 6, intervalUnit: .months, category: .kitchen, leadDays: 7, tracksSpares: true),
        ItemTemplate(name: "Kitchen sponge", symbolName: "sparkles", color: .yellow, intervalValue: 2, intervalUnit: .weeks, category: .kitchen, leadDays: 0, tracksSpares: true),
        // Car
        ItemTemplate(name: "Oil change", symbolName: "car", color: .orange, intervalValue: 6, intervalUnit: .months, category: .car, leadDays: 7),
        ItemTemplate(name: "Wiper blades", symbolName: "car.rear.waves.up", color: .gray, intervalValue: 1, intervalUnit: .years, category: .car, leadDays: 7),
        // Habits
        ItemTemplate(name: "Water bottle deep clean", symbolName: "waterbottle", color: .teal, intervalValue: 1, intervalUnit: .weeks, category: .habits, leadDays: 0),
    ]

    static func templates(in category: Category) -> [ItemTemplate] {
        all.filter { $0.category == category }
    }

    struct Symbol: Hashable {
        var name: String
        /// What VoiceOver reads for the icon.
        var label: String
    }

    /// Icons offered in the editor's icon picker.
    static let symbols: [Symbol] = [
        Symbol(name: "eye", label: "Eye"),
        Symbol(name: "eye.circle", label: "Eye in circle"),
        Symbol(name: "mouth", label: "Mouth"),
        Symbol(name: "bolt", label: "Bolt"),
        Symbol(name: "scissors", label: "Scissors"),
        Symbol(name: "paintbrush", label: "Paintbrush"),
        Symbol(name: "pills", label: "Pills"),
        Symbol(name: "cross.case", label: "First aid kit"),
        Symbol(name: "figure.run", label: "Runner"),
        Symbol(name: "wind", label: "Air"),
        Symbol(name: "battery.100", label: "Battery"),
        Symbol(name: "bed.double", label: "Bed"),
        Symbol(name: "leaf", label: "Leaf"),
        Symbol(name: "drop", label: "Water drop"),
        Symbol(name: "refrigerator", label: "Fridge"),
        Symbol(name: "sparkles", label: "Sparkles"),
        Symbol(name: "car", label: "Car"),
        Symbol(name: "car.rear.waves.up", label: "Car wipers"),
        Symbol(name: "waterbottle", label: "Water bottle"),
        Symbol(name: "flame", label: "Flame"),
        Symbol(name: "house", label: "House"),
        Symbol(name: "tshirt", label: "T-shirt"),
        Symbol(name: "pawprint", label: "Pet"),
        Symbol(name: "heart", label: "Heart"),
        Symbol(name: "lightbulb", label: "Light bulb"),
        Symbol(name: "trash", label: "Trash"),
        Symbol(name: "calendar", label: "Calendar"),
        Symbol(name: "clock", label: "Clock"),
    ]

    /// A few common picks shown on the empty list to get started quickly.
    static let starters: [ItemTemplate] = ["Contact lenses (2-week)", "Toothbrush", "HVAC / furnace filter", "Water pitcher filter"]
        .compactMap { name in all.first { $0.name == name } }
}
