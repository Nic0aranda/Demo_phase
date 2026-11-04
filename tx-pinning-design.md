# Design proposal: relative revision addressing and `DO WITH` time-pinned blocks

**Status: Parts 1 and 2 and the explicit global binding list are all
IMPLEMENTED and tested** (relative revision addressing, `DO WITH @TX(...)`
pinned blocks, and `@BIND(...)`). See "As built" at the end for where the
implementation differs from, or pins down, what was written before it
existed -- in particular, the binding list below was designed as a
whitelist and was built as a pin SELECTOR instead.

## Motivation

`Phase.Storage.Engine`'s `seHistory` already stores every path's
revisions as a list, newest-first (`Map.insertWith (++) key [rev]
hist` on every write/kill prepends). "N revisions back" is therefore
already a cheap operation on data the engine keeps anyway, this
feature exposes something the storage layer already does internally,
rather than adding new bookkeeping.

## Part 1: relative revision addressing (`^[-N]`, `^[LAST]`)

Phase already has a time-travel bracket, `^[tx]NAME(subs)`, where `tx`
is an absolute transaction number. Transaction IDs start at 1 and
never go negative, so a negative number in that slot is currently
meaningless, free syntactic real estate, reused here rather than
introducing a new sigil (the team's original sketch used `@` for this;
`@` is already Phase's parsed-but-rejected indirection operator per
the handbook's "out of scope" list, so reusing the existing bracket
avoids that collision entirely).

- `^[0]FOO("BAR")`, the current (live) revision. Equivalent to the
  bare `^FOO("BAR")`.
- `^[-1]FOO("BAR")`, the PREVIOUS revision (one before the current).
- `^[-N]FOO("BAR")`, N revisions back.
- `^[LAST]FOO("BAR")`, the live value, named explicitly rather than
  left as the implicit default. Outside a `DO WITH` block (Part 2)
  this is just a redundant way of writing the bare reference; INSIDE
  one, it is the escape hatch back to live data (see Part 2).
- `^[LAST-N]FOO("BAR")`, ordinary arithmetic on the `LAST` reference
  point, not a separate piece of grammar. `LAST` is a reference point
  (what "0 revisions back" means), not a keyword with its own rules,
  `-N` already means "N revisions back from the reference point," and
  `LAST` simply makes the reference point nameable for use inside a
  block where it is no longer the default (see Part 2).

Rejected: a `FIRST` tag for "the first non-null value," and an
`@-@`-style self-referential form for the same thing, both from
the team's original sketch. `@-@` (go back N revisions, where N is the
tx of the current revision) does not actually compute "how many
revisions exist", it was dropped as not resolving to the intended
value. A `FIRST` reference point was considered as a replacement but
is deferred: it is a strict superset of what `-N` already gives you
(walk backwards until the oldest revision) and is not needed for the
core feature to be useful.

## Part 2: `DO WITH @TX(...)`, pinned-read blocks

```
DO WITH @TX(^[-2]FOO("BAR"))
  ... body ...
END DO
```

(Or with continuation dots, matching Phase's existing bare-`DO`-block
convention rather than `END DO`, both forms already exist for
ordinary `DO` blocks and `DO WITH` should follow the same convention,
not invent a third.)

`@TX(...)` takes a reference, ordinarily a relative or `LAST`-based
one from Part 1, and resolves it to one absolute transaction number
for the whole block. Inside the block, **every bare global reference**
is read as of that transaction, not live. This is standard database
`AS OF`/snapshot-isolation semantics applied to a block of Phase code.

### Decided semantics

- **Pins every bare global reference inside the block**, not just ones
  that opt in with their own `[-N]`. An unbound global inside the
  block (see "Deferred: binding list" below, once that lands) is an
  EVAL-TIME error, not a parse-time one, consistent with how
  `RefDynamic`/indirection-shaped errors are already handled elsewhere
  in this codebase (recognised at parse time where relevant, rejected
  cleanly at eval time rather than failing to parse).
- **`SET` is unaffected by the pin.** Writing inside a `DO WITH` block
  always lands at the true current transaction, same as anywhere else
 , writing to a past value is already banned, so there is no "write
  to the pinned tx" question to answer. The direct consequence: a bare
  read of a path you just `SET` inside the same block, in the same
  block, does NOT see your own write, it still shows the pinned (old)
  value, because your write created a transaction newer than the pin.
  This is the honest consequence of what "pinned" means (identical to
  how `AS OF` reads behave in real databases), but it is a genuine
  surprise for anyone used to ordinary M, where `SET X=1 WRITE X`
  always shows `1`. State this explicitly wherever this becomes a real
  spec, not just here.
- **Locals are unaffected.** Phase does not scope locals per-block
  anywhere else (only `NEW` does), and `DO WITH` does not change that.
  "Getting a value out of the block" is nothing more than an ordinary
  `SET` to a local that happens to already exist outside the block,
  no export list, no special scoping. ("Exporting" in earlier
  conversation turned out to mean exactly this, not a capture-style
  mechanism, see the dropped idea below.)
- **Nesting:** an inner `DO WITH`'s own `@TX(...)` expression is
  evaluated THROUGH the outer pin, not against live data. "Everything
  inside this block is pinned" applies uniformly, including the
  expression that sets up a nested pin.
- **Pins travel through `DO`/`CALL` by default.** A function reading
  "the balance" should not need to know it is being called from inside
  a pinned context to behave correctly, so the pin is carried via
  whatever process-wide mechanism survives a call, the same way
  `$TEST` and `envLastGlobalRef` (naked references) already do, rather
  than being lexically scoped to the block's own body.
- **Escaping the pin for one call is just nesting**, not a separate
  escape syntax: `DO WITH @TX(LAST) FOOBAR(...)` re-pins to live data
  for the duration of that call, using the exact same mechanism as any
  other nested `DO WITH`. No new rule to remember.

### Explicit global binding list (designed as a whitelist; built as a selector -- see "As built")

the team's instinct: rather than EVERY bare global inside the block being
implicitly reachable (and implicitly pinned), require globals used
inside a `DO WITH` block to be named in a binding list up front, closer
to a capture list than C++ lambda capture-by-value-vs-reference, since
every bound global shares the SAME single pin (there is one TX for the
whole block, not a per-variable one). An unbound global referenced
inside the block is an eval-time error. This makes "which globals does
this block even touch" fully explicit at the boundary, rather than
relying on `LAST` alone to mark the exceptions.

Mechanically this is cheap, a name whitelist checked at eval time, not
a new per-variable data structure, but it is new grammar (a binding
clause on `DO WITH`) and a new error path, on top of everything in Part
1/2 above, none of which has been built yet either. **Decided
sequencing: build bare pinning (Parts 1 and 2 above, unqualified) and
get some real use out of it FIRST; add the binding list as a refinement
afterward**, rather than designing and shipping the whole combined
system before any of it exists as working code.

Strawman syntax for the eventual binding clause (NOT decided, just a
starting point for when this gets picked back up):

```
DO WITH @TX(^[-2]FOO("BAR")) @BIND(^BAZ, ^QUX)
  ... body: ^BAZ and ^QUX are pinned; any other bare global is an eval-time error ...
END DO
```

### Explicitly out of scope / not the same mechanism

- **This is not `TRANSACTION`.** `DO WITH` pins READS (snapshot
  isolation); `TRANSACTION`/`EXPECTING` (per the handbook) governs
  WRITE atomicity and optimistic-concurrency conflict detection. A
  `DO WITH` nested inside a future `TRANSACTION` block is sensible
  ("read something historical while deciding what to write"); the
  reverse, pinning a transaction's own identity to a past tx, is not
  coherent at all, since a transaction's tx number is assigned at
  commit, which is by definition in the future relative to everything
  it read. These two mechanisms may end up sharing block syntax
  someday, but pinning-for-reads should not become the load-bearing
  implementation of write-transaction semantics; they have different
  failure modes (a pin never "fails"; a transaction can).

## Implementation sketch (for whenever this is built)

Not committed to, just the shape things would likely take, based on
precedent already in this codebase:

- A new `Env` field analogous to `envLastGlobalRef`
  (`envLastGlobalRef :: Maybe Text`, added for naked references),
  something like `envPinnedTx :: Maybe TxId`, `Nothing` outside any
  `DO WITH` block. Process-wide/threaded, not frame-scoped, matching
  how `$TEST` and `envLastGlobalRef` already behave, so it survives a
  `DO`/`CALL` the same way they do.
- `resolveWriteTarget`/`evalExpr`'s bare-global-read path
  (`Phase.Eval`) would need to consult `envPinnedTx` when resolving a
  NAMED global reference's time-travel dimension, reads route through
  `readAtTx envPinnedTx ...` instead of `readLive ...` when it is set.
  `SET`/`resolveWriteTarget` would NOT consult it (writes always go to
  the live path, per the decided semantics above).
- `^[-N]`/`^[LAST]`/`^[LAST-N]` need a new `Expr`/time-travel shape
  distinguishing "relative to current" from the EXISTING absolute
  `^[tx]NAME(...)` bracket (`Ref`'s `refTimeTravel :: Maybe Expr`
  field), most likely resolved by walking `seHistory` for the target
  path and indexing N entries back, rather than a new storage-layer
  primitive.

## As built

What the implementation settled that the sections above left open or
got slightly wrong:

- **Dotted blocks only.** `DO WITH @TX(...)` opens a block the way a
  bare `DO` does: the following lines start with `.`/`..`. The
  `END DO` spelling mentioned above is parsed but is a no-op at run
  time in this implementation (block structure is dot depth only), so
  it is not a working alternative. `Phase.Lines.endsWithBareDo` now also
  recognizes a line ending in a `DO WITH @TX(...)` clause (string-aware
  paren matching), so the dotted lines after it are a block rather than
  a continuation of the opener.
- **`@TX(...)` arguments:** `LAST` unpins (back to live data);
  a global reference such as `^[-2]FOO("BAR")` pins to the transaction of
  that revision; an unbracketed reference (`@TX(^FOO("BAR"))`) pins to
  the revision in effect right now (live, or the enclosing pin's); any
  other expression is an absolute transaction number >= 0. A bare
  negative number is a `<SYNTAX>` error, there is no path to count back
  along. `Phase.Eval.resolvePinExpr`.
- **Two baselines inside a block.** Outside a block, `^[-N]` counts
  back from the live latest revision. Inside a pinned block the
  *implicit* baseline is the revision in effect at the pin, so plain
  `^[-N]` counts back from the pin, while `^[LAST]` / `^[LAST-N]`
  always count from the true live latest. That is the whole meaning of
  `LAST` being "a reference point, not a keyword": it names the
  baseline that is no longer the default. An explicit absolute
  `^[tx]` bracket ignores the pin.
- **What is pinned:** every GLOBAL read, plain, `REF("^X")`-dynamic,
  naked `^(...)`, and the `$DATA` / `$GET` / `$ORDER` intrinsics. Locals
  never are, and writes (`SET`/`KILL`/`MERGE`) always land live, so a
  block does not see its own writes. The pin lives in
  `Env.envPinnedTx`, process-wide like `$TEST`, so it carries through
  `DO`/`CALL` made inside the block; `DO WITH @TX(LAST)` re-pins to
  live for one call. It is restored when the block ends normally, and an
  error discards the whole `Env` (see `Phase.Eval.Eval`), so a pin
  cannot leak out of a failed block.
- **`$ORDER` at a past transaction now exists** (`Phase.Storage.Engine.
  orderAtTx`, also reachable directly as `$ORDER(^[tx]G(k))`), because a
  pinned block that cannot iterate is not much use. Candidate children
  come from the resident history map's keys and each is judged by
  `dataAtTx`, so it can only find paths that map knows about, the same
  limit as every other time-travel read before a checkpoint seeds it.
- **REPL.** Entering a dotted block interactively now ends on a blank
  line (or a non-dotted line). This also fixed a pre-existing bug:
  every `DO` word was counted as an unclosed block opener, so even a
  single-line `DO tag` made the REPL swallow the following lines until
  EOF. The rule is `Phase.Lines.needsContinuation`, now shared with and
  tested outside the REPL.

### As built: `@BIND(...)`, nested scopes, and single-call forms

The binding list above was written as a whitelist (an unbound global is an
error). It was built the other way round: **the list selects which globals are pinned; every other global simply reads live.** There is no "unbound global" error.

- **Syntax.** `DO WITH @TX(arg) @BIND(^A,^B)`. No whitespace inside the
  list (Phase argument lists never allow it), and `@BIND` must follow
  `@TX(...)`. Each item is a WHOLE global, `^NAME` or `NS^NAME`; a
  subscripted item is a parse error. `@BIND()` is legal and binds nothing.
  `Phase.Parser.pPinSpec` parses the clause for both `DO WITH` and
  `CALL WITH`.
- **No `@BIND` clause = every global bound**, which is exactly the
  behaviour before this refinement existed.
- **What a bound global is pinned to.** The block's one `@TX(...)`
  transaction, resolved through the enclosing scope as before. Writes to a
  bound global are therefore invisible to that block's own reads of it, the
  same snapshot behaviour as before, now per global.
- **Pins are per global.** `Env.envPinnedTx` is still the block-wide pin
  (a block with no bind list); `envPinOverrides` holds the per-global pins
  a bind list installs (`Nothing` = explicitly live, so it can beat a
  non-live block-wide pin); `envBindScope` records the innermost enclosing
  bind list (`Nothing` = none) so a nested list can be checked against it.
  `Phase.State.effectivePin` is the one lookup every global read uses,
  including the `^[-N]` baseline (`resolveRelativeTx`) and the implicit pin
  of `$DATA`/`$GET`/`$ORDER`. So `^[-N]` counts back from the pin for a bound
  global and from the live latest for an unbound one.
- **Nesting** (`Phase.State.enterPinScope`):
  - A nested bind list must be a subset of the enclosing bind list, else
    `<BIND SCOPE> ^NAME`. With no enclosing bind list (including a block
    with `@TX(LAST)` and no list) any global may be named.
  - Only the globals the nested list names take the nested pin. Globals the
    outer block pinned but the inner list omits KEEP the outer pin: an inner
    block never silently releases an outer pin, only `@TX(LAST)` does.
  - A nested block with no bind list re-pins the globals the enclosing list
    bound, and no others (every global, when there is no enclosing list).
    So `DO WITH @TX(LAST)` inside a bound block releases exactly what the
    outer block pinned, and an inner `@TX(<tx>)` does not sweep in globals
    the outer list left live.
- **Bindings travel through `DO`/`CALL`** the same way the pin does (they
  are the same state), so a callee reads bound globals pinned and the rest
  live. Escaping for one call is the single-call form below.
- **Single-call forms.** `DO WITH @TX(x) [@BIND(...)] TAG(args)` (statement)
  and `CALL WITH @TX(x) [@BIND(...)] TAG(args)` (expression, new `Expr`
  constructor `CallWith`) run exactly one call inside their own pin scope
  and restore the enclosing scope afterwards. `DO WITH @TX(LAST) TAG()`
  releases the globals the enclosing scope pinned for that call only.
  `SET a,b=CALL ...` (multi-value destructuring) has no `WITH` form yet.
- **Lines.** `Phase.Lines.endsWithDoWith` treats a line ending in
  `@TX(...)` or `@TX(...) @BIND(...)` as a block opener; the single-call
  form ends in the call and is not one.
