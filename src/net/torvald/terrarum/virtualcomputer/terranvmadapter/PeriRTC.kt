package net.torvald.terrarum.virtualcomputer.terranvmadapter

import net.torvald.terranvm.runtime.TerranVM
import net.torvald.terranvm.runtime.VMPeripheralHardware
import net.torvald.terranvm.runtime.VMPeripheralWrapper
import java.time.Instant

/**
 * Returns current UNIX time to r1
 *
 * Created by minjaesong on 2018-05-11.
 */
open class PeriRTC(val vm: TerranVM) : VMPeripheralWrapper(0) {

    /**
     * Writes current epoch second to r1
     * @param arg zero for lower 32 bits, non-zero for upper.
     */
    override fun call(arg: Int) {
        val timestamp = Instant.now().epochSecond

        vm.r1 = if (arg == 0) timestamp.and(0xFFFFFFFF).toInt()
        else timestamp.ushr(32).and(0xFFFFFFFF).toInt()
    }
}