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
    Example("00_stop", ExampleKind.ASM, """# example: stop
# description: Halts the CPU.
# steps: 2
# result: 42

# Opcode 00000000.

        load  x         # acc = 42
        stop
        load  y         # never runs

x:      42
y:      99
""", subfolder = "01-basics"),
    Example("01_right", ExampleKind.ASM, """# example: right
# description: Shifts acc right by 1 bit.
# steps: 3
# result: 2

# Opcode 00000001.

        load  x         # acc = 00000100
        right           # acc = 00000010
        stop

x:      4
""", subfolder = "01-basics"),
    Example("02_left", ExampleKind.ASM, """# example: left
# description: Shifts acc left by 1 bit.
# steps: 3
# result: 8

# Opcode 00000010.

        load  x         # acc = 00000100
        left            # acc = 00001000
        stop

x:      4
""", subfolder = "01-basics"),
    Example("03_not", ExampleKind.ASM, """# example: not
# description: Flips every bit of acc.
# steps: 3
# result: 240

# Opcode 00001111.

        load  x         # acc = 00001111
        not             # acc = 11110000
        stop

x:      15
""", subfolder = "01-basics"),
    Example("04_and", ExampleKind.ASM, """# example: and
# description: Computes the bitwise and of acc and a memory value.
# steps: 3
# result: 136

# Opcode 00010001.

        load  x         # acc = 11001100
        and   y         # acc = 10001000
        stop

x:      204             # 11001100
y:      170             # 10101010
""", subfolder = "01-basics"),
    Example("05_or", ExampleKind.ASM, """# example: or
# description: Computes the bitwise or of acc and a memory value.
# steps: 3
# result: 238

# Opcode 00010010.

        load  x         # acc = 11001100
        or    y         # acc = 11101110
        stop

x:      204             # 11001100
y:      170             # 10101010
""", subfolder = "01-basics"),
    Example("06_xor", ExampleKind.ASM, """# example: xor
# description: Computes the bitwise xor of acc and a memory value.
# steps: 3
# result: 102

# Opcode 00010011.

        load  x         # acc = 11001100
        xor   y         # acc = 01100110
        stop

x:      204             # 11001100
y:      170             # 10101010
""", subfolder = "01-basics"),
    Example("07_load", ExampleKind.ASM, """# example: load
# description: Copies a memory value into acc.
# steps: 2
# result: 42

# Opcode 00010100.

        load  x         # acc = 42
        stop

x:      42
""", subfolder = "01-basics"),
    Example("08_store", ExampleKind.ASM, """# example: store
# description: Copies acc into memory.
# steps: 3
# result: 42

# Opcode 00010101.

        load  x         # acc = 42
        store dst       # dst = 42
        stop

x:      42
dst:    0
""", subfolder = "01-basics"),
    Example("09_add", ExampleKind.ASM, """# example: add
# description: Adds a memory value to acc.
# steps: 3
# result: 42

# Opcode 00010110.

        load  x         # acc = 40
        add   y         # acc = 42
        stop

x:      40
y:      2
""", subfolder = "01-basics"),
    Example("10_sub", ExampleKind.ASM, """# example: sub
# description: Subtracts a memory value from acc.
# steps: 3
# result: 42

# Opcode 00010111.

        load  x         # acc = 50
        sub   y         # acc = 42
        stop

x:      50
y:      8
""", subfolder = "01-basics"),
    Example("11_goto", ExampleKind.ASM, """# example: goto
# description: Jumps to an address unconditionally.
# steps: 3
# result: 42

# Opcode 00011000.

        goto  skip
        load  y         # never runs
skip:   load  x         # acc = 42
        stop

x:      42
y:      99
""", subfolder = "01-basics"),
    Example("12_ifzero", ExampleKind.ASM, """# example: ifzero
# description: Jumps to an address only if acc is zero.
# steps: 3
# result: 0

# Opcode 00011001.

        load  x         # acc = 0
        ifzero done     # taken
        load  y         # never runs
done:   stop

x:      0
y:      99
""", subfolder = "01-basics"),
    Example("13_nop", ExampleKind.ASM, """# example: nop
# description: Does nothing.
# steps: 3
# result: 42

# Opcode 10000000.

        load  x         # acc = 42
        nop
        stop

x:      42
""", subfolder = "01-basics"),
    Example("bit_set_clear_toggle", ExampleKind.ASM, """# example: set, clear and toggle a bit
# description: Sets, toggles and clears bit 3 of x, storing each result.
# steps: 10
# result: 1

        load  x
        or    mask        # set bit 3
        store r_set       # 00001001

        load  x
        xor   mask        # toggle bit 3
        store r_toggle    # 00001001

        load  x
        and   keep        # clear bit 3
        store r_clear     # 00000001
        stop

x:        1               # 00000001
mask:     8               # 00001000
keep:     247             # 11110111 (not mask)
r_set:    0
r_toggle: 0
r_clear:  0
""", subfolder = "02-extended"),
    Example("compare_less", ExampleKind.ASM, """# example: compare a < b
# description: Sets acc to 1 if a < b, else to 0.
# steps: 6
# result: 1

# The most significant bit (MSB) of a - b acts as its sign: set means a < b.
# Valid while a - b stays in -128..127.

        load  a
        sub   b
        and   sign        # isolate the MSB
        ifzero ge         # MSB clear -> a >= b
        load  one
        stop
ge:     load  zero
        stop

a:      3
b:      5
sign:   128               # 10000000
one:    1
zero:   0
""", subfolder = "02-extended"),
    Example("divide", ExampleKind.ASM, """# example: divide by repeated subtraction
# description: Computes a / b by counting how often b can be subtracted from a.
# steps: 39
# result: 3

# The most significant bit (MSB) of a - b acts as its sign: set means a < b.
# Valid for a in 0..127, b in 1..128.

loop:   load  a
        sub   b
        and   sign        # isolate the MSB
        ifzero cont       # MSB clear -> a >= b, subtract for real
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
sign:   128               # 10000000
""", subfolder = "02-extended"),
    Example("load_indirect", ExampleKind.ASM, """# example: load indirect
# description: Reads data[i] into acc by patching the address of a load.
# steps: 5
# result: 9

# Self-modifying code: the load is written as raw bytes so its address byte can have a label.

        load  base
        add   i
        store patch

        20                # load opcode
patch:  0                 # <- address of data[i], patched above
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
    Example("modulo", ExampleKind.ASM, """# example: modulo by repeated subtraction
# description: Computes a % b by subtracting b until a < b.
# steps: 30
# result: 2

# The most significant bit (MSB) of a - b acts as its sign: set means a < b.
# Valid for a in 0..127, b in 1..128.

loop:   load  a
        sub   b
        and   sign        # isolate the MSB
        ifzero cont       # MSB clear -> a >= b, subtract for real
        load  a
        stop              # a = remainder
cont:   load  a
        sub   b
        store a           # a -= b
        goto  loop

a:      20
b:      6
sign:   128               # 10000000
""", subfolder = "02-extended"),
    Example("msb_shift_vs_mask", ExampleKind.ASM, """# example: most significant bit (MSB), shift vs mask
# description: Extracts the MSB of x by shifting and by masking, then compares the two.
# steps: 18
# result: 0

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
        and   mask        # isolate the MSB: 0 or 128
        ifzero zero
        load  one
        goto  done
zero:   load  zero_v
done:   store m2          # m2 = (x & 128) ? 1 : 0

        load  m1
        sub   m2
        stop              # 0 -> both agree

x:      200               # 11001000
mask:   128               # 10000000
one:    1
zero_v: 0
m1:     0
m2:     0
""", subfolder = "02-extended"),
    Example("multiply_doubling", ExampleKind.ASM, """# example: multiply by double-and-add
# description: Computes a * b by adding shifted copies of a.
# steps: 43
# result: 42

# One pass per bit of b: add a if the least significant bit (LSB) of b is set, then
# double a and halve b. An 8-bit b needs at most 8 passes, repeated addition up to 255.

        load  b
loop:   ifzero done       # b == 0
        and   one         # isolate the LSB
        ifzero skip       # LSB clear -> don't add
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
    Example("multiply_repeated", ExampleKind.ASM, """# example: multiply by repeated addition
# description: Computes a * b by adding a to res, b times.
# steps: 60
# result: 42

# 255 * 255 would take 255 passes, multiply_doubling.toys at most 8.

        load  b
loop:   ifzero done       # b == 0
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
    Example("negate", ExampleKind.ASM, """# example: negate
# description: Negates x by computing 0 - x.
# steps: 3
# result: 251

# 251 is -5 mod 256.

        load  zero
        sub   x
        stop

zero:   0
x:      5
""", subfolder = "02-extended"),
    Example("store_indirect", ExampleKind.ASM, """# example: store indirect
# description: Writes val into data[i] by patching the address of a store.
# steps: 6
# result: 42

# Self-modifying code: the store is written as raw bytes so its address byte can have a label.
# data[3] changes from 0 to 42.

        load  base
        add   i
        store patch

        load  val
        21                # store opcode
patch:  0                 # <- address of data[i], patched above
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
    Example("switch", ExampleKind.ASM, """# example: switch
# description: Branches on x with equality tests: 0, 1 and 2 map to themselves, else 3.
# steps: 10
# result: 2

# Equality a == b is tested as (a - b) == 0 with sub and ifzero.

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
    Example("fibonacci_iter", ExampleKind.ASM, """# example: iterative Fibonacci
# description: Computes fib(7) in a loop.
# steps: 81
# result: 13

# a = b - a recovers the old b, so no temporary variable is needed.
# fib(14) = 377 does not fit in a byte, so n > 13 overflows.
# Recursive version: fibonacci_rec.toys.

        load  n         # loop counter
loop:   ifzero end      # n == 0

        load  a
        add   b
        store b         # b = a + b

        load  b
        sub   a         # acc = (a + b) - a = old b
        store a

        load  n
        sub   one
        store n         # n--

        goto  loop

end:    load  a         # fib(n)
        stop

# data
n:      7
a:      0
b:      1
one:    1
""", subfolder = "03-programs"),
    Example("fibonacci_rec", ExampleKind.ASM, """# example: recursive Fibonacci
# description: Computes fib(7) recursively on a self-modifying-code stack.
# steps: 2004
# result: 13

# Frame, pushed by the caller: [marker][n]. Marker: 0 = top, 1 = after fib(n-1), 2 = after fib(n-2).
# The top frame is preallocated as data; set `input` to choose n. Iterative version: fibonacci_iter.toys.

        # entry: seed the top frame's n from input
        load  input
        store seedn
        goto  fib

        # fib body: n is read from the frame at sp-1
fib:    load  sp
        sub   one           # address of this frame's n
        store pka
        20                  # load opcode
pka:    0                   # <- address of n, patched above
        store nloc          # nloc = n

        ifzero ret          # n == 0 -> result 0 (acc already 0)
        load  nloc
        sub   one
        ifzero r1           # n == 1 -> result 1
        goto  rec
r1:     load  one
        goto  ret

rec:    load  nloc
        sub   one
        store nm1           # nm1 = n-1

        # push marker=1
        load  sp
        store pa1
        load  one
        21                  # store opcode
pa1:    0                   # <- address of the stack top, patched above
        load  sp
        add   one
        store sp
        # push n-1
        load  sp
        store pa2
        load  nm1
        21                  # store opcode
pa2:    0                   # <- address of the stack top, patched above
        load  sp
        add   one
        store sp
        goto  fib

r1site: store fret1         # acc = fib(n-1); safe in a global, no call before the push
        # Re-derive n-2 from this frame's n (inner calls overwrote the globals).
        # sp still points past this frame, so n is at sp-1, exactly as at fib entry.
        load  sp
        sub   one
        store pka2
        20                  # load opcode
pka2:   0                   # <- address of n, patched above
        sub   two
        store nm2           # nm2 = n-2
        # push saved fib(n-1)
        load  sp
        store pa3
        load  fret1
        21                  # store opcode
pa3:    0                   # <- address of the stack top, patched above
        load  sp
        add   one
        store sp
        # push marker=2
        load  sp
        store pa4
        load  two
        21                  # store opcode
pa4:    0                   # <- address of the stack top, patched above
        load  sp
        add   one
        store sp
        # push n-2
        load  sp
        store pa5
        load  nm2
        21                  # store opcode
pa5:    0                   # <- address of the stack top, patched above
        load  sp
        add   one
        store sp
        goto  fib

r2site: store fscr          # acc = fib(n-2)
        load  sp
        sub   one
        store sp            # pop
        store pa6
        20                  # load opcode
pa6:    0                   # <- address of saved fib(n-1), patched above
        add   fscr          # fib(n-1)+fib(n-2)

        # return: acc = result; pop the frame, dispatch on marker
ret:    store result
        load  sp
        sub   two           # pop n and marker
        store sp
        store pa7           # sp points at the marker
        20                  # load opcode
pa7:    0                   # <- address of the marker, patched above
        store mk
        ifzero top          # marker 0 -> top level
        load  mk
        sub   one
        ifzero r1load       # marker 1 -> after fib(n-1) call
        load  result        # marker 2 -> after fib(n-2) call; restore result
        goto  r2site

r1load: load  result        # restore result into acc for r1site
        goto  r1site

top:    load  result
        stop

# data
one:    1
two:    2
input:  7
result: 0
mk:     0
nloc:   0
nm1:    0
nm2:    0
fret1:  0
fscr:   0
sp:     stackbase
# The top frame sits just below stackbase: [marker=0][n=input].
# sp = stackbase points at the next free slot; sp-1 is n.
        0            # top-frame marker (stackbase-2)
seedn:  0            # top-frame n, set from `input` at entry (stackbase-1)
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
    Example("gcd_iter", ExampleKind.ASM, """# example: iterative GCD by subtraction
# description: Computes gcd(48, 36) in a loop with Euclid's subtraction algorithm.
# steps: 32
# result: 12

# Trace: (48, 36) -> (12, 36) -> (12, 24) -> (12, 12), then a == b.
# The most significant bit (MSB) of a - b acts as its sign: set means a < b.
# Valid for a and b in 1..127.

loop:   load  a
        sub   b
        ifzero done       # a == b -> gcd found
        and   sign        # isolate the MSB
        ifzero a_gt_b     # MSB clear -> a > b

        load  b
        sub   a
        store b           # b -= a
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
sign:   128               # 10000000
""", subfolder = "03-programs"),
    Example("max3", ExampleKind.ASM, """# example: largest of three values
# description: Finds the largest of a, b and c.
# steps: 15
# result: 4

# The most significant bit (MSB) of b - best acts as its sign: set means b < best.
# Valid for values 0..127. Whole array: max_array.toys.

        load  a
        store best

        # compare b with best
        load  b
        sub   best
        and   mask        # isolate the MSB
        ifzero setb       # MSB clear -> b >= best
        goto  chkc
setb:   load  b
        store best

        # compare c with best
chkc:   load  c
        sub   best
        and   mask        # isolate the MSB
        ifzero setc       # MSB clear -> c >= best
        goto  done
setc:   load  c
        store best

done:   load  best
        stop

# data
mask:   128               # 10000000
best:   0
a:      3
b:      1
c:      4
""", subfolder = "03-programs"),
    Example("max_array", ExampleKind.ASM, """# example: largest value in an array
# description: Finds the largest value in an array with a loop.
# steps: 115
# result: 9

# Self-modifying code: the load is written as raw bytes so its address byte can have a label.
# The most significant bit (MSB) of cur - best acts as its sign: set means cur < best.
# Valid for values 0..127. Three values: max3.toys.

        # point the indirect load at arr[0]
        load  ptr
        store iarg

loop:   load  count
        ifzero done

        20                # load opcode
iarg:   0                 # <- address of arr[i], patched each pass
        store cur

        sub   best        # acc = cur - best
        and   mask        # isolate the MSB
        ifzero update     # MSB clear -> cur >= best

        goto  next

update: load  cur
        store best

next:   load  iarg
        add   one
        store iarg        # next element

        load  count
        sub   one
        store count       # count--
        goto  loop

done:   load  best
        stop

# data
count:  7
one:    1
mask:   128               # 10000000
best:   0
cur:    0
ptr:    arr               # address of arr[0]

arr:    3
        1
        4
        1
        5
        9
        2
""", subfolder = "03-programs"),
    Example("sort3", ExampleKind.ASM, """# example: sort three values
# description: Sorts a, b and c; the result is the smallest.
# steps: 20
# result: 1

# Same idea as bubble sort, on three named variables instead of an array.
# The most significant bit (MSB) of b - a acts as its sign: set means b < a.
# Valid for values 0..127. Whole array: sort_array_inline.toys.

        # step 1: if a > b, swap
        load  b
        sub   a
        and   mask        # isolate the MSB
        ifzero s2         # MSB clear -> a <= b -> no swap
        load  a
        store t
        load  b
        store a
        load  t
        store b           # a <-> b

        # step 2: if b > c, swap (the largest ends in c)
s2:     load  c
        sub   b
        and   mask        # isolate the MSB
        ifzero s3         # MSB clear -> b <= c -> no swap
        load  b
        store t
        load  c
        store b
        load  t
        store c           # b <-> c

        # step 3: if a > b, swap (the smallest ends in a)
s3:     load  b
        sub   a
        and   mask        # isolate the MSB
        ifzero done       # MSB clear -> a <= b -> no swap
        load  a
        store t
        load  b
        store a
        load  t
        store b           # a <-> b

done:   load  a
        stop

# data
mask:   128               # 10000000
t:      0
a:      3
b:      1
c:      4
""", subfolder = "03-programs"),
    Example("sort_array_inline", ExampleKind.ASM, """# example: bubble sort of an array
# description: Sorts an array in place with bubble sort; the result is the smallest.
# steps: 1956
# result: 1

# Self-modifying code: loads and stores are written as raw bytes so their address bytes can have labels.
# Always 9 passes of 9 compares, with no early exit.
# The most significant bit (MSB) of b - a acts as its sign: set means b < a. Valid for values 0..127.

        load  npass_init
        store npass          # 9 outer passes

outer:  load  npass
        ifzero done

        load  arrptr
        store jptr           # reset j to arr[0]

        load  ninner_init
        store ninner         # 9 compares this pass

inner:  load  ninner
        ifzero next_pass

        # patch the four indirect instructions from jptr (arr[j]) and jptr+1 (arr[j+1])
        load  jptr
        store ldj_arg
        store stj_arg
        add   one
        store ldj1_arg
        store stj1_arg

        20                   # load opcode
ldj_arg:  0                  # <- address of arr[j], patched each pass
        store a

        20                   # load opcode
ldj1_arg: 0                  # <- address of arr[j+1], patched each pass
        store b

        sub   a              # acc = b - a
        and   mask           # isolate the MSB
        ifzero no_swap       # MSB clear -> b >= a -> no swap

        # swap: arr[j] = b; arr[j+1] = a
        load  b
        21                   # store opcode
stj_arg:  0                  # <- address of arr[j], patched each pass
        load  a
        21                   # store opcode
stj1_arg: 0                  # <- address of arr[j+1], patched each pass

no_swap:
        load  jptr
        add   one
        store jptr           # j++

        load  ninner
        sub   one
        store ninner         # ninner--
        goto  inner

next_pass:
        load  npass
        sub   one
        store npass          # npass--
        goto  outer

done:   load  arr
        stop

# data
npass_init:  9
ninner_init: 9
npass:       0
ninner:      0
one:         1
mask:        128            # 10000000
a:           0
b:           0
jptr:        0
arrptr:      arr            # address of arr[0]

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
    Example("sum3", ExampleKind.ASM, """# example: sum of three values
# description: Adds a, b and c.
# steps: 4
# result: 8

# Whole array: sum_array.toys.

        load  a
        add   b
        add   c
        stop

# data
a:      3
b:      1
c:      4
""", subfolder = "03-programs"),
    Example("sum_array", ExampleKind.ASM, """# example: sum of an array
# description: Sums an array with a loop.
# steps: 90
# result: 25

# Self-modifying code: the load is written as raw bytes so its address byte can have a label.
# Three values: sum3.toys.

        # point the indirect load at arr[0]
        load  ptr
        store iarg

loop:   load  count
        ifzero done

        20                # load opcode
iarg:   0                 # <- address of arr[i], patched each pass
        add   result
        store result      # result += arr[i]

        load  iarg
        add   one
        store iarg        # next element

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

arr:    3
        1
        4
        1
        5
        9
        2
""", subfolder = "03-programs"),
    Example("times7_doubling", ExampleKind.ASM, """# example: times 7 by doubling
# description: Computes 7 * a by doubling and adding.
# steps: 10
# result: 28

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
    Example("countdown", ExampleKind.ASM, """# example: countdown
# description: Counts down from 10 to 1, writing each value to io0..io9.
# pytoy: output=decimal

wloop:  load  n
        ifzero done

        # write n to io[i] via a patched store
        load  n
        21                # store opcode
iarg:   240               # <- address of io[i], patched each pass (starts at io0)

        load  iarg
        add   one
        store iarg        # next io cell

        load  n
        sub   one
        store n           # n--
        goto  wloop

done:   load  n
        stop

# data
n:    10
one:   1
""", subfolder = "04-io"),
    Example("fibonacci_io", ExampleKind.ASM, """# example: Fibonacci numbers
# description: Writes fib(0)..fib(13) to io0..io13.
# pytoy: output=decimal

# fib(14) = 377 does not fit in a byte, so the output stops at fib(13) = 233.

wloop:  load  count
        ifzero done

        # write a to io[i] via a patched store
        load  a
        21                # store opcode
iarg:   240               # <- address of io[i], patched each pass (starts at io0)

        load  iarg
        add   one
        store iarg        # next io cell

        # fib step: next = a + b; a = b; b = next
        load  a
        add   b
        store next
        load  b
        store a
        load  next
        store b

        load  count
        sub   one
        store count       # count--
        goto  wloop

done:   load  a
        stop

# data
a:     0            # fib(i)
b:     1            # fib(i+1)
next:  0
count: 14           # values left to write
one:   1
""", subfolder = "04-io"),
    Example("greet_name", ExampleKind.ASM, """# example: greet by name
# description: Reads a name and writes "hallo NAME!" back. Names over 8 characters overflow.
# pytoy: output=ascii

# Input: type one character per cell into io0, io1, ..., then set ready (mem[255]) to the count.
# The name goes to scratch first because writing "hallo " overwrites io0..io5.

        # busy-poll until ready != 0, then n = ready
wait:   load  ready
        ifzero wait
        load  ready
        store n              # number of name characters

        # copy io0..io[n-1] into scratch[]
        load  iobase
        store rdarg          # read pointer over the io cells
        load  scraddr
        store wrarg          # write pointer over scratch[]
        load  n
        store cnt            # characters left to copy

cploop: load  cnt
        ifzero cpdone

        20                   # load opcode
rdarg:  240                  # <- address of io[i], patched each pass
        21                   # store opcode
wrarg:  scratch              # <- address of scratch[i], patched each pass

        load  rdarg
        add   one
        store rdarg          # next io cell
        load  wrarg
        add   one
        store wrarg          # next scratch slot
        load  cnt
        sub   one
        store cnt            # cnt--
        goto  cploop

cpdone:

        # write "hallo " to io0..io5
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

        # append the name: scratch[0..n-1] -> io6..
        load  scraddr
        store r2arg          # read pointer over scratch[]
        load  nameout
        store w2arg          # write pointer over the io cells
        load  n
        store cnt            # characters left to write

wrloop: load  cnt
        ifzero wrdone

        20                   # load opcode
r2arg:  scratch              # <- address of scratch[i], patched each pass
        21                   # store opcode
w2arg:  246                  # <- address of io[6+i], patched each pass

        load  r2arg
        add   one
        store r2arg          # next scratch slot
        load  w2arg
        add   one
        store w2arg          # next io cell
        load  cnt
        sub   one
        store cnt            # cnt--
        goto  wrloop

wrdone:

        # append "!" at io[6+n]: w2arg points one past the last name cell
        load  w2arg
        store bangarg
        load  gbang
        21                   # store opcode
bangarg: 0                   # <- address of io[6+n], patched above
        stop

# data
one:     1
n:       0
cnt:     0
iobase:  240          # io0
nameout: 246          # io6
scraddr: scratch      # address of scratch[0]

# "hallo " and "!" as ASCII codes
gh:   104             # 'h'
ga:    97             # 'a'
gl:   108             # 'l'
gl2:  108             # 'l'
go:   111             # 'o'
gsp:   32             # ' '
gbang: 33             # '!'

# scratch array for the typed name
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
    Example("hello_world", ExampleKind.ASM, """# example: hello world
# description: Writes "Hello world!" to the io cells as ASCII codes.
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

# "Hello world!" as ASCII codes
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
    Example("numbers", ExampleKind.ASM, """# example: numbers
# description: Writes 0..14 to io0..io14.
# pytoy: output=decimal

# The io cells are memory-mapped, so a plain store writes them.
# The View > I/O format menu shows the same bytes as decimal, binary, hex or ASCII.
# numbers_hex.toys and numbers_ascii.toys are this program opened in those formats.

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

# data
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
    Example("numbers_ascii", ExampleKind.ASM, """# example: numbers in ASCII
# description: Writes 0..14 to io0..io14, displayed as ASCII.
# pytoy: output=ascii

# Same bytes as numbers.toys; codes 0..14 are control characters, so no cell shows a visible character.
# Switch the View > I/O format menu to read them.

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

# data
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
    Example("numbers_hex", ExampleKind.ASM, """# example: numbers in hex
# description: Writes 0..14 to io0..io14, displayed in hex.
# pytoy: output=hex

# Same bytes as numbers.toys; they show as 00..09, then 0A..0E.

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

# data
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
    Example("pin_check", ExampleKind.ASM, """# example: PIN check
# description: Checks the PIN in io0..io3 against 1337 and writes "succ" or "fail".
# pytoy: output=ascii

# Input: type the digits into io0..io3 as ASCII, then set ready (mem[255]) to non-zero.
# The first wrong digit exits early, so step counts leak the PIN digit by digit
# (4 * 10 tries instead of 10^4). See pin_check_hardened.toys and doc/design/pin-security.md.

        # busy-poll until ready != 0
wait:   load  ready
        ifzero wait

        # compare io0..io3 against the PIN, one digit at a time
        load  io0
        sub   pin0
        ifzero ok0         # matched -> check next digit
        goto  fail
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

        # success: write "succ"
ok3:    load  s_s
        store io0
        load  s_u
        store io1
        load  s_c1
        store io2
        load  s_c2
        store io3
        load  answer       # reveal the secret
        stop

        # failure: write "fail"
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

# data
zero:   0
answer: 42             # the secret behind the PIN

# PIN "1337" as ASCII codes
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
    Example("pin_check_hardened", ExampleKind.ASM, """# example: constant-time PIN check
# description: Checks the PIN in io0..io3 against 1337 with no per-digit branch.
# pytoy: output=ascii

# Input: type the digits into io0..io3 as ASCII, then set ready (mem[255]) to non-zero.
# Every digit is always compared, so between polling and the final ifzero all runs take the same steps.
# That one ifzero is the single target for fault injection. See doc/design/pin-security.md.

        # busy-poll until ready != 0
wait:   load  ready
        ifzero wait

        # constant-time compare: diff = or over (io[k] xor pin[k])
        load  io0
        xor   pin0         # 0 exactly when digit 0 matches
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

        # the only conditional: diff == 0 exactly when all four digits match
        load  diff
        ifzero succ
        goto  fail

        # success: write "succ"
succ:   load  s_s
        store io0
        load  s_u
        store io1
        load  s_c1
        store io2
        load  s_c2
        store io3
        load  answer       # reveal the secret
        stop

        # failure: write "fail"
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

# data
zero:   0
answer: 42             # the secret behind the PIN
diff:   0              # accumulated digit differences

# PIN "1337" as ASCII codes
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
    Example("poll_demo", ExampleKind.ASM, """# example: busy-poll input
# description: Waits until ready is non-zero, then copies io0 to io1.
# pytoy: output=decimal

# The Toy CPU has no interrupts and no blocking read, so waiting for input means polling.
# Input: type a value into io0, then set ready (mem[255]) to non-zero.

wait:   load  ready
        ifzero wait        # spin while 0
        load  io0
        store io1          # only to show the byte moved
        load  zero
        store ready        # so a next round could start
        stop

zero:   0
""", subfolder = "04-io"),
    Example("sort_array_io", ExampleKind.ASM, """# example: sort numbers from input
# description: Reads up to 15 numbers and writes them back sorted. More than 15 overflow.
# pytoy: output=decimal

# Input: type the values into io0, io1, ..., then set ready (mem[255]) to their count.
# The most significant bit (MSB) of b - a acts as its sign: set means b < a.
# Valid for values 0..127.

        # busy-poll until ready != 0, then n = ready
wait:   load  ready
        ifzero wait
        load  ready
        store n              # number of values

        # copy io0..io[n-1] into arr[]
        load  iobase
        store rdarg          # read pointer over the io cells
        load  arrptr
        store wrarg          # write pointer over arr[]
        load  n
        store cnt            # values left to copy

cploop: load  cnt
        ifzero cpdone

        20                   # load opcode
rdarg:  240                  # <- address of io[i], patched each pass
        21                   # store opcode
wrarg:  0                    # <- address of arr[i], patched each pass

        load  rdarg
        add   one
        store rdarg          # next io cell
        load  wrarg
        add   one
        store wrarg          # next element
        load  cnt
        sub   one
        store cnt            # cnt--
        goto  cploop

cpdone:

        # bubble sort arr[0..n-1] ascending
        # each pass moves the largest left to the end, so the next compares one pair less
        load  n
        sub   one
        store npass          # n-1 outer passes

outer:  load  npass
        ifzero sorted

        load  arrptr
        store jptr           # reset j to arr[0]

        load  npass
        store ninner         # npass compares this pass

inner:  load  ninner
        ifzero next_pass

        # patch the four indirect instructions from jptr (arr[j]) and jptr+1 (arr[j+1])
        load  jptr
        store ldj_arg
        store stj_arg
        add   one
        store ldj1_arg
        store stj1_arg

        20                   # load opcode
ldj_arg:  0                  # <- address of arr[j], patched each pass
        store a

        20                   # load opcode
ldj1_arg: 0                  # <- address of arr[j+1], patched each pass
        store b

        sub   a              # acc = b - a
        and   mask           # isolate the MSB
        ifzero no_swap       # MSB clear -> b >= a -> no swap

        # swap: arr[j] = b; arr[j+1] = a
        load  b
        21                   # store opcode
stj_arg:  0                  # <- address of arr[j], patched each pass
        load  a
        21                   # store opcode
stj1_arg: 0                  # <- address of arr[j+1], patched each pass

no_swap:
        load  jptr
        add   one
        store jptr           # j++

        load  ninner
        sub   one
        store ninner         # ninner--
        goto  inner

next_pass:
        load  npass
        sub   one
        store npass          # npass--
        goto  outer

        # write arr[0..n-1] back to io0..
sorted:
        load  arrptr
        store rptr           # read pointer over arr[]
        load  outbase
        store optr           # write pointer over the io cells
        load  n
        store left           # values left to write

wloop:  load  left
        ifzero done

        load  rptr           # patch the load/store addresses
        store rd_arg
        load  optr
        store ot_arg

        20                   # load opcode
rd_arg:  0                   # <- address of arr[i], patched each pass
        21                   # store opcode
ot_arg:  240                 # <- address of io[i], patched each pass

        load  rptr
        add   one
        store rptr           # next element
        load  optr
        add   one
        store optr           # next io cell
        load  left
        sub   one
        store left           # left--
        goto  wloop

done:   load  arr            # smallest value
        stop

# data
one:      1
mask:     128          # 10000000
iobase:   240          # io0
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
arrptr:   arr          # address of arr[0]

# scratch array for up to 15 values
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
#                                # example: iterative Fibonacci
#                                # description: Computes fib(7) in a loop.
#                                # steps: 81
#                                # result: 13
#                                
#                                # a = b - a recovers the old b, so no temporary variable is needed.
#                                # fib(14) = 377 does not fit in a byte, so n > 13 overflows.
#                                # Recursive version: fibonacci_rec.toys.
#                                
     0   00010100  00011011              load  n         # loop counter
     2   00011001  00011000      loop:   ifzero end      # n == 0
#                                
     4   00010100  00011100              load  a
     6   00010110  00011101              add   b
     8   00010101  00011101              store b         # b = a + b
#                                
    10   00010100  00011101              load  b
    12   00010111  00011100              sub   a         # acc = (a + b) - a = old b
    14   00010101  00011100              store a
#                                
    16   00010100  00011011              load  n
    18   00010111  00011110              sub   one
    20   00010101  00011011              store n         # n--
#                                
    22   00011000  00000010              goto  loop
#                                
    24   00010100  00011100      end:    load  a         # fib(n)
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
#   io0             = 240  (11110000)
#   io1             = 241  (11110001)
#   io2             = 242  (11110010)
#   io3             = 243  (11110011)
#   io4             = 244  (11110100)
#   io5             = 245  (11110101)
#   io6             = 246  (11110110)
#   io7             = 247  (11110111)
#   io8             = 248  (11111000)
#   io9             = 249  (11111001)
#   io10            = 250  (11111010)
#   io11            = 251  (11111011)
#   io12            = 252  (11111100)
#   io13            = 253  (11111101)
#   io14            = 254  (11111110)
#   ready           = 255  (11111111)
"""),
    Example("multiply_repeated (bytecode)", ExampleKind.TOYO, """# Toy CPU – compiled listing
#
# ADDR  BYTES                   SOURCE
# ────────────────────────────────────────────────────────
#                                # example: multiply by repeated addition
#                                # description: Computes a * b by adding a to res, b times.
#                                # steps: 60
#                                # result: 42
#                                
#                                # 255 * 255 would take 255 passes, multiply_doubling.toys at most 8.
#                                
     0   00010100  00010110              load  b
     2   00011001  00010010      loop:   ifzero done       # b == 0
     4   00010100  00010111              load  res
     6   00010110  00010101              add   a
     8   00010101  00010111              store res         # res += a
    10   00010100  00010110              load  b
    12   00010111  00011000              sub   one
    14   00010101  00010110              store b           # b--
    16   00011000  00000010              goto  loop
    18   00010100  00010111      done:   load  res
    20   00000000                        stop
#                                
    21   00000110                a:      6
    22   00000111                b:      7
    23   00000000                res:    0
    24   00000001                one:    1

# SYMBOLS
#   loop            =   2  (00000010)
#   done            =  18  (00010010)
#   a               =  21  (00010101)
#   b               =  22  (00010110)
#   res             =  23  (00010111)
#   one             =  24  (00011000)
#   io0             = 240  (11110000)
#   io1             = 241  (11110001)
#   io2             = 242  (11110010)
#   io3             = 243  (11110011)
#   io4             = 244  (11110100)
#   io5             = 245  (11110101)
#   io6             = 246  (11110110)
#   io7             = 247  (11110111)
#   io8             = 248  (11111000)
#   io9             = 249  (11111001)
#   io10            = 250  (11111010)
#   io11            = 251  (11111011)
#   io12            = 252  (11111100)
#   io13            = 253  (11111101)
#   io14            = 254  (11111110)
#   ready           = 255  (11111111)
"""),
    Example("sum3 (bytecode)", ExampleKind.TOYO, """# Toy CPU – compiled listing
#
# ADDR  BYTES                   SOURCE
# ────────────────────────────────────────────────────────
#                                # example: sum of three values
#                                # description: Adds a, b and c.
#                                # steps: 4
#                                # result: 8
#                                
#                                # Whole array: sum_array.toys.
#                                
     0   00010100  00000111              load  a
     2   00010110  00001000              add   b
     4   00010110  00001001              add   c
     6   00000000                        stop
#                                
#                                # data
     7   00000011                a:      3
     8   00000001                b:      1
     9   00000100                c:      4

# SYMBOLS
#   a               =   7  (00000111)
#   b               =   8  (00001000)
#   c               =   9  (00001001)
#   io0             = 240  (11110000)
#   io1             = 241  (11110001)
#   io2             = 242  (11110010)
#   io3             = 243  (11110011)
#   io4             = 244  (11110100)
#   io5             = 245  (11110101)
#   io6             = 246  (11110110)
#   io7             = 247  (11110111)
#   io8             = 248  (11111000)
#   io9             = 249  (11111001)
#   io10            = 250  (11111010)
#   io11            = 251  (11111011)
#   io12            = 252  (11111100)
#   io13            = 253  (11111101)
#   io14            = 254  (11111110)
#   ready           = 255  (11111111)
"""),
    Example("bit_operators.toyc", ExampleKind.C, """// example: bitwise operators and shifts
// description: Applies &, ^, >> and <<, which map to and, xor, right and left.
// steps: toycc 27, minicc 30
// result: 36

int main(void) {
    int x = 0xF0;              // 11110000
    int y = 0x3C;              // 00111100
    int a = x & y;             // 00110000
    int c = x ^ y;             // 11001100
    a = a >> 2;                // 00001100
    a = a << 1;                // 00011000
    return a + (c & 0x0F);     // 24 + 12
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("if_then_else.toyc", ExampleKind.C, """// example: if/else
// description: Subtracts the smaller of a and b from the larger with if/else.
// steps: toycc 15, minicc 24
// result: 5

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
    Example("msb_shift_vs_mask.toyc", ExampleKind.C, """// example: most significant bit (MSB), shift vs mask
// description: Extracts the MSB of x by shifting and by masking, then compares the two.
// steps: toycc 24, minicc 27
// result: 0

// x >> 7 takes seven right instructions, x & 128 takes one and.
// The shorter form matters because code and data share the same 256 bytes.

int main(void) {
    int x = 200;              // 11001000, MSB set

    int msb1 = x >> 7;

    int msb2 = 0;
    if (x & 128) msb2 = 1;

    return msb1 ^ msb2;       // 0 -> both agree
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("multiply_doubling.toyc", ExampleKind.C, """// example: multiply by double-and-add
// description: Computes a * b by adding shifted copies of a.
// steps: toycc 76, minicc 98
// result: 42

// One pass per bit of b: add a if the least significant bit (LSB) of b is set, then
// double a and halve b. An 8-bit b needs at most 8 passes, repeated addition up to 255.
// toycc compiles * to this method.

int main(void) {
    int a = 7;
    int b = 6;         // 110 in binary, so 7 * 6 = 7 * 4 + 7 * 2
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
    Example("multiply_operator.toyc", ExampleKind.C, """// example: multiply with the * operator
// description: Computes a * b with the * operator.
// steps: toycc 56, minicc 69
// result: 42

// The Toy CPU has no multiply instruction. toycc compiles * to double-and-add (one pass
// per bit of b), minicc to repeated addition (one pass per unit of b).
// By hand: multiply_doubling.toyc, multiply_repeated.toyc.

int main(void) {
    int a = 7;
    int b = 6;
    return a * b;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("multiply_repeated.toyc", ExampleKind.C, """// example: multiply by repeated addition
// description: Computes a * b by adding a to res, b times.
// steps: toycc 79, minicc 145
// result: 42

// 255 * 255 would take 255 passes, multiply_doubling.toyc at most 8.
// minicc compiles * to this method.

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
    Example("popcount.toyc", ExampleKind.C, """// example: popcount
// description: Counts the set bits of 37 (100101 in binary).
// steps: toycc 120, minicc 163
// result: 3

// The Toy CPU has no %, but x & 1 equals x % 2: the least significant bit (LSB).

int popcount(int x) {
    int count = 0;
    while (x != 0) {
        count = count + (x & 1);   // add the LSB
        x = x >> 1;                // drop it
    }
    return count;
}

int main(void) {
    return popcount(37);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("times7_operator.toyc", ExampleKind.C, """// example: times 7 with the * operator
// description: Computes 7 * a with the * operator.
// steps: toycc 51, minicc 49
// result: 28

// toycc compiles * to double-and-add, minicc to repeated addition.

int main(void) {
    int a = 4;
    return 7 * a;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "02-extended"),
    Example("bigint_add.toyc", ExampleKind.C, """// example: bigint add
// description: Adds two 32-bit numbers stored as four bytes each, lowest byte first.
// steps: toycc 99
// result: 0

// 123456789 + 314159265 = 437616054, whose bytes are [182, 125, 21, 26].
// Carry out of a+b is the most significant bit (MSB) of (a & b) | ((a ^ b) & ~sum).
// Returns 0 if all four bytes match: xor finds the differences, or collects them.

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
    Example("count_iter.toyc", ExampleKind.C, """// example: iterative count
// description: Counts from 1 to 10 in a loop.
// steps: toycc 91, minicc 169
// result: 10

// Recursive version: count_rec.toyc.

int main(void) {
    int x = 1;
    while (x < 10) {
        x = x + 1;
    }
    return x;
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("count_rec.toyc", ExampleKind.C, """// example: recursive count
// description: Counts to 10 recursively with f(n) = f(n - 1) + 1 and f(0) = 0.
// steps: toycc 421
// result: 10

// toycc compiles recursion with a self-modifying-code stack (see the manual).
// Iterative version: count_iter.toyc.

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
    Example("fibonacci_array.toyc", ExampleKind.C, """// example: Fibonacci numbers in an array
// description: Fills an array with the first 10 Fibonacci numbers and returns the last.
// steps: toycc 278, minicc 392
// result: 34

// The array ends up as 0 1 1 2 3 5 8 13 21 34.

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
    Example("fibonacci_iter.toyc", ExampleKind.C, """// example: iterative Fibonacci
// description: Computes fib(7) in a loop.
// steps: toycc 117, minicc 215
// result: 13

// Sequence from fib(0): 0 1 1 2 3 5 8 13.
// fib(14) = 377 does not fit in a byte, so n > 13 overflows.
// Recursive version: fibonacci_rec.toyc.

int fibonacci_iter(int n) {
    int a = 0;
    int b = 1;
    while (n != 0) {
        b = a + b;
        a = b - a;   // a = old b
        n = n - 1;
    }
    return a;
}

int main(void) {
    return fibonacci_iter(7);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("fibonacci_rec.toyc", ExampleKind.C, """// example: recursive Fibonacci
// description: Computes fib(7) recursively on a self-modifying-code stack.
// steps: toycc 2121
// result: 13

// The Toy CPU has no call/return and no stack: toycc gives each function fixed global slots and
// saves the caller's live slots around each call on a self-modifying-code stack, growing down
// from address 239 just below the io cells (see the manual). Iterative version: fibonacci_iter.toyc.

int fibonacci_rec(int n) {
    if (n < 2) {
        return n;
    }
    return fibonacci_rec(n - 1) + fibonacci_rec(n - 2);
}

int main(void) {
    return fibonacci_rec(7);
}
""", compilers = listOf("toycc"), subfolder = "03-programs"),
    Example("gcd_iter.toyc", ExampleKind.C, """// example: iterative GCD by subtraction
// description: Computes gcd(48, 36) in a loop with Euclid's subtraction algorithm.
// steps: toycc 69, minicc 115
// result: 12

// Trace: (48, 36) -> (12, 36) -> (12, 24) -> (12, 12), then a == b.
// Recursive version: gcd_rec.toyc.

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
    Example("gcd_rec.toyc", ExampleKind.C, """// example: recursive GCD by subtraction
// description: Computes gcd(48, 36) recursively with Euclid's subtraction algorithm.
// steps: toycc 175
// result: 12

// Trace: (48, 36) -> (12, 36) -> (12, 24) -> (12, 12), then a == b.
// toycc compiles recursion with a self-modifying-code stack (see the manual).
// Iterative version: gcd_iter.toyc.

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
    Example("max3.toyc", ExampleKind.C, """// example: largest of three values
// description: Finds the largest of a, b and c.
// steps: toycc 39, minicc 52
// result: 4

// Whole array: max_array.toyc.

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
    Example("max_array.toyc", ExampleKind.C, """// example: largest value in an array
// description: Finds the largest value in an array with a loop.
// steps: toycc 222, minicc 332
// result: 9

// Three values: max3.toyc.
// a[i] compiles to self-modifying code; by hand: examples/asm/03-programs/max_array.toys.

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
    Example("sort3.toyc", ExampleKind.C, """// example: sort three values
// description: Sorts a, b and c; the result is the smallest.
// steps: toycc 47, minicc 64
// result: 1

// Same idea as bubble sort, on three named variables instead of an array.
// Whole array: sort_array_inline.toyc.

int sort3(int a, int b, int c) {
    int t;
    if (a > b) { t = a; a = b; b = t; }   // a <= b
    if (b > c) { t = b; b = c; c = t; }   // b <= c
    if (a > b) { t = a; a = b; b = t; }   // a <= b <= c
    return a;
}

int main(void) {
    return sort3(3, 1, 4);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("sort_array_fn.toyc", ExampleKind.C, """// example: bubble sort of an array in a function
// description: Sorts an array in place with bubble sort in a function; the result is the smallest.
// steps: toycc 2116
// result: 1

// The array decays to a pointer: bubblesort() gets main's base address and sorts in place
// through a[i] == *(a + i), reached with self-modifying code.
// Sorted: 1 1 2 3 3 4 5 5 6 9. Inline version: sort_array_inline.toyc.

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
    Example("sort_array_inline.toyc", ExampleKind.C, """// example: bubble sort of an array
// description: Sorts an array in place with bubble sort; the result is the smallest.
// steps: toycc 2097
// result: 1

// a[i] compiles to self-modifying code; by hand: examples/asm/03-programs/sort_array_inline.toys.
// Sorted: 1 1 2 3 3 4 5 5 6 9. Function version: sort_array_fn.toyc.

int main(void) {
    int a[10] = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3};
    int n = 10;
    int i;
    int j;
    int t;
    i = 0;
    while (i < n) {
        j = 0;
        while (j < n - 1 - i) {   // the last i are already in place
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
    Example("sum3.toyc", ExampleKind.C, """// example: sum of three values
// description: Adds a, b and c.
// steps: toycc 28, minicc 33
// result: 8

// Whole array: sum_array.toyc.

int sum3(int a, int b, int c) {
    return a + b + c;
}

int main(void) {
    return sum3(3, 1, 4);
}
""", compilers = listOf("minicc", "toycc"), subfolder = "03-programs"),
    Example("sum_array.toyc", ExampleKind.C, """// example: sum of an array
// description: Sums an array with a loop.
// steps: toycc 202, minicc 288
// result: 39

// Three values: sum3.toyc.
// a[i] compiles to self-modifying code; by hand: examples/asm/03-programs/sum_array.toys.

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
    Example("sum_countdown.toyc", ExampleKind.C, """// example: countdown sum
// description: Sums 5 + 4 + 3 + 2 + 1 with += and -=.
// steps: toycc 66
// result: 15

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
    Example("sum_of_squares.toyc", ExampleKind.C, """// example: sum of squares
// description: Computes 3 * 3 + 4 * 4 with a helper function that calls another.
// steps: toycc 143, minicc 140
// result: 25

// The Toy CPU has no call/return, so toycc gives each non-recursive function fixed global slots
// for parameters and return value, emits one copy of its body, and jumps back to the right
// call site with a marker (see the manual).

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
    Example("greet_name.toyc", ExampleKind.C, """// example: greet by name
// description: Reads a name and writes "Hallo NAME" back. Names over 9 characters overflow.
// pytoy: output=ascii

// Input: type one character per cell into io0, io1, ..., then set ready (mem[255]) to the count.

int main(void) {
    int data[16];
    data[0] = 'H';
    data[1] = 'a';
    data[2] = 'l';
    data[3] = 'l';
    data[4] = 'o';
    data[5] = ' ';
    int k = read(data + 6);   // name goes into data[6..], k = its length
    write(data, k + 6);       // "Hallo " + the name, to io0..
    return 0;
}
""", compilers = listOf("toycc"), subfolder = "04-io"),
    Example("hello_world.toyc", ExampleKind.C, """// example: hello world
// description: Writes "Hello world!" to the io cells as ASCII codes.
// pytoy: output=ascii

int main(void) {
    // A constant initializer list becomes initial data bytes at compile time,
    // far smaller than twelve runtime stores.
    int data[12] = {'H', 'e', 'l', 'l', 'o', ' ',
                    'w', 'o', 'r', 'l', 'd', '!'};
    write(data, 12);
    return 0;
}
""", compilers = listOf("toycc"), subfolder = "04-io"),
    Example("pin_check.toyc", ExampleKind.C, """// example: PIN check
// description: Checks the PIN in io0..io3 against 1337 and writes "succ" or "fail".
// pytoy: output=ascii

// Input: type the digits into io0..io3 as ASCII, then set ready (mem[255]) to 4.
// The first wrong digit exits early, so step counts leak the PIN digit by digit
// (4 * 10 tries instead of 10^4). See pin_check_hardened.toyc and doc/design/pin-security.md.

int main(void) {
    int data[4];
    int succ[4] = {'s', 'u', 'c', 'c'};
    int fail[4] = {'f', 'a', 'i', 'l'};

    read(data);

    // Nested ifs keep it inside the 240 usable bytes; one && chain does not fit.
    if (data[0] == '1') {
        if (data[1] == '3') {
            if (data[2] == '3') {
                if (data[3] == '7') {
                    write(succ, 4);
                    return 42;   // the secret
                }
            }
        }
    }
    write(fail, 4);
    return 0;
}
""", compilers = listOf("toycc"), subfolder = "04-io"),
    Example("pin_check_hardened.toyc", ExampleKind.C, """// example: constant-time PIN check
// description: Checks the PIN in io0..io3 against 1337 with no per-digit branch.
// pytoy: output=ascii

// Input: type the digits into io0..io3 as ASCII, then set ready (mem[255]) to 4.
// Every digit is always compared, so up to the final if all runs take the same steps.
// That one if is the single target for fault injection. See doc/design/pin-security.md.

int main(void) {
    int data[4];
    int succ[4] = {'s', 'u', 'c', 'c'};
    int fail[4] = {'f', 'a', 'i', 'l'};
    int diff;

    read(data);

    // Each (digit ^ expected) is 0 exactly when that digit matches, so the or is 0 exactly
    // when all four match. Unrolled by hand (no pin[] array, no for) to fit 240 bytes.
    diff = (data[0] ^ '1') | (data[1] ^ '3') | (data[2] ^ '3') | (data[3] ^ '7');

    if (diff == 0) {
        write(succ, 4);
        return 42;   // the secret
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
