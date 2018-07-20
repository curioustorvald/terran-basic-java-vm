package net.torvald.terranvm.runtime

import net.torvald.terranvm.assets.Loader
import net.torvald.terranvm.toBytesBin
import net.torvald.terranvm.toReadableBin
import net.torvald.terrarum.virtualcomputer.terranvmadapter.PeriMDA
import java.io.IOException

/**
 * Created by minjaesong on 2018-04-14.
 */
class TerranVMReferenceBIOS(val vm: TerranVM) : VMPeripheralHardware {

    override fun inquireBootstrapper(): ByteArray? {
        TODO("Boot from ROM BASIC")
    }

    /**
     * Puts `prg` into the memory and moves program counter to its starting position.
     */
    private fun eatShitAndPoop(prg: ByteArray) {
        val ptr = vm.malloc(prg.size)
        System.arraycopy(prg, 0, vm.memory, ptr.memAddr, prg.size)
        vm.pc = ptr.memAddr
    }

    private fun Int.KB() = this shl 10

    override fun call(arg: Int) {

        val upper8Bits = arg.ushr(24)
        val midHigh8Bits = arg.and(0xFF0000).ushr(16)
        val midLow8Bits = arg.and(0xFF00).ushr(8)
        val lower8Bits = arg.and(0xFF)

        //println("BIOS called with arg: ${arg.toBytesBin()}")

        when (upper8Bits) {
            // various
            0 -> when (midLow8Bits) {
                // boot from device
                0 -> {
                    when (lower8Bits) {
                        // load BIOS Setup Utility
                        TerranVM.IRQ_BIOS -> eatShitAndPoop(vm.parametreRAM.sliceArray(4.KB()..6.KB()))

                        TerranVM.IRQ_KEYBOARD -> {
                            val asm = Assembler(vm)
                            val prg = asm(Loader.invoke())
                            eatShitAndPoop(prg.bytes)
                        }

                        0 -> {
                            val bootOrder = vm.parametreRAM.sliceArray(0..3)

                            // try to boot using Boot Order
                            for (ord in 0..3) {
                                try {
                                    if (bootOrder[ord] == 0.toByte()) throw IllegalArgumentException()
                                    call(bootOrder[ord].toUint())
                                }
                                catch (e: IllegalArgumentException) {
                                }
                                catch (e: Exception) {
                                    e.printStackTrace()
                                    return
                                }
                            }

                            // boot ROM BASIC
                            try {
                                eatShitAndPoop(vm.parametreRAM.sliceArray(6.KB()..8.KB()))
                            }
                            catch (e: Exception) {
                                e.printStackTrace()
                                throw IOException("No bootable medium")
                            }
                        }

                        else -> {
                            val dev = vm.peripherals[lower8Bits]
                            if (dev == null) throw IllegalArgumentException("No such device: $lower8Bits")
                            else {
                                val prg = dev.inquireBootstrapper()
                                if (prg == null) throw IllegalArgumentException("Medium not bootable: $lower8Bits")
                                else eatShitAndPoop(prg)
                            }
                        }
                    }
                }

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
                    val data = vm.readregInt(arg.and(0b111))
                    (vm.peripherals[3] as VMPeripheralWrapper).call(0x0800 or data)
                }

                // MEMCPY
                in 0b010_0000_0..0b010_1111_1 -> {
                    val rParams = midLow8Bits.ushr(1).and(15)
                    val rFromAddr = lower8Bits.ushr(4).and(15)
                    val rToAddr = lower8Bits.and(15)

                    val params = vm.readregInt(rParams)
                    val src = params.and(0xFF)
                    val dest = params.ushr(8).and(0xFF)
                    val len = params.ushr(16).and(0xFFFF)

                    val srcMem = if (src == 0) vm.memory else vm.peripherals[src]!!.memory
                    val destMem = if (dest == 0) vm.memory else vm.peripherals[dest]!!.memory
                    val fromAddr = vm.readregInt(rFromAddr)
                    val toAddr = vm.readregInt(rToAddr)

                    System.arraycopy(srcMem, fromAddr, destMem, toAddr, len)
                }
            }

            // file operations
            1 -> {
                val fileOp = midHigh8Bits.ushr(3)
                val targetRegister = midHigh8Bits.and(0b111)
                val argument = midLow8Bits
                val device = lower8Bits

                when (fileOp) {

                }
            }

            // print string
            2 -> {
                val dbAddr = arg.shl(2).and(0xFFFFFF)
                var strLen = 0 // length INCLUSIVE to the null terminator
                var _readByte: Byte
                do {
                    _readByte = vm.memory[dbAddr + strLen]
                    strLen++
                } while (_readByte != 0.toByte())

                val mda = vm.peripherals[3]!! as PeriMDA

                //if (strLen > 1) {
                    mda.printStream.write(vm.memSliceBySize(dbAddr, strLen - 1))
                //}
            }

            // get file by name
            4, 5 -> {
                val rDest = arg.ushr(22).and(0b111)
                val dbAddr = arg.shl(2).and(0xFFFFFF)
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

K: peek what byte is in the current address (example output: "0003DC : 3F")
P: put a byte to memory and increment program counter
R: run
T: move program counter

SYNTAX

000100L09P3EP45P00P

As soon as you hit a key, the command is entered, no RETURN key required.

 */