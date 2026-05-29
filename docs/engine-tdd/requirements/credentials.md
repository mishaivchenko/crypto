# Credential Requirements

Primary class: `CredentialAwareExecutionPort`.

- `ENG-CRED-001`: missing API key or secret produces a failed attempt with a configuration hint.
- `ENG-CRED-002`: passphrase is required only for `bitget`, `okx`, and `kucoin`.
- `ENG-CRED-003`: venue names are normalized before credential lookup and failure reporting.
- `ENG-CRED-004`: live order submission remains guarded even when credentials are present.
