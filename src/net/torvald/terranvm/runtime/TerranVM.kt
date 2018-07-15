package net.torvald.terranvm.runtime

import net.torvald.terranvm.*
import net.torvald.terranvm.assets.FreshNewParametreRAM
import java.io.InputStream
import java.io.OutputStream
import java.io.Serializable
import java.nio.charset.Charset
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
class TerranVM(inMemSize: Int,
               //var stackSize: Int = inMemSize.ushr(4).shl(2),
               var stdout: OutputStream = System.out,
               var stdin: InputStream = System.`in`,
               var suppressWarnings: Boolean = false,
               // following is an options for TerranVM's micro operation system
               val doNotInstallInterrupts: Boolean = false,
               // to load from saved shits
               val parametreRAM: ByteArray = FreshNewParametreRAM(),
               internal val memory: ByteArray = ByteArray(inMemSize),
               private val mallocList: ArrayList<Int> = ArrayList<Int>(64),
               var terminate: Boolean = false,
               var isRunning: Boolean = false,
               private val contextHolder: HashMap<Int, Context> = hashMapOf( 0 to Context() ), // main context is always ID 0
               private var uptimeHolder: Long = 0L
) : Runnable, Serializable {

    var context: Context = contextHolder[0]!!
        private set
    private var contextID: Int = 0

    private val memSize = inMemSize.ushr(2).shl(2)
    val bytes_ffffffff = (-1).toLittle()
    val bytes_00000000 = byteArrayOf(0, 0, 0, 0)

    private val DEBUG = true
    private val ERROR = true

    var stackSize: Int? = null
        private set

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
            if (parent.memory.size < memAddr + byteArray.size) throw ArrayIndexOutOfBoundsException("Out of memory")
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

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////
    // Memory Management Unit //
    ////////////////////////////

    fun memSliceBySize(from: Int, size: Int): ByteArray = memory.sliceArray(from until from + size)
    fun memSlice(from: Int, to: Int): ByteArray = memory.sliceArray(from..to)

    //private val mallocList = ArrayList<Int>(64) // can be as large as memSize

    private fun addToMallocList(range: IntRange) { range.forEach { mallocList.add(it) } }
    private fun removeFromMallocList(range: IntRange) { range.forEach { mallocList.remove(it) } }

    /**
     * Return starting position of empty space
     */
    private fun findEmptySlotForMalloc(size: Int): Int {
        val veryStartPoint = ivtSize

        if (mallocList.isEmpty())
            return veryStartPoint

        mallocList.sort()

        val candidates = ArrayList<Pair<Int, Int>>() // startingPos, size
        for (it in 1..mallocList.lastIndex) {
            val gap = mallocList[it] - 1 - (if (it == 0) veryStartPoint else mallocList[it - 1])

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
        if (size % 4 != 0)
            return malloc(size.roundToFour())
        else {
            val addr = findEmptySlotForMalloc(size)
            addToMallocList(addr until addr + size)

            return Pointer(this, addr)
        }
    }
    fun calloc(size: Int): Pointer {
        if (size % 4 != 0)
            return malloc(size.roundToFour())
        else {
            val addr = findEmptySlotForMalloc(size)
            addToMallocList(addr until addr + size)

            (0..size - 1).forEach { memory[addr + it] = 0.toByte() }

            return Pointer(this, addr)
        }
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
    val bios = TerranVMReferenceBIOS(this)

    /**
     * Memory Map
     *
     * 0     96   96+stkSize   userSpaceStart  memSize
     * | IVT | Stack | Program Space | User Space | (more program-user space pair, managed by MMU) |
     *
     * Reg: Register
     *  - Function arguments
     *  - etc.
     *
     *
     *  Memory-mapped peripherals must be mapped to their own memory hardware. In other words,
     *      they don't share your computer's main memory.
     */
    //internal val memory = ByteArray(memSize)

    val ivtSize = 4 * TerranVM.interruptCount

    init {
        // initialise default (index zero) program start position with IVT size
        // must match with "veryStartPoint" of findEmptySlotForMalloc
        context.st = ivtSize
    }

    //var userSpaceStart: Int? = null // lateinit
    //    private set

    //var terminate = false
    //var isRunning = false
    //    private set

    var r1: Int; get() = context.r1; set(value) { context.r1= value }
    var r2: Int; get() = context.r2; set(value) { context.r2= value }
    var r3: Int; get() = context.r3; set(value) { context.r3= value }
    var r4: Int; get() = context.r4; set(value) { context.r4= value }
    var r5: Int; get() = context.r5; set(value) { context.r5= value }
    var r6: Int; get() = context.r6; set(value) { context.r6= value }
    var r7: Int; get() = context.r7; set(value) { context.r7= value }
    var r8: Int; get() = context.r8; set(value) { context.r8= value }
    var cp: Int; get() = context.cp; set(value) { context.cp= value } // compare register
    var pc: Int; get() = context.pc; set(value) { context.pc= value } // program counter
    var sp: Int; get() = context.sp; set(value) { context.sp= value } // stack pointer
    var lr: Int; get() = context.lr; set(value) { context.lr= value } // link register
    var st: Int; get() = context.st; set(value) { context.st= value } // starting point

    fun writeregFloat(register: Int, data: Float) {
        when (register) {
            1 -> r1 = data.toRawBits()
            2 -> r2 = data.toRawBits()
            3 -> r3 = data.toRawBits()
            4 -> r4 = data.toRawBits()
            5 -> r5 = data.toRawBits()
            6 -> r6 = data.toRawBits()
            7 -> r7 = data.toRawBits()
            8 -> r8 = data.toRawBits()
            0 -> throw IllegalArgumentException("Cannot write to '0' register")
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
            0 -> throw IllegalArgumentException("Cannot write to '0' register")
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
        1 -> java.lang.Float.intBitsToFloat(r1)
        2 -> java.lang.Float.intBitsToFloat(r2)
        3 -> java.lang.Float.intBitsToFloat(r3)
        4 -> java.lang.Float.intBitsToFloat(r4)
        5 -> java.lang.Float.intBitsToFloat(r5)
        6 -> java.lang.Float.intBitsToFloat(r6)
        7 -> java.lang.Float.intBitsToFloat(r7)
        8 -> java.lang.Float.intBitsToFloat(r8)
        else -> throw IllegalArgumentException("No such register: r$register")
    }

    //private var uptimeHolder = 0L
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
        else if (memSize < 512) { // arbitrary amount: maximum allowed by FlowerPot instruction set (256 16-bit words)
            throw Error("VM memory size too small — minimum allowed is 512 bytes")
        }
        else if (memSize > 16.MB()) {
            throw Error("Memory size too large -- maximum allowed is 16 MBytes")
        }

        hardReset()
        if (!doNotInstallInterrupts) {
            setDefaultInterrupts()
        }

        VMOpcodesRISC.invoke(this)
    }

    fun loadProgramDynamic(programImage: ProgramImage, contextID: Int? = null) {
        val newContextID = contextID ?: contextHolder.size

        val newContext = if (contextID == null) {
            contextHolder[newContextID] = Context()
            contextHolder[newContextID]!!
        }
        else {
            contextHolder[contextID]!!
        }


        val newPrgStartingPtr = malloc(programImage.bytes.size).memAddr


        newContext.st = newPrgStartingPtr + stackSize!! * 4
        newContext.pc = newContext.st + programImage.stackSize * 4


        val opcodes = programImage.bytes

        if (opcodes.size + newContext.st >= memory.size) {
            throw Error("Out of memory -- required: ${opcodes.size + newContext.st} (${opcodes.size} for program), installed: ${memory.size}")
        }


        System.arraycopy(opcodes, 0, memory, newPrgStartingPtr, opcodes.size)
        // HALT guard
        memory[opcodes.size + newContext.st] = 0
        memory[opcodes.size + newContext.st + 1] = 0
        memory[opcodes.size + newContext.st + 2] = 0
        memory[opcodes.size + newContext.st + 3] = 0



        warn("Program loaded; context ID: $newContextID, pc: $pcHex")
    }


    /**
     * Loads program as current context ID
     */
    fun loadProgram(programImage: ProgramImage) {
        val opcodes = programImage.bytes

        if (opcodes.size + st >= memory.size) {
            throw Error("Out of memory -- required: ${opcodes.size + st} (${opcodes.size} for program), installed: ${memory.size}")
        }

        softReset()

        val newProgramPtr = malloc(opcodes.size)


        System.arraycopy(opcodes, 0, memory, newProgramPtr.memAddr, opcodes.size)
        // HALT guard
        memory[opcodes.size + st] = 0
        memory[opcodes.size + st + 1] = 0
        memory[opcodes.size + st + 2] = 0
        memory[opcodes.size + st + 3] = 0


        pc = st + stackSize!! * 4


        warn("Program loaded; context ID: $contextID, pc: $pcHex")
    }

    /**
     * @return size of the interrupts. 0 if doNotInstallInterrupts == true
     */
    private fun setDefaultInterrupts(): Int {
        if (!doNotInstallInterrupts) {
            val assembler = Assembler(this)
            val intOOM = assembler("""
            .code;
loadhwordi r1, 024Eh; call r1, FFh; # N
loadhwordi r1, 024Fh; call r1, FFh; # O
loadhwordi r1, 024Dh; call r1, FFh; # M
loadhwordi r1, 0245h; call r1, FFh; # E
loadhwordi r1, 024Dh; call r1, FFh; # M
halt;
""").bytes
            val intSegfault = assembler("""
            .code;
loadhwordi r1, 0253h; call r1, FFh; # S
loadhwordi r1, 0245h; call r1, FFh; # E
loadhwordi r1, 0247h; call r1, FFh; # G
loadhwordi r1, 0246h; call r1, FFh; # F
loadhwordi r1, 0255h; call r1, FFh; # U
halt;
""").bytes
            val intDivZero = assembler("""
            .code;
loadhwordi r1, 0244h; call r1, FFh; # D
loadhwordi r1, 0249h; call r1, FFh; # I
loadhwordi r1, 0256h; call r1, FFh; # V
loadhwordi r1, 022Fh; call r1, FFh; # /
loadhwordi r1, 0230h; call r1, FFh; # 0
halt;
""").bytes
            val intIllegalOp = assembler("""
            .code;
loadhwordi r1, 0249h; call r1, FFh; # I
loadhwordi r1, 024Ch; call r1, FFh; # L
loadhwordi r1, 024Ch; call r1, FFh; # L
loadhwordi r1, 024Fh; call r1, FFh; # O
loadhwordi r1, 0250h; call r1, FFh; # P
halt;
""").bytes
            val intStackOverflow = assembler("""
            .code;
loadhwordi r1, 0253h; call r1, FFh; # S
loadhwordi r1, 0254h; call r1, FFh; # T
loadhwordi r1, 024Fh; call r1, FFh; # O
loadhwordi r1, 0256h; call r1, FFh; # V
loadhwordi r1, 0246h; call r1, FFh; # F
halt;
""").bytes
            val intMathFuck = assembler("""
            .code;
loadhwordi r1, 024Dh; call r1, FFh; # M
loadhwordi r1, 0254h; call r1, FFh; # T
loadhwordi r1, 0246h; call r1, FFh; # F
loadhwordi r1, 0243h; call r1, FFh; # C
loadhwordi r1, 024Bh; call r1, FFh; # K
halt;
""").bytes

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

            r3 = 0

            r1 = intOOMPtr.memAddr
            r2 = INT_OUT_OF_MEMORY * 4
            VMOpcodesRISC.STOREWORD(2, 1, 3)
            r1 = intSegfaultPtr.memAddr
            r2 = INT_SEGFAULT * 4
            VMOpcodesRISC.STOREWORD(2, 1, 3)
            r1 = intDivZeroPtr.memAddr
            r2 = INT_DIV_BY_ZERO * 4
            VMOpcodesRISC.STOREWORD(2, 1, 3)
            r1 = intIllegalOpPtr.memAddr
            r2 = INT_ILLEGAL_OP * 4
            VMOpcodesRISC.STOREWORD(2, 1, 3)
            r1 = intStackOvflPtr.memAddr
            r2 = INT_STACK_OVERFLOW * 4
            VMOpcodesRISC.STOREWORD(2, 1, 3)
            r1 = intMathErrPtr.memAddr
            r2 = INT_MATH_ERROR * 4
            VMOpcodesRISC.STOREWORD(2, 1, 3)


            return intOOM.size + intSegfault.size + intDivZero.size + intIllegalOp.size + intStackOverflow.size + intMathFuck.size
        }

        return 0;
    }

    fun softReset() {
        println("[TerranVM] SOFT RESET")

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

        cp = 0
        pc = 0
        sp = 0
        lr = 0
        st = 0
        //... but don't reset the uptime
        resumeExec()
        yieldRequested = false
    }


    fun hardReset() {
        softReset()

        println("[TerranVM] IT WAS HARD RESET ACTUALLY")


        // reset system uptime timer
        uptimeHolder = 0L

        // wipe memory
        for (i in 0 until memSize step 4) {
            System.arraycopy(bytes_00000000, 0, memory, i, 4)
        }
    }


    var delayInMills: Int? = null
    var instPerMill: Int? = null


    fun execDebugMain(any: Any?) { if (DEBUG) println(any) }
    fun execDebugError(any: Any?) { if (ERROR) System.err.println(any) }

    private var pauseRequested = false
    private var yieldRequested = false
    var yieldFlagged = false
    var isPaused = false // is this actually paused?
        private set

    fun pauseExec() {
        pauseRequested = true
    }

    fun resumeExec() {
        vmThread?.resume()
        isPaused = false
    }

    /**
     * resume execution with resumeExec()
     */
    fun requestToYield() {
        yieldRequested = true
    }


    val pcHex: String; get() = pc.toLong().and(0xffffffff).toString(16).toUpperCase() + "h"

    var vmThread: Thread? = null
        private set

    //private var runcnt = 0

    @Synchronized override fun run() {

        execDebugMain("Execution stanted; PC: $pcHex")


        isRunning = true
        uptimeHolder = System.currentTimeMillis()


        while (!terminate) {
            //if (DEBUG && runcnt >= 500) break
            //runcnt++


            if (vmThread == null) {
                vmThread = Thread.currentThread()
            }


            //print("["); (userSpaceStart!!..849).forEach { print("${memory[it].toUint()} ") }; println("]")

            if (pauseRequested) {
                println("[TerranVM] execution paused")
                isPaused = true
                vmThread!!.suspend() // fuck it, i'll use this anyway
                pauseRequested = false
            }

            if (yieldRequested && yieldFlagged) {
                println("[TerranVM] VM is paused due to yield request")
                isPaused = true
                vmThread!!.suspend() // fuck it, i'll use this anyway
                yieldRequested = false
                yieldFlagged = false
            }



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


            execDebugMain("pc: $pcHex; opcode: ${opcode.toReadableBin()}; ${opcode.toReadableOpcode()}")


            // execute
            pc += 4

            // invoke function
            try {
                VMOpcodesRISC.decodeAndExecute(opcode)
            }
            catch (oom: ArrayIndexOutOfBoundsException) {
                execDebugError("[TBASRT] out-of-bound memory address access: from opcode ${opcode.toReadableBin()}; ${opcode.toReadableOpcode()}")
                execDebugError("r1: $r1; ${r1.to8HexString()}; ${readregFloat(1)}f")
                execDebugError("r2: $r2; ${r2.to8HexString()}; ${readregFloat(2)}f")
                execDebugError("r3: $r3; ${r3.to8HexString()}; ${readregFloat(3)}f")
                execDebugError("r4: $r4; ${r4.to8HexString()}; ${readregFloat(4)}f")
                execDebugError("r5: $r5; ${r5.to8HexString()}; ${readregFloat(5)}f")
                execDebugError("r6: $r6; ${r6.to8HexString()}; ${readregFloat(6)}f")
                execDebugError("r7: $r7; ${r7.to8HexString()}; ${readregFloat(7)}f")
                execDebugError("r8: $r8; ${r8.to8HexString()}; ${readregFloat(8)}f")
                execDebugError("pc: $pc; ${pc.toHexString()}")
                oom.printStackTrace()
                interruptOutOfMem()
            }
            catch (e: Exception) {
                execDebugError("r1: $r1; ${r1.to8HexString()}; ${readregFloat(1)}f")
                execDebugError("r2: $r2; ${r2.to8HexString()}; ${readregFloat(2)}f")
                execDebugError("r3: $r3; ${r3.to8HexString()}; ${readregFloat(3)}f")
                execDebugError("r4: $r4; ${r4.to8HexString()}; ${readregFloat(4)}f")
                execDebugError("r5: $r5; ${r5.to8HexString()}; ${readregFloat(5)}f")
                execDebugError("r6: $r6; ${r6.to8HexString()}; ${readregFloat(6)}f")
                execDebugError("r7: $r7; ${r7.to8HexString()}; ${readregFloat(7)}f")
                execDebugError("r8: $r8; ${r8.to8HexString()}; ${readregFloat(8)}f")
                execDebugError("pc: $pc; ${pc.toHexString()}")
                e.printStackTrace()
            }


            if (opcode == 0) {
                execDebugMain("HALT at PC $pcHex")
            }




            if (delayInMills != null) {
                Thread.sleep(delayInMills!!.toLong())
            }
        }

        isRunning = false
    }

    fun performContextSwitch(contextID: Int) {
        if (!contextHolder.containsKey(contextID)) {
            // if specified context is not there, create one right away
            contextHolder[contextID] = Context()
        }

        context = contextHolder[contextID]!!


        warn("Context switch ${this.contextID} --> $contextID")


        this.contextID = contextID



        if (delayInMills != null) {
            Thread.sleep(delayInMills!!.toLong())
        }
    }






    ///////////////
    // CONSTANTS //
    ///////////////
    companion object {
        val charset: Charset = Charset.forName("CP437")

        const val interruptCount = 24
        
        const val INT_DIV_BY_ZERO = 0
        const val INT_ILLEGAL_OP = 1
        const val INT_OUT_OF_MEMORY = 2
        const val INT_STACK_OVERFLOW = 3
        const val INT_MATH_ERROR = 4
        const val INT_SEGFAULT = 5
        const val INT_KEYPRESS = 6
        const val INT_PERI_INPUT = 7
        const val INT_PERI_OUTPUT = 8
        const val INT_INTERRUPT = 9
        const val INT_SERIAL0 = 10
        const val INT_SERIAL1 = 11

        const val INT_RASTER_FBUFFER = 16 // required to fire interrupt following every screen refresh



        const val IRQ_SYSTEM_TIMER = 0
        const val IRQ_KEYBOARD = 1
        const val IRQ_RTC = 2
        const val IRQ_PRIMARY_DISPLAY = 3
        const val IRQ_SERIAL_1 = 6
        const val IRQ_SERIAL_2 = 7
        const val IRQ_DISK_A = 8
        const val IRQ_DISK_B = 9
        const val IRQ_DISK_C = 10
        const val IRQ_DISK_D = 11
        const val IRQ_SCSI_1 = 12
        const val IRQ_SCSI_2 = 13
        const val IRQ_SCSI_3 = 14
        const val IRQ_SCSI_4 = 15
        const val IRQ_BIOS = 255
    }


    private fun warn(any: Any?) { if (!suppressWarnings) println("[TBASRT] WARNING: $any") }

    // Interrupt handlers (its just JMPs) //
    fun interruptDivByZero() { VMOpcodesRISC.JSRI(memSliceBySize(INT_DIV_BY_ZERO * 4, 4).toLittleInt().ushr(2)) }
    fun interruptIllegalOp() { VMOpcodesRISC.JSRI(memSliceBySize(INT_ILLEGAL_OP * 4, 4).toLittleInt().ushr(2)) }
    fun interruptOutOfMem()  { VMOpcodesRISC.JSRI(memSliceBySize(INT_OUT_OF_MEMORY * 4, 4).toLittleInt().ushr(2)) }
    fun interruptStackOverflow() { VMOpcodesRISC.JSRI(memSliceBySize(INT_STACK_OVERFLOW * 4, 4).toLittleInt().ushr(2)) }
    fun interruptMathError() { VMOpcodesRISC.JSRI(memSliceBySize(INT_MATH_ERROR * 4, 4).toLittleInt().ushr(2)) }
    fun interruptSegmentationFault() { VMOpcodesRISC.JSRI(memSliceBySize(INT_SEGFAULT * 4, 4).toLittleInt().ushr(2)) }
    fun interruptKeyPress() { VMOpcodesRISC.JSRI(memSliceBySize(INT_KEYPRESS * 4, 4).toLittleInt().ushr(2)) }
    fun interruptPeripheralInput() { VMOpcodesRISC.JSRI(memSliceBySize(INT_PERI_INPUT * 4, 4).toLittleInt().ushr(2)) }
    fun interruptPeripheralOutput() { VMOpcodesRISC.JSRI(memSliceBySize(INT_PERI_OUTPUT * 4, 4).toLittleInt().ushr(2)) }
    fun interruptStopExecution() { VMOpcodesRISC.JSRI(memSliceBySize(INT_INTERRUPT * 4, 4).toLittleInt().ushr(2)) }




    data class Context(
            var r1: Int = 0,
            var r2: Int = 0,
            var r3: Int = 0,
            var r4: Int = 0,
            var r5: Int = 0,
            var r6: Int = 0,
            var r7: Int = 0,
            var r8: Int = 0,
            var cp: Int = 0, // compare register
            var pc: Int = 0, // program counter
            var sp: Int = 0, // stack pointer
            var lr: Int = 0, // link register
            var st: Int = 0  // starting point, word-wise
    )
}

fun Int.KB() = this shl 10
fun Int.MB() = this shl 20

fun Int.to8HexString() = this.toLong().and(0xffffffff).toString(16).padStart(8, '0').toUpperCase() + "h"
fun Int.toHexString() = this.toLong().and(0xffffffff).toString(16).toUpperCase() + "h"

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

fun Int.roundToFour() = this + ((4 - (this % 4)) % 4)
