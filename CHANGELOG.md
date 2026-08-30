## [Unreleased]

## [1.10.0] - 2026-08-30

### Fixed

- Query parameters carried in the request URI are now included in the signature
  base string, as RFC 5849 section 3.4.1.3.1 requires. They were previously
  dropped, so signing `https://example.com/r?a=1` produced a signature the
  provider rejected. Signing a URI with a query string and signing the same
  request with those parameters passed explicitly now agree. This affects
  `credentials`, the request/access/refresh token exchanges, and xAuth, since
  all of them route through `base-string`; a provider whose configured endpoint
  carries a query string was failing the token exchange itself.
- Query parameter names and values are decoded before normalization, so they are
  percent-encoded exactly once and sorted with the explicitly supplied
  parameters rather than appended after them. A `+` in a query is decoded as a
  space, matching `application/x-www-form-urlencoded`; a literal plus is still
  expressible as `%2B`.

## [1.9.0] - 2026-08-27

### Added

- Add injectable OAuth nonce, timestamp, and clock functions for deterministic
  requests.
- Add local fake-provider protocol coverage for token, refresh, xAuth, and
  protected-resource flows.
- Add configurable token endpoint methods, body encodings, headers, content
  types, and response parsers.

### Changed

- Token request failures now throw structured `ex-info` errors containing
  status, headers, body, and decoded OAuth error parameters.
- Use explicit UTF-8 encoding for cryptographic inputs, platform-independent
  secure randomness, and constant-time signature comparison.

## [1.8.0] - 2026-08-27

### Added

- Accept ordered key-value parameter sequences for query, form, and OAuth
  parameters, retaining duplicate keys during RFC 5849 signature normalization.

## [1.7.0] - 2026-08-27

### Added

- Add `signed-request`, plus signed GET, POST, PUT, and DELETE convenience
  wrappers that generate OAuth parameters, Authorization headers, and execute
  through clj-http.

## [1.6.3] - 2026-08-17

### Fixed

- The signature base string now applies RFC 5849 URI normalization (lowercase
  scheme and host, drop the default port, exclude the query and fragment), so
  requests with a query string, a default port, or a mixed-case host sign
  correctly.
- `verify` no longer double-encodes the token secret.
- `form-decode` splits each pair on the first `=` only, so padded base64 token
  secrets are no longer truncated.

## [1.6.2] - 2026-07-12

### Changed

- Migrate the build to deps.edn and tools.build, with Leiningen supported via lein-tools-deps.

## [1.6.1]

- Bump commons-codec 1.18.0 to 1.22.0 and Bouncy Castle 1.80 to 1.84.

## [1.6.0]

- Add RSA-SHA256 signatures.
- Accept PKCS#8 private keys in addition to PKCS#1.

## [1.5.6]

- First release as `net.clojars.savya/clj-oauth`.
- Release HMAC-SHA256 support.
- Move to current Bouncy Castle, commons-codec, clj-http, and Clojure versions.
