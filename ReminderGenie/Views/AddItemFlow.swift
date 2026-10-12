import SwiftUI

/// Sheet for adding an item: pick a template (or start blank), then fine-tune it in the editor.
struct AddItemFlow: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage(AppSettings.defaultHourKey) private var defaultHour = 9
    @AppStorage(AppSettings.defaultMinuteKey) private var defaultMinute = 0
    @State private var search = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    NavigationLink {
                        ItemEditorView(mode: .create(blankDraft), onFinish: { dismiss() })
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "plus")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(.tint)
                                .frame(width: 34, height: 34)
                                .background(Color.accentColor.opacity(0.15), in: Circle())
                                .accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 1) {
                                Text(trimmedSearch.isEmpty ? "Custom Item" : "Create “\(trimmedSearch)”")
                                Text("Set your own name, schedule and icon")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                ForEach(ItemTemplate.Category.allCases) { category in
                    let templates = filtered(ItemTemplate.templates(in: category))
                    if !templates.isEmpty {
                        Section(category.rawValue) {
                            ForEach(templates) { template in
                                NavigationLink {
                                    ItemEditorView(
                                        mode: .create(ItemDraft(template: template, defaultHour: defaultHour, defaultMinute: defaultMinute)),
                                        onFinish: { dismiss() }
                                    )
                                } label: {
                                    TemplateRow(template: template)
                                }
                            }
                        }
                    }
                }
            }
            .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always), prompt: "Search or name a new item")
            .navigationTitle("Add Item")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var trimmedSearch: String {
        search.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var blankDraft: ItemDraft {
        var draft = ItemDraft()
        draft.name = trimmedSearch
        draft.reminderTime = ItemDraft.time(hour: defaultHour, minute: defaultMinute)
        return draft
    }

    private func filtered(_ templates: [ItemTemplate]) -> [ItemTemplate] {
        guard !trimmedSearch.isEmpty else { return templates }
        return templates.filter { $0.name.localizedCaseInsensitiveContains(trimmedSearch) }
    }
}

/// A template's icon in a tinted circle.
struct TemplateIcon: View {
    let template: ItemTemplate

    var body: some View {
        Image(systemName: template.symbolName)
            .font(.body.weight(.semibold))
            .foregroundStyle(template.color.color)
            .frame(width: 34, height: 34)
            .background(template.color.color.opacity(0.15), in: Circle())
            .accessibilityHidden(true)
    }
}

private struct TemplateRow: View {
    let template: ItemTemplate

    var body: some View {
        HStack(spacing: 12) {
            TemplateIcon(template: template)
            VStack(alignment: .leading, spacing: 1) {
                Text(template.name)
                Text(template.intervalDescription)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
