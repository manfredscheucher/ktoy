package org.bytefred.ktoy.core

/**
 * The bundled example programs, embedded as strings so they ship inside
 * the app (and the tests) with no filesystem access.
 *
 * Three kinds (see [ExampleKind]):
 *   - ASM  — assembly source (*.toys); to load: assemble.
 *   - C    — C source (*.toyc); to load: compile (with a compiler from
 *            [Example.compilers]) then assemble. `source` holds the C text
 *            itself, so the UI can show the C|asm view.
 *   - TOYO — pre-compiled bytecode listing (*.toyo); to load: parse.
 *
 * Each example also carries its repo [Example.subfolder] (e.g. "01-basics",
 * "04-io"; "" for bytecode), so the browser UI can group by subfolder like
 * pytoy's ExamplesDialog instead of a flat list.
 *
 * For C examples, [Example.compilers] names the compiler(s) that accept the
 * source — a subset of "minicc", "toycc". minicc rejects recursion (and
 * other constructs toycc supports), so a recursive example lists only
 * "toycc" while a simple one lists both. The list is empty for ASM/TOYO.
 *
 * GENERATED from pytoy examples/ by scripts/gen_example_sources.py — do
 * not edit by hand; regenerate when the .toys / .toyc / .toyo sources or
 * the compilers change.
 */

enum class ExampleKind { ASM, C, TOYO }

data class Example(
    val name: String,
    val kind: ExampleKind,
    val source: String,
    /** C-only: compilers that accept this source ("minicc"/"toycc"); empty otherwise. */
    val compilers: List<String> = emptyList(),
    /** Repo subfolder this came from (e.g. "01-basics", "04-io"); "" for bytecode. */
    val subfolder: String = "",
)

val EXAMPLES: List<Example> = listOf(
    Example("00_stop", ExampleKind.ASM, """# STOP (00000000) — halt execution
# The CPU runs until STOP. Everything after it is never executed.

        load  x         # acc = 42
        stop            # halt here
        load  y         # never runs (acc stays 42)

x:      42
y:      99
""", subfolder = "01-basics"),
    Example("01_right", ExampleKind.ASM, """# RIGHT (00000001) — shift accumulator right by 1
# 00000100 (4) becomes 00000010 (2).

        load  x         # acc = 4
        right           # acc = 2
        stop

x:      4
""", subfolder = "01-basics"),
    Example("02_left", ExampleKind.ASM, """# LEFT (00000010) — shift accumulator left by 1
# 00000100 (4) becomes 00001000 (8).

        load  x         # acc = 4
        left            # acc = 8
        stop

x:      4
""", subfolder = "01-basics"),
    Example("03_not", ExampleKind.ASM, """# NOT (00001111) — bitwise NOT of the accumulator
# 00001111 (15) becomes 11110000 (240).

        load  x         # acc = 00001111
        not             # acc = 11110000
        stop

x:      15
""", subfolder = "01-basics"),
    Example("04_and", ExampleKind.ASM, """# AND (00010001) — bitwise AND of accumulator with a memory value
# 11001100 AND 10101010 = 10001000.

        load  x         # acc = 11001100
        and   y         # acc = 10001000
        stop

x:      204             # 11001100
y:      170             # 10101010
""", subfolder = "01-basics"),
    Example("05_or", ExampleKind.ASM, """# OR (00010010) — bitwise OR of accumulator with a memory value
# 11001100 OR 10101010 = 11101110.

        load  x         # acc = 11001100
        or    y         # acc = 11101110
        stop

x:      204             # 11001100
y:      170             # 10101010
""", subfolder = "01-basics"),
    Example("06_xor", ExampleKind.ASM, """# XOR (00010011) — bitwise XOR of accumulator with a memory value
# 11001100 XOR 10101010 = 01100110.

        load  x         # acc = 11001100
        xor   y         # acc = 01100110
        stop

x:      204             # 11001100
y:      170             # 10101010
""", subfolder = "01-basics"),
    Example("07_load", ExampleKind.ASM, """# LOAD (00010100) — copy a memory value into the accumulator

        load  x         # acc = 42
        stop

x:      42
""", subfolder = "01-basics"),
    Example("08_store", ExampleKind.ASM, """# STORE (00010101) — copy the accumulator into a memory address
# Watch address `dst` change from 0 to 42 in the memory panel.

        load  x         # acc = 42
        store dst       # dst = 42
        stop

x:      42
dst:    0
""", subfolder = "01-basics"),
    Example("09_add", ExampleKind.ASM, """# ADD (00010110) — add a memory value to the accumulator
# 40 + 2 = 42.

        load  x         # acc = 40
        add   y         # acc = 42
        stop

x:      40
y:      2
""", subfolder = "01-basics"),
    Example("10_sub", ExampleKind.ASM, """# SUB (00010111) — subtract a memory value from the accumulator
# 50 - 8 = 42.

        load  x         # acc = 50
        sub   y         # acc = 42
        stop

x:      50
y:      8
""", subfolder = "01-basics"),
    Example("11_goto", ExampleKind.ASM, """# GOTO (00011000) — unconditional jump to a program address
# Jumps over the middle LOAD, so acc ends up 42, not 99.

        goto  skip
        load  y         # skipped
skip:   load  x         # acc = 42
        stop

x:      42
y:      99
""", subfolder = "01-basics"),
    Example("12_ifzero", ExampleKind.ASM, """# IFZERO (00011001) — jump to an address only if the accumulator is zero
# x is 0, so the jump to `done` is taken and `LOAD y` is skipped; acc stays 0.

        load  x         # acc = x
        ifzero done     # x == 0 -> jump to done
        load  y         # x != 0: acc = y
done:   stop

x:      0
y:      99
""", subfolder = "01-basics"),
    Example("13_nop", ExampleKind.ASM, """# NOP (10000000) — do nothing, just advance to the next instruction

        load  x         # acc = 42
        nop             # no effect
        stop

x:      42
""", subfolder = "01-basics"),
    Example("bit_set_clear_toggle", ExampleKind.ASM, """# set / clear / toggle bit 3 (mask 00001000) of x
# x=00000001 -> set:00001001 -> toggle:00000001 -> stored in results
        load  x
        or    mask        # set bit 3
        store r_set

        load  r_set
        xor   mask        # toggle bit 3 (back off)
        store r_toggle

        load  x
        and   keep        # clear bit 3 (mask's complement)
        store r_clear
        stop

x:        1               # 00000001
mask:     8               # 00001000
keep:     247             # 11110111 (NOT mask)
r_set:    0
r_toggle: 0
r_clear:  0
""", subfolder = "02-extended"),
    Example("compare_less", ExampleKind.ASM, """# compare a < b: sign bit of (a-b) is set on underflow
# a=3, b=5 -> a<b -> acc=1  (try a=5,b=3 -> acc=0)
        load  a
        sub   b
        and   sign        # isolate bit 7
        ifzero ge         # bit 7 clear -> a >= b
        load  one
        stop
ge:     load  zero
        stop

a:      3
b:      5
sign:   128
one:    1
zero:   0
""", subfolder = "02-extended"),
    Example("divide", ExampleKind.ASM, """# divide: a / b by repeated subtraction  (20 / 6 = 3)
# count how often b fits in a; stop when a-b goes negative (msb set).
# valid for a in 0..127 (msb is the sign).
loop:   load  a
        sub   b
        and   sign        # a-b < 0 ?
        ifzero cont       # no -> subtract for real
        load  q
        stop              # q = quotient
cont:   load  a
        sub   b
        store a           # a -= b
        load  q
        add   one
        store q           # q++
        goto  loop

a:      20
b:      6
q:      0
one:    1
sign:   128
""", subfolder = "02-extended"),
    Example("load_indirect", ExampleKind.ASM, """# load indirect: read data[i] into acc  (i=5 -> pi digit 9)
        load  base
        add   i
        store patch       # patch the LOAD address below

load:   20                # LOAD opcode
patch:  0                 # address byte -> data+i
        stop

base:   data
i:      5
data:   3
        1
        4
        1
        5
        9
        2
        6
        5
        3
""", subfolder = "02-extended"),
    Example("modulo", ExampleKind.ASM, """# modulo: a % b by repeated subtraction  (20 % 6 = 2)
# subtract b from a until a-b goes negative; a is then the remainder.
# valid for a in 0..127 (msb is the sign).
loop:   load  a
        sub   b
        and   sign        # a-b < 0 ?
        ifzero cont       # no -> subtract for real
        load  a
        stop              # a = remainder
cont:   load  a
        sub   b
        store a           # a -= b
        goto  loop

a:      20
b:      6
sign:   128
""", subfolder = "02-extended"),
    Example("msb_shift_vs_mask", ExampleKind.ASM, """# MSB of x: two ways, same answer.  x=200 -> msb=1
# shift: 7x RIGHT.  mask: AND 128 then branch (no shifting).
        load  x
        right
        right
        right
        right
        right
        right
        right
        store m1          # m1 = x >> 7

        load  x
        and   mask        # keep bit 7 -> 0 or 128
        ifzero zero
        load  one
        goto  done
zero:   load  zero_v
done:   store m2          # m2 = (x & 128) ? 1 : 0

        load  m1
        sub   m2
        stop              # 0 -> both methods agree

x:      200
mask:   128
one:    1
zero_v: 0
m1:     0
m2:     0
""", subfolder = "02-extended"),
    Example("multiply_doubling", ExampleKind.ASM, """# multiply fast: a * b by double-and-add  (6 * 7 = 42)
# each step: if lsb(b) add a to res; then a <<= 1, b >>= 1
        load  b
loop:   ifzero done       # b == 0 -> done
        and   one         # test lsb(b)
        ifzero skip       # lsb 0 -> don't add
        load  res
        add   a
        store res         # res += a
skip:   load  a
        left
        store a           # a <<= 1
        load  b
        right
        store b           # b >>= 1
        goto  loop
done:   load  res
        stop

a:      6
b:      7
res:    0
one:    1
""", subfolder = "02-extended"),
    Example("multiply_repeated", ExampleKind.ASM, """# multiply: a * b by adding a to itself b times  (6 * 7 = 42)
        load  b
loop:   ifzero done       # b == 0 -> done
        load  res
        add   a
        store res         # res += a
        load  b
        sub   one
        store b           # b--
        goto  loop
done:   load  res
        stop

a:      6
b:      7
res:    0
one:    1
""", subfolder = "02-extended"),
    Example("negate", ExampleKind.ASM, """# negate: 0 - x  (two's complement)  x=5 -> 251 (= -5 mod 256)
        load  zero
        sub   x
        stop

zero:   0
x:      5
""", subfolder = "02-extended"),
    Example("store_indirect", ExampleKind.ASM, """# store indirect: write val into data[i]  (watch data[3]: 0 -> 42)
        load  base
        add   i
        store patch       # patch the STORE address below

        load  val
store:  21                # STORE opcode
patch:  0                 # address byte -> data+i
        stop

base:   data
i:      3
val:    42
data:   0
        0
        0
        0
        0
""", subfolder = "02-extended"),
    Example("switch", ExampleKind.ASM, """# switch(x): 0->0, 1->1, 2->2, else->3   (no jump table, just equality tests)
# equality a==b is (a-b)==0 via SUB + IFZERO.
        load  x
        ifzero case0      # x == 0

        load  x
        sub   one
        ifzero case1      # x == 1

        load  x
        sub   two
        ifzero case2      # x == 2

        load  three       # default
        stop
case0:  load  zero
        stop
case1:  load  one
        stop
case2:  load  two
        stop

x:      2
zero:   0
one:    1
two:    2
three:  3
""", subfolder = "02-extended"),
    Example("fibonacci_iter", ExampleKind.ASM, """# fibonacci.toys
# Computes the n-th Fibonacci number
# Result ends up in the accumulator
#
# Algorithm:
#   a, b = 0, 1
#   for i in range(n):
#       b = a + b
#       a = b - a   (= old b, no temp needed)
#
# Note: fib(14) = 377 > 255, so n > 13 causes overflow
#
# Set before running:
#   n   = desired position (e.g. 10 -> fib(10) = 55)
#   b   = 1
#   one = 1

        load  n         # acc = n (loop counter)
loop:   ifzero end      # n == 0 -> done

        load  a         # acc = a
        add   b         # acc = a + b
        store b         # b = a + b  (new b)

        load  b         # acc = new b
        sub   a         # acc = (a+b) - a = old b
        store a         # a = old b

        load  n         # acc = n
        sub   one       # acc = n - 1
        store n         # n--

        goto  loop

end:    load  a         # result: fib(n) in accumulator
        stop

# data
n:      7
a:      0
b:      1
one:    1
""", subfolder = "03-programs"),
    Example("fibonacci_rec", ExampleKind.ASM, """# Recursive Fibonacci, Toy CPU. fib(n)=n if n<2 else fib(n-1)+fib(n-2) mod 256.
# Real stack via sp + self-modifying indirect load/store.
# Frame (pushed by caller): [marker][n]. markers: 0=top,1=after fib(n-1),2=after fib(n-2).
# The top frame is pre-allocated as data; the entry code seeds its n from
# `input`, so you only set `input` to choose which fib(n) to compute.

# ---------------- entry: seed the top frame's n from input ----------------
        load  input
        store seedn        # top-frame n = input (no manual sync needed)
        goto  fib

# ---------------- fib body ----------------
fib:    load  sp
        sub   one          # acc = sp-1  (address of this frame's n)
        store pka          # patch indirect-load address
lop:    20                 # LOAD opcode (raw byte)
pka:    0                  # <- address of n, patched above
        store nloc         # nloc = n

        ifzero ret         # n==0 -> result 0 (acc already 0), return
        load  nloc
        sub   one
        ifzero r1          # n==1 -> result 1
        goto  rec
r1:     load  one
        goto  ret

rec:    load  nloc
        sub   one
        store nm1          # nm1 = n-1
        load  nloc
        sub   two
        store nm2          # nm2 = n-2

        # push marker=1
        load  sp
        store pa1
        load  one
so1:    21
pa1:    0
        load  sp
        add   one
        store sp
        # push n-1
        load  sp
        store pa2
        load  nm1
so2:    21
pa2:    0
        load  sp
        add   one
        store sp
        goto  fib

r1site: store fret1         # acc=fib(n-1); save it (transient: no call before push)
        # Re-derive n-2 from THIS frame's n (globals were clobbered by inner calls).
        # sp still points past our frame, so n is at sp-1, exactly as at fib entry.
        load  sp
        sub   one
        store pka2
        load  zero
lop2:   20
pka2:   0                   # acc = n (from frame)
        sub   two
        store nm2           # nm2 = n-2  (fresh)
        # push saved fib(n-1)
        load  sp
        store pa3
        load  fret1
so3:    21
pa3:    0
        load  sp
        add   one
        store sp
        # push marker=2
        load  sp
        store pa4
        load  two
so4:    21
pa4:    0
        load  sp
        add   one
        store sp
        # push n-2
        load  sp
        store pa5
        load  nm2
so5:    21
pa5:    0
        load  sp
        add   one
        store sp
        goto  fib

r2site: store fscr          # acc = fib(n-2), stash to scratch
        load  sp
        sub   one
        store sp            # pop
        store pa6
        load  zero
lo6:    20
pa6:    0                   # load saved fib(n-1)
        add   fscr          # fib(n-1)+fib(n-2)

# ---------------- return: acc=result. pop frame, dispatch on marker ----------
ret:    store result
        load  sp
        sub   two           # pop n and marker (sp -= 2)
        store sp
        store pa7           # sp now points at marker slot; patch load addr
        load  zero
lo7:    20
pa7:    0                   # load marker
        store mk
        ifzero top          # marker 0 -> top-level
        load  mk
        sub   one
        ifzero r1load       # marker 1 -> after fib(n-1) call
        load  result        # marker 2 -> after fib(n-2) call; restore result
        goto  r2site

r1load: load  result        # restore result into ACC for r1site
        goto  r1site

top:    load  result
        stop

# data
zero:   0
one:    1
two:    2
input:  4
result: 0
mk:     0
nloc:   0
nm1:    0
nm2:    0
fret1:  0
fscr:   0
sp:     stackbase
# Pre-seeded top frame sits JUST BELOW stackbase: [marker=0][n=input].
# sp = stackbase points at the next free slot; peek (sp-1) reads n.
seedm:  0            # top-frame marker = 0   (addr stackbase-2)
seedn:  0            # top-frame n, set to `input` by the entry code (addr stackbase-1)
stackbase: 0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
""", subfolder = "03-programs"),
    Example("gcd_iter", ExampleKind.ASM, """# gcd.toys
# Greatest common divisor by subtraction (Euclid):
#   while a != b: if a > b: a -= b  else: b -= a
# gcd(48, 36) = 12.  Values 0..127 (msb is used as the sign for a > b).

loop:   load  a
        sub   b
        ifzero done       # a == b -> gcd found
        and   sign        # sign bit of (a - b)
        ifzero a_gt_b     # bit clear -> a > b

        # a < b:  b -= a
        load  b
        sub   a
        store b
        goto  loop

a_gt_b: load  a
        sub   b
        store a           # a -= b
        goto  loop

done:   load  a           # a == b == gcd
        stop

# data
a:      48
b:      36
sign:   128
""", subfolder = "03-programs"),
    Example("max3", ExampleKind.ASM, """# max3.toys
# Largest of three named values a, b, c.
#
# The three values are the first three digits of pi (a=3, b=1, c=4),
# so the expected result is max(3, 1, 4) = 4.
#
# Compare trick (same idea as max.toys, but on named values, not an
# array): to test "x > best", compute x - best. If that subtraction
# underflows (bit 7 set), then x < best; otherwise x >= best.
#
#   best = a
#   if b > best: best = b
#   if c > best: best = c

        load  a
        store best        # best = a

        # compare b vs best
        load  b
        sub   best        # acc = b - best
        and   mask        # bit 7 set -> b < best (underflow)
        ifzero setb       # bit 7 clear -> b >= best -> take b
        goto  chkc
setb:   load  b
        store best        # best = b

        # compare c vs best
chkc:   load  c
        sub   best        # acc = c - best
        and   mask        # bit 7 set -> c < best
        ifzero setc       # bit 7 clear -> c >= best -> take c
        goto  done
setc:   load  c
        store best        # best = c

done:   load  best        # result 4 in accumulator
        stop

# data
mask:   128
best:   0
a:      3
b:      1
c:      4
""", subfolder = "03-programs"),
    Example("max_array", ExampleKind.ASM, """# max_array.toys
# Finds the maximum of an array using self-modifying code
#
# Algorithm:
#   best = 0
#   for each element in arr:
#       if element > best: best = element
#
# Compare trick: compute element - best.
# If no underflow (bit 7 clear), element >= best.
#
# Values: 3, 1, 4, 1, 5, 9, 2  (expected max = 9)

# ── init: set indirect load to point at arr[0] ──────────
        load  ptr
        store iarg

# ── main loop ────────────────────────────────────────────
loop:   load  count
        ifzero done

        # load arr[i]
        20                # LOAD opcode
iarg:   0                 # ← patched to arr[i]
        store cur         # cur = arr[i]

        # compare: cur - best
        sub   best        # acc = cur - best
        and   mask        # bit 7 set if cur < best (underflow)
        ifzero update     # bit 7 clear -> cur >= best -> update

        goto  next

update: load  cur
        store best        # best = cur

next:   load  iarg
        add   one
        store iarg        # ptr++

        load  count
        sub   one
        store count       # count--
        goto  loop

done:   load  best
        stop

# data
count:  7
one:    1
mask:   128
best:   0
cur:    0
ptr:    arr

# array (contiguous)
arr:    3
        1
        4
        1
        5
        9
        2
""", subfolder = "03-programs"),
    Example("sort3", ExampleKind.ASM, """# sort3.toys
# Sorts three named values a, b, c ascending, then returns the smallest.
#
# The three values are the first three digits of pi (a=3, b=1, c=4).
# Sorted they are 1, 3, 4, so the smallest -- left in a -- is 1.
#
# Three compare-and-swap steps (same idea as bubble sort, but on three
# named variables instead of an array):
#   if a > b: swap(a, b)   # a <= b
#   if b > c: swap(b, c)   # b <= c  (largest now in c)
#   if a > b: swap(a, b)   # a <= b  (smallest now in a)
#
# Compare trick: to test "x > y", compute y - x. If that underflows
# (bit 7 set) then y < x, i.e. x > y, so we swap.

        # step 1: if a > b, swap
        load  b
        sub   a           # acc = b - a
        and   mask        # bit 7 set -> b < a -> a > b -> swap
        ifzero s2         # bit 7 clear -> a <= b -> no swap
        load  a
        store t
        load  b
        store a
        load  t
        store b           # a <-> b

        # step 2: if b > c, swap
s2:     load  c
        sub   b           # acc = c - b
        and   mask        # bit 7 set -> c < b -> b > c -> swap
        ifzero s3
        load  b
        store t
        load  c
        store b
        load  t
        store c           # b <-> c

        # step 3: if a > b, swap
s3:     load  b
        sub   a           # acc = b - a
        and   mask        # bit 7 set -> b < a -> a > b -> swap
        ifzero done
        load  a
        store t
        load  b
        store a
        load  t
        store b           # a <-> b

done:   load  a           # smallest, 1 in accumulator
        stop

# data
mask:   128
t:      0
a:      3
b:      1
c:      4
""", subfolder = "03-programs"),
    Example("sort_array_inline", ExampleKind.ASM, """# sort_array.toys
# ---------------------------------------------------------------------------
# Bubble sort of 10 bytes, in place, ascending.
#
# Array = the 10 digits of pi:  3 1 4 1 5 9 2 6 5 3
# After sorting:                1 1 2 3 3 4 5 5 6 9
#
# The Toy CPU has no index register, no compare, no multiply. We reach the
# array elements with SELF-MODIFYING CODE: a bare opcode byte followed by a
# patchable address byte (exactly the sum.toys / max.toys trick). Four such
# "indirect" instructions are used, all patched from one running pointer `j`:
#
#   iload_j:  LOAD arr[j]        (opcode 20 + addr byte)
#   iload_j1: LOAD arr[j+1]      (opcode 20 + addr byte)
#   istore_j: STORE arr[j]       (opcode 21 + addr byte)
#   istr_j1:  STORE arr[j+1]     (opcode 21 + addr byte)
#
# Loop structure (fixed passes, always correct):
#   for pass in 0..8:                 # outer, 9 passes
#       j = addr(arr)
#       for k in 0..8:                # inner, 9 compares over adjacent pairs
#           a = arr[j]; b = arr[j+1]
#           if a > b: swap            # bit 7 of (b - a) set  =>  b < a  => swap
#           j = j + 1
#
# Compare trick (from max.toys): compute (b - a) mod 256. If a > b then b - a
# underflows and bit 7 is set. We test bit 7 with `and mask(128)`:
#     acc==0  -> bit clear -> b >= a -> already ordered, no swap
#     acc!=0  -> bit set   -> b <  a -> swap
# (All values are small digits 0..9, so the sign-bit test is unambiguous.)
# ---------------------------------------------------------------------------

# ── outer loop init ──────────────────────────────────────────────────────
        load  npass_init
        store npass          # npass = 9 outer passes

outer:  load  npass
        ifzero done          # all passes done -> finish

        # reset inner pointer j to the start of the array
        load  arrptr
        store jptr           # jptr = addr(arr[0])

        load  ninner_init
        store ninner         # ninner = 9 comparisons this pass

# ── inner loop ────────────────────────────────────────────────────────────
inner:  load  ninner
        ifzero next_pass     # inner loop finished -> next outer pass

        # patch the four indirect instructions from jptr (a[j]) and jptr+1 (a[j+1])
        load  jptr
        store ldj_arg        # LOAD arr[j]  address
        store stj_arg        # STORE arr[j] address
        add   one
        store ldj1_arg       # LOAD arr[j+1]  address
        store stj1_arg       # STORE arr[j+1] address

        # a = arr[j]
        20                   # LOAD opcode (raw byte)
ldj_arg:  0                  # ← patched: addr of arr[j]
        store a              # a = arr[j]

        # b = arr[j+1]
        20                   # LOAD opcode
ldj1_arg: 0                  # ← patched: addr of arr[j+1]
        store b              # b = arr[j+1]

        # compare: (b - a); bit 7 set  =>  b < a  =>  must swap
        sub   a              # acc = b - a
        and   mask           # isolate bit 7
        ifzero no_swap       # bit clear -> b >= a -> ordered, skip swap

        # swap: arr[j] = b ; arr[j+1] = a
        load  b
        21                   # STORE opcode
stj_arg:  0                  # ← patched: addr of arr[j]
        load  a
        21                   # STORE opcode
stj1_arg: 0                  # ← patched: addr of arr[j+1]

no_swap:
        # j = j + 1
        load  jptr
        add   one
        store jptr

        load  ninner
        sub   one
        store ninner         # ninner--
        goto  inner

next_pass:
        load  npass
        sub   one
        store npass          # npass--
        goto  outer

# ── done: leave arr[0] (smallest element = 1) in ACC ───────────────────────
done:   load  arr           # arr[0], should be 1 after sorting
        stop

# data
npass_init:  9
ninner_init: 9
npass:       0
ninner:      0
one:         1
mask:        128
a:           0
b:           0
jptr:        0
arrptr:      arr            # address constant: start of the array

# array (contiguous) — the 10 digits of pi
arr:    3
        1
        4
        1
        5
        9
        2
        6
        5
        3
""", subfolder = "03-programs"),
    Example("sum3", ExampleKind.ASM, """# sum3.toys
# Adds three named values: a + b + c.
#
# The three values are the first three digits of pi (a=3, b=1, c=4),
# so the expected result is 3 + 1 + 4 = 8.
#
# No loop needed -- just load the first, add the other two.
# (For summing a whole array, see sum.toys.)

        load  a         # acc = a
        add   b         # acc = a + b
        add   c         # acc = a + b + c
        stop            # result 8 in accumulator

# data
a:      3
b:      1
c:      4
""", subfolder = "03-programs"),
    Example("sum_array", ExampleKind.ASM, """# sum_array.toys
# Sums an array of values using self-modifying code
#
# Trick: the LOAD instruction is 2 bytes [opcode=20, address].
# We place the opcode as a raw data byte followed by a patchable
# address byte. At runtime we overwrite the address byte to
# iterate through the array.
#
# Values: 3, 1, 4, 1, 5, 9, 2  (expected sum = 25)

# ── init: set indirect load to point at arr[0] ──────────
        load  ptr         # acc = address of arr[0]
        store iarg        # patch the address byte

# ── main loop ────────────────────────────────────────────
loop:   load  count
        ifzero done

iop:    20                # LOAD opcode (raw byte)
iarg:   0                 # ← address byte, patched to arr[i]
        add   result
        store result      # result += arr[i]

        load  iarg        # current pointer
        add   one
        store iarg        # ptr++

        load  count
        sub   one
        store count       # count--
        goto  loop

done:   load  result
        stop

# data
count:  7
one:    1
result: 0
ptr:    arr               # address of arr[0]

# array (contiguous)
arr:    3
        1
        4
        1
        5
        9
        2
""", subfolder = "03-programs"),
    Example("times7_doubling", ExampleKind.ASM, """# sevenfold.toys
# Computes res = 7 * a using doubling and addition
#
# b = a + a      (2a)
# c = b + b      (4a)
# res = a + b + c (a + 2a + 4a = 7a)
#
# With a = 4: result = 28

        load  a
        add   a
        store b         # b = 2a

        load  b
        add   b
        store c         # c = 4a

        load  a
        add   b
        add   c
        stop            # acc = a + 2a + 4a = 7a

# data
a:      4
b:      0
c:      0
""", subfolder = "03-programs"),
    Example("countdown", ExampleKind.ASM, """# countdown.toys — count down from 10 to 1, writing each step to an out cell.
# io0=10, io1=9, ... io9=1  (decimal).
# Same self-modifying output-pointer trick as fibonacci.toys.
#
# pytoy: output=decimal

wloop:  load  n
        ifzero done       # n == 0 -> done

        # write n to out[i] via a patched STORE
        load  n
iop:    21                # STORE opcode
iarg:   240               # <- address, patched each pass (starts at io0)

        # advance the output pointer
        load  iarg
        add   one
        store iarg

        # n = n - 1
        load  n
        sub   one
        store n
        goto  wloop

done:   load  n
        stop

# data
n:    10
one:   1
""", subfolder = "04-io"),
    Example("fibonacci_io", ExampleKind.ASM, """# fibonacci.toys — compute fib(0..13) and write them to io0..io13 (decimal).
# fib(0)=0, fib(1)=1, fib(n)=fib(n-1)+fib(n-2).  fib(13)=233 is the largest that
# still fits in one byte (fib(14)=377 would overflow).
#
# The output index is advanced by self-modifying code: the STORE below has its
# address byte patched each iteration to point at the next out cell.
#
# pytoy: output=decimal

        load  a
wloop:  load  count
        ifzero done

        # write the current fib value (in `a`) to out[i] via a patched STORE
        load  a
        store ival        # stash the value to write
iop:    21                # STORE opcode (raw byte)
iarg:   240               # <- address, starts at io0 (240), patched each pass
        # (the STORE above wrote ival into out[i])

        # advance the output pointer
        load  iarg
        add   one
        store iarg

        # fib step: next = a + b ; a = b ; b = next
        load  a
        add   b
        store next
        load  b
        store a
        load  next
        store b

        # count down
        load  count
        sub   one
        store count
        goto  wloop

done:   load  a
        stop

# data
a:     0            # fib(i)
b:     1            # fib(i+1)
next:  0
count: 14           # write 14 values: fib(0)..fib(13)
one:   1
ival:  0
""", subfolder = "04-io"),
    Example("greet_name", ExampleKind.ASM, """# greet_name.toys — read a name via busy-poll, then write a greeting.
#
# GUI usage:
#   1. type the name into io0, io1, io2, ... — one ASCII character per cell.
#   2. set `ready` (255) to the NUMBER of characters typed, via Edit.
# The program busy-polls `ready`; once non-zero it reads `ready` as the length
# N, copies io0..io(N-1) (the name) into a scratch area, then overwrites the io
# cells with the greeting  "hallo " + NAME + "!"  starting at io0.
#
#   output layout:  io0..io5 = 'h','a','l','l','o',' '
#                   io6..     = the N name characters
#                   then      = '!'
#
# The name is copied to scratch FIRST, because writing "hallo " clobbers
# io0..io5 (which may still hold name characters when N > 6).
#
# (A '.'-terminated variant is possible — read until a '.' appears — but passing
#  the length in `ready` is cleaner and needs no sentinel, so we use that.)
#
# Example: user types "Max" -> io0='M'(77), io1='a'(97), io2='x'(120), ready=3.
# Result: io0..io9 = h,a,l,l,o,space,M,a,x,!  i.e. "hallo Max!".
#
# Self-modifying code (patched LOAD/STORE address bytes) indexes the io cells by
# the runtime count N, same technique as sort.toys / sum_array.toys.
#
# pytoy: output=ascii

# ── 1. busy-poll until ready != 0, then n = ready ─────────────────
wait:   load  ready
        ifzero wait
        load  ready
        store n              # n = number of name characters

# ── 2. copy io0..io(n-1) into scratch[] ───────────────────────────
        load  iobase
        store rdarg          # read pointer over the io cells (starts at io0=240)
        load  scraddr
        store wrarg          # write pointer over scratch[]
        load  n
        store cnt            # cnt = how many chars still to copy

cploop: load  cnt
        ifzero cpdone

        20                   # LOAD opcode
rdarg:  240                  # ← io cell, patched each pass (io0, io1, ...)
        21                   # STORE opcode
wrarg:  scratch              # ← scratch slot, patched each pass

        load  rdarg          # advance io read pointer
        add   one
        store rdarg
        load  wrarg          # advance scratch write pointer
        add   one
        store wrarg
        load  cnt
        sub   one
        store cnt
        goto  cploop

cpdone:

# ── 3. write "hallo " to io0..io5 ─────────────────────────────────
        load  gh
        store io0
        load  ga
        store io1
        load  gl
        store io2
        load  gl2
        store io3
        load  go
        store io4
        load  gsp
        store io5

# ── 4. append the name: scratch[0..n-1] -> io6.. ──────────────────
        load  scraddr
        store r2arg          # read pointer over scratch[]
        load  nameout        # io6 = 246 = first name slot in the output
        store w2arg          # write pointer over the io cells
        load  n
        store cnt            # reuse cnt as the remaining-name counter

wrloop: load  cnt
        ifzero wrdone

        20                   # LOAD opcode
r2arg:  scratch              # ← scratch slot, patched each pass
        21                   # STORE opcode
w2arg:  246                  # ← io cell (io6..), patched each pass

        load  r2arg          # advance scratch read pointer
        add   one
        store r2arg
        load  w2arg          # advance io write pointer
        add   one
        store w2arg
        load  cnt
        sub   one
        store cnt
        goto  wrloop

wrdone:

# ── 5. append "!" right after the name (at io(6+n)) ───────────────
#   w2arg was advanced once per name char, so it now points one past the last
#   name cell, i.e. io(6+n) — exactly where '!' goes. Copy it into the STORE.
        load  w2arg
        store bangarg        # patch the '!' STORE address
        load  gbang
        21                   # STORE opcode
bangarg: 0                   # ← io(6+n), patched above
        stop

# data
zero:    0
one:     1
n:       0
cnt:     0
iobase:  240          # io0 (numeric init for a self-modifying pointer)
nameout: 246          # io6
scraddr: scratch      # address constant: start of scratch[]

# greeting "hallo " codes
gh:   104             # 'h'
ga:    97             # 'a'
gl:   108             # 'l'
gl2:  108             # 'l'
go:   111             # 'o'
gsp:   32             # ' '
gbang: 33             # '!'

# scratch area for the typed name (up to 15 chars)
scratch: 0
         0
         0
         0
         0
         0
         0
         0
         0
         0
         0
         0
         0
         0
         0
""", subfolder = "04-io"),
    Example("hello_world", ExampleKind.ASM, """# hello_world.toys — write "Hello world!" to the output cells as ASCII.
# Each output cell holds one character code; the I/O widget (ascii mode) shows
# them as letters.
#
# pytoy: output=ascii

        load  H
        store io0
        load  e
        store io1
        load  l
        store io2
        load  l2
        store io3
        load  o
        store io4
        load  sp
        store io5
        load  w
        store io6
        load  o2
        store io7
        load  r
        store io8
        load  l3
        store io9
        load  d
        store io10
        load  ex
        store io11
        stop

# data: ASCII codes for "Hello world!"
H:   72
e:  101
l:  108
l2: 108
o:  111
sp:  32
w:  119
o2: 111
r:  114
l3: 108
d:  100
ex:  33
""", subfolder = "04-io"),
    Example("numbers", ExampleKind.ASM, """# numbers.toys — write 0,1,2,...,14 into the output cells io0..io14.
# The simplest I/O program: just stores to the memory-mapped output region.
#
# Try the View → I/O format menu (decimal / binary / hex / ascii / 7segment)
# to see the same bytes displayed different ways. numbers_hex / numbers_ascii /
# numbers_7seg are the same program opened in those other formats.
#
# pytoy: output=decimal

        load  c0
        store io0
        load  c1
        store io1
        load  c2
        store io2
        load  c3
        store io3
        load  c4
        store io4
        load  c5
        store io5
        load  c6
        store io6
        load  c7
        store io7
        load  c8
        store io8
        load  c9
        store io9
        load  c10
        store io10
        load  c11
        store io11
        load  c12
        store io12
        load  c13
        store io13
        load  c14
        store io14
        stop

# data: the constants 0..14
c0:  0
c1:  1
c2:  2
c3:  3
c4:  4
c5:  5
c6:  6
c7:  7
c8:  8
c9:  9
c10: 10
c11: 11
c12: 12
c13: 13
c14: 14
""", subfolder = "04-io"),
    Example("numbers_ascii", ExampleKind.ASM, """# numbers_ascii.toys — write 0,1,2,...,14 into the output cells io0..io14.
# Same bytes as numbers.toys, but the I/O panel opens in ascii mode. Codes 0..14
# are control characters (no visible glyph) — this file is here to show that the
# raw bytes are unchanged; only the display format differs. Switch the
# View → I/O format menu to decimal/hex/7segment to read them.
#
# pytoy: output=ascii

        load  c0
        store io0
        load  c1
        store io1
        load  c2
        store io2
        load  c3
        store io3
        load  c4
        store io4
        load  c5
        store io5
        load  c6
        store io6
        load  c7
        store io7
        load  c8
        store io8
        load  c9
        store io9
        load  c10
        store io10
        load  c11
        store io11
        load  c12
        store io12
        load  c13
        store io13
        load  c14
        store io14
        stop

# data: the constants 0..14
c0:  0
c1:  1
c2:  2
c3:  3
c4:  4
c5:  5
c6:  6
c7:  7
c8:  8
c9:  9
c10: 10
c11: 11
c12: 12
c13: 13
c14: 14
""", subfolder = "04-io"),
    Example("numbers_hex", ExampleKind.ASM, """# numbers_hex.toys — write 0,1,2,...,14 into the output cells io0..io14.
# Same bytes as numbers.toys, but the I/O panel opens in hex mode, so they show
# as 0..9 then a..e. Switch the View → I/O format menu to see them any other way.
#
# pytoy: output=hex

        load  c0
        store io0
        load  c1
        store io1
        load  c2
        store io2
        load  c3
        store io3
        load  c4
        store io4
        load  c5
        store io5
        load  c6
        store io6
        load  c7
        store io7
        load  c8
        store io8
        load  c9
        store io9
        load  c10
        store io10
        load  c11
        store io11
        load  c12
        store io12
        load  c13
        store io13
        load  c14
        store io14
        stop

# data: the constants 0..14
c0:  0
c1:  1
c2:  2
c3:  3
c4:  4
c5:  5
c6:  6
c7:  7
c8:  8
c9:  9
c10: 10
c11: 11
c12: 12
c13: 13
c14: 14
""", subfolder = "04-io"),
    Example("pin_check", ExampleKind.ASM, """# pin_check.toys — ask for a 4-digit PIN and check it against 1337.
#
# GUI usage (input is ASCII):
#   1. type the four PIN digits into io0, io1, io2, io3 — one character per cell
#      (e.g. '1','3','3','7'). In ascii mode you type the characters directly.
#   2. set `ready` (255) to any non-zero value to submit.
# The program busy-polls `ready`; once non-zero it compares the four entered
# characters against the PIN "1337" and overwrites io0..io3 with the result:
#   right PIN -> "succ", acc = 42   (the secret: the Answer to Life, the
#                                     Universe and Everything — Hitchhiker's)
#   wrong PIN -> "fail", acc = 0
#
# So the PIN guards a secret: enter 1337 and the accumulator reveals 42. This
# is the running example for the security slides — brute force, reading the
# bytecode, side channels, fault injection. See doc/design/pin-security.md.
#
# The comparison is the classic one: for each digit, LOAD the entered character,
# SUB the expected character; if the difference is 0 the digit matched, so
# IFZERO jumps to the next check; otherwise the following GOTO bails to `fail`.
#
# Input is ASCII, so io0 holds the *code* of the typed character (e.g. '1' = 49),
# and the expected values below are the ASCII codes of 1,3,3,7.
#
# pytoy: output=ascii

# ── 1. busy-poll until the user submits (ready != 0) ──────────────
wait:   load  ready
        ifzero wait

# ── 2. compare io0..io3 against the PIN, one digit at a time ──────
        load  io0
        sub   pin0
        ifzero ok0         # matched -> check next digit
        goto  fail         # mismatched -> fail
ok0:    load  io1
        sub   pin1
        ifzero ok1
        goto  fail
ok1:    load  io2
        sub   pin2
        ifzero ok2
        goto  fail
ok2:    load  io3
        sub   pin3
        ifzero ok3
        goto  fail

# ── 3a. success: write "succ", reveal the answer (acc = 42), stop ─
ok3:    load  s_s
        store io0
        load  s_u
        store io1
        load  s_c1
        store io2
        load  s_c2
        store io3
        load  answer       # acc = 42, the secret, now revealed
        stop

# ── 3b. failure: write "fail", acc = 0, stop ──────────────────────
fail:   load  f_f
        store io0
        load  f_a
        store io1
        load  f_i
        store io2
        load  f_l
        store io3
        load  zero         # acc = 0 signals failure
        stop

# ── data ──────────────────────────────────────────────────────────
zero:   0
answer: 42             # the secret behind the PIN

# expected PIN "1337" as ASCII codes
pin0:  49              # '1'
pin1:  51              # '3'
pin2:  51              # '3'
pin3:  55              # '7'

# "succ" as ASCII codes
s_s:  115              # 's'
s_u:  117              # 'u'
s_c1: 99               # 'c'
s_c2: 99               # 'c'

# "fail" as ASCII codes
f_f: 102               # 'f'
f_a:  97               # 'a'
f_i: 105               # 'i'
f_l: 108               # 'l'
""", subfolder = "04-io"),
    Example("pin_check_hardened", ExampleKind.ASM, """# pin_check_hardened.toys — the 1337 PIN check, made constant-time.
#
# The naive pin_check (see pin_check.toys) bails on the first wrong digit with
# an IFZERO/GOTO per digit. That early-out leaks timing: a wrong first digit
# stops sooner than a wrong last digit, so an attacker measuring steps can
# recover the PIN digit by digit — 4*10 tries instead of 10^4. See the security
# slides and doc/design/pin-security.md.
#
# Fix: never branch on a single digit. For each digit compute
# (entered XOR expected) — which is 0 exactly when the digit matches — and OR
# all four together into `diff`. `diff` is 0 iff every digit matched. Only ONE
# conditional, at the very end. Every run executes the same instructions in the
# same order regardless of input, so the timing no longer depends on the PIN.
#
#   right PIN -> "succ", acc = 42   (the Answer, revealed)
#   wrong PIN -> "fail", acc = 0
#
# Residual weakness (slides): the single final `ifzero` is now the one spot a
# fault-injection attack targets — before, skipping the check meant beating
# four separate compares; now there is just one. Constant time kills the timing
# channel, it does not make the check fault-proof.
#
# GUI usage (ASCII input): type the four digits into io0..io3, then set `ready`
# (255) to any non-zero value to submit.
#
# pytoy: output=ascii

# ── 1. busy-poll until the user submits (ready != 0) ──────────────
wait:   load  ready
        ifzero wait

# ── 2. constant-time compare: diff = OR over (io[k] XOR pin[k]) ────
#    No per-digit branch. Every digit is always compared and folded in.
        load  io0
        xor   pin0         # 0 iff digit 0 matches
        store diff         # diff = (io0 ^ pin0)

        load  io1
        xor   pin1
        or    diff
        store diff         # diff |= (io1 ^ pin1)

        load  io2
        xor   pin2
        or    diff
        store diff         # diff |= (io2 ^ pin2)

        load  io3
        xor   pin3
        or    diff
        store diff         # diff |= (io3 ^ pin3)

# ── 3. the ONE conditional: diff == 0 iff all four digits matched ──
        load  diff
        ifzero succ
        goto  fail

# ── 3a. success: write "succ", reveal the answer (acc = 42), stop ─
succ:   load  s_s
        store io0
        load  s_u
        store io1
        load  s_c1
        store io2
        load  s_c2
        store io3
        load  answer       # acc = 42, the secret, now revealed
        stop

# ── 3b. failure: write "fail", acc = 0, stop ──────────────────────
fail:   load  f_f
        store io0
        load  f_a
        store io1
        load  f_i
        store io2
        load  f_l
        store io3
        load  zero         # acc = 0 signals failure
        stop

# ── data ──────────────────────────────────────────────────────────
zero:   0
answer: 42             # the secret behind the PIN
diff:   0              # accumulated digit differences (0 iff PIN correct)

# expected PIN "1337" as ASCII codes
pin0:  49              # '1'
pin1:  51              # '3'
pin2:  51              # '3'
pin3:  55              # '7'

# "succ" as ASCII codes
s_s:  115              # 's'
s_u:  117              # 'u'
s_c1: 99               # 'c'
s_c2: 99               # 'c'

# "fail" as ASCII codes
f_f: 102               # 'f'
f_a:  97               # 'a'
f_i: 105               # 'i'
f_l: 108               # 'l'
""", subfolder = "04-io"),
    Example("poll_demo", ExampleKind.ASM, """# poll_demo.toys — the canonical BUSY-POLL input demo.
#
# The Toy CPU has no interrupts and no blocking read — the only test it has is
# `ifzero`. So to wait for input the program busy-polls the `ready` register
# (address 255): it loads `ready` and, while it is 0, jumps back and loads it
# again. This tight loop is the busy-poll:
#
#   wait: load ready; ifzero wait   # spin while ready == 0
#
# In the GUI the user drives it: type a value into the io0 cell, then set
# `ready` (e.g. to 1) via the Edit mechanism. On its next poll the program sees
# ready != 0, reads io0, echoes it to io1 (just to show the byte moved), clears
# `ready` back to 0 (so a next round could start), and stops.
#
# Note on the NATURE of polling: because the CPU just loops checking a flag, it
# could equally well do OTHER work between polls, give up after a while (time
# out), or take a different branch if input never arrives. Here it does none of
# that — it waits forever. If the user never sets `ready` in the GUI, this
# program spins indefinitely (and a headless `--run` hits the step cap). That is
# expected: polling trades CPU cycles for simplicity.
#
# pytoy: output=decimal

wait:   load  ready       # poll the ready register (255)
        ifzero wait        # spin while 0  (GUI: user sets ready to proceed)
        load  io0          # ready != 0 -> read the input value from io0
        store io1          # echo it to io1 (just to show it moved)
        load  zero
        store ready        # clear ready, ready for next
        stop

zero:   0
""", subfolder = "04-io"),
    Example("sort_array_io", ExampleKind.ASM, """# sort.toys — read up to 15 numbers, sort them ascending, write them back.
#
# GUI usage:
#   1. type the array values into io0, io1, io2, ... — one number per cell.
#   2. set `ready` (255) to the COUNT N of values typed, via Edit.
# The program busy-polls `ready`; once non-zero it reads `ready` as N, copies
# io0..io(N-1) into a scratch array, bubble-sorts it ascending, then writes the
# sorted values back to io0..io(N-1).
#
# Example: user types io0..io3 = 5,3,8,1 and ready=4.
# Result: io0..io3 = 1,3,5,8.
#
# Compare trick (same as 03-programs/sort_array.toys): (b - a) mod 256, bit 7
# set  =>  b < a  =>  swap. Unambiguous as long as any two values differ by
# less than 128 (true for the small inputs here).
#
# Self-modifying code (patched LOAD/STORE address bytes) indexes the io cells
# and the scratch array by the runtime count N.
#
# pytoy: output=decimal

# ── 1. busy-poll until ready != 0, then n = ready ──────────────────────────
wait:   load  ready
        ifzero wait
        load  ready
        store n              # n = how many values the user supplied

# ── 2. copy io0..io(n-1) into arr[] ────────────────────────────────────────
        load  iobase
        store rdarg          # read pointer over the io cells (io0=240..)
        load  arrptr
        store wrarg          # write pointer over arr[]
        load  n
        store cnt

cploop: load  cnt
        ifzero cpdone

        20                   # LOAD opcode
rdarg:  240                  # ← io cell, patched each pass
        21                   # STORE opcode
wrarg:  0                    # ← arr slot, patched each pass

        load  rdarg
        add   one
        store rdarg
        load  wrarg
        add   one
        store wrarg
        load  cnt
        sub   one
        store cnt
        goto  cploop

cpdone:

# ── 3. bubble sort arr[0..n-1] ascending ───────────────────────────────────
# outer runs n-1 passes; inner compares adjacent pairs, n-1 of them per pass.
        load  n
        sub   one
        store npass          # npass = n - 1 outer passes

outer:  load  npass
        ifzero sorted        # all passes done

        load  arrptr
        store jptr           # reset inner pointer j to arr[0]

        load  npass
        store ninner         # ninner = npass comparisons this pass

inner:  load  ninner
        ifzero next_pass

        # patch the four indirect instructions from jptr (a[j]) and jptr+1
        load  jptr
        store ldj_arg
        store stj_arg
        add   one
        store ldj1_arg
        store stj1_arg

        20                   # LOAD opcode
ldj_arg:  0                  # ← arr[j]
        store a

        20                   # LOAD opcode
ldj1_arg: 0                  # ← arr[j+1]
        store b

        sub   a              # acc = b - a
        and   mask           # isolate bit 7
        ifzero no_swap       # bit clear -> b >= a -> ordered, skip swap

        load  b              # swap: arr[j] = b
        21                   # STORE opcode
stj_arg:  0                  # ← arr[j]
        load  a              # arr[j+1] = a
        21                   # STORE opcode
stj1_arg: 0                  # ← arr[j+1]

no_swap:
        load  jptr
        add   one
        store jptr           # j++

        load  ninner
        sub   one
        store ninner
        goto  inner

next_pass:
        load  npass
        sub   one
        store npass
        goto  outer

# ── 4. write arr[0..n-1] back to io0.. in order ────────────────────────────
sorted:
        load  arrptr
        store rptr           # read pointer over arr
        load  outbase
        store optr           # out pointer, starts at io0 (240)
        load  n
        store left           # how many still to write

wloop:  load  left
        ifzero done

        load  rptr           # patch the LOAD/STORE addresses
        store rd_arg
        load  optr
        store ot_arg

        20                   # LOAD opcode
rd_arg:  0                   # ← arr[i]
        21                   # STORE opcode
ot_arg:  240                 # ← io[i]

        load  rptr
        add   one
        store rptr           # i++
        load  optr
        add   one
        store optr           # out pointer++
        load  left
        sub   one
        store left
        goto  wloop

done:   load  arr            # arr[0] = smallest, left in ACC
        stop

# data
zero:     0
one:      1
mask:     128
iobase:   240          # io0 (numeric init for a self-modifying pointer)
outbase:  240          # io0
n:        0
cnt:      0
npass:    0
ninner:   0
jptr:     0
a:        0
b:        0
rptr:     0
optr:     0
left:     0
arrptr:   arr          # address constant: start of the array

# scratch array (contiguous), up to 15 values
arr:    0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
        0
""", subfolder = "04-io"),
    Example("fibonacci (bytecode)", ExampleKind.TOYO, """# Toy CPU – compiled listing
#
# ADDR  BYTES                   SOURCE
# ────────────────────────────────────────────────────────
#                                # fibonacci.toys
#                                # Computes the n-th Fibonacci number
#                                # Result ends up in the accumulator
#                                #
#                                # Algorithm:
#                                #   a, b = 0, 1
#                                #   for i in range(n):
#                                #       b = a + b
#                                #       a = b - a   (= old b, no temp needed)
#                                #
#                                # Note: fib(14) = 377 > 255, so n > 13 causes overflow
#                                #
#                                # Set before running:
#                                #   n   = desired position (e.g. 10 -> fib(10) = 55)
#                                #   b   = 1
#                                #   one = 1
#                                
     0   00010100  00011011              load  n         # acc = n (loop counter)
     2   00011001  00011000      loop:   ifzero end      # n == 0 -> done
#                                
     4   00010100  00011100              load  a         # acc = a
     6   00010110  00011101              add   b         # acc = a + b
     8   00010101  00011101              store b         # b = a + b  (new b)
#                                
    10   00010100  00011101              load  b         # acc = new b
    12   00010111  00011100              sub   a         # acc = (a+b) - a = old b
    14   00010101  00011100              store a         # a = old b
#                                
    16   00010100  00011011              load  n         # acc = n
    18   00010111  00011110              sub   one       # acc = n - 1
    20   00010101  00011011              store n         # n--
#                                
    22   00011000  00000010              goto  loop
#                                
    24   00010100  00011100      end:    load  a         # result: fib(n) in accumulator
    26   00000000                        stop
#                                
#                                # data
    27   00000111                n:      7
    28   00000000                a:      0
    29   00000001                b:      1
    30   00000001                one:    1

# SYMBOLS
#   loop            =   2  (00000010)
#   end             =  24  (00011000)
#   n               =  27  (00011011)
#   a               =  28  (00011100)
#   b               =  29  (00011101)
#   one             =  30  (00011110)
"""),
    Example("sum3 (bytecode)", ExampleKind.TOYO, """# Toy CPU – compiled listing
#
# ADDR  BYTES                   SOURCE
# ────────────────────────────────────────────────────────
#                                # sum3.toys
#                                # Adds three named values: a + b + c.
#                                #
#                                # The three values are the first three digits of pi (a=3, b=1, c=4),
#                                # so the expected result is 3 + 1 + 4 = 8.
#                                #
#                                # No loop needed -- just load the first, add the other two.
#                                # (For summing a whole array, see sum.toys.)
#                                
     0   00010100  00000111              load  a         # acc = a
     2   00010110  00001000              add   b         # acc = a + b
     4   00010110  00001001              add   c         # acc = a + b + c
     6   00000000                        stop            # result 8 in accumulator
#                                
#                                # data
     7   00000011                a:      3
     8   00000001                b:      1
     9   00000100                c:      4

# SYMBOLS
#   a               =   7  (00000111)
#   b               =   8  (00001000)
#   c               =   9  (00001001)
"""),
    Example("bit_operators.toyc", ExampleKind.C, """// expect: 36
/*
 * bitops.c - showcase the bitwise operators and shifts, which map directly
 * to Toy CPU opcodes (and, or, xor, not, left, right).
 *
 *   x    = 0xF0            = 11110000
 *   y    = 0x3C            = 00111100
 *   a = x & y             = 00110000 = 0x30 = 48
 *   b = x | y             = 11111100 = 0xFC = 252
 *   c = x ^ y             = 11001100 = 0xCC = 204
 *   a = a >> 2            = 00001100 = 12
 *   a = a << 1            = 00011000 = 24
 *   result = a + (c & 0x0F)   c & 0x0F = 00001100 = 12; 24 + 12 = 36
 *
 * Expected result: ACC = 36
 */
int main(void) {
    int x = 0xF0;
    int y = 0x3C;
    int a = x & y;
    int c = x ^ y;
    a = a >> 2;
    a = a << 1;
    return a + (c & 0x0F);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("if_then_else.toyc", ExampleKind.C, """// expect: 5
/*
 * ifelse.c - simple if/else demonstration.
 *
 * a = 7, b = 12. Since a < b, the result is b - a = 5.
 *
 * Expected result: ACC = 5
 */
int main(void) {
    int a = 7;
    int b = 12;
    int r;
    if (a < b) {
        r = b - a;
    } else {
        r = a - b;
    }
    return r;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("msb_shift_vs_mask.toyc", ExampleKind.C, """// expect: 0
/*
 * msb_shift_vs_mask.toyc - two ways to read the top bit (bit 7) of a byte.
 *
 * Same result, different cost on the Toy CPU:
 *   x >> 7      shifts right seven times -> seven RIGHT instructions
 *   x & 128     one AND, then test -> much shorter
 *
 * Many tasks have several correct solutions; the cheaper one matters here
 * because code and data share the same 256 bytes.
 *
 * Returns 0 when both methods agree.
 */

int main(void) {
    int x = 200;              // 11001000, top bit set

    int msb1 = x >> 7;        // via shift

    int msb2 = 0;             // via mask
    if (x & 128) msb2 = 1;

    return msb1 ^ msb2;       // 0 -> both agree
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("multiply_doubling.toyc", ExampleKind.C, """// expect: 42
/*
 * multiply_doubling.c - a * b by double-and-add, using +, shifts and a bit test.
 *
 * One step per BIT of b instead of per unit: add a when b's low bit is set,
 * then double a and halve b. b has at most 8 bits, so at most 8 steps -- so
 * 255 * 255 no longer explodes the way repeated addition does.
 *
 * b = 110 binary = 4 + 2, so 7*6 = 7*4 + 7*2 = 28 + 14 = 42.
 *
 * This is the method the '*' operator compiles to. C analogue of
 * examples/asm/02-extended/multiply_doubling.toys.
 */
int main(void) {
    int a = 7;
    int b = 6;
    int res = 0;
    while (b != 0) {
        if (b & 1) {
            res = res + a;
        }
        a = a << 1;
        b = b >> 1;
    }
    return res;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("multiply_operator.toyc", ExampleKind.C, """// expect: 42
/*
 * multiply_operator.c - compute a * b with the '*' operator.
 *
 * The Toy CPU has no multiply instruction, so the compiler expands '*' to a
 * double-and-add loop (one step per bit of b). See multiply_repeated.toyc and
 * multiply_doubling.toyc for the same two methods written out by hand with only
 * +, -, and shifts.
 *
 * With a = 7, b = 6: result = 42.
 */
int main(void) {
    int a = 7;
    int b = 6;
    return a * b;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("multiply_repeated.toyc", ExampleKind.C, """// expect: 42
/*
 * multiply_repeated.c - a * b by repeated addition, using only '+'.
 *
 * Add a to the result b times. Simple, but slow: the loop runs b times, so
 * 255 * 255 would do 255 additions. multiply_doubling.toyc does the same in
 * about 8 steps. (The '*' operator itself compiles to the doubling method;
 * this file spells out the naive one by hand.)
 *
 * C analogue of examples/asm/02-extended/multiply_repeated.toys.
 * With a = 7, b = 6: result = 42.
 */
int main(void) {
    int a = 7;
    int b = 6;
    int res = 0;
    while (b != 0) {
        res = res + a;
        b = b - 1;
    }
    return res;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("popcount.toyc", ExampleKind.C, """// expect: 3
/*
 * popcount.c - count the set bits (1-bits) of a number.
 *
 * Look at the lowest bit, add it to the count, then shift right; repeat until
 * the number is zero. The lowest bit is x & 1 (the Toy CPU has no `%`, but
 * `x & 1` is exactly `x % 2` for the low bit).
 *
 * x = 37 = 32 + 4 + 1 = 100101 in binary -> 3 bits set.
 *
 * Expected result: ACC = 3
 */

int popcount(int x) {
    int count = 0;
    while (x != 0) {
        count = count + (x & 1);   /* add the lowest bit */
        x = x >> 1;                /* drop it */
    }
    return count;
}

int main(void) {
    return popcount(37);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("times7_operator.toyc", ExampleKind.C, """// expect: 28
/*
 * sevenfold.c - compute 7 * a.
 *
 * Port of examples/asm/03-programs/sevenfold.toys. Uses the '*' operator (repeated addition).
 * With a = 4: result = 28.
 *
 * Expected result: ACC = 28
 */
int main(void) {
    int a = 4;
    return 7 * a;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("bigint_add.toyc", ExampleKind.C, """// expect: 0
/*
 * bigint_add.toyc - add two numbers wider than one byte.
 *
 * The Toy CPU is 8-bit, so a 32-bit value is kept as four bytes (little-endian,
 * least significant first). Addition goes byte by byte, carrying into the next
 * byte when a sum overflows past 255.
 *
 * Carry is detected with the top-bit mask (x & 128), not a 7-step shift:
 *   carry out of a+b = bit 7 of  (a & b) | ((a ^ b) & ~sum)
 *
 * A = 123456789  -> bytes [21, 205, 91, 7]
 * B = 314159265  -> bytes [161, 176, 185, 18]
 * C = A + B = 437616054 -> bytes [182, 125, 21, 26]
 *
 * Returns 0: C is checked against the expected bytes by XOR-ing the differences
 * together (0 only if every byte matches).
 */

int main(void) {
    int a0 = 21;  int a1 = 205; int a2 = 91;  int a3 = 7;
    int b0 = 161; int b1 = 176; int b2 = 185; int b3 = 18;
    int k;

    int c0 = (a0 + b0) & 255;
    k = ((a0 & b0) | ((a0 ^ b0) & ~c0)) & 128;

    int c1 = (a1 + b1) & 255; if (k) c1 = (c1 + 1) & 255;
    k = ((a1 & b1) | ((a1 ^ b1) & ~c1)) & 128;

    int c2 = (a2 + b2) & 255; if (k) c2 = (c2 + 1) & 255;
    k = ((a2 & b2) | ((a2 ^ b2) & ~c2)) & 128;

    int c3 = (a3 + b3) & 255; if (k) c3 = (c3 + 1) & 255;

    return (c0 ^ 182) | (c1 ^ 125) | (c2 ^ 21) | (c3 ^ 26);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("count_iter.toyc", ExampleKind.C, """// expect: 10
/*
 * count_iter.c - count up to 10 ITERATIVELY.
 *
 * Start at 1 and increment while x < 10, so x ends at 10.
 * The recursive version is in count_rec.toyc; both return 10.
 *
 * Expected result: ACC = 10
 */

int main(void) {
    int x = 1;
    while (x < 10) {
        x = x + 1;
    }
    return x;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("count_rec.toyc", ExampleKind.C, """// expect: 10
/*
 * count_rec.c - count up to 10 RECURSIVELY.
 *
 * f(n) = 0            if n == 0
 *      = f(n - 1) + 1 otherwise
 * so f(10) = 10.
 *
 * The iterative version is in count_iter.toyc; both return 10.
 * toycc compiles the recursion with a self-modifying-code stack (see the
 * manual).
 *
 * Expected result: ACC = 10
 */

int f(int n) {
    if (n == 0) {
        return 0;
    }
    return f(n - 1) + 1;
}

int main(void) {
    return f(10);
}
""", compilers = listOf("toycc"), subfolder = "03-programs"),
    Example("fibonacci_array.toyc", ExampleKind.C, """// expect: 34
/*
 * fibonacci_array.c - fill an array with the first 10 Fibonacci numbers.
 *
 * a[0]=0, a[1]=1, then a[i] = a[i-1] + a[i-2]. The array ends up holding
 * 0 1 1 2 3 5 8 13 21 34, and main returns the last one, a[9] = 34.
 *
 * Shows arrays with a computed index (a[i], a[i-1], a[i-2]) built from a loop.
 *
 * Expected result: ACC = 34
 */

int main(void) {
    int a[10];
    int i;
    a[0] = 0;
    a[1] = 1;
    i = 2;
    while (i < 10) {
        a[i] = a[i - 1] + a[i - 2];
        i = i + 1;
    }
    return a[9];
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("fibonacci_iter.toyc", ExampleKind.C, """// expect: 55
/*
 * fibonacci_iter.c - the n-th Fibonacci number, ITERATIVE.
 *
 * The recursive version is in fibonacci_rec.toyc; both compute fib(10) = 55.
 * (8-bit values wrap mod 256, so fib(14) = 377 would overflow.)
 *
 * Expected result: ACC = 55
 */

int fibonacci_iter(int n) {
    int a = 0;
    int b = 1;
    while (n != 0) {
        b = a + b;   /* new b = a + old b */
        a = b - a;   /* a = old b */
        n = n - 1;
    }
    return a;
}

int main(void) {
    return fibonacci_iter(10);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("fibonacci_rec.toyc", ExampleKind.C, """// expect: 55
/*
 * fibonacci_rec.c - the n-th Fibonacci number, RECURSIVE.
 *
 * fibonacci_rec(n) = n                              if n < 2
 *                  = fibonacci_rec(n-1) + fibonacci_rec(n-2)   otherwise
 *
 * The iterative version is in fibonacci_iter.toyc; both compute fib(10) = 55.
 *
 * The Toy CPU has no call/return and no stack, yet toycc compiles this real
 * recursion by giving each function fixed global slots and bracketing every
 * call with save/restore of the caller's live-across slots on a stack that
 * grows down from the top of memory (see the manual).
 *
 * Expected result: ACC = 55
 */

int fibonacci_rec(int n) {
    if (n < 2) {
        return n;
    }
    return fibonacci_rec(n - 1) + fibonacci_rec(n - 2);
}

int main(void) {
    return fibonacci_rec(10);
}
""", compilers = listOf("toycc"), subfolder = "03-programs"),
    Example("gcd_iter.toyc", ExampleKind.C, """// expect: 12
/*
 * gcd_iter.c - greatest common divisor, ITERATIVE (subtraction algorithm).
 *
 * The recursive version is in gcd_rec.toyc; both compute the same result.
 *
 * gcd(48, 36): 48-36=12, 36-12=24, 24-12=12, 12-12=0 -> gcd = 12.
 *
 * Expected result: ACC = 12
 */

int gcd_iter(int a, int b) {
    while (a != b) {
        if (a > b) {
            a = a - b;
        } else {
            b = b - a;
        }
    }
    return a;
}

int main(void) {
    return gcd_iter(48, 36);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("gcd_rec.toyc", ExampleKind.C, """// expect: 12
/*
 * gcd_rec.c - greatest common divisor, RECURSIVE (subtraction algorithm).
 *
 * The iterative version is in gcd_iter.toyc; both compute the same result.
 * toycc compiles recursion with a self-modifying-code stack (see the manual).
 *
 * gcd(48, 36) = gcd(12, 36) = gcd(12, 24) = gcd(12, 12) = 12.
 *
 * Expected result: ACC = 12
 */

int gcd_rec(int a, int b) {
    if (a == b) {
        return a;
    }
    if (a > b) {
        return gcd_rec(a - b, b);
    }
    return gcd_rec(a, b - a);
}

int main(void) {
    return gcd_rec(48, 36);
}
""", compilers = listOf("toycc"), subfolder = "03-programs"),
    Example("max3.toyc", ExampleKind.C, """// expect: 4
/*
 * max3.c - the largest of three values a, b, c.
 *
 * A helper function max3(a, b, c) using only the `>` comparison. The three
 * values are the first three digits of pi (3, 1, 4). For the maximum of a whole
 * array, see max_array.toyc.
 *
 * max(3, 1, 4) = 4.
 *
 * Expected result: ACC = 4
 */

int max3(int a, int b, int c) {
    int m = a;
    if (b > m) { m = b; }
    if (c > m) { m = c; }
    return m;
}

int main(void) {
    return max3(3, 1, 4);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("max_array.toyc", ExampleKind.C, """// expect: 9
/*
 * max_array.c - the largest element of an array.
 *
 * The array holds the 10 digits of pi (3 1 4 1 5 9 2 6 5 3). A loop walks it,
 * keeping the largest seen so far, using the `>` comparison. This is the C
 * version of examples/asm/03-programs/max_array.toys (self-modifying code by hand). See
 * max3.toyc for the simpler three-value version.
 *
 * max(3,1,4,1,5,9,2,6,5,3) = 9.
 *
 * Expected result: ACC = 9
 */

int main(void) {
    int a[10] = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3};
    int m = a[0];
    int i;
    i = 1;
    while (i < 10) {
        if (a[i] > m) {
            m = a[i];
        }
        i = i + 1;
    }
    return m;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("sort3.toyc", ExampleKind.C, """// expect: 1
/*
 * sort3.c - sort three values and return the smallest.
 *
 * sort3(a, b, c) orders a <= b <= c with a few compare-and-swap steps (the same
 * idea as bubble sort, but on three named variables instead of an array), then
 * returns the smallest, a. The three values are the first three digits of pi
 * (3, 1, 4). For sorting a whole array, see sort_array_inline.toyc.
 *
 * sorted(3, 1, 4) = 1, 3, 4  ->  smallest = 1.
 *
 * Expected result: ACC = 1
 */

int sort3(int a, int b, int c) {
    int t;
    if (a > b) { t = a; a = b; b = t; }   // a <= b
    if (b > c) { t = b; b = c; c = t; }   // b <= c
    if (a > b) { t = a; a = b; b = t; }   // a <= b again
    return a;                             // smallest
}

int main(void) {
    return sort3(3, 1, 4);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("sort_array_fn.toyc", ExampleKind.C, """// expect: 1
/*
 * sort_array_function.c - bubble sort where the array is passed to a FUNCTION
 * (see sort_array_inline.toyc for the version that sorts inline in main).
 *
 * The array decays to a pointer at the call: bubblesort() receives main's
 * array by its base address and sorts it IN PLACE through indirect element
 * access (a[i] == *(a + i), reached with self-modifying code). main then reads
 * a[0], the smallest element.
 *
 * Array = the 10 digits of pi:  3 1 4 1 5 9 2 6 5 3
 * After sorting:                1 1 2 3 3 4 5 5 6 9
 *
 * Expected result: ACC = 1
 */

int bubblesort(int a[], int n) {
    int i;
    int j;
    int t;
    i = 0;
    while (i < n) {
        j = 0;
        while (j < n - 1 - i) {
            if (a[j] > a[j + 1]) {
                t = a[j];
                a[j] = a[j + 1];
                a[j + 1] = t;
            }
            j = j + 1;
        }
        i = i + 1;
    }
    return 0;
}

int main(void) {
    int a[10] = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3};
    bubblesort(a, 10);
    return a[0];
}
""", compilers = listOf("toycc"), subfolder = "03-programs"),
    Example("sort_array_inline.toyc", ExampleKind.C, """// expect: 1
/*
 * sort_array_inline.c - iterative bubble sort of a fixed 10-element array,
 * done inline in main (see sort_array_function.toyc for the version that
 * passes the array to a function).
 *
 * Port of examples/asm/03-programs/sort_array.toys to C. The array holds the 10 digits of pi
 * (3 1 4 1 5 9 2 6 5 3); after sorting it is 1 1 2 3 3 4 5 5 6 9, so main
 * returns a[0] = 1.
 *
 * toycc compiles the array as a contiguous block of data bytes plus a
 * base-pointer byte, and reaches a[i] with self-modifying code: it computes
 * the element address (base + i), patches it into a raw LOAD/STORE address
 * byte, and executes that instruction (the sum_array.toys / sort_array.toys
 * trick).
 *
 * The // expect annotation only checks ACC (= a[0] = 1); the full sorted array
 * is verified from memory by scripts/verify_bubblesort_c.py and by
 * tests/test_toycc.py::test_bubblesort_full_array.
 *
 * Expected result: ACC = 1
 */
int main(void) {
    int a[10] = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3};
    int n = 10;
    int i;
    int j;
    int t;
    i = 0;
    while (i < n) {
        j = 0;
        while (j < n - 1 - i) {   /* after pass i the last i are in place */
            if (a[j] > a[j + 1]) {
                t = a[j];
                a[j] = a[j + 1];
                a[j + 1] = t;
            }
            j = j + 1;
        }
        i = i + 1;
    }
    return a[0];
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("sum3.toyc", ExampleKind.C, """// expect: 8
/*
 * sum3.c - add three values a + b + c.
 *
 * The simplest possible use of a helper function: sum3(a, b, c) returns their
 * sum. The three values are the first three digits of pi (3, 1, 4). For summing
 * a whole array, see sum_array.toyc.
 *
 * 3 + 1 + 4 = 8.
 *
 * Expected result: ACC = 8
 */

int sum3(int a, int b, int c) {
    return a + b + c;
}

int main(void) {
    return sum3(3, 1, 4);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("sum_array.toyc", ExampleKind.C, """// expect: 39
/*
 * sum_array.c - sum the elements of an array.
 *
 * The array holds the 10 digits of pi (3 1 4 1 5 9 2 6 5 3). A loop walks it
 * with a running index, adding each element. This is the C version of
 * examples/asm/03-programs/sum_array.toys (which does the same with hand-written self-modifying
 * code). See sum3.toyc for the simpler three-value version.
 *
 * 3+1+4+1+5+9+2+6+5+3 = 39.
 *
 * Expected result: ACC = 39
 */

int main(void) {
    int a[10] = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3};
    int s;
    int i;
    s = 0;
    i = 0;
    while (i < 10) {
        s = s + a[i];
        i = i + 1;
    }
    return s;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("sum_countdown.toyc", ExampleKind.C, """// expect: 15
/*
 * countdown.c - count down from 5 and accumulate the sum 5+4+3+2+1.
 *
 * Demonstrates a while loop with a decrementing counter and +=.
 * 5 + 4 + 3 + 2 + 1 = 15.
 *
 * Expected result: ACC = 15
 */
int main(void) {
    int i = 5;
    int total = 0;
    while (i != 0) {
        total += i;
        i -= 1;
    }
    return total;
}
""", compilers = listOf("toycc"), subfolder = "03-programs"),
    Example("sum_of_squares.toyc", ExampleKind.C, """// expect: 25
/*
 * functions.c - small helper functions that call each other.
 *
 * Demonstrates toycc's function support: functions with parameters, called
 * from main and from one another. The Toy CPU has no call/return instruction,
 * so toycc gives each non-recursive function fixed global slots for its
 * parameters and return value, emits one copy of its body, and jumps back to
 * the right call site with a marker (see the compiler README and doc).
 *
 * square(x) = x * x;  sum_of_squares(a, b) = square(a) + square(b).
 * With a = 3, b = 4:  square(3) + square(4) = 9 + 16 = 25.
 *
 * Expected result: ACC = 25
 */

int square(int x) {
    return x * x;
}

int sum_of_squares(int a, int b) {
    return square(a) + square(b);
}

int main(void) {
    return sum_of_squares(3, 4);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("greet_name.toyc", ExampleKind.C, """// greet_name — read a name, write back "Hallo NAME!".
//
// The greeting "Hallo " (6 characters) is placed at the front of the buffer.
// read(data + 6) busy-polls `ready`, then reads the name into data[6..] and
// returns its length k. We write k + 6 bytes out, so the greeting and the name
// (plus whatever trailing character the user supplied) go to the io cells.
//
// In the GUI: type one character per cell (io0, io1, ...), set `ready` to the
// number of characters, and the program greets you. ascii view by default.
//
// pytoy: output=ascii

int main(void) {
    int data[16];
    data[0] = 'H';
    data[1] = 'a';
    data[2] = 'l';
    data[3] = 'l';
    data[4] = 'o';
    data[5] = ' ';
    int k = read(data + 6);   // name goes into data[6..], k = its length
    write(data, k + 6);       // "Hallo " + the name, all out to io0..
    return 0;
}
""", compilers = listOf("toycc"), subfolder = "04-io"),
    Example("hello_world.toyc", ExampleKind.C, """// hello_world — write "Hello world!" out to the io cells.
//
// Each character is stored into a buffer, then write(data, 12) sends all 12
// bytes out to io0..io11. Pick ascii in the GUI (View -> I/O format) to read
// them as letters; it is the default for compiled I/O programs.
//
// pytoy: output=ascii

int main(void) {
    // A constant initializer list: these become the array's initial data bytes
    // at compile time, far smaller than twelve runtime stores (which overflow
    // the 256-byte machine).
    int data[12] = {'H', 'e', 'l', 'l', 'o', ' ',
                    'w', 'o', 'r', 'l', 'd', '!'};
    write(data, 12);
    return 0;
}
""", compilers = listOf("toycc"), subfolder = "04-io"),
    Example("pin_check.toyc", ExampleKind.C, """// pin_check — read a 4-digit PIN and check it against 1337.
//
// The PIN guards a secret: get it right and the program returns 42 — the
// Answer to Life, the Universe and Everything (Hitchhiker's Guide). Wrong PIN
// returns 0. This is the running example for the security slides (brute force,
// reading the bytecode, side channels, fault injection). See
// doc/design/pin-security.md.
//
// GUI usage (ascii input): type the four digits into io0..io3 (e.g. '1','3',
// '3','7'), then set `ready` to a non-zero value to submit. The program reads
// the digits, compares them to "1337", and writes back "succ" or "fail" while
// the return value (the accumulator at STOP) is 42 on success, 0 on failure.
//
// The digits are checked one at a time; the first mismatch falls through to
// "fail". Only when all four match does it reach "succ". (Nested ifs rather
// than one && chain keep the program inside the 240 usable bytes.)
//
// pytoy: output=ascii

int main(void) {
    int data[4];
    int succ[4] = {'s', 'u', 'c', 'c'};
    int fail[4] = {'f', 'a', 'i', 'l'};

    read(data);

    if (data[0] == '1') {
        if (data[1] == '3') {
            if (data[2] == '3') {
                if (data[3] == '7') {
                    write(succ, 4);
                    return 42;   // the secret, revealed
                }
            }
        }
    }
    write(fail, 4);
    return 0;
}
""", compilers = listOf("toycc"), subfolder = "04-io"),
    Example("pin_check_hardened.toyc", ExampleKind.C, """// pin_check_hardened — the same 1337 PIN check, but constant-time.
//
// The naive pin_check bails on the first wrong digit. That early-out is a
// timing side channel: a wrong first digit stops sooner than a wrong last
// digit, so an attacker measuring run time (steps in pytoy) can recover the
// PIN one digit at a time — 4*10 tries instead of 10^4. See the security
// slides and doc/design/pin-security.md.
//
// Fix: never branch on a single digit. OR the differences together and test
// once at the end. Every digit is always compared, so the work — and the
// timing — is the same whatever the input. `diff` is 0 exactly when all four
// digits match (XOR is 0 iff equal; OR stays 0 only if every XOR was 0).
//
// Residual weakness (covered in the slides): the single final `diff == 0` is
// now the one place a fault-injection attack can target. Constant time closes
// the timing channel; it does not make the check fault-proof. Security needs
// both a safe program AND safe hardware.
//
// pytoy: output=ascii

int main(void) {
    int data[4];
    int succ[4] = {'s', 'u', 'c', 'c'};
    int fail[4] = {'f', 'a', 'i', 'l'};
    int diff;

    read(data);

    // Constant-time compare: always touch all four digits, no early-out.
    // Each (digit ^ expected) is 0 iff that digit matches; OR them together so
    // diff is 0 exactly when all four match. One expression, no per-digit
    // branch — the work (and the timing) is the same whatever the input. The
    // compare is unrolled by hand (no pin[] array, no for) to fit 240 bytes.
    diff = (data[0] ^ '1') | (data[1] ^ '3') | (data[2] ^ '3') | (data[3] ^ '7');

    if (diff == 0) {
        write(succ, 4);
        return 42;   // the secret, revealed
    }
    write(fail, 4);
    return 0;
}
""", compilers = listOf("toycc"), subfolder = "04-io"),
)

/**
 * Examples grouped for a browser UI, keyed by display category:
 *   "Assembly", "C (minicc)", "C (toycc)", "Bytecode".
 *
 * A C example appears under EVERY compiler that accepts it (an example
 * both accept shows in both "C (minicc)" and "C (toycc)"). Empty
 * categories are omitted. Mirrors pytoy's grouped_examples.
 */
fun groupedExamples(): Map<String, List<Example>> {
    val groups = linkedMapOf<String, MutableList<Example>>()
    fun add(key: String, ex: Example) {
        groups.getOrPut(key) { mutableListOf() }.add(ex)
    }
    for (ex in EXAMPLES) {
        when (ex.kind) {
            ExampleKind.ASM -> add("Assembly", ex)
            ExampleKind.TOYO -> add("Bytecode", ex)
            ExampleKind.C -> {
                if ("minicc" in ex.compilers) add("C (minicc)", ex)
                if ("toycc" in ex.compilers) add("C (toycc)", ex)
            }
        }
    }
    // Fixed display order; drop any category with no examples.
    val order = listOf("Assembly", "Bytecode", "C (minicc)", "C (toycc)")
    val out = linkedMapOf<String, List<Example>>()
    for (key in order) groups[key]?.let { out[key] = it }
    return out
}

/**
 * The example tree for the browser UI: category -> subfolder -> examples,
 * mirroring pytoy's ExamplesDialog. Top-level categories, in display order:
 *   "Assembly"    — .toys, children grouped by subfolder (01-basics, ...).
 *   "C (minicc)"  — C examples minicc accepts, grouped by subfolder.
 *   "C (toycc)"   — C examples toycc accepts, grouped by subfolder.
 *   "Bytecode"    — .toyo listings under a single "" subfolder (flat).
 *
 * A C example both compilers accept appears under BOTH C nodes; which node
 * a leaf sits under IS the chosen compiler. To make picking a leaf load it
 * on THAT compiler (via load()'s compiler preference), a C leaf's Example is
 * narrowed so `compilers` holds only the node's compiler.
 */
fun groupedExamplesTree(): Map<String, Map<String, List<Example>>> {
    val out = linkedMapOf<String, Map<String, List<Example>>>()

    fun bucket(predicate: (Example) -> Boolean, pick: (Example) -> Example):
        Map<String, List<Example>> {
        val bySub = linkedMapOf<String, MutableList<Example>>()
        for (ex in EXAMPLES) if (predicate(ex)) {
            bySub.getOrPut(ex.subfolder) { mutableListOf() }.add(pick(ex))
        }
        return bySub
    }

    // Assembly: one subfolder bucket per subfolder, preserving EXAMPLES order.
    val asm = bucket({ it.kind == ExampleKind.ASM }, { it })
    if (asm.isNotEmpty()) out["Assembly"] = asm

    // Bytecode: flat — a single "" subfolder holding every .toyo listing.
    val toyo = EXAMPLES.filter { it.kind == ExampleKind.TOYO }
    if (toyo.isNotEmpty()) out["Bytecode"] = linkedMapOf("" to toyo)

    // C: one top-level node per compiler, each example narrowed to that
    // compiler and bucketed by subfolder; a compiler node is omitted if it
    // accepts nothing.
    for (compiler in listOf("minicc", "toycc")) {
        val bySub = bucket(
            { it.kind == ExampleKind.C && compiler in it.compilers },
            { it.copy(compilers = listOf(compiler)) },
        )
        if (bySub.isNotEmpty()) out["C ($compiler)"] = bySub
    }

    return out
}
