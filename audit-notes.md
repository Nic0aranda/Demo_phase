# Audit notes

Findings from code review that are not fixed yet. Nothing here has been
compiled or exercised; each item was found by reading the source.

## Phase.State: latent until TRANSACTION exists

Nothing sets `envActiveTx` today (`TRANSACTION` is `SUnsupported`), so
items 1 and 2 are dormant.

1. **No read-your-writes inside a transaction.** `writeRef` updates
   `envGlobals`, but `readRef`, `dataRef`, `orderRef` and
   `childCountRef` read `seLive`. Decide which is authoritative during
   a transaction before implementing `TRANSACTION`.
2. **Transactional `KILL` stages a point kill.** `killRef` calls
   `stageKill` even when the target has descendants; `stageKillSubtree`
   exists and is unused.
3. **FIXED -- `mergeRef` into a global bypassed the WAL, transactions and
   rollback.** A `MERGE` into a global did not persist, left no revisions
   (so `^[-N]`, time travel and `EXPECTING` never saw it), lost a SET
   already staged against the same global inside a transaction, wrote
   uncommitted staged data into `seLive`, and skipped the per-value
   `^%USERS` last-admin check a SET gets. Now a global `MERGE` is the
   sequence of SETs it stands for: validated per merged path before
   anything is written, staged inside a transaction, and otherwise ONE
   atomic `commitTransaction` (one TxId). See CHANGELOG.
4. **`writeRef` fallback on `commitSet` failure is unreachable today**
   (only `StorageWriteConflict` is possible and `writeRef` never passes
   an expected tx). If it ever fires it updates `envGlobals` but not
   `seLive`.
5. **`commitKill` / `commitKillSubtree` swallow commit failures** and
   return the engine unchanged. Unreachable today for the same reason.
6. **`appendOutput` is O(n) per call** (`envOutput ++ [txt]`), so long
   `WRITE` loops are quadratic. Store the buffer reversed; check
   `app/Main.hs` for direct readers of `envOutput` first.

## Phase.Eval

7. **`**` (Pow) goes through `Double`**, unlike the other five
   arithmetic operators. Integer exponents could use exact repeated
   multiplication; non-integer exponents need a `Double` fallback.
8. **`GOTO ^ROUTINE` / `DO ^ROUTINE` (line-0 entry)** is unsupported
   (`<UNDEFINED ROUTINE>`); cross-namespace `GOTO TAG^NS` cannot fall
   through into the next tag (see README deviation 6).
9. `SDo` with a bare block indexes the line list with `!!` (O(n)).
   Performance only.

## Phase.Storage (Engine, WAL, Transaction, Lock, Types)

Reachable today unless marked latent.

10-12, 15, 16. **Fixed** (see CHANGELOG): torn WAL tails and
    transactions with no commit record are truncated on open
    (`recoverWalFile`), appends are one batched, fsynced write, the
    format-version byte is checked, mid-file corruption and unknown
    files are refused instead of skipped or appended to, invalid UTF-8
    is a decode failure instead of a pure exception, and appends no
    longer read the whole file. Not done: fsync of the parent directory
    when the file is first created.

21. **Windows is unverified.** Nothing has been built or run there.
    Things to check first: the `Win32` branch of `Phase.Storage.Sync`;
    `compactTo`'s rename-over-existing and the backup copy while other
    handles may still be open; the `.phasedb_history` path; and
    haskeline's behaviour in the console you use.
13. **Fixed, format change, per the team's go-ahead on breaking
    compatibility with older databases.** `WalCheckpoint` now carries
    each entry's own last-write `TxId` (`(global, subs, value,
    lastWriteTx)`, was a 3-tuple), not just a flat value list.
    `loadCheckpointEntries` uses it to seed `seHistory`/`seLastTxMap`
    per path (the checkpoint's own timestamp approximates each seeded
    revision's timestamp, the exact one is gone once compacted, same
    as any other pre-compaction detail). `takeCheckpoint` and
    `compactTo`'s baseline both look up each path's true last-write tx
    from `seLastTxMap` rather than assuming the checkpoint's own tx.
    Fixes the live-reopen case AND the deep-replay fallback in one
    shot, since both call `loadCheckpointEntries`.
14. **Fixed.** New `killTombstonePaths` (dumps the subtree about to be
    pruned via the new `Phase.Tree.findSubNode`, before `killNode`
    removes it) now tombstones the killed path AND every descendant,
    in both `applyPointKill` and `applySubtreeKill` (the latter had NO
    history update at all before this, not even for the killed path
    itself, dead code today, since `killRef`'s only reachable path is
    `commitKill`/point-kill, but fixed for when `TRANSACTION` wires up
    `stageKillSubtree`), and in `replayFrom`'s handling of both
    `WalKill` and `WalKillSubtree`. Fix: on kill, walk the
    pruned subtree (`dumpTree`) and add a tombstone revision and
    last-tx per descendant path.
17. **Fixed for checkpoints:** `takeCheckpoint` now flushes pending
    records first. `compactTo` still reads the file as it is on disk, so
    callers must flush before compacting (the REPL does).
18. **Doc/code mismatch:** `Types.hs` says `WalKill` "removes bare value
    only", but `killNode` always prunes the subtree, and both kill ops
    apply it identically. Handbook: point vs subtree is decided at
    evaluation time by whether descendants exist.
19. (latent) **`TxHandle` loses operation order.** Staged writes are
    applied before point kills before subtree kills, so `KILL ^G(1)`
    followed by `SET ^G(1,2)=5` in one transaction ends up killed.
20. Lock table reviewed: path-granular, reentrant, no issues found.

## app/Main.hs (REPL)

22. **Fixed.** Every branch of `.revert`/`.compact`/`.checkpoint` now
    ends in exactly one tail call to `loop`, with whichever env should
    actually continue, no more nested `loop ... >> pure ()` followed
    by a further, stale `loop dbPath env` once the inner one returns.
    Verified against the real executable: `.compact 0` followed by a
    SINGLE `.quit` now exits cleanly (previously needed a second one).
23. **Fixed.** Bootstrap now triggers whenever `hasAnyUser eng == False`,
    not only when the `.db` file didn't exist before this process
    started, so an existing empty or user-less file bootstraps instead
    of dropping into `loginFlow` forever. Also added: `Phase.Auth`
    refuses to `KILL` or demote the database's SOLE remaining admin
    account (`checkUsersKillSafety`/`checkUsersSetSafety`, wired into
    `Phase.State.writeRef`/`killRef`), so an admin can no longer lock
    themselves out even by accident. `%USERS` writes/kills already
    require `RAdmin` (`isHardcodedAdminOnlyGlobal`), so this only ever
    fires for an admin acting on themselves.
24. No login throttling or lockout; the PBKDF2 cost is the only brake.

## Phase.Auth

25. **A user with a malformed salt and an empty hash accepts any
    password.** `hashPassword` returns `""` for a salt that is not valid
    hex, and `verifyPassword` compares that to the stored hash, so a
    record with an invalid `passwordSalt` and `passwordHash=""` (which an
    admin can create with plain `SET`) matches everything. The comment
    calls this the safe failure mode; it is safe only if the stored hash
    is non-empty. Fix: `verifyPassword` returns False when either hash
    is empty.
26. Hash comparison uses `==` on `Text` (not constant time).
    `Data.ByteArray.constEq` is available through `memory`.
27. **Fixed, per the team's decision: non-admins keep catalog write access
    (the only way to define their own routines), but a definition an
    admin has authored is now protected.** `defineItem` records
    `^%CATALOG(ns,name,"AUTHOR_ROLE")` on every write and refuses a
    non-admin's attempt to replace an item whose current version is
    admin-authored (`Phase.Auth.checkCatalogAccess`, called from inside
    `defineItem` itself, so `revertItem` inherits it automatically). A
    RAW `SET`/`KILL`/`MERGE` that reaches `^%CATALOG` directly --
    bypassing `defineItem` entirely, which is how this was originally
    exploitable -- goes through the same check, wired into
    `Phase.State.writeRef`/`killRef`/`mergeRef`. A write that doesn't
    name a specific `(namespace, name)` (a whole namespace, or the
    entire catalog) is refused outright for a non-admin rather than
    walked subtree-by-subtree. Ownership is tracked per CURRENT version,
    not per original author: once an admin touches an item, it stays
    protected even if a non-admin created it originally.

## Phase.Catalog

28. **`revertItem` gives a zero-arity function one empty parameter.**
    A `Just []` definition stores `PARAMS=""`; `lookupItem` maps that to
    `[]`, but `revertItem` uses `T.splitOn "," ""`, which is `[""]`, so
    the reverted function has arity 1 with an unnamed parameter.
29. **`registerRoutineTags` silently drops statements before the first
    tag** (`groupByTag` skips untagged leading lines).
30. **`defineItem` is several separate commits** (4 to 8 `writeRef`
    calls, each its own transaction). A crash part-way leaves a
    half-defined item (new content, old VERSION), and every definition
    burns that many transaction ids, which coarsens time travel and
    `.compact`. Wants a real transaction once `TRANSACTION` exists.

## Phase.Lines

31. **Column-0 commands that are not in the 23-word `isCommandKeyword`
    list become tags.** (`EVALUATE`, `WHEN`, `CONDITION`, `SIGNAL`,
    `HANDLE`, `CATCH` and `RETRY` were added as they were implemented.)
    `TRANSACTION START`, `INDEX`, `BLOBSAVE` and the like are meant to degrade to
    `SUnsupported` (a `<NOTIMPL>` error), but typed at column 0 they are
    classified as a tag plus text, so the "unsupported" diagnostic never
    fires. The list should be the statement parser's vocabulary plus the
    known-unsupported words, or the REPL should indent input.

## Phase.Value / Phase.Tree

32. `canonicalNumber` accepts `-0`, so `"-0"` and `"0"` are the same
    subscript. Real M treats `-0` as non-canonical. The handbook's rule
    list does not mention it.
33. `Eq SubscriptKey` is structural but `Ord` is `collationCompare`, so
    `==` and `compare` disagree for numerically equal canonical keys.
    `Map` only uses `compare`, so nothing breaks today.
34. Every subscript comparison re-parses both texts with `reads`
    (`ReadP`), which is slow for large trees. Cache the canonical number
    in the key.
35. **Fixed.** `writeRef` now refuses any SET (local or global) whose
    subscript list contains `""`, and `mergeRef` refuses one whose
    DESTINATION does, with a new `<SUBSCRIPT>` error
    (`Phase.State.NullSubscript`). `KILL` is unaffected -- killing a
    path that was never legally created is harmless.
36. `canonicalText` uses `show`, which yields `1.0e-2` style output for
    small or large values. It is exported but unused.

## Not yet audited

`Phase.Stmt` (only the dispatch fallbacks) and `Phase.Parser`
(only the call-target and atom sections), `Phase.AST`, and the rest of
`app/Main.hs`.

## Baseline M comparison

Not implementation bugs -- these are gaps between Phase-the-language (as
the handbook specs it) and what a "normal" M/MUMPS system provides.
Two look like unintentional spec gaps rather than deliberate scope
decisions; the rest are decisions the handbook already states plainly,
listed here only so they're in one place.

37. **Fixed, per the team's decision: implemented.** `^(subs)` is a new
    `Ref` constructor (`RefNaked`); `Env` tracks the last NAMED global
    referenced (`envLastGlobalRef`) at every read, write, kill, lock,
    and intrinsic-ref site (`Phase.Eval.rememberGlobalRef`), and a
    naked reference resolves against it (`resolveNakedRef`), replacing
    the ENTIRE subscript list, not just the last element. `<NAKED>` if
    nothing has been named yet. Fixing this also turned up a real bug:
    `LOCK`/`UNLOCK` bare-field-accessed their target's `Ref`, which
    would have crashed on a naked (or dynamic) lock target; both now go
    through `resolveWriteTarget` like every other consumer.
38. **Fixed, per the team's decision: implemented.** New `SetTarget` type
    (`TargetRef` / `TargetPiece` / `TargetExtract`); `SSet` now holds
    `[(SetTarget, Expr)]`. `SET $PIECE(ref,delim[,which])=val` and
    `SET $EXTRACT(ref[,start[,end]])=val` parse and evaluate as a
    read-splice-write against the underlying reference, matching real
    M: `$PIECE`'s `which` beyond the current piece count pads with
    empty pieces; `$EXTRACT`'s `start` beyond the current length pads
    with spaces, and `end < start` inserts without removing. Scope
    limit: only these two; `SCallAssign`/`SMerge`/`SKill` keep plain
    `Ref` targets (no analogous splice operation on any of them in
    real M).

Deliberate, already documented in the handbook -- listed here only for
a single consolidated view against "normal M":

39. `@` indirection is recognized by the parser (`Expr.Indirect`) but
    always rejected at eval time; explicitly out of scope.
40. `$SELECT` is explicitly stubbed (`<NOTIMPL>`), not just missing.
41. `$ORDER` is 1-argument/forward-only; the standard 2-argument
    `$ORDER(ref,-1)` reverse-traversal form isn't in the spec at all.
42. `$$` extrinsic function calls are rejected at parse time on purpose
    (see README deviation 6 / the `$$` discussion already had).
43. A number of later-dialect intrinsics (`$FIND`, `$CHAR`, `$ASCII`,
    `$RANDOM`, `$STACK`, `$VIEW`, `$QUERY`, `$ZDATE`, ...) fall straight
    to `<UNDEFINED FUNCTION>` with no stub, by the handbook's own design.
