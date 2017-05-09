package net.torvald.tbasic.runtime

import net.torvald.tbasic.*
import java.util.*


typealias Number = Double

/**
 * Takes care of mallocs. Endianness is LITTLE
 *
 * @param memSize Memory size in bytes
 *
 * @throws ArrayIndexOutOfBoundsException whenever
 *
 * Created by minjaesong on 2017-05-09.
 */
class VM(memSize: Int, stackSize: Int = 192, var suppressWarnings: Boolean = false) {

    companion object {
        val charset = Charsets.UTF_8
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
    private fun warn(any: Any?) { if (!suppressWarnings) println("[TBASRT] $any") }


    class Pointer(val parent: VM, var memAddr: Int, var type: PointerType = Pointer.PointerType.BYTE) {
        /*
        NIL     in memory: 0x00
        BOOLEAN in memory: 0x00 if false, 0xFF if true
         */

        enum class PointerType { BYTE, INTERNAL_INT, NUMBER, BOOLEAN, NIL }
        companion object {
            fun sizeOf(type: PointerType) = when(type) {
                PointerType.BYTE    -> 1 // Internal for ByteArray (and thus String)
                PointerType.BOOLEAN -> 1
                PointerType.NUMBER -> 8 // Double
                PointerType.INTERNAL_INT -> 4
                PointerType.NIL     -> 1
            }
        }

        fun size() = sizeOf(type)
        operator fun plusAssign(offset: Int) { memAddr += size() * offset }
        operator fun minusAssign(offset: Int) { memAddr -= size() * offset }
        operator fun plus(offset: Int) = Pointer(parent, memAddr + size() * offset, type)
        operator fun minus(offset: Int) = Pointer(parent, memAddr - size() * offset, type)
        fun inc() { plusAssign(1) }
        fun dec() { minusAssign(1) }

        fun toBoolean() = (type != PointerType.NIL && (type == PointerType.BOOLEAN && readData() as Boolean))

        /**
         * Usage: ```readData() as Number```
         */
        fun readData(): Any = when(type) {
            PointerType.NIL     -> TBASNil()
            PointerType.BOOLEAN -> (parent.memory[memAddr] != 0.toByte())
            PointerType.BYTE    -> parent.memory[memAddr]
            PointerType.INTERNAL_INT -> {
                        parent.memory[memAddr].toInt() or parent.memory[memAddr + 1].toInt().shl(8) or
                        parent.memory[memAddr + 2].toInt().shl(16) or parent.memory[memAddr + 3].toInt().shl(24)
            }
            PointerType.NUMBER -> {
                java.lang.Double.longBitsToDouble(parent.memory[memAddr].toLong() or parent.memory[memAddr + 1].toLong().shl(8) or
                        parent.memory[memAddr + 2].toLong().shl(16) or parent.memory[memAddr + 3].toLong().shl(24) or
                        parent.memory[memAddr + 4].toLong().shl(32) or parent.memory[memAddr + 5].toLong().shl(40) or
                        parent.memory[memAddr + 6].toLong().shl(48) or parent.memory[memAddr + 7].toLong().shl(56)
                        )
            }
        }

        fun write(byte: Byte) { parent.memory[memAddr] = byte }
        fun write(double: Double) {
            val doubleBytes = java.lang.Double.doubleToRawLongBits(double)
            (0..7).forEach { parent.memory[memAddr + it] = doubleBytes.ushr(8 * it).and(0xFF).toByte() }
        }
        fun write(int: Int) {
            (0..3).forEach { parent.memory[memAddr + it] = int.ushr(8 * it).and(0xFF).toByte() }
        }
        fun write(byteArray: ByteArray) {
            if (parent.memory.size < memAddr + byteArray.size) throw ArrayIndexOutOfBoundsException()
            System.arraycopy(byteArray, 0, parent.memory, memAddr, byteArray.size)
        }
        fun write(string: String) {
            write(string.toByteArray(VM.charset))
        }
    }

    private class Stack(vm: VM, val startPointer: Int, val stackSize: Int) {
        private val ptr = Pointer(vm, startPointer, Pointer.PointerType.INTERNAL_INT)

        fun push(value: Int) {
            if (ptr.memAddr - startPointer >= stackSize) throw StackOverflowError()
            ptr.write(value); ptr.inc()
        }
        fun pop(): Int {
            if (ptr.memAddr <= startPointer) throw EmptyStackException()
            val ret = ptr.readData() as Int; ptr.dec(); return ret
        }
        fun peek(): Int = ptr.readData() as Int
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    fun memSlice(from: Int, size: Int) = memory.sliceArray(from..from + size - 1)

    /**
     * This function assumes all pointers are well placed, without gaps
     */
    fun malloc(size: Int): Pointer {
        var mPtr = stackEnd
        varTable.forEach { _, varPtr ->
            mPtr += varPtr.size()
        }
        if (memory.size - mPtr < size) throw OutOfMemoryError()

        return Pointer(this, mPtr)
    }
    fun calloc(size: Int): Pointer {
        var mPtr = stackEnd
        varTable.forEach { _, varPtr ->
            mPtr += varPtr.size()
        }
        if (memory.size - mPtr < size) throw OutOfMemoryError()

        // fill zero
        (0..size - 1).forEach { memory[mPtr + it] = 0.toByte() }

        return Pointer(this, mPtr)
    }
    fun free(variable: TBASValue) {
        // move blocks back
        val moveOffset = variable.sizeOf()
        varTable.filter { it.value.memAddr > variable.pointer.memAddr }.forEach { variable, ptr ->
            System.arraycopy(memory, ptr.memAddr, memory, ptr.memAddr - moveOffset, variable.sizeOf())
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Memory Map
     *
     * 0    256    1024*        memSize
     * | Reg | CStck | User space |
     *
     * Reg: Register
     *  - Function arguments
     *  - etc.
     * CStck: Call stack
     *  - Stack pointer end: use val stackEnd (256 + (4 * stackSize))
     */
    private val memory = ByteArray(memSize)
    private val lineCounter = -1 // BASIC statement line number

    private val varTable = HashMap<TBASValue, Pointer>()
    private val callStack = Stack(this, 256, stackSize)

    private val stackEnd = 256 + (4 * stackSize)

    val r1 = Pointer(this,  0, Pointer.PointerType.NUMBER) // I think null pointer is unnecessary
    val r2 = Pointer(this,  8, Pointer.PointerType.NUMBER)
    val r3 = Pointer(this, 16, Pointer.PointerType.NUMBER)
    val r4 = Pointer(this, 24, Pointer.PointerType.NUMBER)
    val r5 = Pointer(this, 32, Pointer.PointerType.NUMBER)
    val r6 = Pointer(this, 40, Pointer.PointerType.NUMBER)
    val r7 = Pointer(this, 48, Pointer.PointerType.NUMBER)
    val r8 = Pointer(this, 56, Pointer.PointerType.NUMBER)


    init {
        if (memSize > 16.MB()) {
            warn("Memory size might be too big — recommended max is 16 MBytes")
        }
        else if (memSize < 4.KB()) {
            throw Error("Memory size too small — minimum allowed is 4 KBytes")
        }
    }













}