// Intentionally empty: no root-level plugin aggregation block. Each module declares its own
// plugins directly (see app/build.gradle.kts, scheduling/build.gradle.kts). This keeps
// `:scheduling:test` buildable with configuration-on-demand even in environments where the
// Android Gradle Plugin cannot be resolved (it lives on dl.google.com).
