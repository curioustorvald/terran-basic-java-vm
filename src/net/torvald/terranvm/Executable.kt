package net.torvald.terranvm

import net.torvald.terranvm.runtime.*

/**
 * Created by minjaesong on 2017-05-25.
 */
object Executable {

    val vm = TerranVM(2048, tbasic_remove_string_dupes = true)


    fun main() {

    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
    private fun Byte.toUint() = java.lang.Byte.toUnsignedInt(this)

}

fun main(args: Array<String>) {
    Executable.main()
}
