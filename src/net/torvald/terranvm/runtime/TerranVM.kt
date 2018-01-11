package net.torvald.terranvm.runtime

import net.torvald.terranvm.*
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList


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
class TerranVM(memSize: Int,
               private val stackSize: Int = 2500,
               var stdout: OutputStream = System.out,
               var stdin: InputStream = System.`in`,
               var suppressWarnings: Boolean = false,
         // following is an options for TerranVM's micro operation system
               val tbasic_remove_string_dupes: Boolean = false // only meaningful for TBASIC TODO: turning this on makes it run faster?!
) : Runnable {
    private val DEBUG = false
    private val ERROR = true

    class Pointer(val parent: TerranVM, memoryAddress: Int, type: PointerType = Pointer.PointerType.BYTE, val noCast: Boolean = false) {
        /*
        VOID    in memory: 0x00
        BOOLEAN in memory: 0x00 if false, 0xFF if true
         */

        val traceMemAddrChange = false

        var memAddr: Int = memoryAddress
            set(value) {
                if (traceMemAddrChange) {
                    println("!! Pointer: memAddr change $memAddr -> $value")
                }
                field = value
            }

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
            if (parent.memory.size < memAddr + byteArray.size) throw ArrayIndexOutOfBoundsException("Out of memory; couldn't install default interrupts")
            System.arraycopy(byteArray, 0, parent.memory, memAddr, byteArray.size)
        }
        fun write(string: String) {
            val strBytes = string.toByteArray(TerranVM.charset)
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

    class IntStack(vm: TerranVM, val startPointer: Int, val stackSize: Int) {
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

    class Stack(vm: TerranVM, val stackSize: Int, type: Pointer.PointerType) {
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

    fun memSliceBySize(from: Int, size: Int): ByteArray = memory.sliceArray(from until from + size)
    fun memSlice(from: Int, to: Int): ByteArray = memory.sliceArray(from..to)

    private val mallocList = ArrayList<Int>(64) // can be as large as memSize

    private inline fun addToMallocList(range: IntRange) { range.forEach { mallocList.add(it) } }
    private inline fun removeFromMallocList(range: IntRange) { range.forEach { mallocList.remove(it) } }

    /**
     * Return starting position of empty space
     */
    private fun findEmptySlotForMalloc(size: Int): Int {
        if (mallocList.isEmpty())
            return userSpaceStart!!

        mallocList.sort()

        val candidates = ArrayList<Pair<Int, Int>>() // startingPos, size
        for (it in 1..mallocList.lastIndex) {
            val gap = mallocList[it] - 1 - (if (it == 0) userSpaceStart!! else mallocList[it - 1])

            if (gap >= size) {
                candidates.add(Pair(mallocList[it] + 1, gap))
            }
        }


        if (candidates.isNotEmpty()) {
            candidates.sortBy { it.second } // tight gap comes first
            return candidates.first().first
        }
        else {
            return mallocList.last() + 1
        }
    }

    /**
     * This function assumes all pointers are well placed, without gaps
     *
     * Will throw nullPointerException if program is not loaded
     */
    fun malloc(size: Int): Pointer {
        val addr = findEmptySlotForMalloc(size)
        addToMallocList(addr until addr + size)

        return Pointer(this, addr)
    }
    fun calloc(size: Int): Pointer {
        val addr = findEmptySlotForMalloc(size)
        addToMallocList(addr until addr + size)

        (0..size - 1).forEach { memory[addr + it] = 0.toByte() }

        return Pointer(this, addr)
    }
    fun freeBlock(variable: TBASValue) {
        freeBlock(variable.pointer.memAddr until variable.pointer.memAddr + variable.sizeOf())
    }
    fun freeBlock(range: IntRange) {
        removeFromMallocList(range)
    }
    fun reduceAllocatedBlock(range: IntRange, sizeToReduce: Int) {
        if (sizeToReduce == 0) return
        if (sizeToReduce < 0) throw IllegalArgumentException("Reducing negative amount of space ($range, $sizeToReduce)")
        val residual = (range.last - sizeToReduce + 1)..range.last
        removeFromMallocList(residual)
    }

    fun makeStringDB(string: ByteArray): Pointer {
        val string = if (string.last() == 0.toByte()) string else string + 0 // safeguard null terminator

        // look for dupes
        if (tbasic_remove_string_dupes) {
            val existingPtnStart = memory.search(string)

            if (existingPtnStart == null) {
                return makeBytesDB(string)
            }
            else {
                return Pointer(this, existingPtnStart)
            }
        }
        else {
            return makeBytesDB(string)
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

    val peripherals = Array<VMPeripheralWrapper?>(255, { null }) // peri addr: 0x00..0xFE;
    /** - 0x00: system timer
     *  - 0x01: keyboard
     *  - 0x02: real-time clock
     *  - 0x03: primary display adaptor
     *  - 0x04: reserved
     *  - 0x05: reserved
     *  - 0x06: serial port
     *  - 0x07: serial port
     *  - 0x08: disk drive A
     *  - 0x09: disk drive B
     *  - 0x0A: disk drive C
     *  - 0x0B: disk drive D
     *  - 0x0C: SCSI
     *  - 0x0D: SCSI
     *  - 0x0E: SCSI
     *  - 0x0F: SCSI
     *  - 0x10..0xFE: user space
     *  - 0xFF: Used to address BIOS/UEFI
     *
     * (see IRQ_ vars in the companion object)
     */
    val bios = BIOS(this)

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

    private val varTable = HashMap<String, TBASValue>() // UPPERCASE ONLY!

    fun setvar(name: String, value: TBASValue) { varTable[name] = value }
    fun getvar(name: String) = varTable[name]
    fun hasvar(name: String) = varTable.containsKey(name)

    val callStack = IntArray(stackSize, { 0 })

    val ivtSize = 4 * TerranVM.interruptCount
    var userSpaceStart: Int? = null // lateinit
        private set

    var terminate = false
    var isRunning = false
        private set

    // number registers (32 bit)
    var r1 = 0
    var r2 = 0
    var r3 = 0
    var r4 = 0
    var r5 = 0
    var r6 = 0
    var r7 = 0
    var r8 = 0

    fun writeregFloat(register: Int, data: Float) {
        when (register) {
            1 -> r1 = java.lang.Float.floatToIntBits(data)
            2 -> r2 = java.lang.Float.floatToIntBits(data)
            3 -> r3 = java.lang.Float.floatToIntBits(data)
            4 -> r4 = java.lang.Float.floatToIntBits(data)
            5 -> r5 = java.lang.Float.floatToIntBits(data)
            6 -> r6 = java.lang.Float.floatToIntBits(data)
            7 -> r7 = java.lang.Float.floatToIntBits(data)
            8 -> r8 = java.lang.Float.floatToIntBits(data)
            else -> throw IllegalArgumentException("No such register: r$register")
        }
    }
    fun writeregInt(register: Int, data: Int) {
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
    fun readregInt(register: Int) = when (register) {
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
    fun readregFloat(register: Int) = when (register) {
        1 -> r1.toFloat()
        2 -> r2.toFloat()
        3 -> r3.toFloat()
        4 -> r4.toFloat()
        5 -> r5.toFloat()
        6 -> r6.toFloat()
        7 -> r7.toFloat()
        8 -> r8.toFloat()
        else -> throw IllegalArgumentException("No such register: r$register")
    }

    // memory registers (32-bit)
    var rCMP = 0 // compare register
    var m2 = 0 // string counter?
    //var m3 = 0 // general-use flags or variable
    //var m4 = 0 // general-use flags or variable
    var pc = 0 // program counter
    var sp = 0 // stack pointer
    var lr = 0 // link register

    private var uptimeHolder = 0L
    val uptime: Int // uptime register
        get() {
            val currentTime = System.currentTimeMillis()
            val ret = currentTime - uptimeHolder
            return ret.toInt()
        }


    init {
        if (memSize > 4.MB()) {
            warn("VM memory size might be too large — recommended max is 4 MBytes")
        }
        else if (memSize < 256) { // arbitrary amount (note - ATtiny has at least 2K Flash + 128 EEPROM + 128 SRAM. Atari 2600 had 128)
            throw Error("VM memory size too small — minimum allowed is 256 bytes")
        }
        else if (memSize > 16.MB()) {
            throw Error("Memory size too large -- maximum allowed is 16 MBytes")
        }

    }

    fun loadProgram(opcodes: ByteArray) {
        if (opcodes.size + ivtSize >= memory.size) {
            throw Error("Out of memory -- required: ${opcodes.size + ivtSize} (${opcodes.size} for program), installed: ${memory.size}")
        }

        softReset()

        VMOpcodesRISC.invoke(this)


        System.arraycopy(opcodes, 0, memory, ivtSize, opcodes.size)
        memory[opcodes.size + ivtSize] = 0
        memory[opcodes.size + ivtSize + 1] = 0
        memory[opcodes.size + ivtSize + 2] = 0
        memory[opcodes.size + ivtSize + 3] = 0

        pc = ivtSize
        userSpaceStart = opcodes.size + 1 + ivtSize

        userSpaceStart = userSpaceStart!! + setDefaultInterrepts() // renew userSpaceStart after interrupts
        userSpaceStart = userSpaceStart!!.ushr(2).shl(2) + 4 // manually align
        mallocList.clear()


        warn("Program loaded; pc: $pcHex, userSpaceStart: $userSpaceStart")
    }

    private fun setDefaultInterrepts(): Int {
        val intOOM = Assembler("""
loadstrinline r1,
NOMEM
; printstr; halt;
""")
        val intSegfault = Assembler("""
loadstrinline r1,
SEGFU
; printstr; halt;
""")
        val intDivZero = Assembler("""
loadstrinline r1,
DIV/0
; printstr; halt;
""")
        val intIllegalOp = Assembler("""
loadstrinline r1,
ILLOP
; printstr; halt;
""")
        val intStackOverflow = Assembler("""
loadstrinline r1,
STKOF
; printstr; halt;
""")
        val intMathFuck = Assembler("""
loadstrinline r1,
MTHFU
; printstr; halt;
""")

        val intOOMPtr = malloc(intOOM.size)
        val intSegfaultPtr = malloc(intSegfault.size)
        val intDivZeroPtr = malloc(intDivZero.size)
        val intIllegalOpPtr = malloc(intIllegalOp.size)
        val intStackOvflPtr = malloc(intStackOverflow.size)
        val intMathErrPtr = malloc(intMathFuck.size)

        intOOMPtr.write(intOOM)
        intSegfaultPtr.write(intSegfault)
        intDivZeroPtr.write(intDivZero)
        intIllegalOpPtr.write(intIllegalOp)
        intStackOvflPtr.write(intStackOverflow)
        intMathErrPtr.write(intMathFuck)


        r1 = intOOMPtr.memAddr
        r2 = INT_OUT_OF_MEMORY * 4
        VMOpcodesRISC.STOREWORD(2, 1, 0)
        r1 = intSegfaultPtr.memAddr
        r2 = INT_SEGFAULT * 4
        VMOpcodesRISC.STOREWORD(2, 1, 0)
        r1 = intDivZeroPtr.memAddr
        r2 = INT_DIV_BY_ZERO * 4
        VMOpcodesRISC.STOREWORD(2, 1, 0)
        r1 = intIllegalOpPtr.memAddr
        r2 = INT_ILLEGAL_OP * 4
        VMOpcodesRISC.STOREWORD(2, 1, 0)
        r1 = intStackOvflPtr.memAddr
        r2 = INT_STACK_OVERFLOW * 4
        VMOpcodesRISC.STOREWORD(2, 1, 0)
        r1 = intMathErrPtr.memAddr
        r2 = INT_MATH_ERROR * 4
        VMOpcodesRISC.STOREWORD(2, 1, 0)


        return intOOM.size + intSegfault.size + intDivZero.size + intIllegalOp.size + intStackOverflow.size + intMathFuck.size
    }

    fun softReset() {
        varTable.clear()
        Arrays.fill(callStack, 0)
        userSpaceStart = null
        terminate = false

        // reset malloc table
        mallocList.clear()

        // reset registers
        r1 = 0
        r2 = 0
        r3 = 0
        r4 = 0
        r5 = 0
        r6 = 0
        r7 = 0
        r8 = 0
        rCMP = 0
        m2 = 0
        //m3 = 0
        //m4 = 0
        //strCntr = 0
        pc = 0
        sp = 0
        lr = 0
        //... but don't reset the uptime
    }

    fun hardReset() {
        softReset()

        // reset system uptime timer
        uptimeHolder = 0L

        // erase memory
        Arrays.fill(memory, 0)
    }


    var delayInMills: Int? = null


    fun execDebugMain(any: Any?) { if (DEBUG) println(any) }
    fun execDebugError(any: Any?) { if (ERROR) System.err.println(any) }

    private var pauseExec = false

    fun pauseExec() {
        pauseExec = true
    }

    fun resumeExec() {
        pauseExec = false
        synchronized(lock) {
            lock.notify()
        }
    }

    override fun run() {
        execute()
    }

    val lock = java.lang.Object()

    val pcHex: String; get() = pc.toString(16) + "h"

    fun execute() {

        if (userSpaceStart != null) {

            isRunning = true
            uptimeHolder = System.currentTimeMillis()


            while (!terminate) {
                //print("["); (userSpaceStart!!..849).forEach { print("${memory[it].toUint()} ") }; println("]")



                if (pc >= memory.size) {
                    val oldpc = pc
                    execDebugError("Out of memory: Illegal PC; pc ${oldpc.toLong().and(0xffffffff).toString(16).toUpperCase()}h")
                    interruptOutOfMem()
                }
                else if (pc < 0) {
                    val oldpc = pc
                    execDebugError("Segmentation fault: Illegal PC; pc ${oldpc.toLong().and(0xffffffff).toString(16).toUpperCase()}h")
                    interruptSegmentationFault()
                }


                var opcode = memory[pc].toUint() or memory[pc + 1].toUint().shl(8) or memory[pc + 2].toUint().shl(16) or memory[pc + 3].toUint().shl(24)


                // execute
                pc += 4

                // invoke function
                try {
                    VMOpcodesRISC.decodeAndExecute(opcode)
                }
                catch (oom: ArrayIndexOutOfBoundsException) {
                    execDebugError("[TBASRT] illegal memory address access")
                    interruptOutOfMem()
                }


                if (opcode == 0) {
                    execDebugMain("HALT at PC $pcHex")
                }




                synchronized(lock) {
                    if (pauseExec) {
                        lock.wait()
                    }
                }



                if (delayInMills != null) {
                    Thread.sleep(delayInMills!!.toLong())
                }
            }

            isRunning = false
        }
    }








    ///////////////
    // CONSTANTS //
    ///////////////
    companion object {
        val charset = Charsets.UTF_8

        val interruptCount = 16
        
        val INT_DIV_BY_ZERO = 0
        val INT_ILLEGAL_OP = 1
        val INT_OUT_OF_MEMORY = 2
        val INT_STACK_OVERFLOW = 3
        val INT_MATH_ERROR = 4
        val INT_SEGFAULT = 5
        val INT_KEYPRESS = 6
        val INT_PERI_INPUT = 7
        val INT_PERI_OUTPUT = 8
        val INT_INTERRUPT = 9


        val IRQ_SYSTEM_TIMER = 0
        val IRQ_KEYBOARD = 1
        val IRQ_RTC = 2
        val IRQ_PRIMARY_DISPLAY = 3
        val IRQ_SERIAL_1 = 6
        val IRQ_SERIAL_2 = 7
        val IRQ_DISK_A = 8
        val IRQ_DISK_B = 9
        val IRQ_DISK_C = 10
        val IRQ_DISK_D = 11
        val IRQ_SCSI_1 = 12
        val IRQ_SCSI_2 = 13
        val IRQ_SCSI_3 = 14
        val IRQ_SCSI_4 = 15
        val IRQ_BIOS = 255
    }


    private fun warn(any: Any?) { if (!suppressWarnings) println("[TBASRT] WARNING: $any") }

    // Interrupt handlers (its just JMPs) //
    fun interruptDivByZero() { VMOpcodesRISC.GOSUB(memSliceBySize(INT_DIV_BY_ZERO * 4, 4).toLittleInt()) }
    fun interruptIllegalOp() { VMOpcodesRISC.GOSUB(memSliceBySize(INT_ILLEGAL_OP * 4, 4).toLittleInt()) }
    fun interruptOutOfMem()  { VMOpcodesRISC.GOSUB(memSliceBySize(INT_OUT_OF_MEMORY * 4, 4).toLittleInt()) }
    fun interruptStackOverflow() { VMOpcodesRISC.GOSUB(memSliceBySize(INT_STACK_OVERFLOW * 4, 4).toLittleInt()) }
    fun interruptMathError() { VMOpcodesRISC.GOSUB(memSliceBySize(INT_MATH_ERROR * 4, 4).toLittleInt()) }
    fun interruptSegmentationFault() { VMOpcodesRISC.GOSUB(memSliceBySize(INT_SEGFAULT * 4, 4).toLittleInt()) }
    fun interruptKeyPress() { VMOpcodesRISC.GOSUB(memSliceBySize(INT_KEYPRESS * 4, 4).toLittleInt()) }
    fun interruptPeripheralInput() { VMOpcodesRISC.GOSUB(memSliceBySize(INT_PERI_INPUT * 4, 4).toLittleInt()) }
    fun interruptPeripheralOutput() { VMOpcodesRISC.GOSUB(memSliceBySize(INT_PERI_OUTPUT * 4, 4).toLittleInt()) }
    fun interruptStopExecution() { VMOpcodesRISC.GOSUB(memSliceBySize(INT_INTERRUPT * 4, 4).toLittleInt()) }


    class BIOS(val vm: TerranVM) : VMPeripheralHardware {
        override fun call(arg: Int) {

        }
    }

}

fun Int.KB() = this shl 10
fun Int.MB() = this shl 20

/** Turn string into byte array with null terminator */
fun String.toCString() = this.toByteArray(TerranVM.charset) + 0
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
fun Float.toLittle() = java.lang.Float.floatToRawIntBits(this).toLittle()
fun Boolean.toLittle() = byteArrayOf(if (this) 0xFF.toByte() else 0.toByte())

fun ByteArray.toLittleInt() =
        if (this.size != 4) throw Error()
        else    this[0].toUint() or
                this[1].toUint().shl(8) or
                this[2].toUint().shl(16) or
                this[3].toUint().shl(24)
fun ByteArray.toLittleLong() =
        if (this.size != 8) throw Error()
        else    this[0].toUlong() or
                this[1].toUlong().shl(8) or
                this[2].toUlong().shl(16) or
                this[3].toUlong().shl(24) or
                this[4].toUlong().shl(32) or
                this[5].toUlong().shl(40) or
                this[6].toUlong().shl(48) or
                this[7].toUlong().shl(56)
fun ByteArray.toLittleDouble() = java.lang.Double.longBitsToDouble(this.toLittleLong())

fun Byte.toUlong() = java.lang.Byte.toUnsignedLong(this)
fun Byte.toUint() = java.lang.Byte.toUnsignedInt(this)

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
