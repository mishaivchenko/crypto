# Credential Requirements

Primary classes: `CredentialAwareExecutionPort`, `EngineCredentialCache`.

- `ENG-CRED-001`: missing API key or secret produces a failed attempt with a configuration hint.
- `ENG-CRED-002`: passphrase is required only for `bitget`, `okx`, and `kucoin`.
- `ENG-CRED-003`: venue names are normalized before credential lookup and failure reporting.
- `ENG-CRED-004`: live order submission remains guarded even when credentials are present.
- `ENG-CACHE-001`: `EngineCredentialCache` loads and caches credentials from monitor on startup for each live-enabled venue.
- `ENG-CACHE-002`: missing credentials (monitor returns empty) are logged as a warning without throwing.
- `ENG-CACHE-003`: `get()` returns empty for unknown venues and present for loaded venues.
- `ENG-CACHE-004`: `loadOnStartup` iterates all live-enabled venues using the configured access mode.
- `ENG-CRED-CTL-003`: `EngineCredentialStatusController.reload` delegates to `EngineCredentialCache.loadOnStartup` and returns the updated status map.
