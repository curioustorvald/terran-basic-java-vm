package net.torvald.terranvm

import net.torvald.terranvm.runtime.*
import net.torvald.terranvm.runtime.compiler.cflat.Cflat

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
        val vm = TerranVM(1024)
        val assembler = Assembler(vm)

        val program = Cflat.buildTree(Cflat.tokenise(Cflat.preprocess(testProgram)))
        val notatedProgram = Cflat.treeToProperNotation(program)
        val programInIR = Cflat.notationToIR(notatedProgram)
        val programInNewIR = Cflat.preprocessIR(programInIR)
        val programASM = Cflat.IRtoASM(programInNewIR)
        val code = assembler(programASM.joinToString("\n"))

        vm.loadProgram(code)



        // test print mem
        for (i in 64..255 step 4) {
            val opcode = vm.memory[i].toUint() or
                    vm.memory[i + 1].toUint().shl(8) or
                    vm.memory[i + 2].toUint().shl(16) or
                    vm.memory[i + 3].toUint().shl(24)

            println("${i.toString().padStart(4, '0')}  ${opcode.toReadableBin()}")
        }



        val vmThread = Thread(vm)
        vmThread.start()
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
    private fun Byte.toUint() = java.lang.Byte.toUnsignedInt(this)

}

fun main(args: Array<String>) {
    Executable.main()
}
