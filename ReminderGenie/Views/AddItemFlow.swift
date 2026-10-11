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
                        Label("Custom item", systemImage: "square.and.pencil")
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
            .searchable(text: $search, prompt: "Search templates")
            .navigationTitle("Add item")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private var blankDraft: ItemDraft {
        var draft = ItemDraft()
        draft.reminderTime = ItemDraft.time(hour: defaultHour, minute: defaultMinute)
        return draft
    }

    private func filtered(_ templates: [ItemTemplate]) -> [ItemTemplate] {
        guard !search.isEmpty else { return templates }
        return templates.filter { $0.name.localizedCaseInsensitiveContains(search) }
    }
}

private struct TemplateRow: View {
    let template: ItemTemplate

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: template.symbolName)
                .font(.body.weight(.semibold))
                .foregroundStyle(template.color.color)
                .frame(width: 32, height: 32)
                .background(template.color.color.opacity(0.15), in: Circle())
            VStack(alignment: .leading) {
                Text(template.name)
                Text(template.intervalDescription)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
