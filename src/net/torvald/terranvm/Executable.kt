package net.torvald.terranvm

import net.torvald.terranvm.runtime.*
import net.torvald.terranvm.runtime.compiler.simplec.SimpleC

/**
 * Created by minjaesong on 2017-05-25.
 */
object Executable {

    val testProgram = """
        float x;
        float y;
        float z;

        x = 255;
        y = 69;
        z = x - y;
    """.trimIndent()


    fun main() {
        val program = SimpleC.buildTree(SimpleC.tokenise(SimpleC.preprocess(testProgram)))
        val notatedProgram = SimpleC.treeToProperNotation(program)
        val programInIR = SimpleC.notationToIR(notatedProgram)
        val programInNewIR = SimpleC.preprocessIR(programInIR)
        val programASM = SimpleC.IRtoASM(programInNewIR)
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
    private fun Byte.toUint() = java.lang.Byte.toUnsignedInt(this)

}

fun main(args: Array<String>) {
    Executable.main()
}
