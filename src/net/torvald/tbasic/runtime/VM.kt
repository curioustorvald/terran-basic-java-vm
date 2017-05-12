package net.torvald.tbasic.runtime

import net.torvald.tbasic.*
import java.util.*


typealias Number = Double

/**
 * Takes care of mallocs. Endianness is LITTLE
 *
 * @param memSize Memory size in bytes, max size is (2 GB - 1 byte)
 *
 * @throws ArrayIndexOutOfBoundsException whenever
 *
 * Created by minjaesong on 2017-05-09.
 */
class VM(memSize: Int, private val stackSize: Int = 192, var suppressWarnings: Boolean = false) {

    companion object {
        val charset = Charsets.UTF_8
    }


    private fun Int.KB() = this shl 10
    private fun Int.MB() = this shl 20
    private fun warn(any: Any?) { if (!suppressWarnings) println("[TBASRT] $any") }


    class Pointer(val parent: VM, var memAddr: Int, type: PointerType = Pointer.PointerType.BYTE, var noCast: Boolean = false) {
        /*
        VOID    in memory: 0x00
        BOOLEAN in memory: 0x00 if false, 0xFF if true
         */

        var type = type
            set(value) {
                if (noCast) throw TypeCastException("This pointer is not castable")
                else        field = value
            }
        fun cast(value: PointerType) { type = value }

        enum class PointerType { BYTE, INT32, DOUBLE, BOOLEAN, VOID, INT16, INT64 }
        companion object {
            fun sizeOf(type: PointerType) = when(type) {
                PointerType.BYTE    -> 1 // Internal for ByteArray (and thus String)
                PointerType.BOOLEAN -> 1 //
                PointerType.DOUBLE  -> 8 // NUMBER in TBASIC
                PointerType.INT32   -> 4 // internal use; non-TBASIC
                PointerType.VOID    -> 1 // also NIL in TBASIC
                PointerType.INT16   -> 2 // non-TBASIC
                PointerType.INT64   -> 8 // non-TBASIC
            }
        }

        fun size() = sizeOf(type)
        operator fun plusAssign(offset: Int) { memAddr += size() * offset }
        operator fun minusAssign(offset: Int) { memAddr -= size() * offset }
        operator fun plus(offset: Int) = Pointer(parent, memAddr + size() * offset, type)
        operator fun minus(offset: Int) = Pointer(parent, memAddr - size() * offset, type)
        fun inc() { plusAssign(1) }
        fun dec() { minusAssign(1) }

        fun toBoolean() = (type != PointerType.VOID && (type == PointerType.BOOLEAN && readData() as Boolean))

        /**
         * Usage: ```readData() as Number```
         */
        fun readData(): Any = when(type) {
            PointerType.VOID -> 0x00.toByte() // cast it to TBASNil if you're working with TBASIC
            PointerType.BOOLEAN -> (parent.memory[memAddr] != 0.toByte())
            PointerType.BYTE    -> parent.memory[memAddr]
            PointerType.INT32 -> {
                        parent.memory[memAddr].toInt() or parent.memory[memAddr + 1].toInt().shl(8) or
                        parent.memory[memAddr + 2].toInt().shl(16) or parent.memory[memAddr + 3].toInt().shl(24)
            }
            PointerType.DOUBLE -> {
                java.lang.Double.longBitsToDouble(
                        parent.memory[memAddr].toLong() or parent.memory[memAddr + 1].toLong().shl(8) or
                        parent.memory[memAddr + 2].toLong().shl(16) or parent.memory[memAddr + 3].toLong().shl(24) or
                        parent.memory[memAddr + 4].toLong().shl(32) or parent.memory[memAddr + 5].toLong().shl(40) or
                        parent.memory[memAddr + 6].toLong().shl(48) or parent.memory[memAddr + 7].toLong().shl(56)
                        )
            }
            PointerType.INT64 -> {
                parent.memory[memAddr].toLong() or parent.memory[memAddr + 1].toLong().shl(8) or
                        parent.memory[memAddr + 2].toLong().shl(16) or parent.memory[memAddr + 3].toLong().shl(24) or
                        parent.memory[memAddr + 4].toLong().shl(32) or parent.memory[memAddr + 5].toLong().shl(40) or
                        parent.memory[memAddr + 6].toLong().shl(48) or parent.memory[memAddr + 7].toLong().shl(56)
            }
            PointerType.INT16 -> {
                parent.memory[memAddr].toLong() or parent.memory[memAddr + 1].toLong().shl(8)
            }
        }
        fun readAsDouble(): Double {
            if (type == PointerType.DOUBLE)
                return readData() as Double
            else if (type == PointerType.INT64)
                return java.lang.Double.longBitsToDouble(readData() as Long)
            else
                throw TypeCastException("The pointer is neither DOUBLE nor INT64")
        }
        fun readAsLong(): Long {
            if (type == PointerType.DOUBLE || type == PointerType.INT64)
                return parent.memory[memAddr].toLong() or parent.memory[memAddr + 1].toLong().shl(8) or
                        parent.memory[memAddr + 2].toLong().shl(16) or parent.memory[memAddr + 3].toLong().shl(24) or
                        parent.memory[memAddr + 4].toLong().shl(32) or parent.memory[memAddr + 5].toLong().shl(40) or
                        parent.memory[memAddr + 6].toLong().shl(48) or parent.memory[memAddr + 7].toLong().shl(56)
            else
                throw TypeCastException("The pointer is neither DOUBLE nor INT64")
        }

        fun write(byte: Byte) { parent.memory[memAddr] = byte }
        fun write(double: Double) {
            val doubleBytes = java.lang.Double.doubleToRawLongBits(double)
            write(doubleBytes)
        }
        fun write(int: Int) {
            (0..3).forEach { parent.memory[memAddr + it] = int.ushr(8 * it).and(0xFF).toByte() }
        }
        fun write(long: Long) {
            (0..7).forEach { parent.memory[memAddr + it] = long.ushr(8 * it).and(0xFF).toByte() }
        }
        fun write(byteArray: ByteArray) {
            if (parent.memory.size < memAddr + byteArray.size) throw ArrayIndexOutOfBoundsException()
            System.arraycopy(byteArray, 0, parent.memory, memAddr, byteArray.size)
        }
        fun write(string: String) {
            write(string.toByteArray(VM.charset))
        }
        fun write(value: Any) {
            if (value is Byte) write(value as Byte)
            else if (value is Double) write (value as Double)
            else if (value is Int) write(value as Int)
            else if (value is Long) write(value as Long)
            else if (value is ByteArray) write(value as ByteArray)
            else if (value is String) write(value as String)
            else throw IllegalArgumentException("Unsupported type: ${value.javaClass.canonicalName}")
        }
    }

    class IntStack(vm: VM, val startPointer: Int, val stackSize: Int) {
        private val ptr = Pointer(vm, startPointer, Pointer.PointerType.INT32)

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

    class Stack(vm: VM, val stackSize: Int, type: Pointer.PointerType) {
        init {
            if (type == Pointer.PointerType.VOID) {
                throw Error("Unsupported pointer type: VOID")
            }
        }

        private val ptr = vm.malloc(stackSize)
        private val startPointer = ptr.memAddr

        fun push(value: Any) {
            if (ptr.memAddr - startPointer >= stackSize) throw StackOverflowError()
            ptr.write(value); ptr.inc()
        }
        fun pop(): Any {
            if (ptr.memAddr <= startPointer) throw EmptyStackException()
            val ret = ptr.readData() as Int; ptr.dec(); return ret
        }
        fun peek(): Any = ptr.readData()
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    fun memSlice(from: Int, size: Int) = memory.sliceArray(from..from + size - 1)

    /**
     * This function assumes all pointers are well placed, without gaps
     *
     * Will throw nullPointerException if program is not loaded
     */
    fun malloc(size: Int): Pointer {
        var mPtr = uspStart!!
        varTable.forEach { _, variable ->
            mPtr += variable.pointer.size()
        }
        if (memory.size - mPtr < size) throw OutOfMemoryError()

        return Pointer(this, mPtr)
    }
    fun calloc(size: Int): Pointer {
        var mPtr = uspStart!!
        varTable.forEach { _, variable ->
            mPtr += variable.pointer.size()
        }
        if (memory.size - mPtr < size) throw OutOfMemoryError()

        // fill zero
        (0..size - 1).forEach { memory[mPtr + it] = 0.toByte() }

        return Pointer(this, mPtr)
    }
    fun freeBlock(variable: TBASValue) {
        // move blocks back
        val moveOffset = variable.sizeOf()
        varTable.filter { it.value.pointer.memAddr > variable.pointer.memAddr }.forEach { _, variable ->
            System.arraycopy(memory, variable.pointer.memAddr, memory, variable.pointer.memAddr - moveOffset, variable.sizeOf())
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Memory Map
     *
     * 0    256    1024*          uspStart      memSize
     * | Reg | CStck | Program space | User space |
     *
     * Reg: Register
     *  - Function arguments
     *  - etc.
     * CStck: Call stack
     *  - Stack pointer end: use val stackEnd (256 + (4 * stackSize))
     */
    private val memory = ByteArray(memSize)

    val varTable = HashMap<String, TBASValue>()
    var callStack = IntStack(this, 256, stackSize)
        private set

    private val prgStart = 256 + (4 * stackSize)
    private var uspStart: Int? = null // lateinit

    var programLoaded = false
        private set

    var terminate = false

    val userSpaceSize: Int
        get() = memory.size - uspStart!!

    // NOTE: if you want, you can ignore r1-r8 here and devise your by referencing the memory directly
    val r1 = Pointer(this,  0, Pointer.PointerType.INT64, noCast = true) // I think null pointer is unnecessary
    val r2 = Pointer(this,  8, Pointer.PointerType.INT64, noCast = true)
    val r3 = Pointer(this, 16, Pointer.PointerType.INT64, noCast = true)
    val r4 = Pointer(this, 24, Pointer.PointerType.INT64, noCast = true)
    val r5 = Pointer(this, 32, Pointer.PointerType.INT64, noCast = true)
    val r6 = Pointer(this, 40, Pointer.PointerType.INT64, noCast = true)
    val r7 = Pointer(this, 48, Pointer.PointerType.INT64, noCast = true)
    val r8 = Pointer(this, 56, Pointer.PointerType.INT64, noCast = true)

    val registers = arrayOf(r1,r2,r3,r4,r5,r6,r7,r8)

    var pc = 0


    init {
        if (memSize > 16.MB()) {
            warn("Memory size might be too big — recommended max is 16 MBytes")
        }
        else if (memSize < 4.KB()) { // 4 KB is arbitrary TBH
            throw Error("Memory size too small — minimum allowed is 4 KBytes")
        }
    }

    fun loadProgram(opcodes: ByteArray) {
        System.arraycopy(opcodes, 0, memory, prgStart, opcodes.size)
        uspStart = prgStart + opcodes.size
        programLoaded = true
    }

    fun reset() {
        varTable.clear()
        callStack = IntStack(this, 256, stackSize)
        programLoaded = false
        uspStart = null
    }











}