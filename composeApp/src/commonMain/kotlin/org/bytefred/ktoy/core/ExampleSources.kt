package org.bytefred.ktoy.core

/**
 * The bundled example programs, embedded as strings so they ship inside
 * the app (and the tests) with no filesystem access.
 *
 * GENERATED from pytoy examples/ by scripts/gen_example_sources.py — do
 * not edit by hand; regenerate when the .toys / .toyo sources change.
 */

data class Example(val name: String, val kind: ExampleKind, val source: String)

enum class ExampleKind { ASM, TOYO }

val EXAMPLES: List<Example> = listOf(
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
"""),
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
)
