# The Phase Handbook

## Contents

- **Part I**, the language core: values, variables, globals, operators, control flow, line continuation
- **Part II**, calling conventions: `DO`/`CALL`/`RETURN`, reference parameters, `EVALUATE`
- **Part III**, error handling: conditions, `HANDLE`/`CATCH`/`RETRY`, transactions
- **Part IV**, string tools: literals, slicing, `FOR EACH`
- **Part V**, namespaces
- **Part VI**, dynamic references: `REF(...)`
- **Part VII**, time travel
- **Part VIII**, bulk data movement: `MERGE` and materializing joins
- **Part IX**, JSON bridge: `ZMERGE` and `ZWRITE`
- **Part X**, blob storage
- **Part XI**, storage and concurrency internals
- **Part XII**, intrinsic function reference
- **Part XIII**, using the REPL
- **Part XIV**, what Phase does not implement yet
- **Part XV**, the HTTP server
- **Appendix A**, error reference
- **Appendix B**, operator precedence

---

# Part I, The Phase Language

## 1. What Phase is

Phase is a MUMPS-derived language and database in one. There is no ORM
and no separate query language. A variable name that starts with `^` is a
database write, durable the moment `SET` runs.

Phase keeps M's best ideas: sparse hierarchical storage as the native
data model, strings as the one value type, `$ORDER`-style cursor
traversal, and a flat line-oriented routine structure. Phase drops the
parts of M76 that were hardware constraints of 1976, not design virtues.
Phase has normal operator precedence instead of strict left-to-right
evaluation, full command words instead of single-letter abbreviations,
and no string-eval indirection (`@`).

## 2. Values: everything is a string

Phase has exactly one data type: the string. There is no integer type, no
float type, no boolean type, and no date type. A number in Phase is a
string that looks numeric, coerced to a number only when code uses it in
arithmetic or comparison.

The coercion rule reads a **leading numeric prefix** and ignores the rest:

```
"12abc"  → 12    (leading numeric prefix, rest ignored)
"abc"    → 0     (no valid leading numeric prefix)
"  12"   → 0     (leading whitespace is NOT skipped, this is intentional M76 behaviour)
```

**Truthiness** follows the same rule: coerce to a number; nonzero is
true. `IF X` means `IF X≠0` after coercion. A non-numeric string like
`"hello"` is false because it coerces to `0`.

**Arithmetic** (`+` `-` `*` `/` `\` `#` `**`) runs at 34 digits of
decimal precision with `HALF_UP` rounding. `/`, `\` (integer division),
and `#` (modulo) throw `<DIVIDE BY ZERO>` when the divisor is zero. If
a handler is active (Part III), Phase signals `<DIVIDE-BY-ZERO>` instead.

**Equality (`=`) is not plain string equality.** Both sides are checked
against the canonical-numeric-form rule (§3.2). When both operands are
canonical numbers, `=` compares them numerically. Otherwise it falls
back to exact string comparison:

```
1=1       → true    (both canonical numbers)
1=1.0     → false   ("1.0" is not canonical, trailing zero after the decimal point)
"01"="1"  → false   ("01" is not canonical, leading zero, so this is a string compare)
```

**`]]`** ("sorts after") compares two values using Phase's collation rule
(§3.2), not plain ASCII order.

## 3. Variables: locals vs. globals

Phase has exactly two kinds of variable, distinguished by a sigil:

| Kind       | Syntax              | Lifetime                                        | Scope                               |
|------------|---------------------|-------------------------------------------------|-------------------------------------|
| **Local**  | `X`, `SUM`, `%TMP` | Process lifetime, or until `KILL` / `NEW` scope | The whole process by default (§7)   |
| **Global** | `^ORDERS`, `^X`    | Persists in the database; survives process exit | Shared by every open process        |

```
SET X=5       ; local, in-memory only
SET ^X=5      ; global, written to disk, durable, shared
```

The `^` sigil is the entire difference between a scratch variable and a
database write. Phase has no `SAVE` command and no separate `commit()`
call.

### 3.1 Node duality

Every variable, local or global, is addressed by a name plus zero or
more subscripts:

```
^ORDERS                       ; root of the ORDERS tree
^ORDERS(1)                    ; subscript 1
^ORDERS(1,"PRICE")            ; two levels deep
^ORDERS(1,"LINES",3,"QTY")   ; four levels deep, no fixed depth limit
```

**A node can hold a value and have children at the same time.** These
two writes do not conflict:

```
SET ^ORDERS(1)="summary"
SET ^ORDERS(1,"PRICE")=100
```

After both writes, `^ORDERS(1)` has its own value and a child at
`^ORDERS(1,"PRICE")`. `$DATA` (§6) reports which of four states a node
is in.

The same duality applies to locals. `SET X=1` and `SET X(1)=2` occupy
independent storage slots and never interfere.

### 3.2 Subscript collation: numbers sort before strings

Phase defines a **canonical numeric form** for a subscript: no leading
zero, no leading `+`, no trailing zero after a decimal point, no trailing
`.`, and no exponent notation.

Canonical: `1`, `23`, `-4`, `1.5`.  
Not canonical: `01`, `+1`, `1.50`, `1.0`, `1E2`.

Every canonical-numeric subscript sorts before every string subscript.
Within numbers, Phase sorts by numeric value (`1 < 2 < 10`, not by
character order). Within strings, Phase sorts by character order.

`^A(1)` and `^A("1.0")` are genuinely different keys. They are not the
same key with different formatting.

This is also why `$ORDER` traversal (§6) can produce an order that
surprises newcomers: all canonical numbers first, in numeric order, then
all strings, in character order.

## 4. Operators and precedence

Phase implements normal operator precedence. From loosest to tightest:

| Level        | Operators                                                        | Associativity |
|--------------|------------------------------------------------------------------|---------------|
| 1 (loosest)  | `&` (AND), `!` (OR)                                              | left          |
| 2            | `=` `<` `>` `[` (contains) `]]` (sorts-after) `?` (pattern)      | left          |
| 3            | `_` (concatenation)                                              | left          |
| 4            | `+` `-`                                                          | left          |
| 5            | `*` `/` `\` (integer divide) `#` (modulo)                        | left          |
| 6            | `**` (exponentiation)                                            | **right**     |
| 7 (tightest) | unary `-` `+`, `'` (logical NOT), `@` (indirection, Part XIV)    | prefix        |

```
WRITE 1+2*3,!     ; 7 , * binds tighter than +
WRITE 2**3**2,!   ; 512, ** is right-associative: 2**(3**2)
WRITE '(3>2),!    ; 0 , ' negates any truthy value
```

Slice syntax (`s[start:end]`, Part IV §2) binds tighter than every
operator above as a postfix form.

`&` and `!` do **not** short-circuit. Phase always evaluates both
operands, exactly as M76 does.

## 5. Control flow is line-oriented

`IF` and `FOR` each govern **the rest of the current line** only, not
a block. A routine is a flat, indexed list of lines with optional labels.

### 5.1 `IF` / `ELSE` and `$TEST`

```
IF cond1,cond2,...   ; comma = AND; short-circuits on first false condition
```

Every `IF` sets `$TEST` to its boolean result. A false `IF` skips
forward to the next `ELSE` on the same line, if any:

```
IF X>0 WRITE "positive",!  ELSE  WRITE "not positive",!
```

`ELSE` checks `$TEST` from the most recent `IF` on the same line. If
that `IF` was true, `ELSE` skips the rest of the line. If it was false,
`ELSE` runs. Note the two spaces before `WRITE` on the `ELSE` side,
that is the statement-boundary convention (§9).

### 5.2 `FOR`

```
FOR                             ; infinite loop, exit with QUIT
FOR I=1                         ; runs once with I=1
FOR I=1:1                       ; I=1, 2, 3, ... forever
FOR I=1:1:10                    ; I=1 through 10, inclusive
FOR EACH K IN ^ORDERS DO        ; iterate subscripts, see Part IV §3
. WRITE K,":",^ORDERS(K),!
```

`QUIT` (or `QUIT:cond`) inside a `FOR` exits only the loop, not the
enclosing routine.

### 5.3 `GOTO` and `DO TAG`

A call target is one of: a bare `TAG`, `TAG^ROUTINE`, or `^ROUTINE`
(entry at the first line). An unresolvable tag throws `<UNDEFINED LABEL>`.

```
 GOTO DONE              ; jump to a label in this routine
 DO PROCESSORDER(id)    ; call a tag in this routine
 DO SHIP^LOGISTICS(id)  ; call a tag in another loaded routine
```

### 5.4 Dotted blocks

A bare `DO` opens an implicit dotted block. Leading `.` characters mark
which lines belong to it:

```
FOR EACH K IN ^LIST DO
. WRITE K,!
```

`Phase.Lines` strips the leading dots before parsing and uses the dot
count as the block depth. A bare `DO` runs every following line whose
depth is greater than the `DO` line's own depth.

Phase also supports an explicit `DO ... END DO` form, required for
`HANDLE`/`CATCH`/`RETRY` blocks (Part III):

```
DO
    SET ^A=1
    SET ^B=2
HANDLE <CONFLICT> AS c
    RETRY
END DO
```

### 5.5 Line continuation

A long statement can span multiple physical lines. A leading dot on a
continuation line joins it to the line above when the dot count is
**greater** than what a new block line at that position would have:

```
MERGE report(oid,"CUSTOMER")=^CUSTOMERS(^ORDERS(oid,
.                            "CUSTOMER"))
```

The parser strips all leading dots and the whitespace immediately after
them before joining. No space is added at the join point. To preserve a
space, put it before the line break:

```
WRITE "This sentence continues on the 
.next line with a space before the break.",!
```

A continuation inside an already-dotted block needs one extra dot per
block level:

```
FOR EACH oid IN ^ORDERS DO
. MERGE nested(oid,"CUST")=^CUSTOMERS(^ORDERS(oid,
.. "CUSTOMER"))
```

## 6. Core intrinsic functions

Part XII holds the full reference. Three intrinsics appear in almost
every routine.

### `$DATA(ref)`, four-state existence check

| Result | Meaning                                         |
|--------|-------------------------------------------------|
| `0`    | No value at this node, no children              |
| `1`    | Has a value at this node, no children           |
| `10`   | No value at this node, has children             |
| `11`   | Has a value at this node, and has children      |

```
SET ^ORDERS(1,"PRICE")=100
WRITE $DATA(^ORDERS(1)),!        ; -> 10  (has children, no own value)
WRITE $DATA(^ORDERS(1,"PRICE")), ! ; -> 1   (leaf with a value)
WRITE $DATA(^ORDERS(999)),!      ; -> 0   (does not exist)
```

`IF $DATA(^X)>9`, "this node has children, regardless of its own
value", is an idiom worth recognising on sight.

### `$ORDER(ref)`, walk a subscript level

`$ORDER` takes a starting subscript (which need not exist) and returns
the next subscript that does exist, in collation order (§3.2). An empty
string as the seed starts from the beginning; an empty string as the
result means the level is exhausted.

```
SET ^ITEM("a")=1, ^ITEM("b")=2, ^ITEM("c")=3
SET K=""
FOR  SET K=$ORDER(^ITEM(K))  QUIT:K=""  WRITE K,":",^ITEM(K),!
; -> a:1  b:2  c:3
```

`$ORDER` works identically on a local and on a global. It does not work
through a time-travel bracket (Part VII).

### `$TEST`, the last IF/postcondition result

See §5.1. `WRITE $TEST,!` prints `1` after a true branch and `0` after
a false one.

## 7. Locals are process-global, not per-routine

A local variable is visible to the whole process by default:

```
SET X=1
DO SUB
WRITE X,!      ; prints whatever SUB left X as
```

Use `NEW` to stack-scope a variable for the lifetime of the current
`DO`/call frame:

```
SUB  NEW X
     SET X=99
     QUIT
     ; X reverts to its pre-call value on QUIT
```

`NEW X` saves the current binding (or saves the fact that `X` was
undefined), clears `X`, and restores everything when the frame exits.
Bare `NEW` (no arguments) does this for every local currently defined.

## 8. Postconditions, the `:` qualifier

Nearly every command accepts `:expr` immediately after the command word:

```
WRITE:X>0 "positive",!
QUIT:I=""
SET:DONE X=1
```

The postcondition is evaluated first. If it is false, the whole
statement, arguments and all, is skipped.

`FOR` and `LOCK` also use `:` for their own syntax (increment/stop, and
timeout). A command's postcondition is always the **first** `:` after the
command word.

`NAMESPACE`, `CONDITION`, `HANDLE`, `CATCH`, and `WHEN` do not accept a
postcondition. The parser throws an error if `:` follows any of them.

## 9. Commands reference

Phase recognises **full command words only**. Writing `S X=1` does not
parse as `SET`. An unrecognised leading word is captured as
`SUnsupported` and throws `<NOTIMPL>` at execution time, not a parse
error, so a script never fails to load merely for containing something
Phase has not implemented yet.

| Command                              | Forms                                                                                                           |
|--------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `SET`                                | `SET t=v`; `SET t1=v1,t2=v2,...`; `SET t1,t2=CALL tag(...)` (Part II §1)                                      |
| `KILL`                               | Bare `KILL` (kills all locals); `KILL target,...`                                                               |
| `WRITE`                              | `WRITE expr,...`; items may be `!` (newline), `#` (form feed), `?N` (tab to column N)                         |
| `IF` / `ELSE`                        | `IF cond1,cond2,...`; bare `ELSE`                                                                               |
| `FOR`                                | Bare `FOR`; `FOR X=start`; `FOR X=start:inc`; `FOR X=start:inc:stop`; `FOR EACH var IN coll`                  |
| `DO`                                 | Bare `DO`; `DO TAG`; `DO TAG(args)`; `DO TAG^ROUTINE`; `DO ^ROUTINE`; `DO tag(&ref,...)` (Part II §2)          |
| `DO WITH`                            | `DO WITH @TX(expr) [@BIND(^G,...)]`, time-travel pin scope (Part VII §3)                                      |
| `GOTO`                               | `GOTO TAG`; `GOTO TAG^ROUTINE`; `GOTO ^ROUTINE`                                                                |
| `QUIT`                               | Bare `QUIT`; `QUIT expr`                                                                                        |
| `RETURN`                             | Bare `RETURN`; `RETURN v1,v2,...` (Part II §1)                                                                 |
| `NEW`                                | Bare `NEW`; `NEW X,Y,Z`                                                                                        |
| `LOCK` / `UNLOCK`                    | Bare (release/acquire all); `LOCK ref,+ref,-ref[:timeout]` (Part XI §3)                                        |
| `NAMESPACE`                          | `NAMESPACE name` (Part V)                                                                                       |
| `CONDITION`                          | `CONDITION <NAME>(field1,field2,...)` (Part III §1)                                                             |
| `SIGNAL`                             | `SIGNAL <NAME>(field: expr,...)` (Part III §1)                                                                  |
| `HANDLE` / `CATCH`                   | Clause headers inside `DO...END DO` blocks (Part III §1)                                                        |
| `RETRY`                              | Bare `RETRY`; `RETRY [n TIMES] [WITH field=expr,...]` (Part III §1)                                            |
| `EVALUATE` / `WHEN` / `END EVALUATE` | Part II §3                                                                                                      |
| `MERGE`                              | `MERGE t=s`; `MERGE t1=s1,t2=s2,...` (Part VIII §1)                                                            |
| `ZMERGE`                             | `ZMERGE t=json,...`, merge JSON into a node subtree (Part IX §1)                                              |
| `ZWRITE`                             | Bare `ZWRITE`; `ZWRITE ref,...`, render a node subtree as JSON (Part IX §2)                                   |
| `BLOBSAVE`                           | `BLOBSAVE ref TO path` (Part X §2)                                                                              |
| `BLOBMETA`                           | `BLOBMETA ref SET field=expr,...` (Part X §3)                                                                   |

## 10. A minimal working example

```
MAIN
 NEW I,SUM
 SET SUM=0
 FOR I=1:1:5 SET SUM=SUM+I
 WRITE "SUM=",SUM,!
 SET ^ORDERS(1,"PRICE")=100
 IF ^ORDERS(1,"PRICE")>50 WRITE "expensive",!  ELSE  WRITE "cheap",!
 QUIT
```

`NEW` stack-scopes two working locals. `FOR I=1:1:5` runs the rest of
the line five times. `SET` on a `^`-prefixed name performs a durable
database write. The `IF`/`ELSE` pair uses `$TEST`. `QUIT` ends the
routine.

---

# Part II, Calling Conventions & Structured Control

## 1. `DO` vs. `CALL`

`DO` is statement-position. It produces side effects only. `CALL` is the
expression-position counterpart: it invokes a tag and yields a value.

```
DOUBLE(n)
 RETURN n*2

TOTAL(orders,taxRate)
 RETURN orders, orders*taxRate
```

```
WRITE CALL DOUBLE(21),!               ; -> 42
SET total,tax=CALL TOTAL(500,0.1)   ; multi-value destructuring
```

A tag's formal parameter list names each parameter by position. Phase
binds each argument at the call site and auto-`NEW`s it into the
callee's own frame. Parameters never leak into, or overwrite, a
same-named local in the caller.

`RETURN v1,v2,...` is the multi-value alternative to bare `QUIT`.
Phase destructures the values only at a `SET t1,t2=CALL ...` site. An
ordinary expression-position `CALL` yields only the first value.

A wrong argument count is a hard runtime error:

```
<SYNTAX> CALL returned 1 value(s), 2 target(s) expected
```

## 2. Reference parameters

```
DOUBLEIT(n)
 SET n=n*2
 QUIT
```

```
SET X=5
DO DOUBLEIT(&X)
WRITE X,!          ; -> 10
```

A `&name` argument at the call site binds the callee's formal parameter
as a true alias into the caller's local storage. This is not a copy. A
write inside the callee is immediately visible in the caller. The operand
of `&` must be a bare, unsubscripted local, never a global, never a
subscripted local. Phase rejects any other form with `<SYNTAX>`.

## 3. `EVALUATE`, multi-branch dispatch

`EVALUATE` is a generalized switch construct with three modes.

**Switch mode**, test one subject for equality:

```
EVALUATE status
    WHEN "PENDING"  WRITE "pending",!
    WHEN "SHIPPED"  WRITE "shipped",!
    WHEN OTHER      WRITE "unknown",!
END EVALUATE
```

**Condition-chain mode**, use `TRUE` as the subject; each `WHEN` is
its own boolean test:

```
EVALUATE TRUE
    WHEN price>1000  WRITE "bulk",!
    WHEN price>100   WRITE "standard",!
    WHEN OTHER       WRITE "none",!
END EVALUATE
```

This is the idiomatic replacement for a long `IF`/`ELSE IF` chain.

**Multi-subject tuple matching**, one test per subject, joined with
`ALSO`:

```
EVALUATE customerType ALSO orderTotal
    WHEN "PREMIUM" ALSO ANY   WRITE "free shipping",!
    WHEN ANY       ALSO >500  WRITE "free shipping",!
    WHEN OTHER                WRITE "standard shipping",!
END EVALUATE
```

`ANY` always matches. A bare comparison operator (`>500`, `'=x`) tests
directly against that subject. Every clause may carry an optional `WHERE`
guard:

```
EVALUATE TRUE
    WHEN TRUE WHERE n#2=0  WRITE "even",!
    WHEN OTHER             WRITE "odd",!
END EVALUATE
```

If nothing matches and no `OTHER` clause exists, `EVALUATE` falls through
silently.

A dotted form is also accepted, with `WHEN` lines at one level deeper
than the header:

```
EVALUATE status
. WHEN "OK"   WRITE "ok",!
. WHEN OTHER  WRITE "other",!
```

Rules:
- Every subject is evaluated once; the first matching clause wins with no
  fall-through.
- `EVALUATE` is not a scope. `QUIT`/`RETURN`/`GOTO` inside a clause
  affect the enclosing routine.
- `WHEN` takes no postcondition. `EVALUATE` and `END EVALUATE` accept one.
- A `WHEN` with a different number of tests than the header has subjects
  is a `<SYNTAX>` error.

---

# Part III, Error Handling & Transactions

## 1. Conditions: `CONDITION`, `SIGNAL`, `HANDLE`/`CATCH`/`RETRY`

Phase implements a narrowed version of a condition system: no true
resumption, no continuations. A signal unwinds to the nearest enclosing
handler. Recovery means re-running the enclosing block from its top.

```
CONDITION <BADORDER>(orderId,reason)

PROCESSORDER(id)
 DO
     IF '$DATA(^ORDERS(id))  SIGNAL <BADORDER>(orderId: id, reason: "not found")
     SET ^ORDERS(id,"STATUS")="PROCESSING"
     ; ... more work ...
 CATCH <BADORDER> AS e
     WRITE "Skipping order ",e("orderId"),": ",e("reason"),!
 END DO
 QUIT
```

- **`CONDITION <NAME>(f1,f2,...)`** declares a condition's field list.
  A `SIGNAL` that names an undeclared field throws `<SYNTAX>`.
- **`SIGNAL <NAME>(field: expr,...)`** raises the condition. Note the
  colon between field name and value.
- **`HANDLE <NAME> AS binding`** is a retry-capable clause. `RETRY`
  inside its body re-runs the entire enclosing block from the top.
- **`CATCH <NAME> AS binding`** is a run-once clause. After the body
  runs, control falls through to after `END DO`.
- `CATCH <ANYTHING>` is a catch-all. A specific clause always wins over
  it, regardless of source order.
- Fields are accessed as `binding("fieldName")`. Every signaled condition
  gets an automatic `message` field with a human-readable summary.
- A plain `DO...END DO` with no `HANDLE`/`CATCH` is an ordinary block.

**`RETRY [n TIMES] [WITH field=expr,...]`** re-enters the enclosing
block. `RETRY WITH` seeds named locals for the next pass:

```
DO
    SET ^LEDGER(id,"BALANCE")=newBalance EXPECTING $TX = expectedTx
HANDLE <CONFLICT> AS c
    SET expectedTx=$GET(c("actualTx"),0)
    RETRY 5 TIMES
CATCH <CONFLICT> AS c
    WRITE "Gave up after 5 retries",!
END DO
```

`RETRY n TIMES` lets the block run `n+1` times in total. Once `n`
re-entries are used up, the `RETRY` does nothing and the handler body
continues past it. A plain `RETRY` has a ceiling of
`^%CONFIG("retry","max")` (default 1000) to prevent infinite loops on
errors that never resolve.

`RETRY` is valid only inside a `HANDLE` body, not in `CATCH`, not in
the block body itself.

### 1.1 Built-in conditions

| Condition          | Fields                        | When raised                                                                                                           |
|--------------------|-------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `<CONFLICT>`       | `detail`                      | A `SET ... EXPECTING` check fails inside a transaction, or the transaction commit fails. Phase rolls back first.      |
| `<LOCK-TIMEOUT>`   | `global`, `path`, `timeoutMs` | A `LOCK` times out, and at least one enclosing `HANDLE`/`CATCH` is present. Without a handler, `LOCK` returns false. |
| `<UNDEFINED>`      | `name`, `path`                | A bare read finds no value, and a handler is present. Without a handler, the plain `<UNDEFINED VARIABLE>` error fires.|
| `<DIVIDE-BY-ZERO>` | `dividend`, `divisor`         | Division by zero, and a handler is present. Without a handler, the plain `<DIVIDE BY ZERO>` error fires.             |

An unhandled signal propagates outward and eventually halts as a plain
error. Code that never uses `HANDLE`/`CATCH` behaves exactly as if the
condition system did not exist.

### 1.2 How handled blocks run

- Each top-level line of the block body is atomic. On a caught condition,
  the state is rolled back to the start of the line that raised it.
  Everything earlier lines did stays; everything that line had done is
  dropped.
- `RETRY` re-runs on the state the handler left behind, not the state at
  block entry.
- Clauses run outside the block's own handler scope. A signal from a
  clause goes to the handlers outside the block, never to sibling
  clauses.
- A handler is active for the dynamic extent of the body, including all
  callees. It is not active during clause execution.
- Only conditions are caught. Plain errors (`<SYNTAX>`, `<PROTECT>`,
  etc.) are never seen by `HANDLE`/`CATCH`, even with `<ANYTHING>`.

## 2. Transactions

Every global write in Phase is durable and atomic by default: each `SET`
or `KILL` commits immediately. To group multiple writes into a single
atomic unit, use an active transaction handle.

Phase does not have a `TRANSACTION` keyword yet (see Part XIV). The
transaction engine is fully operational and is used internally. The
`EXPECTING` optimistic-concurrency clause for `SET` is also reserved for
a future release. In the meantime, atomicity across multiple writes is
achieved by using the HTTP server's session model (Part XV §5) together
with the built-in `<CONFLICT>` condition.

## 3. Standard runtime errors

For anything a condition handler does not catch, Phase halts with a
plain error. The `<TAG>` bracket is a diagnostic convention. See
Appendix A for the full table.

---

# Part IV, String Tools

## 1. String literals

Phase uses M-style quote-doubling for escaping. `""` inside a
double-quoted string is a literal `"`. There is no backslash escape.

```
WRITE "she said ""hi""",!   ; -> she said "hi"
```

## 2. Slice syntax

`s[start:end]` lowers to `$EXTRACT(s, start, end)`. Indices are
1-based, inclusive. Both bounds are optional.

```
SET X="HELLO WORLD"
WRITE X[1:5],!    ; -> HELLO
WRITE X[7:11],!   ; -> WORLD
WRITE X[7:],!     ; -> WORLD  (no end bound = rest of string)
```

A single-index form `s[i]` (no colon) lowers to `$EXTRACT(s, i, i)`:

```
WRITE X[1],!      ; -> H
```

Phase distinguishes slice from the `[` "contains" operator by adjacency
and context: `s[i]` or `s[i:j]` with no space before `[` is always a
slice.

## 3. `FOR EACH ... IN`

```
SET ^LIST(1)="alpha", ^LIST(2)="beta", ^LIST(3)="gamma"

FOR EACH K IN ^LIST DO
. WRITE K,": ",^LIST(K),!
; -> 1: alpha   2: beta   3: gamma
```

`FOR EACH` iterates the immediate subscript level of any local or global
reference. It lowers at parse time to a `$ORDER` loop:

```
SET K=""
FOR  SET K=$ORDER(^LIST(K))  QUIT:K=""  ...
```

The variable named after `EACH` receives each subscript in collation
order. The variable is created fresh each pass; it does not need to be
`NEW`-scoped.

`FOR EACH` works against a subscripted collection too:

```
FOR EACH item IN ^ORDERS(1,"LINES") DO
. WRITE item,": ",^ORDERS(1,"LINES",item,"QTY"),!
```

---

# Part V, Namespaces

```
NAMESPACE Billing

TOTAL(orders,taxRate)
 RETURN orders, orders*taxRate

VALIDATE(id)
 QUIT '$DATA(^ORDERS(id))
```

`NAMESPACE name` is a compile-time directive. It prefixes every tag
defined after it in the same routine with `name.`. `TOTAL` above is
stored as `Billing.TOTAL` in the routine's tag map.

**Intra-namespace calls resolve automatically.** Inside a routine that
declared `NAMESPACE Billing`, a bare `DO TOTAL` or `GOTO VALIDATE` first
tries `Billing.TOTAL` / `Billing.VALIDATE`. If that fails, Phase falls
back to a bare lookup.

**Cross-namespace calls are not supported.** There is no syntax to call
a tag in a different namespace from outside it. The only resolution path
is the same-namespace automatic fallback.

**Global names may contain dots as decoration.** `^Billing.ORDERS` is a
legal global name; `.` and `_` are valid interior characters. This
carries no semantic weight: `^Billing.ORDERS` and `^BillingORDERS` are
equally flat as far as storage is concerned. `NAMESPACE` does not
implicitly prefix global references. A bare `^ORDERS` inside a
`NAMESPACE Billing` block stays `^ORDERS`.

---

# Part VI, Dynamic References: `REF(...)`

A subscript is already an ordinary expression, so `SET ^ORDERS(id,"STATUS")="SHIPPED"`
already works with no special syntax. `REF(...)` covers the case where
the **variable name itself** is dynamic:

```
SET gname="^ORDERS"
SET REF(gname,1,"STATUS")="SHIPPED"
WRITE ^ORDERS(1,"STATUS"),!        ; -> SHIPPED
WRITE REF(gname,1,"STATUS"),!      ; -> SHIPPED
```

```
SET lname="ITEM"
SET REF(lname,3)="widget"
WRITE ITEM(3),!                    ; -> widget
```

The first argument is a runtime string. A leading `^` means global
(Phase strips the sigil). Anything else means local. The remaining
arguments are subscripts, evaluated in order.

`REF(...)` works in `SET`, `KILL`, and ordinary reads.

```
KILL REF(gname,1,"STATUS")         ; kill by dynamic name

; Iterate using $DATA
SET name="^ACCOUNTS"
IF $DATA(REF(name,"alice"))  WRITE "exists",!
```

---

# Part VII, Time Travel

Phase stores every write in a write-ahead log with MVCC revision chains.
This makes it possible to read any global exactly as it existed at a
past point in time.

## 1. Reading at a past transaction

The time-travel bracket sits directly after the `^` sigil, with no space
between them:

```
^[50]ORDERS(id)            ; as of transaction 50
^[TX]ORDERS(id)            ; TX is any Phase expression
^[-1]ORDERS(id)            ; one commit before the current live value
^[-3]ORDERS(id)            ; three commits back
```

A plain integer is an **absolute transaction id**. A negative integer is
a **relative offset** from the most recent commit that touched anything
in the named subtree: `^[-1]` is "one write ago", `^[-2]` is "two writes
ago", and so on.

```
SET ^ORDERS(1,"STATUS")="PENDING"
SET ^ORDERS(1,"STATUS")="SHIPPED"
SET ^ORDERS(1,"STATUS")="DELIVERED"

WRITE ^ORDERS(1,"STATUS"),!      ; -> DELIVERED  (live)
WRITE ^[-1]ORDERS(1,"STATUS"),!  ; -> SHIPPED    (one commit back)
WRITE ^[-2]ORDERS(1,"STATUS"),!  ; -> PENDING    (two commits back)
```

`$TX` returns the last-committed transaction id as a string:

```
SET snapshot=$TX
; ... more writes happen ...
WRITE ^[snapshot]ORDERS(1,"STATUS"),!   ; reads the state at snapshot time
```

## 2. Timestamps

A non-numeric bracket value is treated as an ISO-8601 timestamp cutoff:

```
WRITE ^["2025-01-01 00:00:00"]ORDERS(1,"STATUS"),!
```

Phase returns the value as it stood at the last commit on or before that
timestamp. A value that fits neither a number nor an ISO-8601 date
throws `<SYNTAX>`.

## 3. Pinned read scopes, `DO WITH @TX(...)`

For a block of code that must read multiple globals consistently from
the same point in history, use a pin scope:

```
SET pin=$TX
; ... some writes happen here ...
DO WITH @TX(pin)
. WRITE ^ORDERS(1,"STATUS"),!     ; reads as of 'pin'
. WRITE ^ORDERS(1,"PRICE"),!      ; same snapshot
```

Inside the block, every bare global read is answered as of the pinned
transaction. Local reads are unaffected. The pin does not affect writes.

To pin only specific globals and leave others live, add `@BIND(...)`:

```
DO WITH @TX(pin) @BIND(^ORDERS,^CUSTOMERS)
. ; ^ORDERS and ^CUSTOMERS read from pin; every other global is live
```

`CALL WITH @TX(pin) tag(args)` is the expression-position equivalent:

```
SET result = CALL WITH @TX(pin) SUMMARIZE(1)
```

The pin scope lasts only for the duration of the block or call. An error
inside the block discards the entire environment, so a pin cannot leak.

## 4. Rules

- **Read-only.** `SET ^[50]ORDERS(id)=...` throws `<SET ON READONLY>`.
- **No new "undefined" concept.** A time-traveled read of a path that
  did not exist yet behaves like reading an undefined live reference:
  `$DATA` returns 0 (or 10 if descendants existed), a bare read throws
  `<UNDEFINED VARIABLE>`.
- **`$ORDER` through a time-traveled reference is not implemented.** It
  throws `<NOTIMPL>`. `$DATA` and bare reads through time-travel both
  work.

---

# Part VIII, Bulk Data Movement: `MERGE` and Materializing Joins

## 1. `MERGE`, copying a whole subtree

`MERGE` copies every live value under a source node into the matching
positions under a destination node, in one atomic commit.

```
MERGE target=source
MERGE target1=source1,target2=source2
MERGE:cond target=source
```

Source and destination may be any combination of local and global:

```
MERGE snap=^CUSTOMERS(1)         ; global into local
MERGE ^BACKUP(3)=^CUSTOMERS(3)   ; global into global
MERGE ^DEST=local                ; local into global
```

`MERGE` does **not** clear the destination first. Existing nodes under
the destination that are not present in the source are left untouched.
Only overlapping paths are overwritten. To replace a subtree completely,
`KILL` the destination first:

```
KILL ^BACKUP(3)
MERGE ^BACKUP(3)=^CUSTOMERS(3)
```

`MERGE` from a source that does not exist copies nothing and is not an
error.

A global-to-global `MERGE` commits all leaves atomically in a single
transaction. A `MERGE` inside an active transaction (when Phase
eventually exposes one) stages all the leaves together.

### Example: daily snapshot

```
SET today=$HOROLOG
FOR EACH id IN ^ORDERS DO
.  MERGE ^ARCHIVE(today,id)=^ORDERS(id)
```

## 2. Materializing a join

A join reads two related pieces of data and stores them together. Use
`FOR EACH` and `MERGE`:

```
FOR EACH oid IN ^ORDERS
.  MERGE report(oid)=^ORDERS(oid)
.  MERGE report(oid,"CUSTOMER")=^CUSTOMERS(^ORDERS(oid,"CUSTOMER"))
.  DO
```

Each pass copies one order's data, then copies that order's customer
data, into one local tree keyed by order id.

To filter which rows produce an entry, add a postcondition:

```
FOR EACH oid IN ^ORDERS
.  MERGE:^ORDERS(oid,"STATUS")="SHIPPED" shipped(oid)=^ORDERS(oid)
.  DO
```

To keep every row but conditionally enrich it:

```
FOR EACH oid IN ^ORDERS
.  MERGE london(oid)=^ORDERS(oid)
.  MERGE:^CUSTOMERS(^ORDERS(oid,"CUST"),"CITY")="London" london(oid,"CUST")=^CUSTOMERS(^ORDERS(oid,"CUST"))
.  DO
```

---

# Part IX, JSON Bridge: `ZMERGE` and `ZWRITE`

Phase's sparse hierarchical tree maps directly onto JSON objects.
`ZMERGE` and `ZWRITE` bridge the two without any intermediate
serialization step.

## 1. `ZMERGE`, loading JSON into a node

`ZMERGE` writes a JSON value into a target node exactly as if you had
run one `SET` per leaf:

```
ZMERGE ^CUSTOMERS(3)={ "name": { "first": "Bob", "last": "Smith" }, "balance": 156.5 }
```

This is equivalent to:

```
SET ^CUSTOMERS(3,"name","first")="Bob"
SET ^CUSTOMERS(3,"name","last")="Smith"
SET ^CUSTOMERS(3,"balance")=156.5
```

The right-hand side can be:

- An **inline JSON literal**, written directly after `=` in the source.
- A **Phase expression** that evaluates to a JSON string at runtime.

```
SET JSON="{ ""role"": ""admin"" }"
ZMERGE ^ACCOUNTS(id)=JSON
```

The comma-separated multi-target form works just like `SET`:

```
ZMERGE ^A(1)={"x":1},^B(2)={"y":2}
```

### 1.1 Merge semantics

`ZMERGE` does **not** clear the target first. Existing sibling nodes
survive; only overlapping paths are overwritten. This is identical to
`MERGE`'s own non-destructive behaviour:

```
ZMERGE ^CUSTOMERS(3)={"name":{"first":"Bob","last":"Smith"},"balance":156.5}

; Now update just the balance, name is untouched
ZMERGE ^CUSTOMERS(3)={"balance":200}

ZWRITE ^CUSTOMERS(3)
; -> {"balance":200,"name":{"first":"Bob","last":"Smith"}}
```

To replace a subtree completely, `KILL` it first.

### 1.2 Type mapping

| JSON type | Stored as                                       |
|-----------|-------------------------------------------------|
| `string`  | The string value itself                         |
| `number`  | Canonical MUMPS numeric form (Part I §3.2)      |
| `true`    | `"1"`                                           |
| `false`   | `"0"`                                           |
| `null`    | `""` (the empty string)                         |
| `object`  | Child nodes keyed by the object's string keys   |
| `array`   | Child nodes keyed `"1"`, `"2"`, ... (1-based)   |

Numbers are stored in canonical form: no trailing decimal zeros, no
exponent notation. `156.50` becomes `"156.5"`. `200` becomes `"200"`,
not `"200.0"`.

### 1.3 Errors

- Invalid JSON in the expression form throws `<SYNTAX>`.
- A path containing an empty-string subscript throws `<SUBSCRIPT>`.
- A time-traveled target throws `<SET ON READONLY>`.

## 2. `ZWRITE`, rendering a node as JSON

`ZWRITE` renders a node and its entire descendant subtree as a single
JSON value followed by a newline:

```
ZWRITE ^CUSTOMERS(3)
; -> {"balance":156.5,"name":{"first":"Bob","last":"Smith"}}
```

Leaf values in canonical numeric form become JSON numbers. Everything
else becomes a JSON string:

```
ZWRITE ^CUSTOMERS(3,"name")
; -> {"first":"Bob","last":"Smith"}

ZWRITE ^CUSTOMERS(3,"balance")
; -> 156.5
```

**Time-travel references are fully supported:**

```
ZWRITE ^[-1]CUSTOMERS(3)        ; the subtree one commit ago
ZWRITE ^[50]CUSTOMERS(3)        ; as of transaction 50
```

**Bare `ZWRITE`** dumps every local variable as `name=json` pairs:

```
SET A=10, B=20
ZMERGE C={"x":1,"y":2}
ZWRITE
; -> A=10
; -> B=20
; -> C={"x":1,"y":2}
```

**Multiple targets** each print on their own line:

```
ZWRITE ^CUSTOMERS(3),^ORDERS(1)
```

### 2.1 Node duality

When a node has both its own value and children, `ZWRITE` uses the
reserved key `"_value"` to preserve the node's own value inside the
JSON object:

```
SET ^X="root"
SET ^X("child")="leaf"
ZWRITE ^X
; -> {"_value":"root","child":"leaf"}
```

`ZMERGE` with an object that contains `"_value"` writes that key as a
literal child subscript, not as the node's own bare value. Use an
explicit `SET` if you need to set a node's bare value alongside children.

### 2.2 Errors

- `ZWRITE ^NONEXISTENT` throws `<UNDEFINED VARIABLE>` when the node and
  all its descendants do not exist.

---

# Part X, Blob Storage

Content-addressed storage stores each file once, identified by the
SHA-256 hash of its bytes. Two uploads of identical bytes always produce
the same hash; Phase stores the bytes only once.

## 1. Writing and reading a blob

`$BLOBWRITE(path)` reads a local file, stores it, and returns a
`blob:`-prefixed pointer string:

```
SET ptr=$BLOBWRITE("invoice.pdf")
SET ^ORDERS(1,"INVOICE")=ptr
WRITE ptr,!    ; -> blob:d5adb33f...
```

Optional additional arguments name the uploader and content type:

```
SET ptr=$BLOBWRITE("photo.png","photo.png","alice","image/png")
```

Store the pointer anywhere a plain string goes. To retrieve the raw
bytes, use `BLOBSAVE`:

```
BLOBSAVE ^ORDERS(1,"INVOICE") TO "invoice-copy.pdf"
```

Uploading the same bytes twice is idempotent: Phase adds the new filename
and uploader to the existing record rather than storing the bytes again.

## 2. Querying blob facts

| Function                  | Returns                                                                    |
|---------------------------|----------------------------------------------------------------------------|
| `$BLOBSIZE(ptr)`          | Size in bytes, or `""` if the blob does not exist                         |
| `$BLOBEXISTS(ptr)`        | `"1"` if the blob exists, `"0"` if not                                    |
| `$BLOBMETA(ptr,"field")`  | One metadata field (see below)                                             |

Metadata fields: `filenames`, `uploaders`, `contentType`, `size`,
`note`, `firstSeen`, `lastSeen`. `filenames` and `uploaders` each return
all recorded values joined by commas.

## 3. Updating blob metadata

```
BLOBMETA ^ORDERS(1,"INVOICE") SET note="Reprinted for billing dispute"
BLOBMETA ^ORDERS(1,"INVOICE") SET contentType="application/pdf",filenames="invoice-v2.pdf"
```

Settable fields: `note`, `contentType`, `filenames`, `uploaders`.
Setting `filenames` or `uploaders` replaces the whole list.

---

# Part XI, Storage and Concurrency Internals

## 1. Architecture

```
source text
    │
    ▼
Phase.Parser        one logical line → AST (Expr, Stmt)
    │
    ▼
Phase.Lines         groups physical lines into logical lines,
                    strips dot-block markers, resolves HANDLE/CATCH/
                    EVALUATE markers into their final shapes
    │
    ▼
Phase.Eval          tree-walks the flat line list against one Env
                    (locals) and one StorageEngine (globals), via a
                    (lineIndex, stmtIndex) program counter
```

The AST is a flat `[Line]`, not a nested-block tree. `IF`/`FOR` hold no
body. `GOTO`/`DO TAG` resolve to line indices.

## 2. Storage, WAL, and MVCC

- Every value is stored as a plain string; the storage layer does no
  type-aware encoding.
- Every write goes through a write-ahead log. Periodic checkpoint records
  hold only the latest revision per path. Full revision history lives in
  the WAL's commit entries, which is what makes time travel possible.
- `StorageEngine` provides multi-change atomicity: `commitTransaction`
  stages a set of changes and checks every optimistic-concurrency
  assertion before touching the log. If any check fails, nothing is
  written.

## 3. Locking

`LOCK`/`UNLOCK` implement advisory, cooperative locking:

- **Advisory.** Holding a lock has no effect on reads or writes.
  `LOCK` is a signal between processes that choose to check it.
- **Path-granular and non-recursive.** Locking `^ORDERS(id)` does not
  lock or block `^ORDERS(id,"PRICE")`. They are independent keys.
- **Reentrant per owner.** A second `LOCK` call by the same thread on
  the same path increments a depth counter rather than deadlocking.

A known, accepted limitation: `KILL` on a subtree never consults the
lock table. A lock held on a descendant path can be silently erased by
an unrelated subtree kill. This matches behaviour in real production
MUMPS systems and is documented as accepted, not a bug.

---

# Part XII, Intrinsic Function Reference

## Intrinsic variables

| Name       | Value                                                                               |
|------------|-------------------------------------------------------------------------------------|
| `$TEST`    | Boolean result of the most recent `IF` or postcondition test                        |
| `$HOROLOG` | `"days,seconds"`, days since 1840-12-31 and seconds since midnight                |
| `$JOB`     | The current session's job identifier string                                         |
| `$TX`      | The last-committed transaction id, as a string, used with time-travel brackets     |

Any other bare `$NAME` throws `<UNDEFINED VARIABLE>`.

## Function-call intrinsics

| Function                          | Args  | Behaviour                                                                                               |
|-----------------------------------|-------|---------------------------------------------------------------------------------------------------------|
| `$DATA(ref)`                      | 1     | Four-state existence check (Part I §6)                                                                  |
| `$ORDER(ref)`                     | 1     | Next sibling subscript in collation order, or `""`. Ref must be subscripted. Not through time-travel.  |
| `$GET(ref[,default])`             | 1–2   | Safe read; returns `default` (or `""`) instead of throwing on undefined. Works with time-travel refs.  |
| `$LENGTH(str)`                    | 1     | Character count of `str`                                                                                |
| `$EXTRACT(str[,start[,end]])`     | 1–3   | 1-indexed inclusive substring. `start` defaults to 1; `end` defaults to `start`                        |
| `$PIECE(str,delim[,which])`       | 2–3   | Split on a literal delimiter string; `which` defaults to 1 (1-indexed)                                 |
| `$CHILDCOUNT(ref)`                | 1     | Count of immediate children of the referenced node; 0 if absent or no children                         |
| `$BLOBWRITE(path[,name[,u[,ct]]])` | 1–4  | Store a local file as a blob; returns a `blob:`-prefixed pointer (Part X §1)                           |
| `$BLOBSIZE(ptr)`                  | 1     | Stored byte size, or `""` (Part X §2)                                                                  |
| `$BLOBEXISTS(ptr)`                | 1     | `"1"` or `"0"` (Part X §2)                                                                             |
| `$BLOBMETA(ptr,field)`            | 2     | One metadata field (Part X §2)                                                                          |

**Recognised but not yet implemented**, each throws `<NOTIMPL>`:
`$TEXT`, `$TRANSLATE`, `$JUSTIFY`, `$SELECT`.

**Not recognised at all**, each throws `<UNDEFINED FUNCTION>`:
`$FIND`, `$CHAR`, `$ASCII`, `$RANDOM`, `$STACK`, `$VIEW`, `$QUERY`,
`$ZDATE`, and other later-dialect intrinsics.

Phase rejects `$$` extrinsic function calls at parse time with a
dedicated error. `$$` calls are out of scope.

`$PIECE` and `$EXTRACT` may also appear as **SET targets** for in-place
string surgery:

```
SET X="one:two:three"
SET $PIECE(X,":",2)="TWO"
WRITE X,!                  ; -> one:TWO:three

SET $EXTRACT(X,1,3)="ONE"
WRITE X,!                  ; -> ONE:TWO:three
```

---

# Part XIII, Using the REPL

Start the REPL by running `phasedb` with a database file:

```
phasedb phase.db
```

On first run, the REPL prompts for a username and password to create the
initial admin account. On subsequent runs, it prompts for credentials.

```
phase# SET X=5  WRITE X,!
5
phase# SET ^ORDERS(1,"PRICE")=100
phase# WRITE $DATA(^ORDERS(1)),!
10
phase# ^ORDERS(1,"PRICE")
- : 100
```

- **A tag label must start at column 0.** A line beginning right at
  column 0 defines a tag. All other lines are statements.
- **A bare expression is evaluated and echoed** with a `- : ` prefix.
  This never shadows a real command; the statement pipeline always runs
  first.
- **Multi-line blocks continue automatically.** A line ending with bare
  `DO` opens a dot-block; the REPL keeps prompting until you enter a
  blank line. An explicit `:{` / `:}` pair is the alternative for cases
  the auto-continuation rule does not cover.

  ```
  phase# FOR I=1:1:3 DO
       > . WRITE I,!
       >
  1
  2
  3
  phase#
  ```

- **Ctrl-C** discards the current input without exiting. **Ctrl-D**
  exits the REPL.

### Dot commands

| Command                         | Effect                                                                       |
|---------------------------------|------------------------------------------------------------------------------|
| `.quit` / `.exit`               | Close the database cleanly and exit                                          |
| `.help`                         | Print the dot-command summary                                                |
| `.ns`                           | Show the current namespace                                                   |
| `.namespaces`                   | List all namespaces in `^%CATALOG`                                           |
| `.fns [ns]`                     | List functions and constants in a namespace                                  |
| `.history name [ns]`            | Show version history for a catalog item                                      |
| `.revert name ver [ns]`         | Revert a catalog item to an earlier version                                  |
| `.checkpoint` / `.save`         | Write a WAL checkpoint                                                       |
| `.compact tx`                   | Discard history before transaction `tx` (admin only); creates a backup before touching the file |
| `.compact 2026-10-17T12:00:00Z` | Discard history before a timestamp (ISO 8601, UTC); resolves to the last committed transaction at or before that time |
| `.adduser name [role]`          | Create a user; role is `admin`, `write`, `read`, or `none` (default `write`) |

---

# Part XIV, What Phase Does Not Implement Yet

This part exists so nobody spends an afternoon trying to fix something
that is a known, deliberate gap.

- **`TRANSACTION` / `END TRANSACTION` blocks.** The storage engine
  supports atomic multi-change commits internally, but the language
  keyword does not exist yet. The parser recognises `TRANSACTION` and
  captures it as `SUnsupported`, so scripts that contain it load without
  error but throw `<NOTIMPL>` when the line runs.
- **`SET t EXPECTING expr = v` (optimistic concurrency).** The `EXPECTING`
  clause is recognised and captured as `SUnsupported` for the same reason.
  Use the `<CONFLICT>` condition and `RETRY` (Part III §1) as the
  current workaround.
- **`EDGE` and graph operations.** The command is captured as
  `SUnsupported`. Graph traversal (`PATH`, `SHORTEST`) does not exist.
- **`INDEX`, `REBUILD INDEX`, and `$INDEXLOOKUP`.** Secondary indexes are
  not implemented. The commands are captured as `SUnsupported`.
- **`DEFINE MACRO` / `DROP MACRO` (MACLIB).** The macro system is not
  implemented. The commands are captured as `SUnsupported`.
- **CLOSURE / value-snapshot closures.** The `CLOSURE ... CAPTURING`
  literal syntax is not implemented.
- **Expression-position `IF ... THEN ... ELSE ... END IF`.** Only the
  statement form of `IF` exists.
- **String interpolation `{expr}` inside string literals.** Not
  implemented; use `_` concatenation instead.
- **PICTURE formatting `{expr:mask}` and `$FMT(mask,value)`.** Not
  implemented.
- **Explicit cross-namespace calls** (`CALL Other.Namespace.TAG(...)`).
  Only same-namespace automatic fallback resolution works (Part V).
- **`$ORDER` through a time-traveled reference.** Throws `<NOTIMPL>`.
  `$DATA` and bare reads through time travel both work.
- **Path-shape `WHEN` clauses** (`WHEN (_, "LINES", n) ...`). Deferred
  pending a broader decision on value types.
- **`?` pattern-match operator.** The parser accepts it; evaluation
  throws `<NOTIMPL>`.
- **`@` indirection.** The parser accepts it; evaluation throws
  `<NOTIMPL>`. Use `REF(...)` (Part VI) instead.
- **`<TRAVERSAL-LIMIT>` and `<CONFLICT>` as fully structured conditions.**
  `<CONFLICT>`'s fields are a single `detail` string rather than the
  structured `global`, `path`, `expectedTx`, `actualTx` tuple the design
  intends.

---

# Part XV, The HTTP Server

PhaseDB ships a standalone HTTP server, `phasedb-server`, that exposes
the database over a JSON/REST API. All data access goes through Phase
commands, so there is one authorization path. The server holds an
exclusive lock on the database file while it runs; neither the REPL nor
another server instance may open the same file at the same time.

## 1. Starting the server

```
phasedb-server --db phase.db --port 8484
```

The server binds `127.0.0.1` by default. Change the bind address:

```
phasedb-server --db phase.db --listen 0.0.0.0 --port 8484
```

Binding to a non-loopback address without TLS requires
`--allow-insecure-basic`. The server refuses to start without it.

## 2. TLS

```
phasedb-server --db phase.db \
               --tls-cert /etc/phasedb/server.crt \
               --tls-key  /etc/phasedb/server.key
```

TLS 1.2 or newer is required. Without certificate files, the server
speaks plain HTTP and only accepts credentials from a loopback address
unless `--allow-insecure-basic` is given.

A reverse-proxy setup is valid: terminate TLS at the proxy, pass plain
HTTP to `127.0.0.1:8484`, and set `--allow-insecure-basic`.

## 3. CORS

CORS is off by default. Enable it for one origin:

```
phasedb-server --db phase.db --cors-origin https://app.example.com
```

Wildcard origins with credentials are never permitted.

## 4. Authentication

### 4.1 Roles

Every account has a role: `none`, `read`, `write`, or `admin`. `none`
cannot make API calls. The server re-reads the caller's role on every
request, so a role change takes effect on the very next request.

### 4.2 Basic authentication (one-shot)

Send `Authorization: Basic base64(username:password)`. The request runs
with fresh locals and leaves no session behind. PBKDF2-SHA256 runs on
every one-shot call; frequent callers should use a session token.

### 4.3 Session tokens

Log in:

```
POST /v1/sessions
Content-Type: application/json

{ "username": "alice", "password": "secret" }
```

Response (HTTP 201):

```json
{
  "token":              "3f8a…",
  "expiresAt":          "2026-10-14T08:00:00Z",
  "idleTimeoutSeconds": 1800,
  "user":               "alice",
  "role":               "write",
  "namespace":          "%GLOBAL"
}
```

Use the token with `Authorization: Bearer 3f8a…`. A session keeps local
variables, the current namespace, held locks, and a `$JOB` id between
requests. Two concurrent requests for the same session queue; the second
waits at most `queueTimeoutMs` before getting `503 BUSY`.

Sessions expire after 30 minutes of inactivity (`--session-idle-seconds`,
default 1800) or 12 hours absolute (`--session-max-seconds`, default
43200). Sessions do not survive a server restart.

Inspect the current session:

```
GET /v1/sessions/current
Authorization: Bearer 3f8a…
```

Log out:

```
DELETE /v1/sessions/current
Authorization: Bearer 3f8a…
```

### 4.4 Rate limiting

Repeated login failures trigger exponential per-username and
per-address backoff. A `429` response carries a `Retry-After` header.

## 5. Execute endpoints

### 5.1 One-shot execute

```
POST /v1/execute
Authorization: Basic base64(alice:secret)
Content-Type: application/json

{ "command": "WRITE \"hello\",!" }
```

### 5.2 Session execute

```
POST /v1/sessions/current/execute
Authorization: Bearer 3f8a…
Content-Type: application/json

{ "command": "SET x=x+1\nWRITE x,!" }
```

Both endpoints accept the same body:

| Field       | Required | Description                                                                   |
|-------------|----------|-------------------------------------------------------------------------------|
| `command`   | yes      | Phase source, one or more lines                                               |
| `namespace` | no       | Overrides the session namespace for this request                              |
| `timeoutMs` | no       | Per-request timeout, capped by the server maximum (default 10 000 ms)        |

REPL dot-commands are not accepted here. Use the admin endpoints instead.

**Success response:**

```json
{
  "ok":        true,
  "output":    "hello\n",
  "value":     null,
  "lastTx":    42,
  "elapsedMs": 2,
  "requestId": "b1c2d3e4"
}
```

- `output`, everything `WRITE` printed.
- `value`, the result of a bare expression; `null` for a plain `WRITE`.
- Registering a column-0 tag adds `"registered": ["TAG"]` instead of
  `output`/`value`.

**Phase-level failure (HTTP 200, `ok: false`):**

```json
{
  "ok":    false,
  "output": "",
  "error": {
    "code":      "DIVIDE BY ZERO",
    "message":   "<DIVIDE BY ZERO>",
    "condition": null
  },
  "lastTx":    42,
  "elapsedMs": 1,
  "requestId": "b1c2d3e4"
}
```

Phase errors are `ok: false` at HTTP 200, they are language-level
results, not transport errors.

**Transport-level errors:**

| HTTP | `code`          | Cause                                     |
|------|-----------------|-------------------------------------------|
| 400  | `BAD_REQUEST`   | Malformed JSON, missing field, wrong type |
| 401  | `UNAUTHENTICATED` | Unknown user or wrong password          |
| 403  | `FORBIDDEN`     | Role too low for this endpoint            |
| 408  | `REQUEST_TIMEOUT` | Client too slow sending the body        |
| 413  | `TOO_LARGE`     | Body over the limit                       |
| 429  | `RATE_LIMITED`  | Too many login failures                   |
| 503  | `BUSY`          | Session or engine lock timeout            |

**Server-added error codes (Phase-level, `ok: false`):**

| Code                   | Meaning                                                             |
|------------------------|---------------------------------------------------------------------|
| `TIMEOUT`              | `timeoutMs` exceeded                                                |
| `OUTPUT LIMIT`         | Output exceeded `--max-output-bytes`                                |
| `FILE ACCESS DISABLED` | Phase code called `BLOBSAVE`/`$BLOBWRITE` inside an API session    |

## 6. Admin endpoints

All admin endpoints require admin role. A non-admin gets `403 FORBIDDEN`.

### 6.1 Status

```
GET /v1/admin/status
```

```json
{
  "ok":           true,
  "lastTx":       1234,
  "sessionCount": 3,
  "fileSizeBytes": 20480,
  "version":      "0.1.0.0",
  "config": {
    "maxBodyBytes":       { "value": 1048576, "source": "default" },
    "execTimeoutMs":      { "value": 5000,    "source": "flag"    },
    "sessionIdleSeconds": { "value": 1800,    "source": "config"  }
  },
  "requestId": "b1c2d3e4"
}
```

`source` is `flag`, `config` (from `^%CONFIG("server",…)`), or
`default`. Status reads the committed snapshot and never waits for the
engine lock.

### 6.2 Checkpoint

```
POST /v1/admin/checkpoint
```

Appends a WAL checkpoint record. Non-destructive: no data is removed and
`lastTx` is unchanged. Failure code: `CHECKPOINT FAILED`.

### 6.3 Compact

```
POST /v1/admin/compact
Content-Type: application/json

{ "beforeTx": 500 }
```

```
POST /v1/admin/compact
Content-Type: application/json

{ "beforeTime": "2024-01-15T12:00:00Z" }
```

The body must contain exactly one of `beforeTx` or `beforeTime`. If
both are present, `beforeTx` takes precedence.

**`beforeTx`**, the state as of transaction `n` becomes the new
baseline. History before transaction `n` is permanently removed from
the file. History after `n` is kept verbatim. Supplying a value
greater than the current `lastTx` is an error.

**`beforeTime`**, a UTC timestamp in ISO 8601 format (e.g.
`"2024-01-15T12:00:00Z"`). The server resolves this to the highest
committed transaction whose commit timestamp is at or before the given
time, then compacts to that transaction. If no committed transaction
falls at or before the cutoff, the database is compacted to tx 0,
which is effectively a no-op (a backup is still written).

In both cases the engine lock is held for the entire compaction. The
server writes a full backup to `<db>.backup-<timestamp>` before
touching the file. `lastTx` is unchanged after a successful compaction.

Success: `{ "ok": true, "lastTx": n, … }`.
Failure: `{ "ok": false, "error": { "code": "COMPACT FAILED", … } }`.

### 6.4 Add user

```
POST /v1/admin/users
Content-Type: application/json

{ "username": "bob", "password": "hunter2", "role": "write" }
```

Valid roles: `none`, `read`, `write`, `admin`. The password is hashed
before the engine lock is taken, so hashing does not block writers.
Concurrent requests for the same username give one success and one
`USER EXISTS`.

## 7. Blob endpoints

Blobs are binary objects stored inside the database and
content-addressed by SHA-256 hash. They are immutable once written.

### 7.1 Upload

```
PUT /v1/blobs
Authorization: Bearer 3f8a…
Content-Type: image/png
X-Phase-Filename: photo.png

<raw bytes>
```

Optional: `?ref=^GLOBAL(sub)`. When present, the server sets that node
to the blob pointer in the same WAL transaction as the blob write.

Response:

```json
{ "ok": true, "pointer": "blob:d5a…", "hash": "d5a…", "size": 4096 }
```

Uploading the same bytes twice is idempotent. Limit: `--max-blob-bytes`
(default 10 MiB).

### 7.2 Download by reference

```
GET /v1/blobs?ref=^MYDATA("avatar")
Authorization: Bearer 3f8a…
```

The value at the referenced node must be a `blob:` pointer. Returns the
raw bytes with `Content-Type` and `Content-Length` from stored metadata.

`HEAD /v1/blobs?ref=…` returns headers with an empty body.

### 7.3 Download by hash (admin only)

```
GET /v1/blobs/{hash}
Authorization: Bearer admin-token…
```

`HEAD /v1/blobs/{hash}` returns headers only.

### 7.4 Blob pointers in Phase code

A blob pointer is a plain string: `blob:<hex-sha256>`. Store it like any
other value:

```
SET ^PROFILE("alice","avatar")="blob:d5a…"
```

Retrieve via `GET /v1/blobs?ref=^PROFILE("alice","avatar")`.

---

# Appendix A, Error Reference

| Tag                    | When thrown                                                                                                                                                         |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `<UNDEFINED VARIABLE>` | A bare read of a local or global finds no value at that exact node, and no handler is catching `<UNDEFINED>`.                                                       |
| `<UNDEFINED LABEL>`    | `DO`/`GOTO` targets a tag not found in the routine.                                                                                                                 |
| `<UNDEFINED ROUTINE>`  | A cross-routine call targets a routine that is not loaded.                                                                                                          |
| `<UNDEFINED FUNCTION>` | Code calls an unrecognised `$FUNCTION(...)`.                                                                                                                        |
| `<DIVIDE BY ZERO>`     | `/`, `\`, or `#` divides by zero, and no handler is catching `<DIVIDE-BY-ZERO>`.                                                                                   |
| `<SUBSCRIPT>`          | `$ORDER` is called on a bare, unsubscripted reference, or a `SET`/`MERGE`/`ZMERGE` path contains an empty-string subscript.                                        |
| `<SYNTAX>`             | A parse-time grammar error, or a runtime shape error: wrong intrinsic argument count, invalid `SET`/`KILL` target, `CALL`/`&ref` misuse, `EVALUATE`/`WHEN` arity mismatch, `RETRY` outside a `HANDLE` clause, undeclared `SIGNAL` field, or unparseable time-travel bracket. |
| `<SET ON READONLY>`    | `SET` or `ZMERGE` targets a time-traveled (`^[…]`) reference.                                                                                                      |
| `<PROTECT>`            | A read or write was denied by the user/role permission system.                                                                                                      |
| `<NAKED>`              | A naked reference (`^(subs)`) was used before any named global had been referenced in this session.                                                                 |
| `<BIND SCOPE>`         | A nested `DO WITH @BIND(…)` named a global not bound by the enclosing block's own bind list.                                                                       |
| `<NOTIMPL>`            | Code ran an unsupported statement, `@` indirection, time-traveled `$ORDER`, the `?` pattern operator, or a recognised-but-unimplemented command. See Part XIV.      |
| `<INTERNAL>`           | A defensive "should not happen" branch ran. Report this if you see it.                                                                                              |
| `<IO ERROR>`           | An IO exception surfaced from the storage layer during a write or checkpoint operation.                                                                             |
| `<OUTPUT LIMIT>`       | `WRITE` output exceeded the session's configured maximum.                                                                                                           |
| `<FILE ACCESS DISABLED>` | Phase code called `BLOBSAVE`/`$BLOBWRITE` inside an API session where filesystem I/O is disabled.                                                                |

Catchable **conditions** (`<CONFLICT>`, `<LOCK-TIMEOUT>`, `<UNDEFINED>`,
`<DIVIDE-BY-ZERO>`) are a separate structured mechanism layered on top of
several rows above. See Part III §1.

---

# Appendix B, Operator Precedence

Loosest to tightest binding:

```
&   !                                     logical AND / OR (non-short-circuiting)
=   <   >   [   ]]   ?   (and ' forms)   comparison / contains / sorts-after / pattern
_                                         string concatenation
+   -                                     additive
*   /   \   #                             multiplicative / integer-divide / modulo
**                                        exponentiation (right-associative)
-   +   '   @                             unary prefix (highest)
```

`s[start:end]` slice syntax binds tighter than every operator above,
as a postfix form applied directly to the preceding value.
