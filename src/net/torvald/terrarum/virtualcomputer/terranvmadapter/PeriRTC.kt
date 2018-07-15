package net.torvald.terrarum.virtualcomputer.terranvmadapter

import net.torvald.terranvm.runtime.TerranVM
import net.torvald.terranvm.runtime.VMPeripheralHardware
import net.torvald.terranvm.runtime.VMPeripheralWrapper
import net.torvald.terranvm.runtime.to8HexString
import java.time.Instant
import java.util.Calendar



/**
 * Returns current UNIX time to r1
 *
 * Created by minjaesong on 2018-05-11.
 */
open class PeriRTC(val vm: TerranVM) : VMPeripheralWrapper(0) {

    /**
     * Writes current epoch second to r1 as an int
     * @param arg
     * 0: epoch second lower 32 bits
     *
     * 1: epoch second higher 32 bits
     *
     * 2: seconds
     *
     * 3: minutes
     *
     * 4: hours (24h)
     *
     * 5: day of the month
     *
     * 6: month (1 - January)
     *
     * 7: weekday (0 - Monday, 6 - Sunday, 7 - World-day for The World Calendar, if the base system supports it)
     *
     * 8: year
     *
     * 16: yearly week
     *
     * 17: yearly day
     */
    override fun call(arg: Int) {
        val time = Instant.now()
        val timestamp = time.epochSecond
        val cal = Calendar.getInstance()

        // to accomodate ingame clock, you'll need to override or modify this code
        vm.r1 = when (arg) {
            0 -> timestamp.and(0xFFFFFFFF).toInt()
            1 -> timestamp.ushr(32).and(0xFFFFFFFF).toInt()
            2 -> cal.get(Calendar.SECOND)
            3 -> cal.get(Calendar.MINUTE)
            4 -> cal.get(Calendar.HOUR_OF_DAY)
            5 -> cal.get(Calendar.DAY_OF_MONTH)
            6 -> cal.get(Calendar.MONTH) + 1
            7 -> Math.floorMod(cal.get(Calendar.DAY_OF_WEEK).minus(Calendar.MONDAY), 7)
            8 -> cal.get(Calendar.YEAR)

            16 -> cal.get(Calendar.WEEK_OF_YEAR)
            17 -> cal.get(Calendar.DAY_OF_YEAR)

            else -> throw IllegalArgumentException("Unknown argument: ${arg.to8HexString()}")
        }
    }


    override fun inquireBootstrapper(): ByteArray? {
        return null
    }
}