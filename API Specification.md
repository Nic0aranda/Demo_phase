# PhaseDB HTTP API Specification

*Draft 4, 2026-10-15.* See "Changes since draft 3" at the end.

## 1. Purpose and scope

The API lets a remote client run Phase code as a specific PhaseDB user and get the output back, and (later) move blobs in and out. It must run on Linux/UNIX and Windows, like the engine itself.

**Goals**

- Run Phase source under a user's credentials, either as a single call (authenticate, run, return output) or inside a stateful session.
- Every request is atomic and durable, as REPL input already is.
- Use exactly the engine's existing authorization (roles, per-global denylist, admin-only `^%USERS`/`^%CONFIG`). No second permission system.
- Admin operations that today exist only as REPL dot-commands: checkpoint, compact, add user.
- Blob upload and download by reference (phase 2, after the blob store exists).

**Non-goals for v1**

- Direct node CRUD or a second query language. All data access goes through Phase commands, so there is one authorization path to secure.
- Streaming/push (WebSocket), replication, clustering, SSO/OAuth.
- Transactions spanning several requests. `TRANSACTION` blocks are a language feature and arrive with the language, not the API.

## 2. Design principles

1. **Thin transport over the language.** The API accepts Phase source and returns what the REPL would have shown.
2. **A request is the unit of atomicity.** Verified: the REPL discards the whole `Env` when `runRoutine` returns an error and flushes the WAL only on success. The API keeps exactly that guarantee.
3. **The server owns the database.** One process holds the file. Nothing else may append to it while it runs (see §9.4).
4. **Portable by construction.** Pure-Haskell dependencies, no `unix`-only APIs, platform differences kept behind the same `CPP` pattern `Phase.Storage.Sync` already uses.

## 3. Concepts

| Term | Meaning |
| --- | --- |
| **Server** | One process serving one database file. |
| **Request** | One HTTP call that runs Phase source. Atomic. |
| **Session** | Server-side state for one authenticated user: its local variables, namespace, held `LOCK`s and a unique job id (`$JOB`). Created by login, found by bearer token. |
| **One-shot** | A request that authenticates itself and leaves no session behind. Fresh locals, default namespace, no locks survive it. |
| **Role** | `none < read < write < admin`, as in `Phase.Auth`, plus the per-global denylist. |

## 4. Protocol conventions

- HTTP/1.1, JSON in UTF-8 (`application/json; charset=utf-8`), except blob bodies (§6.5).
- All paths under `/v1`. A breaking change gets `/v2`; additive fields do not.
- Every response carries `X-Request-Id` (server-generated; a client-supplied one is echoed if it is 1 to 64 visible ASCII characters with no spaces; anything else is ignored and the server generates its own). It also appears in the body and the server log.
- Phase values are strings, so the API never invents numeric or boolean JSON types for them.
- Timestamps are RFC 3339 UTC.

### 4.1 Response envelope

Success of a Phase run:

```json
{ "ok": true, "output": "hello\n", "value": null,
  "lastTx": 1234, "elapsedMs": 3, "requestId": "b1c2…" }
```

Failure of a Phase run (the run happened and the engine rejected it):

```json
{ "ok": false, "output": "",
  "error": { "code": "DIVIDE BY ZERO", "message": "<DIVIDE BY ZERO>", "condition": null },
  "lastTx": 1234, "elapsedMs": 1, "requestId": "b1c2…" }
```

For an unhandled `SIGNAL`, `code` is the condition name and `condition` is `{ "name": "OOPS", "fields": { "reason": "bad" } }`.

`code` is the text between the angle brackets that `formatError` already produces. Verified list: `UNDEFINED VARIABLE`, `UNDEFINED FUNCTION`, `UNDEFINED LABEL`, `UNDEFINED ROUTINE`, `DIVIDE BY ZERO`, `CONFLICT`, `SET ON READONLY`, `PROTECT`, `NOTIMPL`, `SYNTAX`, `ERROR`, `SUBSCRIPT`, `NAKED`, `BIND SCOPE`, plus signalled condition names. A parse failure uses `SYNTAX`. The server adds three of its own, `TIMEOUT`, `OUTPUT LIMIT` and `FILE ACCESS DISABLED`, all with `ok: false`. The admin endpoints add `USER EXISTS`, `COMPACT FAILED` and `CHECKPOINT FAILED` (§6.3). An admin failure uses the same envelope as a failed Phase run, without `output` and `elapsedMs`: `{ "ok": false, "error": { "code": "USER EXISTS", "message": "…", "condition": null }, "lastTx": 1234, "requestId": "b1c2…" }`.

### 4.2 Transport-level errors

These mean the request never ran. They use a smaller envelope: `{ "ok": false, "error": { "code": "…", "message": "…" }, "requestId": "…" }`.

| HTTP | `code` | When |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | Malformed JSON, missing field, wrong type |
| 401 | `UNAUTHENTICATED` | No/invalid credentials or token. Identical for unknown user and wrong password. |
| 403 | `FORBIDDEN` | Authenticated, but the endpoint needs a higher role (admin endpoints) |
| 404 | `NOT_FOUND` | Unknown path, or unknown blob reference |
| 408 | `REQUEST_TIMEOUT` | Client too slow sending the body |
| 413 | `TOO_LARGE` | Body over the limit |
| 429 | `RATE_LIMITED` | Too many login attempts, with `Retry-After` |
| 503 | `BUSY` | Waited longer than `queueTimeoutMs` for the engine lock (a commit, an admin operation, a logout) or for a session's own lock, or too many sessions |

Phase-level failures, including `PROTECT` and permission failures raised by the language, are **HTTP 200 with `ok: false`** (decision D1).

## 5. Authentication

### 5.1 One-shot

A request carries `Authorization: Basic base64(user:password)`. Basic is accepted only over TLS or a loopback connection; otherwise the server answers 401 with an explanatory message (override flag `--allow-insecure-basic`, off by default).

### 5.2 Sessions

`POST /v1/sessions` with `{ "username", "password" }` returns a token; later calls send `Authorization: Bearer <token>`.

- The token is 32 random bytes from the OS entropy source (`Crypto.Random.Entropy`, already used for salts), hex-encoded. It is opaque: no JWT, because sessions must be revocable and the server is stateful anyway.
- The server keeps only a SHA-256 digest of each token in its session table.
- Idle timeout 30 minutes, absolute lifetime 12 hours, both configurable. Sessions do not survive a server restart in v1 (D5).
- Closing or expiring a session releases its `LOCK`s.

### 5.3 Keeping login from becoming a weak point

Verified: passwords are PBKDF2-SHA256 at 100,000 iterations, and `verifyPassword` compares with plain `==`. Consequences:

- **CPU cost.** Each attempt costs one PBKDF2. Unauthenticated callers must not be able to occupy every core: a small semaphore limits concurrent verifications (default 4) and excess attempts get 429/503.
- **Brute force.** Per-username and per-source-address failure counters with exponential backoff, held in memory, answered with 429 and `Retry-After`.
- **No user enumeration.** An unknown user still performs a PBKDF2 against a dummy salt, so response time and body match a wrong password.
- **Constant-time comparison.** `verifyPassword` must compare with `Data.ByteArray.constEq` (the `memory` package is already a dependency) before the API ships. This is a prerequisite change to `Phase.Auth`.
- **No credential caching.** The server never caches credentials or verification results (decision D3). Every one-shot call pays a PBKDF2, so clients that call often should log in once and send the session token. A frontend that wants to cache credentials must do it on its own side.
- **Revocation.** Each request re-reads the user's role and denylist (`lookupUserRole`, `isGloballyDenied`), so a role change or deletion takes effect on the next request, even inside an existing session (D8).

## 6. Endpoints

| Method and path | Auth | Role | Purpose |
| --- | --- | --- | --- |
| `GET /v1/health` | none |  | Liveness: `{ "status": "ok" }` only |
| `POST /v1/execute` | Basic | any not `none` | One-shot run |
| `POST /v1/sessions` | body |  | Log in, get a token |
| `GET /v1/sessions/current` | Bearer |  | User, role, namespace, expiry |
| `DELETE /v1/sessions/current` | Bearer |  | Log out |
| `POST /v1/sessions/current/execute` | Bearer | any not `none` | Run inside the session |
| `GET /v1/admin/status` | Bearer or Basic | admin | `lastTx`, session count, file size, version, effective settings with their sources |
| `POST /v1/admin/checkpoint` | Bearer or Basic | admin | Write a WAL checkpoint |
| `POST /v1/admin/compact` | Bearer or Basic | admin | `{ "beforeTx": n }`, collapse history up to `n` |
| `POST /v1/admin/users` | Bearer or Basic | admin | `{ "username", "password", "role" }` |
| `PUT /v1/blobs` | Bearer or Basic | write | Phase 2: upload |
| `GET`/`HEAD /v1/blobs?ref=…` | Bearer or Basic | read | Phase 2: download by reference |

### 6.1 Execute

Request (both execute endpoints):

```json
{ "command": "SET ^T(1)=5\nWRITE ^T(1),!", "namespace": "%GLOBAL", "timeoutMs": 5000 }
```

- `command` is Phase source, possibly several lines. It goes through the same path as REPL input: `buildLogicalLines`, then either routine-tag registration or `runRoutine`. Block openers, `DO … END DO`, `EVALUATE` and handlers work as in a script.
- `namespace` is optional; the default is the session's, or `%GLOBAL` for one-shot.
- `timeoutMs` is optional and capped by the server maximum.
- If `command` is a single bare expression, `value` holds its result (the REPL's `- : value` echo) and `output` is empty.
- If `command` defines column-0 routine tags, the response is `{ "ok": true, "registered": ["NS.NAME"], … }`.
- REPL dot-commands (`.save`, `.compact`, …) are **not** accepted here; their API forms are the admin endpoints.

### 6.2 Sessions

`POST /v1/sessions` → 201

```json
{ "token": "…", "expiresAt": "2026-10-13T20:00:00Z", "idleTimeoutSeconds": 1800,
  "user": "SYSTEM", "role": "admin", "namespace": "%GLOBAL" }
```

Two requests for one session never run concurrently: the second waits on a per-session lock, for at most `queueTimeoutMs` (else 503 `BUSY`) (D6).

`DELETE /v1/sessions/current` → 200 `{ "ok": true, "requestId": "…" }`. It takes the same per-session lock, so a logout waits for a request of that session that is still running (same bound, else 503 `BUSY`), then removes the session and releases its `LOCK`s.

### 6.3 Admin

These are the library functions behind `.checkpoint`, `.compact` and `.adduser`, which must live in the library first (§12).

**Common rules**

- **Authentication.** Bearer or Basic, as for `/execute`. The role is re-read on every request (D8) and checked again against the latest committed state when the operation runs, so a revoked admin cannot slip through while queued.
- **Errors.** A malformed body, a wrong type, or an unknown role is a transport error: 400 `BAD_REQUEST`. A caller who is not an admin gets 403 `FORBIDDEN`. A well-formed request that the engine refuses is a result, not a transport error (D1, D13): HTTP 200, `ok: false`, with one of the codes below.
- **Write operations** (`checkpoint`, `compact`, `users`) follow the commit protocol of §7 steps 5 and 6, without running any Phase code. Each takes the engine lock, waiting at most `queueTimeoutMs` (else 503 `BUSY`); does its work against the latest committed state, not an older snapshot; flushes to the WAL before publishing; publishes a new snapshot (version `v + 1`, so racing writers re-run); releases the lock; and only then responds. Success is `{ "ok": true, "lastTx": n, "requestId": "…" }`, where `n` is the last transaction after the operation. If the operation fails or the request is cancelled, nothing is published.

**`GET /v1/admin/status`** reads the committed snapshot and never waits for the engine lock.

```json
{ "ok": true, "lastTx": 1234, "sessionCount": 3, "fileSizeBytes": 20480,
  "version": "0.1.0.0",
  "config": { "maxBodyBytes": { "value": 1048576, "source": "default" },
              "execTimeoutMs": { "value": 5000, "source": "flag" } },
  "requestId": "b1c2…" }
```

- `fileSizeBytes` is the size of the database file at the time of the request.
- `version` is the package version from the `.cabal` file (§12, item 10).
- `config` has one entry for each of the eight resource limits of §9.1, keyed by its `^%CONFIG("server", name)` name. `value` is the effective value, and `source` is `flag`, `config` or `default`, following the order of D9.
- `sessionCount` is taken after expired sessions have been swept (§8).

**`POST /v1/admin/checkpoint`** takes no body (an empty body or `{}` is accepted). It appends a checkpoint record to the WAL. This is non-destructive and is not a transaction, so `lastTx` is unchanged. Failure: `CHECKPOINT FAILED`.

**`POST /v1/admin/compact`** takes `{ "beforeTx": n }`, with `n` a non-negative integer (anything else is 400). The state as of transaction `n` becomes the baseline, and the individual transactions up to `n` are discarded. Time travel to before `n` is no longer possible in the database file. Transactions after `n` are kept as they are, and `lastTx` is unchanged. An `n` greater than `lastTx` fails with `COMPACT FAILED`, as does any storage failure; in both cases the database file is untouched (the library writes a new file and renames it into place).

- **The engine lock is held for the whole compaction** (D14). Reads keep running on the previous snapshot. Writers queue, and those that wait longer than `queueTimeoutMs` get 503 `BUSY`. When the compaction finishes, the reopened engine is published as the new snapshot. The lock table and the sessions are not touched.
- **Backup.** Before touching anything, the library copies the current file in full to `<db>.backup-<timestamp>`, as the REPL does. The path is not returned to clients (it is a server file path); it is logged. Backups are never deleted automatically, so each compaction adds one full copy to the disk.

**`POST /v1/admin/users`** takes `{ "username": "…", "password": "…", "role": "…" }`. `username` and `password` must be non-empty strings. `role` is one of `none`, `read`, `write` or `admin`; any other value is 400. Further limits on usernames are the engine's own, as for `.adduser`.

- The password is hashed (PBKDF2, a fresh salt) **before** the engine lock is taken, so the hash time does not block writers (D15).
- Under the lock, the server checks that the user does not exist, against the latest state, and writes the account. Two concurrent requests for the same name therefore give one success and one `USER EXISTS`.
- Failure: `USER EXISTS`. The password is never logged or echoed.

### 6.4 Not in v1

`.history`, `.revert`, `.fns` and `.namespaces` equivalents, direct node access, time-travel conveniences. They can be reached through Phase commands in the meantime.

### 6.5 Blobs (phase 2, blocked on the blob store)

Bytes live inside the database file, as decided; this is the HTTP surface only.

- `PUT /v1/blobs`, body is the raw bytes (`Content-Type` stored as metadata; `X-Phase-Filename` optional). Streamed and hashed incrementally, so memory stays bounded. Optional `?ref=^GLOBAL(subs)` sets that node to `BLOB:<hash>` in the same WAL transaction as the blob. Response: `{ "ok": true, "pointer": "BLOB:<hash>", "hash": "…", "size": n }`. Requires write role and write access to `ref`'s global.
- `GET`/`HEAD /v1/blobs?ref=^GLOBAL(subs)`, the server reads that node **with the caller's credentials**, requires its value to be a `BLOB:` pointer, then streams the bytes with `Content-Type` and `Content-Length`. The caller therefore needs read access to the pointing global: no way to reach a blob through a global they are denied.
- `GET /v1/blobs/{hash}`, bare-hash access, admin only. `^%CATALOG` is readable by admins only, so hashes cannot be enumerated.
- Limits: `--max-blob-bytes`. Range requests are a later addition.
- Blob statements that touch the server's filesystem (`BLOBSAVE`, `$BLOBWRITE` with a path) are disabled for API sessions; they raise `FILE ACCESS DISABLED`.

## 7. Execution semantics

1. Authenticate (§5), then find or create the session. A second request for the same session waits for the first, for at most `queueTimeoutMs` (else 503 `BUSY`). The session is looked up again once its lock is held, because the first request may have changed its locals, or the session may have been closed or expired meanwhile (then 401).
2. Take a snapshot from the committed-snapshot cell (§8): engine, lock table and a version number `v`. No engine lock is held.
3. Build the `Env` from the snapshot: session locals and namespace, `envNow` = now, `envJobId` = the session's job id, file-I/O capability off. The job id is random and independent of the token, so that `$JOB` cannot reveal part of it. A one-shot request gets a job id of its own.
4. Run under a timeout and an output cap.
5. **Read-only outcome** (no pending WAL records, lock table unchanged): publish only the session state and respond. The request saw one consistent snapshot, as of `v`.
6. **Write outcome:** take the engine lock, waiting at most `--queue-timeout` (else 503). If the server is still at `v`, flush pending WAL records (and fsync, through `Phase.Storage.Sync`) *before* publishing; then publish engine, lock table and session state, release the lock and respond. If the server has moved past `v`, discard this attempt and run the request again from step 3 on the latest state, *while holding the lock*; that second run commits.
7. **On failure or timeout** (either attempt): discard the `Env`. Engine, lock table and session are exactly as before the request. Respond.

Guarantees:

- **Atomic.** All of a request's effects or none.
- **Durable on `ok: true`.** A request that wrote anything gets its response only after the WAL flush returns.
- **Writes are serialized; reads are snapshot reads.** Commits happen one at a time in a single order, and a write that raced another is re-run on the latest state, so no update is lost. A read-only request sees a consistent snapshot as of its start; it may be slightly behind a commit that finished while it ran. No request ever sees a half-applied one.
- **Output on failure is empty**, as in the REPL, where the error discards the buffer. Returning partial output would contradict atomicity (D7).
- **Limits.** `timeoutMs` (default 10 s, server max 60 s), output cap (default 1 MiB, error `OUTPUT LIMIT`), request body cap (default 1 MiB).
- **Cancelling a request is safe**, because nothing is published until the final step. The flush-and-publish step runs with asynchronous exceptions masked.
- `System.Timeout.timeout` interrupts only at allocation points. To stop non-allocating loops the library is compiled with `-fno-omit-yields`.

## 8. Concurrency model

The engine is an immutable `StorageEngine` value, carried in `Env`. The REPL threads one `Env` sequentially, which is why it needs no locking. A server with several sessions needs a single point of truth:

- **Shared server state:** the current `StorageEngine` and `LockTable` plus a version counter `v`, in one `MVar` (the engine lock). Sessions' own state is in a `TVar (Map TokenDigest Session)`. A committed-snapshot cell, an `IORef` holding the latest committed engine, lock table and `v`, is rewritten at every commit. Requests, health, status and configuration reads take their snapshot from it without waiting for any running request (a `readMVar` would block while a request holds the engine lock).
- **Optimistic execution (decision D2).** A request runs on a snapshot with no lock held. Only the commit takes the engine lock, briefly: if `v` is unchanged it publishes, otherwise it re-runs the request once on the latest state under the lock. Reads therefore run fully in parallel with each other and with writes. Writes are serialized at commit, and the re-run guarantees progress, so a writer cannot starve. Holding the lock for a whole run remains the always-correct path, and it is what the re-run uses.
- **Locks are shared.** `envLocks` lives in `Env` today, so each session would otherwise have private locks that exclude nobody. The lock table moves to server state and the session's job id is its owner. A `LOCK` or `UNLOCK` changes the lock table, so such a request counts as a write. A one-shot request has no session to outlive it, so the locks it took are released when it ends (D11); a request that only took and released locks changes nothing and publishes nothing. Closing a session releases its locks (§6.2).
- **Expiry is a sweep, not a timer** (D12). At the start of every request except `GET /v1/health`, before the snapshot is taken, the server checks whether any session is past its idle timeout or absolute lifetime. If one is, it removes the session and releases its locks, and publishes the released lock table as a write. There is no background thread. A session that is running a request is skipped: it is not idle, and removing it would leak the locks that request is about to take. The check itself reads only the session table. The sweep **never waits for the engine lock**: it takes the lock only if it is free and is skipped otherwise, so a read is never delayed by a commit or a compaction, and the next request tries again. The price is that, while the engine lock is busy, a request can still see the locks of a session that has already expired.
- **Waiting is bounded.** Every wait a request makes for the engine lock (a commit, an admin operation, a logout) or for a session's own lock is bounded by `queueTimeoutMs`; on expiry the request does not run and the answer is 503 `BUSY`. Only the first, optimistic run of a write is unbounded by this rule, because it holds no lock.
- **Runtime.** The server is built with `-threaded` and started with `-N`, otherwise parallel requests would share one core. Both work on Windows. An old snapshot is kept alive only while a request that started on it is still running.

## 9. Operational and security requirements

1. **TLS.** Built in (decision D4): `warp-tls` with `--tls-cert` and `--tls-key`, TLS 1.2 or newer, certificate and key read at startup from PEM files. Terminating TLS in a reverse proxy remains possible for deployments that prefer it. Default bind is `127.0.0.1`. Binding to another address without TLS requires `--allow-insecure-basic`.
2. **No secrets in logs.** Passwords, tokens and `Authorization` headers are never logged. Command text is logged only at debug level.
3. **Access log** (JSON lines to stderr): request id, user, method, path, status, `elapsedMs`, bytes. Admin actions and failed logins are always logged.
4. **CORS** is off unless `--cors-origin` is given. No wildcard with credentials.
5. **Single owner of the database file.** The server takes an exclusive lock on `<db>.lock` for its lifetime, using `GHC.IO.Handle.Lock.hTryLock` from `base`. It is cross-platform (`LockFileEx` on Windows, `flock`/`fcntl` on POSIX) and adds no dependency. The REPL must take the same lock, otherwise it could append to the WAL while the server runs. I found no cross-process lock on the database file today.
6. **Graceful shutdown** on SIGTERM and Ctrl-C: stop accepting, let in-flight requests finish (bounded), release the lock. POSIX signal handlers are not available on Windows; the portable path is catching `UserInterrupt` and using Warp's shutdown hook.
7. **Request validation.** Strict JSON, size caps, and no use of client-supplied values in file paths or logs unescaped.

### 9.1 Configuration

Every setting has one of three sources, checked in this order (decision D9): a command-line flag or its `PHASEDB_*` environment variable, then `^%CONFIG("server", name)` in the database, then the built-in default. A flag **overrides** the database value. The database is read on each use, as `^%CONFIG("retry","max")` already is, so an administrator can change a limit with `SET` and it applies from the next request without a restart, unless a flag pins it. `GET /v1/admin/status` reports each effective value and its source (`flag`, `config` or `default`); the format is in §6.3.

Only resource limits can come from the database: `execTimeoutMs`, `execTimeoutMaxMs`, `maxOutputBytes`, `maxBodyBytes`, `sessionIdleSeconds`, `sessionMaxSeconds`, `maxSessions` and `queueTimeoutMs`. Settings that are needed before the database opens, or that affect exposure, are flags only: `--db`, `--listen`, `--port`, `--tls-cert`, `--tls-key`, `--cors-origin` and `--allow-insecure-basic`.

| Flag | Default |
| --- | --- |
| `--db PATH` | `phase.db` |
| `--listen ADDR` / `--port N` | `127.0.0.1` / `8484` |
| `--tls-cert`, `--tls-key` | off |
| `--max-body-bytes` | 1 MiB |
| `--exec-timeout-ms` (default / max) | 10000 / 60000 |
| `--max-output-bytes` | 1 MiB |
| `--session-idle-seconds` / `--session-max-seconds` | 1800 / 43200 |
| `--max-sessions` | 256 |
| `--queue-timeout-ms` | 5000 |
| `--cors-origin` | none |

## 10. Libraries

Criteria: pure Haskell (or Windows-safe), builds on GHC 9.10 (the `.cabal` pins `base ^>=4.20`), few transitive dependencies, streaming support.

| Need | Choice | Why |
| --- | --- | --- |
| HTTP server | **`wai` + `warp`** | Standard and mature. Works on Windows. Streams request and response bodies, which blobs need. |
| Routing | **hand-written on WAI** (about a dozen routes) | `servant` gives generated clients and docs, but at the price of long compile times and type-level machinery that a dozen routes do not repay. `scotty` is shorter to write but its body handling is awkward for streaming. |
| JSON | **`aeson`** | The standard. `scientific` is already a dependency. |
| TLS (built-in) | **`warp-tls`** | Pure Haskell `tls`. Works on Windows. Optional. |
| HTTP types | `http-types` | Status codes, headers. |
| Concurrency | `stm`, `MVar`, `System.Timeout` | All in `base`/boot libraries. |
| Tokens, hashing | `cryptonite`/`memory` (already used) | `Crypto.Random.Entropy`, SHA-256, `constEq`. |
| CLI | **`optparse-applicative`** | Pure Haskell. |
| In-process tests | **`wai-extra`** (`Network.Wai.Test`) | Calls the WAI `Application` with no sockets: fast, deterministic, no port clashes on Windows CI. |
| Socket-level smoke test | `http-client` | One end-to-end test over a real port. |

Notes:

- `cryptonite` is unmaintained; its successor `crypton` keeps the same `Crypto.*` module names. `tls` depends on `crypton`, so a build would carry both. Switching `phasedb-hs` to `crypton` is a package-name change in the `.cabal` file and is worth doing at the same time.
- Keep the engine library free of web dependencies. The server is a separate component, `phasedb-server` (its own cabal library and executable), so a build without `warp`/`aeson` stays possible (decision D10). The REPL executable is renamed from `phasedb-hs` to `phasedb`; the package keeps the name `phasedb-hs`.
- WebSockets (`wai-websockets`, `websockets`, both pure Haskell) are the natural route for a future streaming console. Not in v1.
- The file size reported by `GET /v1/admin/status` comes from `System.Directory.getFileSize` (`directory`, already a dependency of the server).
- Avoid the `unix` package in the server. Use `directory`, `filepath`, `network`-based Warp and the `CPP` split pattern already in `Phase.Storage.Sync`.

## 11. Testing strategy

Tests come first for each milestone, in the existing runner, using `Network.Wai.Test`.

- **Protocol:** every endpoint's success and every transport error; envelope shape; `X-Request-Id`.
- **Auth:** wrong password and unknown user are indistinguishable; rate-limit and backoff; token expiry; revoked role takes effect next request.
- **Atomicity:** a request that fails after earlier lines wrote leaves no trace; a timed-out request leaves no trace and releases the engine lock.
- **Durability:** after `ok: true`, reopening the file shows the writes.
- **Concurrency:** many parallel sessions incrementing one global lose no updates, and the final state equals some sequential order; a long-running read does not delay a concurrent write or other reads; a write that raced another is re-run exactly once and reflects the latest state; a read-only request never publishes anything; a `LOCK` held by one session blocks another and counts as a write; closing a session frees its locks; with `-N`, at least two requests are observed running at the same time.
- **Sessions and locks:** a one-shot request's locks are released when it ends and publish nothing; an expired session's locks are released by the next request; a session running a request is not expired; a second request on a busy session gets 503 `BUSY` after `queueTimeoutMs`, or runs if the session frees up in time; a logout waits for a running request.
- **Admin:** the effects of `users`, `checkpoint` and `compact` are visible to the very next request (a new user can log in at once, and the next write continues from the right `lastTx`); no admin operation reuses a transaction number; `users` answers `USER EXISTS` for a duplicate and 400 for an unknown role; two concurrent `users` requests for one name give one success; `compact` and `checkpoint` leave committed data readable and durable (reopening the file shows it); a non-admin gets 403 on every admin endpoint; `status` reports each effective setting with the right source for a flag, a `^%CONFIG` value and a default; writers that wait longer than `queueTimeoutMs` behind a compaction get 503 `BUSY`, and reads are not delayed by it.
- **Limits:** oversized body, oversized output, long-running loop (including a non-allocating one).
- **Error mapping:** one test per `EvalError` constructor and for a signalled condition.
- **Portability:** the suite runs on both a Linux and a Windows runner.

## 12. Prerequisite changes before any API code

Each is a small change in the existing engine, with its own tests:

1. Extract the body of `runInput` (in `app/Main.hs`) into a library function that returns a structured result (`output`, `value`, `registered`, `Either EvalError`) and prints nothing. The REPL becomes a client of it.
2. Move `.checkpoint`, `.compact` and `.adduser` logic into library functions with the same permission checks. Split `.adduser` into `prepareNewUser` (salt and hash, no access to the engine) and `applyAddUser` (existence check and writes, no hashing); `adminAddUser` remains as their composition, and the REPL keeps using it.
3. Make `verifyPassword` constant-time.
4. Add the cross-process file lock (§9.5) and have the REPL take it.
5. Add an `Env` capability for file I/O (off for API sessions) and an optional output-size limit checked as output is written.
6. Make the lock table shareable: the server loads it into a session's `Env` per request and stores it back.
7. Compile the library with `-fno-omit-yields`, and build the server with `-threaded` (run with `-N`).
8. Blob store inside the database file, with GC at compaction (separate work item; blocks only §6.5).
9. Rename the REPL executable from `phasedb-hs` to `phasedb` (cabal file, README, help text) and add an empty `phasedb-server` executable.
10. Expose the package version through the generated `Paths_phasedb_hs` module. Every component that compiles the module that imports it (`phasedb-server` and the test suite) lists `Paths_phasedb_hs` under both `other-modules` and `autogen-modules`.

## 13. Milestones

|  | Content |
| --- | --- |
| M0 | Prerequisites 1–7 and 9 |
| M1 | Server skeleton, `GET /health`, `POST /execute` one-shot, limits, error mapping, snapshot execution with serialized commits |
| M2 | Sessions, shared locks, rate limiting |
| M3 | Admin endpoints (`status`, `checkpoint`, `compact`, `users`) as write operations through the commit protocol; bounded engine-lock waits (503 `BUSY`); the try-lock sweep |
| M4 | TLS, graceful shutdown, access log, Windows CI |
| M5 | Blobs (after prerequisite 8) |

## 14. Decisions

| # | Question | Outcome |
| --- | --- | --- |
| D1 | Phase failures as HTTP 200 with `ok: false`? | Yes. They are language-level results, not transport errors. |
| D2 | Execution model in v1? | Optimistic snapshot execution: reads run in parallel, writes are serialized at commit and re-run once if they raced another write. Concurrency is a priority. |
| D3 | Cache verified credentials? | No. The server never caches them. Caching, if wanted, belongs in a frontend. |
| D4 | Built-in TLS? | Yes, with `warp-tls`. A reverse proxy stays possible. |
| D5 | Sessions lost on restart? | Accepted. |
| D6 | Concurrent requests in one session? | They queue, each for at most `queueTimeoutMs` (else 503 `BUSY`). A logout waits for a request of that session that is still running. |
| D7 | Partial output on failure? | Dropped, as in the REPL. |
| D8 | Re-validate role and denylist on every request? | Yes. |
| D9 | Where do server settings live? | Flags override matching `^%CONFIG("server", …)` values (§9.1). |
| D10 | Executables? | `phasedb-server` for the server; the REPL executable is renamed `phasedb`. |
| D11 | What happens to the locks of a one-shot request? | It gets its own job id and its locks are released when it ends. The job id of a session is random, not derived from its token. |
| D12 | How do expired sessions release their locks? | A sweep at the start of every request (not health), with no reaper thread. It skips sessions with a request in flight and is skipped when the engine lock is busy (try-lock). |
| D13 | How do admin endpoints report failure? | Bad body or unknown role: 400. Not an admin: 403. Anything the engine refuses (`USER EXISTS`, `COMPACT FAILED`, `CHECKPOINT FAILED`): HTTP 200 with `ok: false`, as in D1. |
| D14 | Does compaction block the server? | It holds the engine lock throughout. Reads continue on the old snapshot, queued writers get 503 after `queueTimeoutMs`. Each compaction leaves a full backup file, whose path is logged but not returned, and which is never deleted automatically. |
| D15 | Where is the password hashed when adding a user? | Outside the engine lock (`prepareNewUser`); only the existence check and the writes (`applyAddUser`) run under it. |
| D16 | Where does `version` in `status` come from? | The cabal package version, through `Paths_phasedb_hs`. |

## Changes since draft 3

- §6.3 rewritten: request and response bodies for all four admin endpoints, the error shape, the commit protocol for admin writes, compaction and backup behaviour, hashing outside the lock (D13 to D16).
- §4.1, §4.2: admin failure codes and envelope; `503 BUSY` now also covers a session's own lock and logout; precise rule for echoing `X-Request-Id`.
- §6.2, §7, §8, D6: waits for a session's lock are bounded and a logout waits for a running request; the job id is independent of the token; one-shot locks are released at the end of the request (D11); expiry is a sweep with a try-lock (D12); every wait for the engine lock is bounded.
- §10, §11, §12, §13: `directory` for the file size, test list for sessions, locks and admin, prerequisites 2 (split of `.adduser`) and 10 (`Paths_`), the content of M3.
