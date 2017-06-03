package net.torvald.tbasic.runtime

/**
 * Created by minjaesong on 2017-06-03.
 */
interface VMPeripheralHardware {
    /**
     * @param arg can be a number or a pointer
     */
    fun call(arg: Int)
}