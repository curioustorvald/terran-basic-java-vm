package net.torvald.terranvm.runtime

import net.torvald.terranvm.toReadableBin
import net.torvald.terrarum.virtualcomputer.terranvmadapter.PeriMDA

/**
 * Created by minjaesong on 2018-04-14.
 */
class TerranVMReferenceBIOS(val vm: TerranVM) : VMPeripheralHardware {
    override fun call(arg: Int) {

        val upper8Bits = arg.ushr(24)
        val midHigh8Bits = arg.and(0xFF0000).ushr(16)
        val midLow8Bits = arg.and(0xFF00).ushr(8)
        val lower8Bits = arg.and(0xFF)

        when (upper8Bits) {
            // various
            0 -> when (midLow8Bits) {
                // boot from device
                0 -> TODO("Boot from device")

                // getchar from keyboard
                1 -> {
                    (vm.peripherals[1] as VMPeripheralWrapper).call(lower8Bits and 0b111)
                }

                // printchar immediate
                2 -> {
                    (vm.peripherals[3] as VMPeripheralWrapper).call(0x0800 or lower8Bits)
                }

                // printchar from register
                3 -> {
                    val data = vm.readregInt(arg.and(0b111) + 1)
                    (vm.peripherals[3] as VMPeripheralWrapper).call(0x0800 or data)
                }
            }

            // file operations
            1 -> {

            }

            // compatible BIOS private use area -- ReferenceBIOS will ignore it
            in 128..255 -> {

            }
            else -> {
                throw UnsupportedOperationException("Unsupported op: ${arg.toReadableBin().replace('_', 0.toChar())}")
            }
        }
    }
}


/* LOADER commands

CASE INSENSITIVE

0..9, A..F: hex literal

L: move program counter
K: peek what byte is in the current address (example output: "0003DC : 3F")
P: put a byte to memory and increment program counter
R: run

SYNTAX

000100L09P3EP45P00P

As soon as you hit a key, the command is entered, no RETURN key required.

 */