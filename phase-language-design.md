# The Phase Language: Design Document

**Status:** Full vision, phased. This document describes the intended long-term
shape of Phase, the language layer of Phase DB, superseding "M76 with a few
extensions" as the project's framing. Individual features below are marked with
a rough priority so the document can be read either as a whole or as a backlog.

**Audience:** This is a working design document for the author's own use in
evolving the interpreter (`Interpreter.java`, `Parser.java`, `Tokenizer.java`,
`Stmt.java`, `Expr.java`) and the storage engine underneath it (`PhaseDB.java`
and the `com.phase.db.model` package). It assumes familiarity with that codebase
and does not re-explain it from scratch, though each section states which
existing classes it touches.

---

## 1. Why "Phase" and not "M"

Phase DB's storage model, hierarchical, sparse, transactionally versioned, and
time-traveled, is derived from MUMPS but has already diverged from it
substantially: per-`TRANS` time travel, a `JOIN` operator, `BigDecimal`-backed
arithmetic, and a WAL-based storage architecture are not things the 1976
standard has any notion of. The language sitting on top of that storage has,
until now, stayed close to M76 out of fidelity to the source material rather
than because M76's specific syntax choices are worth preserving on their own
merits.

This document takes the position that they mostly are not. M76 was designed for
teletypes and 8KB partitions. Its terseness (`S`, `W`, `$O`, `$D`), its total
absence of lexical scoping or structured error handling, its flat left-to-right
arithmetic, and its lack of any namespacing were load-bearing constraints of
1976 hardware, not design virtues worth keeping in 2026 for their own sake. At
the same time, M's actual good ideas, sparse hierarchical storage as the native
data model, values as strings coerced at the point of use, `$ORDER`-style cursor
traversal, and a flat line-oriented AST that maps directly onto a future
bytecode VM, are genuinely good and this document keeps every one of them.

The name change reflects this. Phase is not a MUMPS implementation with
extensions bolted on. It is a new language, MUMPS-derived, built for the storage
engine actually underneath it rather than for the storage engine M76 originally
assumed.

### 1.1 What stays from M76, and why

- **Sparse hierarchical storage as the native data model.**
  `^ORDERS(id,"LINES",1,"PRICE")` is the schema. No ORM layer, no relational
  translation step.
- **Values as strings at rest**, coerced to numeric form on use. `MValue`'s
  whole design, no boxed value type, numeric coercion only at
  arithmetic/comparison time, already captures this correctly and nothing below
  changes it.
- **The node-duality model.** A bare name and a subscripted name are independent
  storage slots that can both be live at once. `Environment` already implements
  this for locals; `SubscriptPath`/`GlobalRanges` implement it for globals.
  Every extension below is built on top of this, not around it.
- **Flat, line-oriented routine structure.** `Stmt.Routine` staying a flat
  `List<Stmt.Line>` rather than a nested-block AST is what makes a future
  bytecode VM a small step away rather than a rewrite. Nothing in this document
  proposes nested-block statements.
- **`$ORDER`-style traversal** as the fundamental cursor primitive over a sparse
  namespace.

### 1.2 What does not survive, and why

- **Mandatory terseness.** Abbreviated command forms (`S` for `SET`) remain
  legal, but the language's idiomatic, documented style is the full word.
  Terseness becomes a REPL convenience, not the only spelling available.
- **Left-to-right, no-precedence arithmetic.** `2+3*4` being 20 rather than 14
  is a real, well-documented M quirk rooted in a simple one-pass teletype
  parser. It buys nothing today and costs new readers real confusion. Phase
  adopts conventional operator precedence.
- **Dynamic-scoping-only locals with no true parameter passing.** `NEW`'s
  stack-scoping is a genuinely good mechanism and is kept, but it becomes one
  tool among several rather than the only one. Named parameters, multiple return
  values, and reference parameters are added.
- **String-eval indirection (`@`).** Arbitrary runtime code-as-text is a
  correctness and security liability with no offsetting benefit once the
  language has real dynamic-reference and macro mechanisms. `@` is retired in
  favor of narrower, safer replacements (Section 9).
- **A flat, unnamespaced pool of routine tags and global names.** Workable for
  small routines, actively painful once a codebase has multiple substantial
  subsystems.

---

## 2. Operator precedence

**Priority: high. Touches:** `Expr.java`, `Parser.java` (`parseExpr`),
`Interpreter.evalBinary`.

### 2.1 The decision

M76 has no operator precedence at all. `Expr.Binary`'s class doc states this
directly: binary operators associate strictly left to right, so `2+3*4`
evaluates as `(2+3)*4 = 20`. `Parser.parseExpr` implements this as a single flat
loop with no precedence table.

Phase replaces this with conventional precedence, ordered from lowest to highest
binding:

1. `&` (logical AND), `!` (logical OR)
2. `=` `<` `>` `[` (contains) `]]` (sorts after) `?` (pattern match)
3. `_` (string concatenation)
4. `+` `-` (binary)
5. `*` `/` `\` (integer division) `#` (modulo)
6. `**` (exponentiation)
7. unary `-` `+` `'` (logical NOT)

This ordering follows ordinary arithmetic convention (multiplication before
addition, exponentiation before multiplication) with string concatenation placed
between comparison and additive, matching how most string-supporting languages
resolve the ambiguity, and logical operators placed lowest, matching
short-circuit-adjacent conventions in most C-derived languages even though
Phase's `&`/`!` are not short-circuiting (see 2.3).

### 2.2 Why fix it rather than keep it as a faithful quirk

The no-precedence rule is frequently cited as one of M's most surprising
properties to newcomers, and it earns nothing today. It existed because a
single-pass, no-lookahead parser is what 1976 assembly-language MUMPS
implementations could afford to write and execute quickly on the hardware of the
era, not because flat left-to-right evaluation is a good property for a language
to have. Phase is not trying to be executable by a 1976 interpreter running on a
PDP-11, so this constraint is not preserved.

A pragma-based dual-mode ("interpret this line/routine in legacy left-to-right
mode") was considered and rejected. Two arithmetic semantics coexisting in one
language, distinguished by an easy-to-miss annotation, is a worse failure mode
than simply changing the rule once: it means every reader of Phase source has to
check which mode a given line is in before trusting what an expression evaluates
to. If historical M76 source ever needs to run verbatim (for instance, replaying
an old COSTAR/DHCP-era routine unmodified), that is better served by a separate,
explicitly labeled compatibility mode at the file level, not a silent per-line
pragma. No such compatibility mode is planned unless a concrete need for one
materializes.

### 2.3 Short-circuit evaluation

M76's `&` and `!` are not short-circuiting; both operands are always evaluated.
Phase keeps this. Short-circuiting matters most when an operand has a side
effect or can fail (division, array access past bounds), and Phase's expression
grammar deliberately keeps expressions side-effect-free to evaluate (per
`Expr`'s own class doc: "every node either holds literal data or references to
child Exprs, with no hidden environment capture"). Introducing short-circuiting
would only pay off once expressions can also signal conditions (Section 3) as
part of ordinary evaluation, which is deliberately not part of this design;
conditions are signaled from statements and intrinsics, not from bare operator
evaluation. This is revisited only if experience shows it is actually needed.

### 2.4 Implementation shape

`Parser.parseExpr` changes from a flat loop to conventional precedence-climbing
(or an explicit table of `parseOr` / `parseCompare` / `parseConcat` /
`parseAdditive` / `parseMultiplicative` / `parsePower` / `parseUnary` layers,
each calling down to the next). `Expr.Binary` itself needs no change; it already
just holds `(left, op, right)` and the tree shape it produces is identical in
kind to what it produces today, only nested differently.
`Interpreter.evalBinary`'s dispatch table is unaffected; it evaluates whatever
tree shape it is handed.

---

## 3. Error handling: conditions and retry-scoped blocks

**Priority: high. Touches:** `MRuntimeException.java` (kept, gains a sibling),
`Interpreter.java` (new handler stack), `Stmt.java` (new node kinds),
`Parser.java`, `PhaseDB.java` (`conflictError` and friends become signals).

### 3.1 The problem with plain unwind-only exceptions

`MRuntimeException` today models exactly Java's exception behavior: something
goes wrong, it throws, the routine unwinds, done. This is a reasonable default
and is kept as the fallback path (Section 3.5), but it is a poor fit for
situations Phase DB's own storage layer produces routinely rather than
exceptionally:

- A write conflict (`PhaseDB.Transaction.commit()`'s `conflictError`,
  first-writer-wins optimistic concurrency) is an expected, common event under
  real concurrent load, not a bug. The correct response is very often "retry
  with the current value," and that decision can only sensibly be made by the
  caller, not by the storage layer raising a bare `IllegalStateException`.
- `<UNDEFINED VARIABLE>` on a bare read is sometimes a real bug and sometimes
  just means "use a default," which is exactly why `$GET` already exists as a
  workaround, silently swallowing the exception. The existence of that
  workaround is itself evidence that the language is missing a real primitive
  here.
- A `LOCK` timeout currently returns a bare `boolean`, discarding the reason for
  failure and forcing the caller to pre-commit to a policy before even
  attempting the lock.

Common Lisp's condition system solves this by separating "signal that something
happened" from "decide how to unwind, if at all." Phase adopts a substantially
narrowed version of that idea: no true resumption, no continuations. A signal
can only ever mean "unwind to the nearest enclosing handler," and a recovery
action can only ever mean "re-run the enclosing block from its top." This is
strictly less powerful than full Lisp conditions, and that is a deliberate
simplification: it covers every actual use case identified above (conflict
retry, default-on-undefined, lock-timeout recovery) without requiring any change
to how the interpreter's Java call stack behaves, because "retry the block" is
mechanically identical to the loop-back jump `FOR` already performs.

### 3.2 Surface syntax

```
DO
    SET ^ORDERS(id,"STATUS") = "SHIPPED"
HANDLE <CONFLICT> AS c
    WRITE "conflict on ", c("global"), c("path"), " retrying with tx ", c("actualTx"), !
    RETRY WITH expectedTx = c("actualTx")
CATCH <ANYTHING> AS e
    WRITE "unrecoverable: ", e("message"), !
END DO
```

- `DO ... END DO` is an ordinary block, the same bare `DO` that exists today,
  given an explicit closing keyword instead of relying purely on dot-depth or
  the next `QUIT`.
- `HANDLE <NAME> AS binding ... ` introduces a retry-capable clause. Its body
  runs when a matching condition is signaled anywhere in the block's dynamic
  extent. If the body executes `RETRY [WITH field=expr, ...]`, the entire
  enclosing `DO` block re-executes from its first statement; fields passed via
  `WITH` are available to expressions inside the block on the next pass through
  a small per-block retry context. A `HANDLE` body that falls off its end
  without executing `RETRY` is a parse-time-detectable error (`<SYNTAX> HANDLE
  block must RETRY or fall through to a CATCH`); this is deliberate, so a
  `HANDLE` clause can never leave a signal half-handled.
- `CATCH <NAME> AS binding` is the ordinary unwind-and-move-on form. Its body
  runs once, and control then falls through to whatever follows `END DO`,
  exactly as if the block had completed normally.
- `CONDITION <NAME> (field1, field2, ...)` declares a condition type up front,
  giving it named, typed-by-string fields rather than a single opaque message
  string. `<CONFLICT>` becomes:

```
CONDITION <CONFLICT> (global, path, expectedTx, actualTx)
```

- `SIGNAL <NAME> (field: expr, ...)` raises a condition. Signaling walks the
  enclosing handler stack outward for the nearest block with a matching `HANDLE`
  or `CATCH` clause. If none exists anywhere up to the routine boundary, it
  degrades to today's unwind-and-halt behavior; see 3.5.

### 3.3 Why this is a thin layer over existing mechanisms, not a new execution model

The load-bearing realization is that "retry the block" is exactly what
`Interpreter`'s existing `ForState`/`forStack` machinery already does. A `FOR`
loop is a `(line, stmtAfterFor, loop variables)` record pushed on entry and
consulted by `stepForOrFallThrough` to decide "loop back" versus "fall through."
A retryable `DO ... HANDLE ... END DO` block needs the identical shape:

```java
private static final class HandlerFrame {
    final int blockStartLine, blockStartStmt;
    final int endDoLine;
    final Map<String, ConditionClause> handleClauses;
    final Map<String, ConditionClause> catchClauses;
    final Map<String, String> retryContext = new HashMap<>();
}
```

`SIGNAL <NAME>(...)` is implemented as an ordinary Java exception, a new
`Condition` class, sibling to `MRuntimeException`, carrying a condition name and
a `Map<String, String>` of field values, consistent with `MValue`'s
everything-is-a-string model. It is thrown from wherever the signal originates
(an intrinsic, a `PhaseDB` call, an explicit `SIGNAL` statement) and caught by
`Interpreter`'s dispatch loop at the boundary of each active `HandlerFrame`,
exactly the same pattern already used for `QuitSignal`/`HaltSignal`. On catching
a match:

- If it matched a `catchClauses` entry: run that body, then jump past `END DO`,
  the same "pop and resume at return point" pattern `doReturn` already
  implements for `QUIT`.
- If it matched a `handleClauses` entry: run that body; if it executes `RETRY`,
  overwrite `retryContext` and jump `pcLine`/`pcStmt` back to
  `blockStartLine`/`blockStartStmt`, literally reusing `stepForOrFallThrough`'s
  jump.

No continuations, no re-entrant handler execution, no change to how Java's own
call stack unwinds. This is the entire reason the "restrict restarts to
retry-the-block" simplification was chosen over true resumption: it costs
nothing beyond what `FOR` already pays for.

### 3.4 Condition fields and typed access

A signaled condition's bound fields are exposed to the handler body as
`c("fieldName")` function-call syntax, not dotted `c.fieldName` access. This was
a deliberate choice made during implementation: Phase has no other
dotted-member-access grammar anywhere (`NAMESPACE`'s dotted names in Section 7
are qualified tag/global names, a lexical convention, not a runtime field-access
operator), so introducing dot access solely for condition fields would add a
one-off grammar rule for a single narrow case rather than reusing a shape the
language already has. Function-call-style access is sugar over a small
locally-scoped bag of name-to-string bindings, `NEW`-scoped to the handler body
automatically, mechanically no different from how `Expr.LocalIndex` already
resolves a subscripted local, just spelled with parentheses instead of a dot.
`CONDITION` declarations are collected once, by a small new `ConditionRegistry`
populated the same way `RoutineBuilder.build` already collects the `tags` map,
so that `SIGNAL <NAME>(...)` can be checked against the declared field list
rather than silently accepting a typo'd field name.

Every built-in condition (the full table in 3.6, plus `<TRAVERSAL-LIMIT>` from
Section 10.4) is pre-registered into `ConditionRegistry` with its real field
list at interpreter startup, rather than left unregistered and exempted from
validation. This matters for a reason beyond consistency: if built-ins were
exempt rather than pre-registered, nothing would stop a routine from declaring
its own `CONDITION <CONFLICT>(foo, bar)` with a field list that has nothing to
do with what `PhaseDB.Transaction.commit()` actually signals, silently shadowing
the real contract with no error anywhere, since an exempt name is never checked
against anything. Pre-registration closes that hole by making every built-in
condition name reserved: a routine attempting to declare its own `CONDITION
<CONFLICT>(...)` fails at build time (`<SYNTAX> <CONFLICT> is a reserved
condition name`), and every `SIGNAL <CONFLICT>(...)` call site, built-in or user
code alike, is checked against the one real, interpreter-owned field list rather
than going unchecked.

### 3.5 Escape and the unwind fallback

`SIGNAL` for a condition with no matching `HANDLE`/`CATCH` anywhere in the
current call stack propagates outward exactly like `MRuntimeException` does
today: it is, in fact, promoted into an `MRuntimeException`-shaped halt once it
escapes the routine entirely. This is what makes the mechanism strictly additive
rather than a rewrite. Every code path that does not opt into `HANDLE`/`CATCH`
behaves exactly as it does today.

### 3.6 What Phase's built-in conditions replace

This table is the exact, complete list of condition names pre-registered into
`ConditionRegistry` at interpreter startup, per 3.4; `<TRAVERSAL-LIMIT>`
(Section 10.4) is a fifth entry, added when graph traversal was designed, and
follows the identical pre-registration rule.

| Today                                                                         | Becomes                                                                                                                                                                    |
|-------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `IllegalStateException` from `PhaseDB.Transaction.commit()`'s `conflictError` | `SIGNAL <CONFLICT>(global, path, expectedTx, actualTx)`                                                                                                                    |
| `<UNDEFINED VARIABLE>` on a bare read                                         | `SIGNAL <UNDEFINED>(name, path)`, catchable structurally; `$GET` remains as the cheap default-value shortcut for the common case that does not need full block ceremony    |
| `<DIVIDE BY ZERO>`                                                            | `SIGNAL <DIVIDE-BY-ZERO>(dividend, divisor)`, fatal by default (no sensible built-in retry story), but catchable for routines that want to substitute a sentinel value     |
| `LOCK` timeout returning a bare `false`                                       | `SIGNAL <LOCK-TIMEOUT>(global, path, timeoutMs)` for callers who opt into the block form; the plain boolean-returning `LOCK` statement is unchanged for callers who do not |

The last row is the general principle stated explicitly: **this is an opt-in
overlay, not a replacement for the terse paths.** `LOCK +^X(1):5` returning a
boolean and `$GET` silently defaulting on undefined are both still available at
full speed. `DO ... HANDLE ... END DO` exists for the cases, write conflicts
under real concurrency chief among them, where a bare boolean or a
silently-substituted default is not sufficient and structured recovery is
actually wanted.

### 3.7 Explicitly out of scope

- **True resumption / continuations.** Rejected per 3.1; would require the
  interpreter's core loop to support re-entrant handler execution without
  unwinding the Java stack, a materially larger project with no identified use
  case that retry-the-block does not already cover.
- **Signals as general control flow.** `SIGNAL`/`HANDLE` are reserved for
  genuine error and recovery situations. Ordinary control flow continues to use
  `IF`, `FOR`, `GOTO`, and the `EVALUATE` construct described in Section 6.
  Using conditions as a `goto`-with-extra-steps is explicitly discouraged and
  not a design goal.

---

## 4. Calling convention: named parameters, multiple returns, reference parameters, value-snapshot closures

**Priority: high. Touches:** `Stmt.java` (`Do`, new `Return`/`Call` nodes),
`Interpreter.java` (`Frame`, `execDo`, `doReturn`), `Environment.java` (aliased
locals for reference parameters), `Parser.java`.

### 4.1 The problem

M76's calling convention, as implemented today, is positional-by-value only, and
mostly in name: `execDo`'s own comment states plainly that arguments are
"evaluated for their side effects and ignored positionally until a signature
mechanism exists." There is no way to name a parameter, no way to return more
than one value without manually writing into shared locals, and no way to pass
anything by reference short of using a global as a side channel. This is a
widely cited weak point of real M as well, not an artifact specific to this
codebase; M's own later standards revisions added formal parameter lists
precisely because positional-only calling was recognized as insufficient.

### 4.2 `CALL` as the value-producing counterpart to `DO`

Phase splits M's overloaded `DO` (used today for both "run a block" and "invoke
a tag, ignoring any return value") into two forms:

- `DO` remains statement-position, side-effects-only, exactly as today.
- `CALL` is the new expression-position form. It invokes a tag and produces one
  or more values.

```
TOTAL(orders, taxRate)
    NEW sum
    SET sum = 0
    FOR i=1:1:$LENGTH(orders) SET sum = sum + orders(i)
    RETURN sum, sum * taxRate
```

```
SET total, tax = CALL TOTAL(myOrders, 0.19)
```

`RETURN expr, expr, ...` is a first-class multi-value alternative to bare
`QUIT`, resolved at the call site by destructuring against the target list on
the left of `SET`. A return arity mismatch (a `CALL` producing one value
assigned to two targets, or vice versa) is a parse/runtime error, `<SYNTAX> CALL
returned 1 value, 2 targets expected`, rather than silently padding with
undefined values. This follows the same instinct already visible elsewhere in
the codebase (`sealed` interface exhaustiveness, `Stmt.Kill`'s explicit refusal
to guess subtree-vs-point-kill) of failing loudly on a shape mismatch rather
than quietly guessing.

**AST shape, as implemented.** `SET total, tax = CALL TOTAL(...)` is its own
`Stmt.CallAssign(List<Expr> targets, Expr.Call call, Expr postcondition)` node,
distinct from ordinary `Stmt.Set`, because its right-hand side is always exactly
one `Expr.Call` evaluated once and fanned out positionally across every target,
whereas `Set`'s targets are independent `target=value` pairs, each with its own
right-hand side. `Parser.parseSet` disambiguates the two forms with a
speculative parse, since both share the same leading `target(,target)*` shape up
to the `=`. `Expr.Call` itself, used in ordinary expression position (not as a
`CallAssign` right-hand side), yields only the first value a callee returns; the
full multi-value list only ever gets destructured at a `CallAssign` site, since
an ordinary expression context, a single arithmetic slot, a `WRITE` argument,
has nowhere to put more than one value. This is a sharper resolution of what
this section originally left implicit and is the authoritative shape going
forward.

### 4.3 Named parameters

Tag definitions gain named formal parameters, bound by position at the call site
but referred to by name inside the body (as already shown in `TOTAL(orders,
taxRate)` above). This requires `RoutineBuilder`/`Parser` to capture a tag's
formal parameter list (today parsed and discarded, per `Parser.line`'s comment:
"here we just don't choke on the parens if present") and for `execDo`'s
call-binding step to actually assign each argument's evaluated value into a
correspondingly-named local in the callee's fresh `NEW`-scoped frame, rather
than discarding the association after evaluation.

### 4.4 Reference parameters

Alongside by-value parameters, Phase adds an explicit by-reference form:

```
CALL ADJUSTTOTAL(&total, discount)
```

A parameter written with a leading `&` at the call site is bound in the callee
not as a copy but as an alias into the caller's own `Environment` slot. This
requires `Environment` to support an aliased `LocalVar` entry, a `LocalVar`
whose storage is not its own `TreeMap` but a live reference to another name's
entry, scoped and un-aliased on frame pop using exactly the same push/pop
discipline `NEW`'s restore mechanism already implements. This is a real,
contained addition to `Environment`, not a change to its overall model: locals
remain a single flat process-wide pool with `NEW`'s stack-scoped restore as the
only lifetime tool: an aliased entry is still popped and restored on frame exit
exactly like an ordinary `NEW`ed one, it merely points elsewhere while live.

### 4.5 Closures: value-snapshot only

A fuller closure design, a `DO`-block value capturing its defining environment
by reference and outliving the frame that defined it, was considered and
explicitly rejected for this document. `Environment`'s entire design assumes
locals exist only for the lifetime of the frame that owns them, with `NEW`'s
stack-discipline restore as the sole lifetime mechanism; a true closure needs
its captured bindings to outlive the frame that created them, which is a direct
conflict with that invariant, and one that would ripple into `KILL`,
`$ORDER`-over-locals, and every other Environment consumer that currently
assumes a single flat pool.

Phase instead supports **value-snapshot closures**: a bundle of copied values
plus a routine/tag reference, re-injected as fresh locals on each invocation.

```
SET discountFn = CLOSURE APPLYDISCOUNT() CAPTURING rate = 0.15
...
SET adjusted = CALL discountFn(orderTotal)
```

This covers the actual ergonomic want, passing a "configured function" around as
a value, without touching `Environment`'s lifetime model at all: `discountFn` is
really just a small local record naming a tag and holding a snapshot of captured
values, and invoking it through `CALL` is ordinary tag invocation with those
captured values pre-seeded as additional locals in the fresh frame. True
closures over shared mutable state remain future work, explicitly deferred, with
the reason stated here rather than left implicit: they would require rethinking
`Environment`'s core lifetime invariant, which every other local-variable
mechanism in the language currently depends on.

---

## 5. Transaction blocks

**Priority: high (low implementation cost, direct wrap of existing engine
capability). Touches:** `Interpreter.java` (new transaction stack), `Stmt.java`,
`Parser.java`, `PhaseDB.java` (no changes needed; wrapped, not modified).

### 5.1 Motivation

`PhaseDB` already has real ACID multi-change transactions (`Transaction`,
WAL-backed, `expectedTx`-guarded optimistic concurrency), but nothing at the
language level exposes them as a language construct. Today, a routine wanting an
atomic multi-`SET` transaction has no way to express that at all; every
`SET`/`KILL` from M code goes through the single-change convenience wrappers
(`db.set(...)`, `db.killSubtree(...)`), each its own separate commit.
`TRANSACTION ... END TRANSACTION` closes this gap with what is close to a direct
syntactic wrapper, not a new engine capability.

### 5.2 Syntax

```
TRANSACTION
    SET ^ORDERS(id,"STATUS") EXPECTING $TX = "SHIPPED"
    SET ^ORDERS(id,"SHIPPED-AT") = $HOROLOG
HANDLE <CONFLICT> AS c
    RETRY
END TRANSACTION
```

- Every `SET`/`KILL` inside the block routes through one `PhaseDB.Transaction`
  opened on entry, rather than each call opening and committing its own, via
  `tx.set(...)`/`tx.pointKill(...)`/`tx.killSubtree(...)`.
- `tx.commit()` fires at `END TRANSACTION`. If a condition escapes the block
  unhandled, `tx.rollback()` fires instead (mirroring the existing
  `Transaction.close()`'s auto-rollback-if-not-done behavior).
- `EXPECTING expr` is a `SET` modifier threading a value into `tx.set(global,
  path, value, expectedTx)`'s `expectedTx` parameter, exposing first-writer-wins
  conflict checking at the statement level.
- `HANDLE`/`CATCH`/`RETRY` are exactly the mechanism from Section 3, not a
  second parallel mechanism. `TRANSACTION` is sugar for "a `HANDLE`-capable
  block that also opens a `PhaseDB.Transaction`," and a conflict on commit
  signals `<CONFLICT>` through the same handler-frame machinery a plain `DO ...
  HANDLE ... END DO` block would use. This is a deliberate design constraint,
  not an incidental similarity: building two separate "retry the block"
  implementations, one for transactions and one for general conditions, would be
  pure duplicated maintenance burden with no offsetting benefit.

### 5.3 Implementation shape

`Interpreter` gains a `Deque<PhaseDB.Transaction>`, mirroring the existing
`callStack`/`forStack`/handler-frame stacks in structure: when non-empty,
`assign()` and `killOne()` check the top of this stack and route through it
instead of calling `db.set(...)`/`db.killSubtree(...)` directly. This is the
same "an optional stack changes how a primitive statement executes" pattern the
interpreter already implements three times over (`FOR`'s loop stack, `DO`'s call
stack, and the handler-frame stack from Section 3); this is simply a fourth
instance of an established pattern, not a new kind of interpreter state.

### 5.4 Nesting: explicitly forbidden in this phase

`PhaseDB.Transaction` has no support for nested transactions or savepoints
today, and MUMPS's own TP model (`TSTART`/`TCOMMIT`) which does support nesting
is a substantially larger commitment to take on. Rather than leave this
ambiguous, Phase's v1 `TRANSACTION` block **forbids lexical nesting outright**:
a `TRANSACTION` block found lexically inside another `TRANSACTION` block is a
parse-time error, `<SYNTAX> nested TRANSACTION not supported`. Building real
savepoint support is left as explicit future work, contingent on
`PhaseDB.Transaction` itself gaining that capability first; this document does
not commit to a timeline for that.

---

## 6. EVALUATE: a COBOL-style multi-branch construct, generalized

**Priority: medium. Touches:** `Stmt.java` (new `Evaluate` node), `Parser.java`,
`Interpreter.java`, `SubscriptPath.java` (consulted, not modified).

### 6.1 Background and a scope note

An earlier iteration of Phase DB (the pre-M76, Pick-derived version) had a
query-language `EVALUATE` construct, part of a now-deprecated `QueryEngine` that
is not wired up to anything in the current system. This section does not attempt
to preserve compatibility with that implementation; it is dead code from a
different lineage. It is used here only as useful prior art for what a
COBOL-flavored `EVALUATE` can look like, since COBOL's `EVALUATE` is one of that
language's genuinely well-regarded constructs: a real generalization of
`switch`, not merely sugar for equality dispatch.

A separate, narrower `MATCH` construct for destructuring an already-bound
`SubscriptPath` value (segment-count and wildcard matching) was considered
during design discussion and rejected in favor of folding that capability into
`EVALUATE` directly, as one more clause kind, rather than introducing a second
keyword that does a structurally similar job.

### 6.2 COBOL's three modes, all supported

**Switch mode**, one subject, multiple value tests:

```
EVALUATE orderStatus
    WHEN "PENDING" DO ShipOrder()
    WHEN "SHIPPED" DO NotifyCustomer()
    WHEN OTHER      DO LogUnknownStatus()
END EVALUATE
```

**Condition-chain mode**, no meaningful subject, each `WHEN` its own boolean
tested in order. This is Phase's replacement for a long `IF` / `ELSE IF` chain,
which M76 has no native support for beyond nested postconditionals:

```
EVALUATE TRUE
    WHEN price > 1000 DO ApplyBulkDiscount()
    WHEN price > 100  DO ApplyStandardDiscount()
    WHEN OTHER          DO NoDiscount()
END EVALUATE
```

**Multi-subject tuple matching (`ALSO`)**, COBOL's most distinctive idea, each
`WHEN` supplying one test per subject:

```
EVALUATE customerType ALSO orderTotal
    WHEN "PREMIUM" ALSO ANY    DO FreeShipping()
    WHEN ANY       ALSO > 500  DO FreeShipping()
    WHEN OTHER                  DO StandardShipping()
END EVALUATE
```

### 6.3 The addition beyond COBOL: path-shape `WHEN` clauses

Where `MATCH` would have destructured a `SubscriptPath` by segment count and
wildcard, `EVALUATE` absorbs this as one more `WHEN` clause flavor, usable when
the subject is a path value rather than a scalar:

```
EVALUATE path
    WHEN (_, "LINES", n) DO HandleLineItem(n)
    WHEN (_, "STATUS")    DO HandleStatus()
    WHEN OTHER              WRITE "other: ", path, !
END EVALUATE
```

`_` matches and discards exactly one segment; a bare identifier binds that
segment's value into a fresh local for the clause body; `*` matches any
remaining depth as an open-ended tail. A `WHERE` guard is permitted on any
clause, evaluated with that clause's bindings already in scope:

```
WHEN (_, "LINES", n) WHERE $DATA(n) DO HandleLineItem(n)
```

Mechanically, a path-shape clause compiles to a sequence of
`SubscriptPath.depth()` and per-segment `Subscript` comparisons, structurally
similar to what `SubscriptPath.isPrefixOf` already does, generated once per
`WHEN` clause rather than hand-written. No new storage primitive is introduced;
this is sugar over `SubscriptPath` methods that already exist.

`EVALUATE` is deliberately scoped to destructuring a path value already in hand.
It does not drive traversal and does not query storage on its own; anything
query-shaped (walking a global, joining across records, filtering by predicate
across many paths) is a job for a real query surface, not for this construct,
should Phase ever build one. This boundary is stated explicitly so `EVALUATE`
does not grow into a second, weaker query language by accretion.

**Implementation status: deferred.** Building this surfaced a real gap this
section did not anticipate: every `Expr` in Phase evaluates to a plain `String`,
per `MValue`'s everything-is-a-string invariant, and nothing in the expression
model has a notion of "a path value" for a path-shape `WHEN` clause to
destructure in the first place. This is not a parser or interpreter-dispatch
problem to solve locally inside `EVALUATE`; it is the same foundational question
already on record in Section 8.5 and Section 14 (does Phase ever want a value
kind beyond plain strings, and if so, what does that do to `MValue`'s core
invariant). Path-shape `WHEN` clauses are therefore deferred alongside
list/array literals and true closures, to be resolved together in one dedicated
values-model design pass rather than settled as a side effect of building
`EVALUATE`. The `Stmt.Evaluate` AST already reserves a `WhenTest` shape for this
(see 6.4), so no rework is needed once that question is answered; the switch,
condition-chain, and `ALSO` tuple-matching modes from 6.2 are unaffected and are
the current, complete, implemented scope of `EVALUATE`.

### 6.4 Implementation shape

`Stmt.Evaluate(List<Expr> subjects, List<WhenClause> clauses, Expr
postcondition)` holds subjects and clauses directly rather than as line-index
references, since a `WHEN` clause's body is a single statement (the rest of its
own line), not a multi-line body needing the line-index-reference scheme
`HANDLE`/`CATCH` blocks need (Section 3.3); folding everything into one node is
both simpler here and what this shape asks for. Each `WhenClause(boolean
isOther, List<WhenTest> tests, Expr guard, Statement body)` holds either the
catch-all `WHEN OTHER` (`isOther = true`, tests ignored) or exactly one
`WhenTest` per subject, ALSO-joined at parse time, plus the optional `WHERE`
guard.

`WhenTest` is a three-case sealed interface, and all three of COBOL's modes from
6.2 reduce to it uniformly: `ValueTest(Expr value)` (equality; condition-chain
mode's `WHEN price > 1000` also goes through this case, since the subject `TRUE`
evaluates to `"1"` and the comparison expression's own boolean result is what
gets equality-tested against it, with no special-casing needed for that mode),
`CompareTest(boolean negate, Token.Type op, Expr value)` (a bare leading
relational operator applied directly against the subject, e.g. `ALSO`-mode's `>
500`), and `AnyTest()` (the `ANY` wildcard, always matches). A fourth case for
path-shape patterns is deliberately not present yet, per the
implementation-status note in 6.3.

`Interpreter` gains one new `execEvaluate` dispatch case alongside the existing
`execIf`/`execFor`/etc., evaluating subjects once, then testing each `WHEN`
clause in order and running the first matching body, falling through to `OTHER`
(or falling through with no effect, if no `OTHER` clause is present and nothing
matches, matching COBOL's own permissive behavior here rather than erroring).
`RoutineBuilder` resolves the flat
`EvaluateHeader`/`WhenClauseLine`/`EndEvaluate` marker lines into one `Evaluate`
node, mirroring how `HandledBlock` is resolved from its own flat marker lines
(Section 3.3).

---

## 7. Namespaces

**Priority: medium, low implementation cost. Touches:** `RoutineBuilder.java`,
`Parser.java`, `Interpreter.resolveRef`, `PhaseDB.java` (`GLOBAL_PAT` widened).

### 7.1 The problem

M76's namespace model is entirely flat: `TAG^ROUTINE` is the only qualification
mechanism, routine names are one flat pool, and global names are similarly flat
(`PhaseDB.GLOBAL_PAT` currently permits `[A-Za-z][A-Za-z0-9]*`, no separators of
any kind). This is workable for small routines and becomes a real liability once
a codebase spans multiple substantial subsystems, as any project of real size
eventually does.

### 7.2 Two separate concerns, two different costs

Routine/tag organization and global-name identity are kept deliberately separate
in this design, because they have very different implementation costs and very
different risk profiles.

**Routine and tag namespacing** is pure language-layer sugar with no storage
implications at all:

```
NAMESPACE Billing.Invoices

TOTAL(orders, taxRate)
    ...
```

```
CALL Billing.Invoices.TOTAL(orders, 0.19)
```

A `NAMESPACE X.Y` declaration at the top of a routine's source is a
`RoutineBuilder`-level prefix applied to every tag it defines, so `TOTAL` is
stored in the routine's `tags` map under the key `Billing.Invoices.TOTAL`. A
qualified `CALL`/`DO` reference resolves via the same dotted-name lookup. Within
a `NAMESPACE` block, an unqualified tag reference resolves against the current
namespace's prefix first (`resolveRef` tries `currentNamespace + "." + tag` in
`routine.tags()` before falling back to a bare, unprefixed lookup), so routines
within one namespace can call each other without repeating the full qualified
name at every call site. No `Interpreter` change beyond this resolution order,
and no `PhaseDB` change at all, since `resolveRef`'s tag lookup already accepts
an arbitrary string key.

**Global names** gain dots as legal, purely cosmetic characters:
`^Billing.Invoices.ORDERS` becomes valid, requiring only that
`PhaseDB.GLOBAL_PAT` widen from `[A-Za-z][A-Za-z0-9]*` to something like
`[A-Za-z][A-Za-z0-9.]*`. This carries **no semantic weight whatsoever**.
`^Billing.Invoices.ORDERS` and `^BillingInvoicesOrders` are equally flat as far
as `PhaseDB` is concerned; dots are a human organizational convention only. No
`SubscriptPath`, `GlobalRanges`, or WAL mechanism needs to change, since none of
them are sensitive to what characters appear in a global's name, only to what
appears in its subscripts, which already accept arbitrary strings.

### 7.3 What is explicitly rejected: semantic namespace isolation

A version of this feature where `NAMESPACE` changes actual storage isolation
(separate index roots per namespace, access control scoped to a namespace, and
so on) was considered and rejected for this document. Global names are already
an unstructured flat pool by design; `PhaseDB`'s own class documentation states
that subscripts, not global names, already carry all the real hierarchy the
storage model needs. Introducing genuine per-namespace storage isolation would
be solving a multi-tenancy or access-control problem the project does not
currently have, at real and ongoing cost to the on-disk format (touching
`Checkpoint.files`, WAL replay, and likely the format version), for no
identified present benefit.

### 7.4 A deliberate asymmetry, stated explicitly

`NAMESPACE` does **not** implicitly prefix bare global references. Writing
`^ORDERS` inside a `NAMESPACE Billing.Invoices` block does not silently become
`^Billing.Invoices.ORDERS`. Globals must always be written in full; only
routine/tag references receive the qualification sugar described in 7.2. This is
a deliberate choice, not an oversight: implicit, lexically-context-dependent
name resolution (where a bare reference means different things depending on what
block encloses it) is exactly the kind of thing that made real M code hard to
read cold in the first place. Routine/tag organization is a call-graph concern;
global names are data identity, and data identity should never depend on which
`NAMESPACE` block happens to lexically surround the reference to it.

---

## 8. String ergonomics: interpolation, slicing, and value formatting

**Priority: medium, low implementation cost. Touches:** `Tokenizer.java`
(`readString`), `Parser.java`, `PicFormat.java` (revived, moved, and
repurposed).

### 8.1 Interpolation

M's only string-building tool is `_` concatenation:

```
WRITE "Order " _ id _ " shipped at " _ $HOROLOG, !
```

Phase adds interpolated string literals:

```
WRITE "Order {id} shipped at {$HOROLOG}", !
```

This is front-end sugar only. `Tokenizer.readString` recognizes `{...}` runs
inside a string literal, and the parser lowers an interpolated literal into
exactly the `Expr.Binary(UNDERSCORE)` chain it already builds for `_`. No
`Interpreter` or `MValue` change is required. Escaping a literal brace uses
doubling, `{{`/`}}`, consistent with how M string literals already escape an
embedded quote via `""` rather than introducing a new escape character class;
Phase has no backslash-escape convention today and this document does not add
one solely for this feature.

### 8.2 Value formatting inside interpolation, replacing a standalone PICTURE command

An earlier design pass considered reviving COBOL-style `PICTURE` formatting
(present in the pre-fork codebase as `PicFormat.java`, a compiled-mask value
formatter with TEXT / DIGIT-CODE / DECIMAL rendering modes and a `#`-fill
overflow convention) as its own standalone command or call form. This was
reconsidered: once interpolation exists as the natural place to turn a value
into display text, a second, separately-invoked formatting command is redundant
ceremony for the same job. Phase instead folds PICTURE formatting directly into
the interpolation syntax as an optional format specifier:

```
WRITE "Total: {total:9(6)V99}", !
WRITE "Order {id:9(8)} shipped {shippedAt:9(4)-99-99}", !
```

`{expr}` remains plain interpolation. `{expr:mask}` routes the evaluated value
through `PicFormat.compile(mask).format(...)` before splicing it into the
surrounding string. `PicFormat` itself requires no change; it already does
exactly this job, compile-once caching, mask-driven rendering, `#`-fill on
overflow, it is simply invoked from string interpolation rather than from the
old `QueryEngine`'s query-rendering path it originally served. This composes for
free with the slicing sugar in 8.3: `{name[1:20]:X(20)}` truncates then formats,
in one expression, with no additional mechanism.

`PicFormat.java` moves from the deprecated `com.phase.query` package into the
language layer proper as part of this revival.

### 8.3 Slice sugar

`s[start:end]` lowers directly to `$EXTRACT(s, start, end)`:

```
WRITE name[1:20], !
```

Pure parser sugar. No semantic change versus calling `$EXTRACT` directly, and it
composes with both interpolation (8.1) and formatting (8.2).

### 8.4 `FOR EACH ... IN`

The idiomatic M traversal of a subscripted structure:

```
SET i="" FOR  SET i=$ORDER(items(i)) QUIT:i=""  DO
. WRITE items(i),!
```

becomes:

```
FOR EACH i IN items DO
. WRITE items(i), !
```

Pure sugar lowering to exactly the `$ORDER` loop shown above. No interpreter
change beyond a parser rewrite. This is likely the single highest-frequency
ergonomics win in this document, given how often the `$ORDER` idiom above
appears in real M code touching any subscripted structure.

### 8.5 List and array literals: explicitly deferred

A `[1, 2, 3, "four"]` literal syntax was discussed and is **not** resolved by
this document. The reason is not cost of syntax but a genuine values-model
question: `MValue`'s foundational invariant is that every value is a string,
with no boxed value type, and a real list-as-value either requires introducing a
boxed value type (breaking that invariant directly) or requires
copy-the-whole-subtree assignment semantics for a subscripted local tree (which
M has no existing mechanism for at all: subscripted locals are not first-class
values that can be assigned wholesale to another name today). This is a
foundational decision on the same level as the operator precedence change in
Section 2, not a small ergonomics item, and deserves its own focused design
discussion rather than being resolved as a side effect of a string-ergonomics
pass. It is listed here as explicitly unresolved future work.

---

## 9. Replacing `@` indirection: `REF(...)` and a revived MACLIB macro system

**Priority: medium. Touches:** `Tokenizer.java` / `Expr.java` (`Expr.Indirect`
retired), `Parser.java` (dual statement/expression macro dispatch),
`PhaseDB.java` (reserved global names, a new concept), `Interpreter.java`, and a
new `com.phase.lang.macro` package reviving the shape of the deprecated
`MacroDef` / `MacroRegistry`.

### 9.1 What `@` was for, and why it does not survive

Real M's `@` is runtime string-eval indirection: `SET @X = 5`, where `X` holds
the literal text of another reference, re-tokenized and re-parsed at execution
time. Phase's own `Tokenizer` and `Expr.Indirect` already defer this rather than
implement it, and this document does not revive it. Arbitrary code-as-text at
runtime is a correctness and security liability, and the two genuinely useful
things `@` is typically reached for (constructing a reference whose name or
subscripts are not known until runtime, and generating repetitive code) both
have better, narrower replacements below that do not require re-entering the
parser at execution time at all.

### 9.2 `REF(...)`: dynamic reference construction without string-eval

Subscripts in Phase are already ordinary expressions, evaluated at the point of
use by `pathOf`; `SET ^ORDERS(id, fieldName) = value` already works today with
no indirection needed, since `fieldName` is just a runtime string plugged into
an already-parsed reference shape. What `@` additionally allows that Phase does
not yet have a replacement for is the **global or local name itself** being
dynamic. Phase adds an explicit primitive for exactly this case:

```
SET REF(gname, path) = value
```

`REF(...)` is a pseudo-target the parser recognizes specifically in assignment
(and read) position. `gname` and `path` are ordinary already-evaluated runtime
values; the interpreter builds a `GlobalRef`/`LocalIndex`-shaped node from those
evaluated strings directly, rather than re-tokenizing or re-parsing any text.
This gives the same practical power real `@` gave for reference construction,
with none of the "arbitrary code as data" surface, and it costs very little to
implement: `pathOf` and `assign` already do almost exactly this mechanically,
`REF(...)` just exposes the name itself as a third dynamic component alongside
the subscripts that are already dynamic today.

### 9.3 MACLIB: a revived, runtime-mutable macro system, replacing a compile-time `DEFMACRO` design

An earlier design pass in this project sketched a compile-time-only `DEFMACRO`,
expanding at parse time via a tree-rewrite pass with no runtime cost. That
design is **superseded** by reviving a materially better mechanism that already
existed in the pre-M76 fork of this project: a runtime-mutable, data-stored
macro system called MACLIB.

**Why the data-stored model wins for Phase specifically.** Phase routines are
already stored as `PhaseDB` items, and the storage engine already provides
transactional writes, versioning, and time travel for free on anything stored as
a global. A macro stored as ordinary data gets audit history (`LIST MACLIB
HISTORY`) and tombstone-based recoverability (`DROP MACRO` as a tombstone,
recoverable via the same time-travel machinery every other global already has)
automatically, using mechanisms `PhaseDB` already implements. A
compile-time-only macro system would need its own bespoke versioning story to
get any of that, duplicating work the storage engine has already done. Given the
project is a database with a language layer, not a language with incidental
storage, the data-stored model is the more Phase-native choice.

**Storage.** A reserved global, `^MACLIB`, subscripted using the same
node-duality machinery every other global already uses: `^MACLIB(id,"PATTERN")`,
`^MACLIB(id,"BODY")`, `^MACLIB(id,"DESC")`. This is the first place in the
project that needs a genuine concept of a storage-engine-reserved global name;
`PhaseDB` currently has no such notion, anything nameable by `checkGlobal` is
writable by ordinary `SET`/`KILL` from M code. `PhaseDB` gains a small
reserved-names check so `^MACLIB` can only be written through the `DEFINE MACRO`
/ `DROP MACRO` commands described below, not through an ordinary `SET`.

**Command surface**, a live part of the language rather than a separate DDL
dialect:

```
DEFINE MACRO MAX BETWEEN &A AND &B AS "IF &A > &B THEN &A ELSE &B END IF"
DROP MACRO "MAX BETWEEN &A AND &B"
```

The `&NAME` parameter syntax from the original `MacroDef` carries over
unchanged; it is already string-based and fits Phase's everything-is-a-string
value model with no friction at all.

**Body language becomes Phase expressions.** The original design's body language
was a separate ENGLISH-derived formula grammar, compiling to a `FormulaNode`
tree. In Phase, a macro body compiles to an ordinary `Expr` tree, the same
sealed interface every other Phase expression uses, not a second parallel
expression language. This has a direct, convenient consequence: the original
`FormulaIf` (an `IF <cond> THEN <expr> ELSE <expr> END IF` conditional
*expression*, evaluating to a value, something the current M76-derived grammar
does not have at all since `IF` today is purely a statement gating subsequent
statements via `$TEST`) becomes Phase's new expression-position conditional,
`Expr.IfExpr(cond, then, else)`, a sibling to `Expr.Group`, usable anywhere an
expression is usable:

```
SET shippingCost = IF orderTotal > 500 THEN 0 ELSE 9.99 END IF
```

and the original `FormulaField("&A")` parameter reference becomes an ordinary
`Expr.LocalRef("&A")`, substituted at macro-expansion time by pre-binding `&A`
as a real local (an unusual name, but Phase's local-name grammar has no reason
to forbid a leading `&` in this one reserved context) in a fresh scope for the
expansion. Macro expansion therefore needs no separate evaluator of its own: it
is `Interpreter.eval()` run against an ordinary `Expr` tree, with parameters
pre-seeded into a scratch `Environment` frame exactly the way a `CALL`'s
arguments are bound into a callee's frame in Section 4.

**Caching.** The original `MacroRegistry`'s single-slot, `txId`-keyed cache
(rebuilt transparently the first time a query runs after any change to
`^MACLIB`, since every write, including to `^MACLIB` itself, advances `txId`)
ports over unchanged. `PhaseDB.currentTxId()` already provides exactly the value
this cache needs; no redesign is required here.

**Dispatch and overload resolution.** Macros are indexed by leading keyword,
with same-keyword overloads (differing arity or pattern shape) tried
longest-pattern-first, exactly as the original design specified. This requires
the *parser* to consult the macro registry mid-parse: on encountering a bare
`NAME` token that is not a recognized command word (in `Parser.statement()`) or
not a recognized primary-expression form (in `Parser.parsePrimary()`), the
parser checks `MacroRegistry.hasMacroStartingWith(...)` before falling through
to `Stmt.Unsupported` or a plain `Expr.LocalRef`. This is a genuine
architectural departure worth stating plainly: parsing a Phase line can now
depend on live database state (whatever macros currently exist in `^MACLIB`),
which was not true of any previous part of this parser. It is a deliberate,
accepted exception to "parsing is a pure function of source text," made because
the alternative (a separate macro-preprocessing pass with its own bespoke
versioning, as the earlier `DEFMACRO` sketch would have required) is worse on
every axis that matters for this project.

**Both statement and expression position.** Consistent with how the language
already treats statement-position commands (`SET`, `WRITE`) and
expression-position functions (`$PIECE`, `$ORDER`) as separate categories with
the same underlying values, a MACLIB macro is dispatched at whichever position
it is invoked from: `Parser.statement()`'s command dispatch and
`Parser.parsePrimary()`'s expression dispatch both consult the registry. A macro
invoked at statement position evaluates its `Expr` body and discards the result,
mirroring how `execDo` already evaluates positional `DO` arguments purely for
side effects today; a macro invoked at expression position yields its evaluated
value in the ordinary way.

### 9.4 Summary of what changes and what does not

| Mechanism                                     | Status                                                                                                                                 |
|-----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `@` string-eval indirection                   | Retired. `Expr.Indirect` is removed rather than implemented.                                                                           |
| Dynamic subscripts                            | Unchanged; already fully supported today via ordinary expression evaluation in `pathOf`.                                               |
| Dynamic reference *names*                     | New: `REF(gname, path)`, no string-eval, built from already-evaluated values.                                                          |
| Compile-time `DEFMACRO` (earlier design pass) | Superseded by the MACLIB model below; not part of the final design.                                                                    |
| Runtime-mutable, data-stored macros           | New: `DEFINE MACRO` / `DROP MACRO`, stored in reserved `^MACLIB`, `txId`-cached, dispatched at both statement and expression position. |
| Expression-position conditional               | New: `IF <cond> THEN <expr> ELSE <expr> END IF`, revived from the original design's `FormulaIf`.                                       |

---

## 10. Directed graph operations: `EDGE`, `PATH`, `SHORTEST`

**Priority: medium. Touches:** `Stmt.java` (`EdgeCreate`, `EdgeDelete`),
`Expr.java` (new `PathQuery`), `Parser.java`, `Interpreter.java`, no
`PhaseDB`/WAL/`Checkpoint` changes.

### 10.1 What a graph edge is, under this storage model

The load-bearing decision for this whole section: a graph edge is not a new
storage primitive. It is an ordinary global write, using the same node-duality
and `SubscriptPath` machinery every other part of this document is built on:

```
^GRAPH(graphName, "EDGES", src, dst) = weight
```

Enumerating `src`'s outbound edges is an ordinary immediate-children walk
(`GlobalRanges.immediateChildren`, the same primitive `$ORDER` and Section 6's
path-shape `EVALUATE` clauses already use) over `^GRAPH(graphName,"EDGES",src)`.
Deleting an edge is an ordinary point-kill. Time travel is not a separate
feature graph operations need to earn; it falls out for free because a graph
reference is a real `Expr.GlobalRef`, and `Expr.GlobalRef.asOf` already exists.

This mirrors the reasoning in Section 7.3 against semantic namespace isolation
almost exactly: a first-class storage primitive (its own `WalChange.Kind`, its
own `Checkpoint` field, an in-memory adjacency cache) would be solving a
performance or capability problem that has not been demonstrated to exist yet,
at real and ongoing cost to the on-disk format. If graph workloads later prove
this insufficient, that is a storage-engine design pass in its own right, not
something to pre-build speculatively here.

Edges are strictly directed. `EDGE FROM A TO B` does not imply or create a
reverse edge `B` to `A`; a caller wanting a bidirectional relationship writes
both edges explicitly.

### 10.2 Syntax

```
EDGE FROM ^GRAPH("supplyChain","A") TO "B" WEIGHT 4.2
EDGE DELETE ^GRAPH("supplyChain","A") TO "B"

SET route    = PATH FROM ^GRAPH("supplyChain","A") TO "D"
SET shortest = SHORTEST FROM ^GRAPH("supplyChain","A") TO "D" LIMIT 500
```

`EDGE FROM`/`EDGE DELETE` take a real `^GRAPH(...)` global reference rather than
a bare graph-name string, with the edge's source vertex as that reference's
final subscript. This is a deliberate choice, consistent with Section 7.4's
stance that Phase never invents a second, parallel way to name something the
language can already name: a graph reference is a global reference, full stop,
and `EDGE FROM ^[tx]GRAPH(...)` composes with time-travel bracket syntax with
zero new parsing work, exactly as an ordinary `SET ^[tx]ORDERS(...)` does today.

`PATH FROM ... TO ...` and `SHORTEST FROM ... TO ...` are expression-position,
not statement-position, following the same reasoning as `CALL` in Section 4.2: a
construct that produces a value belongs in expression position, usable inline in
`SET`, `WRITE`, or anywhere else an expression is legal, rather than forced
through a statement that writes into one fixed local variable name. Both
evaluate to a comma-separated vertex-list string, consistent with the
everything-is-a-string model; an empty string means no path was found, which
composes with existing `IF 'x` / `$DATA`-style truthiness checks with no new
sentinel value needed.

`PATH` and `SHORTEST` are two separate keywords rather than one construct that
silently changes behavior depending on whether edge weights happen to be
populated, for the same reason `DO` and `CALL` were split in Section 4.2: two
constructs that look similar but commit to genuinely different semantics should
say so at the call site, not leave the reader to infer which algorithm actually
ran from data they cannot see.

### 10.3 Algorithms

**`PATH FROM ... TO ...`** runs ordinary queue-based BFS: a visited set keyed by
vertex string, each expansion reading a vertex's outbound edges via the
immediate-children walk described in 10.1. Edge weight is not consulted at all;
the first path BFS finds is definitionally fewest-hops, which is the entire
contract this keyword makes.

**`SHORTEST FROM ... TO ...`** runs Dijkstra: a priority queue keyed on
accumulated weight, the same per-vertex adjacency read as BFS on each pop, edge
weight read via `MValue.toBigDecimal` (so a malformed or missing weight coerces
to zero under existing numeric-coercion rules, rather than opening a second
error path specific to graph weights), and a predecessor map to reconstruct the
path once the destination is popped.

Both algorithms read directly against `PhaseDB` (or the active
`PhaseDB.Transaction`, if invoked inside a `TRANSACTION` block, routed through
the same transaction stack described in Section 5.3) at whatever tx or timestamp
the graph reference's `asOf` resolves to. Neither copies the graph into an
in-memory snapshot first; respecting `AS OF` means the traversal must see the
graph exactly as it existed at that point, not a materialized approximation of
it.

### 10.4 Traversal limits, and why this connects back to Section 3

A traversal runs synchronously inside the interpreter, with no async or
cancellation mechanism available if it runs long. An unbounded or cyclic graph,
or simply a bad query, would otherwise be able to hang the interpreter
indefinitely. Both `PATH` and `SHORTEST` therefore take an optional `LIMIT expr`
(nodes-visited cap; unspecified defaults to a fixed constant, high enough not to
bother legitimate use, low enough to bound a runaway call):

```
SET route = PATH FROM ^GRAPH("supplyChain","A") TO "D" LIMIT 500
```

Exceeding the limit is not a raw unconditional halt. It signals through the
exact mechanism Section 3 already built:

```
CONDITION <TRAVERSAL-LIMIT> (graphName, src, dst, limit, visited)
```

`visited` (the count actually reached before the cap was hit) is included since
it costs nothing extra, the traversal loop already tracks it, and it is
genuinely useful data for a handler that wants to log or report on the failure.
This keeps graph traversal from needing a bespoke error-handling story of its
own; it is simply one more condition type alongside `<CONFLICT>`, `<UNDEFINED>`,
`<DIVIDE-BY-ZERO>`, and `<LOCK-TIMEOUT>` from Section 3.6, catchable by an
enclosing `HANDLE`/`CATCH` in exactly the same way.

### 10.5 AST shape

```java
// Expr.java
record PathQuery(Expr.GlobalRef graphRef, Expr dst, boolean shortest, Expr limit) implements Expr {}
```

One node, not two, since `PATH` and `SHORTEST` differ only in which algorithm
`Interpreter.eval` dispatches to, not in shape; `limit` is null for "use the
default," consistent with how `Stmt.For`'s `increment`/`stop` already use null
for "not specified."

```java
// Stmt.java
record EdgeCreate(Expr.GlobalRef graphRef, Expr dst, Expr weight) implements Statement {
    public Expr postcondition() { return null; }
}
record EdgeDelete(Expr.GlobalRef graphRef, Expr dst) implements Statement {
    public Expr postcondition() { return null; }
}
```

`graphRef` already carries the source vertex as its final subscript, so no
separate `src` field is needed. `EdgeCreate` appends `"EDGES"`, `dst` onto
`graphRef`'s path and calls the exact same `assign()` path an ordinary `SET
^GRAPH(...)=...` would use; `EdgeDelete` calls the exact same
`killOne()`/point-kill path an ordinary `KILL ^GRAPH(...)` would use. No new
`PhaseDB` method and no new `Interpreter` primitive beyond path-building is
introduced anywhere in this section; that is the entire point of treating graph
operations as sugar rather than as a subsystem.

---

## 11. Secondary indexes: `INDEX ON`, `REBUILD INDEX`, `$INDEXLOOKUP`

**Priority: medium, higher implementation risk than any section before it.
Touches:** `Stmt.java` (`IndexDecl`), `Interpreter.java` (`assign`/`killOne`
gain index-aware write staging), `Parser.java`, a small new reserved-global
convention (`^_IDX_<name>`, `^_IDXMETA`), no WAL format change (reuses
`WalCommit`'s existing multi-`WalChange` atomicity).

### 11.1 Why this section is a different kind of feature than everything before it

Every feature designed so far, transactions, `EVALUATE`, namespaces, graph
operations, is either read-only sugar or a pure wrapper around an existing write
path: the person writes ordinary `SET`/`KILL` (or something that compiles
straight down to it), and nothing invisible happens on top. A secondary index
breaks that pattern deliberately: once `^ORDERS(*,"STATUS")` is indexed, an
ordinary `SET ^ORDERS(id,"STATUS")="SHIPPED"`, with no special syntax at the
call site at all, now has to also write into the index behind the scenes. That
is real, load-bearing, invisible behavior attached to a plain `SET`, and it
deserves to be called out plainly rather than described as more sugar, because
it is not: it is the first feature in this document that changes what an
ordinary write statement actually does.

### 11.2 Atomicity: reusing `WalCommit`, not inventing a new primitive

The requirement that index maintenance be atomic with the primary write does not
need a new WAL entry kind. `WalCommit` already bundles a `List<WalChange>` into
one atomically-written unit; an indexed write simply stages two `WalChange`s
(the primary write, and the index entry) into the same transaction before it
commits, exactly the same shape `PhaseDB.Transaction` already provides for an
explicit multi-statement `TRANSACTION` block (Section 5.3).
`Interpreter.assign()` and `killOne()` gain one additional check: if the global
being written has a registered, `READY` index covering the subscript position
being written, stage a second `WalChange` targeting `^_IDX_<indexName>(value,
primaryKey...)` (or its removal, for a `KILL`) into the same transaction as the
primary write, before that transaction's `commit()` is called.

This has a consequence worth stating explicitly rather than glossing over: **a
bare `SET`/`KILL` against an indexed global, entirely outside any explicit
`TRANSACTION` block, becomes implicitly transactional.** Something that looks
like a single, ordinary statement now silently opens and commits a two-change
atomic unit whenever it touches an indexed global. This is the correct behavior
given the atomicity requirement, but it means declaring an index changes the
runtime behavior of every future write to that global, not just of code that
explicitly asks to use the index.

### 11.3 Syntax

```
INDEX ON ^ORDERS(*,"STATUS") AS statusIdx
INDEX DELETE statusIdx
REBUILD INDEX statusIdx
```

`INDEX ON` takes a real `Expr.GlobalRef`-shaped reference with `*` marking the
subscript position being indexed, rather than a bare integer position
disconnected from the global's actual shape. This mirrors the same reasoning
that decided `EDGE FROM ^GRAPH(...)` over a bare graph-name string in Section
10.2: the thing being indexed stays visible and checkable at the declaration
site as a real reference, not a positional integer the reader has to count out
by hand against the global's subscript list.

### 11.4 `INDEX ON` is a live command: declare and backfill together

Declaring an index against a global that already has data immediately backfills
it; there is no separate "declare now, populate later" step. Backfill walks the
source global's existing entries via `GlobalRanges.subtree`, in the same
`SubscriptPath` order `$ORDER` already uses, and stages one
`^_IDX_<indexName>(value, primaryKey...) = ""` `WalChange` per matching entry.

**Backfill is chunked, not one giant transaction.** A single all-or-nothing
commit covering an entire large global would mean one very long-running,
uninterruptible write; instead, backfill commits in batches of a fixed size (an
implementation constant, not exposed as language syntax), each batch its own
atomic `WalCommit`. This means a crash partway through backfill can leave a
real, already-committed *partial* index sitting on disk, which is exactly the
failure mode the status-tracking mechanism below exists to catch.

### 11.5 Index metadata: `BUILDING` / `READY` / `CORRUPT`, and why it has to be durable

An index's build status is itself stored as ordinary data, not held only in
memory, since an in-memory-only flag would not survive the crash that caused the
problem it needs to detect:

```
^_IDXMETA(indexName, "STATUS")    = "BUILDING" | "READY" | "CORRUPT"
^_IDXMETA(indexName, "LAST-PATH") = <SubscriptPathCodec-encoded path of the last entry committed by the current or most recent backfill batch, empty if none yet>
```

`LAST-PATH` reuses `SubscriptPathCodec.encode`/`decode` directly, the exact same
encoding `WalChange.pathKey` already uses; no new serialization format is
introduced. Mechanics:

1. `INDEX ON` sets `STATUS = "BUILDING"`, then loops: read the next batch of
   entries starting immediately after `LAST-PATH` (from the root, if `LAST-PATH`
   is empty), stage each as an index `WalChange` plus one `LAST-PATH`-updating
   `WalChange` covering that batch's final entry, commit the batch as one
   `WalCommit`, and repeat until the source subtree is exhausted. A final commit
   then sets `STATUS = "READY"`.
2. Any index found with `STATUS = "BUILDING"` on database open (meaning a crash
   interrupted a prior backfill, since a clean run always ends by flipping to
   `READY`) is treated as `STATUS = "CORRUPT"` and refuses to serve
   `$INDEXLOOKUP` queries until explicitly repaired.
3. `REBUILD INDEX indexName` re-enters the identical chunked-backfill loop,
   resuming from the persisted `LAST-PATH` rather than necessarily rescanning
   from the root. This is safe, not merely convenient, because index writes are
   idempotent: writing the same `^_IDX_<indexName>(value, primaryKey...) = ""`
   entry a second time is harmless, so even an imprecise `LAST-PATH` (committed,
   but its own metadata update did not land before a crash) only costs a small
   amount of redundant re-work, never incorrectness. A full rescan from the root
   is always also correct; resuming from `LAST-PATH` is purely an optimization
   on top of that.

### 11.6 Failure as a signaled condition, not a raw halt

```
CONDITION <INDEX-BUILD-FAILED> (indexName, globalName, reason, lastPath)
```

Any failure during backfill, whether the initial `INDEX ON` run or a `REBUILD
INDEX` resume, signals `<INDEX-BUILD-FAILED>` rather than propagating a raw
internal exception, joining `<CONFLICT>`, `<UNDEFINED>`, `<DIVIDE-BY-ZERO>`,
`<LOCK-TIMEOUT>`, and `<TRAVERSAL-LIMIT>` in Section 3.6's pre-registered
built-in condition table. `lastPath` is included in the signaled fields for the
same reason `<TRAVERSAL-LIMIT>`'s `visited` field was included in Section 10.4:
the loop already tracks it, and it is genuinely useful diagnostic data for
whatever `HANDLE`/`CATCH` clause is wrapped around the `INDEX ON`/`REBUILD
INDEX` statement, without any extra cost to produce it. Any query attempted
against an index that is not `READY`, whether newly `BUILDING` or already-known
`CORRUPT`, signals the same condition at query time, rather than silently
answering from an incomplete index.

### 11.7 Querying through an index: `$INDEXLOOKUP`

```
SET shippedIds = $INDEXLOOKUP("statusIdx", "SHIPPED")
```

`$INDEXLOOKUP` is an ordinary `$INTRINSIC` function, the same category
`$ORDER`/`$DATA` already belong to, taking an index name and a value, and
returning a comma-separated list of matching primary keys. This is deliberately
the same kind of return shape `PATH`/`SHORTEST` settled on in Section 10.2: a
plain, flat list of already-atomic string tokens joined by commas, consistent
with the everything-is-a-string model, and worth distinguishing carefully from
the rejected `VectorLiteral` approach (Section 8.5's cross-reference, and the
explicit reversal recorded in this document's history): a vector literal was
comma-joining *compound numeric values* to fake a boxed type that does not
otherwise exist in the language, whereas a primary-key list here is a plain
sequence of already-scalar tokens, exactly the same case `PATH FROM...TO`'s
vertex list already established as acceptable. No new value kind is introduced
by this return shape; it is the same pattern already in use elsewhere in this
document, not a new exception to `MValue`'s invariant.

### 11.8 AST shape

```java
// Stmt.java
record IndexDecl(Expr.GlobalRef sourceRef, int wildcardPosition, String indexName) implements Statement {
    public Expr postcondition() { return null; }
}
```

`wildcardPosition` is derived at parse time from where `*` appears among
`sourceRef`'s subscripts, replacing the current `IndexDecl`'s bare `int
subscriptPosition` field with a value actually anchored to the reference written
at the declaration site, per 11.3's reasoning. `INDEX DELETE`/`REBUILD INDEX`
need no new expression or value machinery beyond an index-name string; both are
effectively single-argument commands dispatching straight into `PhaseDB`-level
index maintenance, the same shape `Stmt.EdgeDelete` already has in Section 10.5.

---

Stated plainly, so these are not silently reconsidered later without someone
re-reading the reasoning:

---

## 12. Explicitly rejected

Stated plainly, so these are not silently reconsidered later without someone
re-reading the reasoning:

- **Storage-level schema / type validation (a PICTURE-as-`SET`-time-validator
  design).** Rejected in favor of PICTURE as a pure `WRITE`-side,
  interpolation-integrated display formatter (Section 8.2). Phase globals remain
  schemaless by design; a value's shape is never checked or constrained at write
  time by the language itself. This is a considered position, not an oversight:
  schemaless-at-write is one of the properties this document explicitly keeps
  from M76 (Section 1.1), and a storage-level type layer would cut directly
  against it.
- **A dedicated `MATCH` construct for path destructuring.** Rejected in favor of
  folding path-shape matching into `EVALUATE` as one clause kind (Section 6.3).
  That absorption is itself currently deferred pending the values-model question
  (Section 6.3's implementation-status note, Section 14), so path destructuring
  has no working syntax in Phase yet, either as `MATCH` or as `EVALUATE`. The
  rejection of a separate `MATCH` keyword stands regardless of when the
  underlying capability lands; two constructs doing structurally similar jobs
  was the thing being avoided, not the capability itself.
- **Semantic namespace isolation** (separate storage roots, access control per
  namespace). Rejected per Section 7.3; namespaces in this design are purely a
  human-organizational, language-layer convenience with zero storage-layer
  weight.
- **True lexical closures over shared mutable state.** Rejected per Section 4.5
  in favor of value-snapshot closures only, to avoid breaking `Environment`'s
  single-flat-pool lifetime invariant.
- **True resumption / continuations for the condition system.** Rejected per
  Section 3.7 in favor of retry-the-block only.
- **A left-to-right-arithmetic compatibility pragma.** Rejected per Section 2.2;
  two silently coexisting arithmetic semantics in one language was judged a
  worse failure mode than a clean, one-time break from M76 fidelity.
- **`@` string-eval indirection**, revived in any form. Rejected outright per
  Section 9.1; `REF(...)` and MACLIB macros cover its legitimate use cases
  without its risk.
- **List/array literals as a resolved feature.** Not rejected, but explicitly
  left unresolved (Section 8.5) pending a dedicated values-model design
  discussion, rather than folded into the string-ergonomics pass that raised it.
- **Statement-level (whole-block) macros.** Considered briefly during design
  discussion and set aside; MACLIB macros in this document are expression-bodied
  only (Section 9.3). Not documented further as future work.

---

## 13. Phasing summary

Ordered roughly by a combination of implementation cost and how much other work
depends on it landing first.

**Phase 1, foundational, low risk, high everyday impact:**
- Operator precedence (Section 2)
- `TRANSACTION` blocks (Section 5)
- String interpolation, slicing, `FOR EACH ... IN` (Sections 8.1, 8.3, 8.4)
- Routine/tag namespacing and cosmetic dotted global names (Section 7)

**Phase 2, moderate cost, depends on Phase 1's block-structure conventions being
settled:**
- Conditions and retry-scoped `HANDLE`/`CATCH` blocks (Section 3), since
  `TRANSACTION` (Phase 1) is defined in terms of it
- `CALL`, named parameters, multiple `RETURN`, reference parameters (Section 4.1
  to 4.4)
- `EVALUATE` (Section 6), switch/condition-chain/`ALSO` modes only, per 6.3's
  deferral
- Directed graph operations (Section 10), since it depends on
  `CALL`/expression-position constructs and, for `<TRAVERSAL-LIMIT>`, on Section
  3's condition mechanism already existing

**Phase 3, larger or more speculative:**
- Value-snapshot closures (Section 4.5)
- PICTURE-in-interpolation formatting (Section 8.2), depends on Sections 8.1 and
  a revived `PicFormat`
- `REF(...)` (Section 9.2), low cost on its own but naturally sequenced
  alongside macro work
- MACLIB macros and expression-position `IF/THEN/ELSE` (Section 9.3), the
  largest single item in this document, given the parser-consults-live-state
  departure it requires
- Secondary indexes (Section 11), sequenced last among implemented features
  deliberately: it is the first feature that changes the behavior of an ordinary
  `SET`/`KILL` rather than adding new syntax alongside existing behavior (11.1),
  and it depends on Section 3's condition mechanism for `<INDEX-BUILD-FAILED>`
  (11.6)

**Explicitly unresolved, not scheduled:**
- List/array literals (Section 8.5)
- Path-shape `EVALUATE` clauses (Section 6.3)

---

## 14. Open questions for future design passes

These are not blocking anything in this document but are worth tracking:

- Should `HANDLE`/`CATCH` clauses (Section 3) eventually be attachable to
  `TRANSACTION` blocks with any different defaults than plain `DO` blocks get,
  given how routinely `<CONFLICT>` is expected to fire under real concurrent
  load compared to other condition types?
- Once MACLIB macros exist (Section 9.3), should `NAMESPACE` (Section 7) apply
  to macro names at all, or should `^MACLIB` remain a single flat pool
  regardless of namespace? This document does not take a position.
- Savepoint-based nested transactions (Section 5.4) are left for a future pass
  once, and if, `PhaseDB.Transaction` itself grows that capability. No
  commitment is made here to build that capability.
- True closures (Section 4.5), list/array literals (Section 8.5), and path-shape
  `EVALUATE` clauses (Section 6.3) all eventually run into the same underlying
  question: does Phase ever want a value kind beyond a plain string, breaking
  `MValue`'s current everything-is-a-string invariant? All three should probably
  be resolved together, in one dedicated design pass, rather than separately;
  `EVALUATE`'s `WhenTest` shape (Section 6.4) already reserves room for a fourth
  case once that pass happens, so nothing already built needs to be reworked to
  accommodate the answer.
- Section 11's implicit-transaction behavior (11.2: a bare `SET`/`KILL` against
  an indexed global becomes atomically two-`WalChange` even outside any explicit
  `TRANSACTION` block) interacts with Section 3's `<CONFLICT>` retry mechanism
  in a way this document has not worked out: if that implicit transaction hits a
  write conflict, does the surrounding code get any way to `HANDLE`/`RETRY` it
  at all, given it never opened a `TRANSACTION` block or a `DO...HANDLE...END
  DO` in the first place? Left as a real open question rather than resolved
  here.
