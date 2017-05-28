package net.torvald.tbasic.runtime

import net.torvald.tbasic.*
import net.torvald.tbasic.TBASOpcodes.READ_UNTIL_ZERO
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.experimental.or


typealias Number = Double

/**
 * VM with minimal operation system that takes care of mallocs. Endianness is LITTLE
 *
 * @param memSize Memory size in bytes, max size is (2 GB - 1 byte)
 * @param stackSize Note: stack is separated from system memory
 *
 * @throws ArrayIndexOutOfBoundsException whenever
 *
 * Created by minjaesong on 2017-05-09.
 */
class VM(memSize: Int,
         private val stackSize: Int = 2500,
         val stdout: OutputStream = System.out,
         val stdin: InputStream = System.`in`,
         var suppressWarnings: Boolean = false
) {
    class Pointer(val parent: VM, var memAddr: Int, type: PointerType = Pointer.PointerType.BYTE, val noCast: Boolean = false) {
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
                (parent.memory[memAddr].toInt() or parent.memory[memAddr + 1].toInt().shl(8)).toShort()
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
            val strBytes = string.toByteArray(VM.charset)
            write(strBytes.size.toLittle() + strBytes) // according to TBASString
        }
        fun write(boolean: Boolean) {
            if (boolean) write(0xFF.toByte()) else write(0.toByte())
        }
        fun write(value: Any) {
            if (value is Byte) write(value as Byte)
            else if (value is Double) write (value as Double)
            else if (value is Int) write(value as Int)
            else if (value is Long) write(value as Long)
            else if (value is ByteArray) write(value as ByteArray)
            else if (value is String) write(value as String)
            else if (value is Pointer) write(value.readData())
            else throw IllegalArgumentException("Unsupported type: ${value.javaClass.canonicalName}")
        }

        fun toLittle() = memAddr.toLittle()
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

    fun memSliceBySize(from: Int, size: Int): ByteArray = memory.sliceArray(from..from + size - 1)
    fun memSlice(from: Int, to: Int): ByteArray = memory.sliceArray(from..to)

    private val mallocList = ArrayList<IntRange>()

    private fun findEmptySlotForMalloc(size: Int): Int {
        if (mallocList.isEmpty())
            return userSpaceStart!!

        mallocList.sortBy { it.first }

        val gaps = ArrayList<IntRange>()
        var foundRightGap = true
        for (it in 1..mallocList.lastIndex) {
            val gap = mallocList[it - 1].endInclusive + 1..mallocList[it].start - 1
            gaps.add(gap)

            if (gap.endInclusive - gap.start + 1 == size) {
                foundRightGap = true
                break
            } // found right gap, no need to search further
        }

        if (foundRightGap) {
            return gaps.last().start
        }
        else {
            gaps.forEach {
                if (it.endInclusive - it.start + 1 >= size)
                    return it.start
            }

            return gaps.last().endInclusive + 1
        }
    }

    /**
     * This function assumes all pointers are well placed, without gaps
     *
     * Will throw nullPointerException if program is not loaded
     */
    fun malloc(size: Int): Pointer {
        val addr = findEmptySlotForMalloc(size)
        mallocList.add(addr..addr + size - 1)

        return Pointer(this, addr)
    }
    fun calloc(size: Int): Pointer {
        val addr = findEmptySlotForMalloc(size)
        mallocList.add(addr..addr + size - 1)

        (0..size - 1).forEach { memory[addr + it] = 0.toByte() }

        return Pointer(this, addr)
    }
    fun freeBlock(variable: TBASValue) {
        freeBlock(variable.pointer.memAddr..variable.pointer.memAddr + variable.sizeOf() - 1)
    }
    fun freeBlock(range: IntRange) {
        if (!mallocList.remove(range)) {
            interruptSegmentationFault()
            throw RuntimeException("Access violation -- no such block was assigned by operation system.")
        }
    }
    fun reduceAllocatedBlock(range: IntRange, sizeToReduce: Int) {
        freeBlock(range)
        mallocList.add(range.start..range.endInclusive - sizeToReduce)
    }

    fun makeStringDB(string: ByteArray): Pointer {
        val string = if (string.last() == 0.toByte()) string else string + 0 // safeguard null terminator

        // look for dupes
        val existingPtnStart = memory.search(string)

        if (existingPtnStart == null) {
            return makeBytesDB(string)
        }
        else {
            return Pointer(this, existingPtnStart)
        }
    }
    fun makeStringDB(string: String): Pointer {
        return makeStringDB(string.toCString())
    }
    fun makeNumberDB(number: Number): TBASNumber {
        val ptr = malloc(8)
        ptr.type = Pointer.PointerType.INT64
        ptr.write(number)
        return TBASNumber(ptr)
    }
    fun makeBytesDB(bytes: ByteArray): Pointer {
        val ptr = malloc(bytes.size)
        ptr.write(bytes)
        return ptr
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    val peripherals = ArrayList<VMPeripheralWrapper>()

    /**
     * Memory Map
     *
     * 0     32        userSpaceStart  memSize
     * | IVT | program space | User space |
     *
     * Reg: Register
     *  - Function arguments
     *  - etc.
     * CStck: Call stack
     *  - Stack pointer end: use val stackEnd (256 + (4 * stackSize))
     */
    internal val memory = ByteArray(memSize)

    val varTable = HashMap<String, TBASValue>() // UPPERCASE ONLY!
    val callStack = IntArray(stackSize, { 0 })

    val ivtSize = 4 * VM.interruptCount
    var userSpaceStart: Int? = null // lateinit
        private set

    var terminate = false

    // number registers (64 bit, function args)
    var r1 = 0.0
    var r2 = 0.0
    var r3 = 0.0
    var r4 = 0.0
    var r5 = 0.0
    var r6 = 0.0
    var r7 = 0.0
    var r8 = 0.0

    fun writereg(register: Int, data: Number) {
        when (register) {
            1 -> r1 = data
            2 -> r2 = data
            3 -> r3 = data
            4 -> r4 = data
            5 -> r5 = data
            6 -> r6 = data
            7 -> r7 = data
            8 -> r8 = data
            else -> throw IllegalArgumentException("No such register: r$register")
        }
    }
    fun readreg(register: Int) = when (register) {
        1 -> r1
        2 -> r2
        3 -> r3
        4 -> r4
        5 -> r5
        6 -> r6
        7 -> r7
        8 -> r8
        else -> throw IllegalArgumentException("No such register: r$register")
    }
    fun writebreg(register: Int, data: Byte) {
        when (register) {
            1 -> b1 = data
            2 -> b2 = data
            3 -> b3 = data
            4 -> b4 = data
            5 -> b5 = data
            6 -> b6 = data
            7 -> b7 = data
            8 -> b8 = data
            else -> throw IllegalArgumentException("No such register: r$register")
        }
    }
    fun readbreg(register: Int) = when (register) {
        1 -> b1
        2 -> b2
        3 -> b3
        4 -> b4
        5 -> b5
        6 -> b6
        7 -> b7
        8 -> b8
        else -> throw IllegalArgumentException("No such register: r$register")
    }

    // byte registers (flags for r registers)
    var b1 = 0.toByte()
    var b2 = 0.toByte()
    var b3 = 0.toByte()
    var b4 = 0.toByte()
    var b5 = 0.toByte()
    var b6 = 0.toByte()
    var b7 = 0.toByte()
    var b8 = 0.toByte()

    // memory registers (32-bit)
    var m1 = 0 // general-use flags or variable
    var pc = 0 // program counter
    var sp = 0 // stack pointer
    var lr = 0 // link register

    private var uptimeHolder = 0L
    val uptime: Int // uptime register
        get() {
            val currentTime = System.currentTimeMillis()
            val ret = currentTime - uptimeHolder
            uptimeHolder = currentTime
            return ret.toInt()
        }


    init {
        if (memSize > 16.MB()) {
            warn("VM memory size might be too big — recommended max is 16 MBytes")
        }
        else if (memSize < 256) { // arbitrary unit
            throw Error("VM memory size too small — minimum allowed is 256 bytes")
        }

    }

    fun loadProgram(opcodes: ByteArray) {
        reset()

        TBASOpcodes.invoke(this)
        //TBASOpcodes.initTBasicEnv()

        System.arraycopy(opcodes, 0, memory, ivtSize, opcodes.size)
        memory[opcodes.size + ivtSize] = TBASOpcodes.opcodesList["HALT"]!!

        pc = ivtSize
        userSpaceStart = opcodes.size + 1 + ivtSize

        execDebug("Program loaded; pc: $pc, userSpaceStart: $userSpaceStart")
    }

    fun reset() {
        varTable.clear()
        Arrays.fill(callStack, 0)
        userSpaceStart = null
        terminate = false
    }

    fun execDebugMain(any: Any?) { if (false) print(any) }
    fun execDebug(any: Any?)     { if (false) print(any) }

    fun execute() {
        if (userSpaceStart != null) {

            uptimeHolder = System.currentTimeMillis()


            while (!terminate) {

                //(0..512).forEach { print("${memory[it]} ") }

                val instruction = memory[pc]
                val instAsm = TBASOpcodes.opcodesListInverse[instruction]!!

                execDebugMain("\nExec: $instAsm, ")

                val argumentsInfo = TBASOpcodes.opcodeArgsList[instAsm] ?: intArrayOf()

                val arguments = argumentsInfo.mapIndexed { index, i ->
                    if (i > 0) {
                        memSliceBySize(pc + 1 + (0..index - 1).map { argumentsInfo[it] }.sum(), i)
                    }
                    else if (i == READ_UNTIL_ZERO) { // READ_UNTIL_ZERO(-2) is guaranteed to be the last
                        val indexStart = pc + 1 + (0..index - 1).map { argumentsInfo[it] }.sum()
                        var indexEnd = indexStart
                        while (memory[indexEnd] != 0.toByte()) {
                            indexEnd += 1
                        }
                        // indexEnd now points \0
                        execDebug("[varargCounter] indexStart: $indexStart, indexEnd: $indexEnd (byteat: ${memory[indexEnd]})")
                        memSlice(indexStart, indexEnd)
                    }
                    else {
                        throw InternalError("Unknown arguments flag: $i")
                    }
                }
                val totalArgsSize = arguments.map { it.size }.sum()
                execDebugMain("ArgsCount: ${arguments.size}, ArgsLen: $totalArgsSize, PC: $pc")


                // execute
                pc += (1 + totalArgsSize)
                execDebugMain(", PC-next: $pc\n")
                TBASOpcodes.opcodesFunList[instAsm]!!(arguments)


                if (pc >= memory.size) {
                    interruptOutOfMem()
                }
            }
        }
    }








    ///////////////
    // CONSTANTS //
    ///////////////
    companion object {
        val charset = Charsets.UTF_8

        val interruptCount = 8
        val INT_DIV_BY_ZERO = 0
        val INT_ILLEGAL_OP = 1
        val INT_OUT_OF_MEMORY = 2
        val INT_STACK_OVERFLOW = 3
        val INT_MATH_ERROR = 4
        val INT_SEGFAULT = 5
    }


    private fun warn(any: Any?) { if (!suppressWarnings) println("[TBASRT] WARNING: $any") }

    // Interrupt handlers (its just JMPs) //
    fun interruptDivByZero() { pc = memSliceBySize(INT_DIV_BY_ZERO * 4, 4).toLittleInt() }
    fun interruptIllegalOp() { pc = memSliceBySize(INT_ILLEGAL_OP * 4, 4).toLittleInt() }
    fun interruptOutOfMem()  { pc = memSliceBySize(INT_OUT_OF_MEMORY * 4, 4).toLittleInt() }
    fun interruptStackOverflow() { pc = memSliceBySize(INT_STACK_OVERFLOW * 4, 4).toLittleInt() }
    fun interruptMathError() { pc = memSliceBySize(INT_MATH_ERROR * 4, 4).toLittleInt() }
    fun interruptSegmentationFault() { pc = memSliceBySize(INT_SEGFAULT * 4, 4).toLittleInt() }
}

fun Int.KB() = this shl 10
fun Int.MB() = this shl 20

fun String.toCString() = this.toByteArray(VM.charset) + 0
fun Int.toLittle() = byteArrayOf(
        this.and(0xFF).toByte(),
        this.ushr(8).and(0xFF).toByte(),
        this.ushr(16).and(0xFF).toByte(),
        this.ushr(24).and(0xFF).toByte()
)
fun Long.toLittle() = byteArrayOf(
        this.and(0xFF).toByte(),
        this.ushr(8).and(0xFF).toByte(),
        this.ushr(16).and(0xFF).toByte(),
        this.ushr(24).and(0xFF).toByte(),
        this.ushr(32).and(0xFF).toByte(),
        this.ushr(40).and(0xFF).toByte(),
        this.ushr(48).and(0xFF).toByte(),
        this.ushr(56).and(0xFF).toByte()
)
fun Double.toLittle() = java.lang.Double.doubleToRawLongBits(this).toLittle()
fun Boolean.toLittle() = byteArrayOf(if (this) 0xFF.toByte() else 0.toByte())

fun ByteArray.toLittleInt() =
        this[0].toInt() or
                this[1].toInt().shl(8) or
                this[2].toInt().shl(16) or
                this[3].toInt().shl(24)
fun ByteArray.toLittleLong() =
        this[0].toLong() or
                this[1].toLong().shl(8) or
                this[2].toLong().shl(16) or
                this[3].toLong().shl(24) or
                this[4].toLong().shl(32) or
                this[5].toLong().shl(40) or
                this[6].toLong().shl(48) or
                this[7].toLong().shl(56)
fun ByteArray.toLittleDouble() = java.lang.Double.longBitsToDouble(this.toLittleLong())

/**
 * Return first occurrence of the byte pattern
 * @return starting index of the first occurrence of the pattern, or null if not found
 * @see https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm
 */
fun ByteArray.search(pattern: ByteArray, start: Int = 0, length: Int = pattern.size - start): Int? {
    /*
    this: text to be searched
    pattern: the word sought
    */

    var m = 0
    var i = 0
    val T = IntArray(this.size, { -1 })


    //println("Searching pattern (ptn len: ${pattern.size})")

    while (m + 1 < this.size) {

        //println("m: $m, i: $i")

        if (pattern[i] == this[m + i]) {
            i += 1
            if (i == pattern.size) {
                /*val return_m = m

                m = m + i - T[i]
                i = T[i]

                return return_m*/
                return m
            }
        }
        else {
            if (T[i] > -1) {
                m = m + i - T[i]
                i = T[i]
            }
            else {
                m += i + 1
                i = 0
            }
        }
    }

    return null
}
