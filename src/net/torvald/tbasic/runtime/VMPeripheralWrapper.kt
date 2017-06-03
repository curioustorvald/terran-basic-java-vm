package net.torvald.tbasic.runtime

/**
 * Peripheral wrapper that provides memory access to the peripheral
 *
 * Created by minjaesong on 2017-05-27.
 */
abstract class VMPeripheralWrapper(val parent: VM, memSize: Int, var suppressWarnings: Boolean = false): VMPeripheralHardware {

    internal val memory = ByteArray(memSize)

    init {
        if (memSize > 1.MB()) {
            warn("Peripheral memory size might be too big — recommended max is 1 MBytes")
        }
    }

    private fun warn(any: Any?) { if (!suppressWarnings) println("[TBASRT] WARNING: $any") }


}