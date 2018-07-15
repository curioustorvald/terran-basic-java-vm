package net.torvald.terranvm.runtime

/**
 * Created by minjaesong on 2017-06-03.
 */
interface VMPeripheralHardware {
    /**
     * @param arg can be a number or a pointer
     */
    fun call(arg: Int)

    /**
     * Returns bootstrapper program in raw machine code, `null` if not available or the device is not bootable.
     */
    fun inquireBootstrapper(): ByteArray?
}