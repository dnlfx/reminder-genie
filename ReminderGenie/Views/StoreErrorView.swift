import SwiftUI

/// Shown instead of the list when the database can't be opened, so the app explains itself
/// rather than crashing on launch.
struct StoreErrorView: View {
    let message: String
    @State private var showingDetails = false

    var body: some View {
        ContentUnavailableView {
            Label("Couldn't Open Your Items", systemImage: "externaldrive.badge.exclamationmark")
        } description: {
            Text("Your items are still on this iPhone and haven't been changed. Close Reminder Genie and open it again, or install the latest update from the App Store.")
        } actions: {
            Button(showingDetails ? "Hide Details" : "Show Details") {
                withAnimation { showingDetails.toggle() }
            }
            if showingDetails {
                Text(message)
                    .font(.footnote.monospaced())
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
                ShareLink(item: message) {
                    Label("Share Details", systemImage: "square.and.arrow.up")
                }
            }
        }
    }
}

#Preview {
    StoreErrorView(message: "The model used to open the store is incompatible with the one used to create the store.")
}
