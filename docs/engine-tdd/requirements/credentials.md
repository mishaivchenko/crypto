# Credential Requirements

Primary class: `CredentialAwareExecutionPort`.

- `ENG-CRED-001`: missing API key or secret produces a failed attempt with a configuration hint.
- `ENG-CRED-002`: passphrase is required only for `bitget`, `okx`, and `kucoin`.
- `ENG-CRED-003`: venue names are normalized before credential lookup and failure reporting.
- `ENG-CRED-004`: live order submission remains guarded even when credentials are present.
- `ENG-CRED-CTL-001`: `EngineCredentialStatusController.status()` returns a per-venue boolean map for all live-enabled venues.
- `ENG-CRED-CTL-002`: `EngineCredentialStatusController.status()` returns an empty map when no live venues are configured.
