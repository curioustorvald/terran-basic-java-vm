package net.torvald.tbasic

import net.torvald.tbasic.runtime.TBASOpcodeAssembler
import net.torvald.tbasic.runtime.VM
import net.torvald.tbasic.runtime.toCString
import net.torvald.tbasic.runtime.toLittle

/**
 * Created by minjaesong on 2017-05-25.
 */
fun main(args: Array<String>) {
    Executable().main()
}

class Executable {

    val vm = VM(256)

    val rudimentaryHello =
            TBASOpcodes.LOADSTR + 1.toByte() + "Hell\no, w\norld!325432".toCString() +
                    TBASOpcodes.PRINTSTR

    val twoPlusTwo =
            TBASOpcodes.LOADNUM + 2.toByte() + 2.0.toLittle() +
                    TBASOpcodes.LOADNUM + 3.toByte() + 2.0.toLittle() +
                    TBASOpcodes.ADD +
                    TBASOpcodes.PRINTNUM

    val testProgram = TBASOpcodeAssembler("""
; komment
LOADNUM 2, 2.0
LOADNUM 3, 2.0
ADD
PRINTNUM
""")

    fun main() {
        //testProgram.forEach { print("$it ") }

        vm.loadProgram(testProgram)
        //(0..255).forEach { print("${vm.memory[it]} ") }
        vm.execute()
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
}