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

    /// Icons offered in the editor's icon picker.
    static let symbolChoices: [String] = [
        "eye", "eye.circle", "mouth", "bolt", "scissors", "paintbrush", "pills", "cross.case",
        "figure.run", "wind", "battery.100", "bed.double", "leaf", "drop", "refrigerator",
        "sparkles", "car", "car.rear.waves.up", "waterbottle", "flame", "house", "tshirt",
        "pawprint", "heart", "lightbulb", "trash", "calendar", "clock",
    ]
}
