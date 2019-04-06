package net.torvald.terranvm.assets

object TestMemsize {
    operator fun invoke() = """
.code;


# setup out-of-mem interrupt
loadwordilo r2, 02h;
loadwordimem r13, r2; # store old interrupt vector


# iterate though the memory
mov r1, r0;


    """.trimIndent()

}