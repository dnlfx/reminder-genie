import SwiftData

/// The database layout as shipped in 1.0.
///
/// To change a model later: copy `TrackedItem` and `ReplacementLog` as they are now into this
/// enum as nested types (so V1 keeps describing the old layout), add `ReminderGenieSchemaV2`
/// pointing at the changed top-level models, and add a V1 → V2 stage to the migration plan.
enum ReminderGenieSchemaV1: VersionedSchema {
    static var versionIdentifier: Schema.Version { Schema.Version(1, 0, 0) }
    static var models: [any PersistentModel.Type] { [TrackedItem.self, ReplacementLog.self] }
}

/// Every schema version in order, and how to move between them.
enum ReminderGenieMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [ReminderGenieSchemaV1.self] }
    static var stages: [MigrationStage] { [] }
}
