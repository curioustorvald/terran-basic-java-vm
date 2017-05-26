package net.torvald.tbasic

import net.torvald.tbasic.runtime.VM
import net.torvald.tbasic.runtime.toCString

/**
 * Created by minjaesong on 2017-05-25.
 */
fun main(args: Array<String>) {
    Executable().main()
}

class Executable {

    val vm = VM(256)

    val rudimentaryHello =
            byteArrayOf(TBASOpcodes.opcodesList["LOADSTRING"]!!) + 1.toByte() + "Hell\no, w\norld!325432".toCString() +
            byteArrayOf(TBASOpcodes.opcodesList["PRINTSTR"]!!)

    fun main() {
        vm.loadProgram(rudimentaryHello)
        //(0..255).forEach { print("${vm.memory[it]} ") }
        vm.execute()
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
}