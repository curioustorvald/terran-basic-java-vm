package net.torvald.tbasic

import net.torvald.tbasic.runtime.*
import kotlin.experimental.and


typealias Register = Int

/**
 * Register usage:
 *
 * * r1-r4: function arguments
 * * b1-b4: function argument supplements (type marker, etc)
 *
 * Created by minjaesong on 2017-05-10.
 */
object TBASOpcodes {

    val TBASVERSION = 0.4

    lateinit var vm: VM

    fun invoke(vm: VM) {
        this.vm = vm

        //initTBasicEnv()
        //resetTBASVarTable()
    }

    /*
    rudimentary Hello World:

    LOADSTR  "Hello, world!\n"   r1
    PRINTSTR

     */

    fun initTBasicEnv() {
        // load things for TBASIC

        // Compose Interrupt Vector Table to display errors //
        val writePointer = VM.Pointer(vm, 0, VM.Pointer.PointerType.INT32, true)

        run { // --> SYNTAX ERROR (invalid opcode??)
            val syntaxErrorPtr = vm.makeBytesDB(byteArrayOf(TBASOpcodes.opcodesList["LOADSTR"]!!) + 1 + "?SYNTAX\tERROR\n".toCString() + byteArrayOf(TBASOpcodes.opcodesList["PRINTSTR"]!!))
            // write INT32 using yet another pointer
            writePointer.memAddr = VM.INT_ILLEGAL_OP * 4
            writePointer.write(syntaxErrorPtr.memAddr)
        }

        run { // --> DIVISION BY ZERO ERROR (invalid opcode??)
            val div0Ptr = vm.makeBytesDB(byteArrayOf(TBASOpcodes.opcodesList["LOADSTR"]!!) + 1 + "?DIVISION BY ZERO\tERROR\n".toCString() + byteArrayOf(TBASOpcodes.opcodesList["PRINTSTR"]!!))
            // write INT32 using yet another pointer
            writePointer.memAddr = VM.INT_DIV_BY_ZERO * 4
            writePointer.write(div0Ptr.memAddr)
        }

        run { // --> OUT OF MEMORY ERROR
            val oomPtr = vm.makeBytesDB(byteArrayOf(TBASOpcodes.opcodesList["LOADSTR"]!!) + 1 + "?OUT OF MEMORY\tERROR\n".toCString() + byteArrayOf(TBASOpcodes.opcodesList["PRINTSTR"]!!))
            // write INT32 using yet another pointer
            writePointer.memAddr = VM.INT_OUT_OF_MEMORY * 4
            writePointer.write(oomPtr.memAddr)
        }
    }

    fun resetTBASVarTable() {
        val ptrM_PI = vm.malloc(SIZEOF_NUMBER)
        ptrM_PI.type = VM.Pointer.PointerType.DOUBLE
        ptrM_PI.write(3.141592653589793)
        val ptrM_2PI = vm.malloc(SIZEOF_NUMBER)
        ptrM_2PI.type = VM.Pointer.PointerType.DOUBLE
        ptrM_2PI.write(6.283185307179586)
        val ptrM_E = vm.malloc(SIZEOF_NUMBER)
        ptrM_E.type = VM.Pointer.PointerType.DOUBLE
        ptrM_E.write(2.718281828459045)
        val ptrM_ROOT2 = vm.malloc(SIZEOF_NUMBER)
        ptrM_ROOT2.type = VM.Pointer.PointerType.DOUBLE
        ptrM_ROOT2.write(1.414213562373095)
        val ptrTRUE = vm.malloc(SIZEOF_BYTE)
        ptrM_PI.type = VM.Pointer.PointerType.BOOLEAN
        ptrM_PI.write(true)
        val ptrFALSE = vm.malloc(SIZEOF_BYTE)
        ptrM_PI.type = VM.Pointer.PointerType.BOOLEAN
        ptrM_PI.write(false)
        val ptrNIL = vm.malloc(SIZEOF_BYTE)
        ptrM_PI.type = VM.Pointer.PointerType.VOID
        ptrM_PI.write(false)
        val ptr_VERSION = vm.malloc(SIZEOF_NUMBER)
        ptrM_PI.type = VM.Pointer.PointerType.DOUBLE
        ptrM_PI.write(TBASVERSION)

        vm.varTable.clear()
        vm.varTable.put("M_PI", TBASNumber(ptrM_PI))
        vm.varTable.put("M_2PI", TBASNumber(ptrM_2PI))
        vm.varTable.put("M_E", TBASNumber(ptrM_E))
        vm.varTable.put("M_ROOT2", TBASNumber(ptrM_ROOT2))
        vm.varTable.put("TRUE", TBASBoolean(ptrTRUE))
        vm.varTable.put("FALSE", TBASBoolean(ptrFALSE))
        vm.varTable.put("NIL", TBASNil(ptrNIL))
        vm.varTable.put("_VERSION", TBASNumber(ptr_VERSION))
    }


    // variable control //

    fun CLR() { resetTBASVarTable() }

    // flow control //

    fun PUSH(addr: Int) { vm.callStack[vm.sp++] = addr }
    fun POP() { vm.lr = vm.callStack[vm.sp--] }

    fun RETURN() { POP(); vm.pc = vm.lr }
    fun GOSUB(addr: Int) { PUSH(vm.pc); vm.pc = addr }
    fun JMP(addr: Int) { vm.pc = addr }

    fun JZ(addr: Int) { if (vm.m1 == 0) JMP(addr) }
    fun JNZ(addr: Int) { if (vm.m1 != 0) JMP(addr) }
    fun JGT(addr: Int) { if (vm.m1 > 0) JMP(addr) }
    fun JLS(addr: Int) { if (vm.m1 < 0) JMP(addr) }

    fun HALT() { vm.terminate = true }

    
    // stdIO //
    /**
     * prints any byte (stored as Number) on r1 as a character. If r1 has a number of 33.0, '!' will be printed.
     */
    fun PUTCHAR() { vm.stdout.write(java.lang.Double.doubleToRawLongBits(vm.r1).toInt()); vm.stdout.flush() }
    /**
     * print a string. String is prepared to r1 as pointer.
     */
    fun PRINTSTR() {
        val string = TBASString(VM.Pointer(vm, java.lang.Double.doubleToRawLongBits(vm.r1).toInt()))
        vm.strCntr = 0 // string counter

        while (true) {
            vm.r1 = java.lang.Double.longBitsToDouble(vm.memory[string.pointer.memAddr + vm.strCntr].toUint().toLong())

            if (vm.r1 == 0.0) break

            PUTCHAR()
            vm.strCntr++
        }
    }
    fun GETCHAR() { vm.r1 = vm.stdin.read().toDouble() }
    /** Any pre-existing variable will be overwritten. */
    fun READSTR(varname: String) {
        val maxStrLen = 255 // plus null terminator
        val readTerminator = '\n'.toInt().toDouble()

        val strPtr = vm.calloc(maxStrLen + 1)
        val strPtrInitPos = strPtr.memAddr
        vm.r1 = -1.0

        while (vm.r1 != readTerminator && strPtr.memAddr - strPtrInitPos <= maxStrLen) {
            GETCHAR()
            strPtr.write(vm.r1.toByte())
            strPtr.inc()
            PUTCHAR() // print out what the hell the user has just hit
        }

        // truncate and free remaining bytes
        vm.reduceAllocatedBlock(strPtrInitPos..strPtrInitPos + maxStrLen, strPtrInitPos + maxStrLen - strPtr.memAddr)


        LOADPTR(strPtr.memAddr, 1)
        SETVARIABLE(varname)
    }

    /**
     * prints out whatever number in r1 register
     */
    fun PRINTNUM() {
        val oldnum = vm.r1 // little cheat, but whatever.

        val str = vm.r1.toString()

        // LOADSTR
        try {
            val strPtr = vm.makeStringDB(str)
            // LOADPTR
            vm.writereg(1, java.lang.Double.longBitsToDouble(strPtr.memAddr.toLong()))
            if (strPtr.memAddr == -1) {
                vm.writebreg(1, 0b10.toByte())
            } else {
                vm.writebreg(1, 0b11.toByte())
            }
        }
        catch (e: OutOfMemoryError) {
            e.printStackTrace(System.out)
            vm.interruptOutOfMem()
        }
        // END LOADSTR

        val string = TBASString(VM.Pointer(vm, java.lang.Double.doubleToRawLongBits(vm.r1).toInt()))
        vm.strCntr = 0 // string counter

        while (true) {
            vm.r1 = java.lang.Double.longBitsToDouble(vm.memory[string.pointer.memAddr + vm.strCntr].toUint().toLong())

            if (vm.r1 == 0.0) break

            PUTCHAR()
            vm.strCntr++
        }

        vm.r1 = oldnum

        vm.freeBlock(string)
    }



    // MATHEMATICAL OPERATORS //

    /**
     * r1 <- r2 op r3 (no vararg)
     */
    fun ADD() { vm.r1 = vm.r2 + vm.r3 }
    fun SUB() { vm.r1 = vm.r2 - vm.r3 }
    fun MUL() { vm.r1 = vm.r2 * vm.r3 }
    fun DIV() { vm.r1 = vm.r2 / vm.r3 }
    fun POW() { vm.r1 = Math.pow(vm.r2, vm.r3) }
    fun MOD() { vm.r1 = Math.floorMod(vm.r2.toLong(), vm.r3.toLong()).toDouble() } // FMOD

    fun SHL()  { vm.r1 = (vm.r2.toInt() shl  vm.r3.toInt()).toDouble() }
    fun SHR()  { vm.r1 = (vm.r2.toInt() shr  vm.r3.toInt()).toDouble() }
    fun USHR() { vm.r1 = (vm.r2.toInt() ushr vm.r3.toInt()).toDouble() }
    fun AND()  { vm.r1 = (vm.r2.toInt() and  vm.r3.toInt()).toDouble() }
    fun OR()   { vm.r1 = (vm.r2.toInt() or   vm.r3.toInt()).toDouble() }
    fun XOR()  { vm.r1 = (vm.r2.toInt() xor  vm.r3.toInt()).toDouble() }

    fun CMP()  { vm.m1 = if (vm.r2 == vm.r3) 0 else if (vm.r2 > vm.r3) 1 else -1 }

    /**
     * r1 <- r2 (no vararg)
     */
    fun ABS()   { vm.r1 = Math.abs  (vm.r2) }
    fun SIN()   { vm.r1 = Math.sin  (vm.r2) }
    fun COS()   { vm.r1 = Math.cos  (vm.r2) }
    fun TAN()   { vm.r1 = Math.tan  (vm.r2) }
    fun FLOOR() { vm.r1 = Math.floor(vm.r2) }
    fun CEIL()  { vm.r1 = Math.ceil (vm.r2) }
    fun ROUND() { vm.r1 = Math.round(vm.r2).toDouble() }
    fun LOG()   { vm.r1 = Math.log  (vm.r2) }
    fun INT()   { if (vm.r2 >= 0.0) FLOOR() else CEIL() }
    fun RND()   { vm.r1 = Math.random() }
    fun SGN()   { vm.r1 = Math.signum(vm.r2) }
    fun SQRT() { LOADNUM(3, 2.0); POW() }
    fun CBRT() { LOADNUM(3, 3.0); POW() }
    fun INV() { MOV(2, 3); LOADNUM(2, 1.0); DIV() }
    fun RAD() { LOADRAWNUM(3, 0x4081ABE4B73FEFB5L); DIV() } // r1 <- r2 / (180.0 * PI)

    fun NOT() { vm.r1 = vm.r2.toInt().inv().toDouble() }

    
    fun INC1() { vm.r1 += 1.0 }
    fun INC2() { vm.r2 += 1.0 }
    fun INC3() { vm.r3 += 1.0 }
    fun INC4() { vm.r4 += 1.0 }
    fun INC5() { vm.r5 += 1.0 }
    fun INC6() { vm.r6 += 1.0 }
    fun INC7() { vm.r7 += 1.0 }
    fun INC8() { vm.r8 += 1.0 }
    fun INCM() { vm.m1 += 1 }

    fun DEC1() { vm.r1 -= 1.0 }
    fun DEC2() { vm.r2 -= 1.0 }
    fun DEC3() { vm.r3 -= 1.0 }
    fun DEC4() { vm.r4 -= 1.0 }
    fun DEC5() { vm.r5 -= 1.0 }
    fun DEC6() { vm.r6 -= 1.0 }
    fun DEC7() { vm.r7 -= 1.0 }
    fun DEC8() { vm.r8 -= 1.0 }
    fun DECM() { vm.m1 -= 1 }
    
    

    // INTERNAL //

    fun NOP() { }

    fun INTERRUPT(interrupt: Int) {
        JMP(interrupt * 4)
    }

    /** memory(r2) <- r1 */
    fun POKE() { vm.memory[vm.r2.toInt()] = vm.r1.toByte() }
    /** r1 <- data in memory addr r2 */
    fun PEEK() { vm.r1 = vm.memory[vm.r2.toInt()].toUint().toDouble() }

    /** Peripheral(r3).memory(r2) <- r1 */
    fun POKEPERI() { vm.peripherals[vm.r3.toInt()].memory[vm.r2.toInt()] = vm.r1.toByte() }
    /** r1 <- data in memory addr r2 of peripheral r3 */
    fun PEEKPERI() { vm.r1 = vm.peripherals[vm.r3.toInt()].memory[vm.r2.toInt()].toUint().toDouble() }


    /** Memory copy - source: r2, destination: r3, length: r4 */
    fun MEMCPY() { System.arraycopy(vm.memory, vm.r2.toInt(), vm.memory, vm.r3.toInt(), vm.r4.toInt()) }
    /** Memory copy - peripheral index: r5, source (machine): r2, destination (peripheral): r3, length: r4 */
    fun MEMCPYPERI() { System.arraycopy(vm.memory, vm.r2.toInt(), vm.peripherals[vm.r5.toInt()], vm.r3.toInt(), vm.r4.toInt()) }


    /*
     b registers:

     0 - true (1) if INT, false (0) if Number
     1 - true (1) if Pointer (NOTE: all valid pointer will have '11' as LSB.
                              '10' (pointer but is number) stands for NULL POINTER)
     2, 3, 4 - Type ID
     */

    /**
     * r1 <- Number (Double)
     *
     * @param register 1-4 for r1-r4
     */
    fun LOADNUM(register: Register, number: Double) {
        vm.writereg(register, number)
        vm.writebreg(register, 0.toByte())
    }
    
    fun LOADMNUM(number: Int) {
        vm.m1 = number
    }

    fun REGTOM() {
        vm.m1 = vm.r1.toInt()
    }

    /**
     * r1 <- Int (raw Double value) transformed to Double
     *
     * @param register 1-4 for r1-r4
     */
    fun LOADRAWNUM(register: Register, num_as_bytes: Long) {
        vm.writereg(register, java.lang.Double.longBitsToDouble(num_as_bytes))
        vm.writebreg(register, 1.toByte())
    }

    /**
     * Loads pointer's pointing address to r1, along with the marker that states r1 now holds memory address
     */
    fun LOADPTR(register: Register, addr: Int) {
        try {
            vm.writereg(register, java.lang.Double.longBitsToDouble(addr.toLong()))
            if (addr == -1) {
                vm.writebreg(register, 0b10.toByte())
            } else {
                vm.writebreg(register, 0b11.toByte())
            }
        }
        catch (e: OutOfMemoryError) {
            e.printStackTrace(System.out)
            vm.interruptOutOfMem()
        }
    }

    fun LOADSTR(register: Register, string: ByteArray) {
        try {
            val strPtr = vm.makeStringDB(string)
            LOADPTR(register, strPtr.memAddr)
        }
        catch (e: OutOfMemoryError) {
            e.printStackTrace(System.out)
            vm.interruptOutOfMem()
        }
    }

    /**
     * r(to) <- r(from)
     *
     * @param from 1-4 for r1-r4
     * @param to 1-4 for r1-r4
     */
    fun MOV(from: Register, to: Register) {
        vm.writereg(to, vm.readreg(from))
        vm.writebreg(to, vm.readbreg(from))
    }

    fun XCHG(a: Register, b: Register) {
        val rTmp = vm.readreg(b)
        val bTmp = vm.readbreg(b)
        vm.writereg(b, vm.readreg(a))
        vm.writebreg(b, vm.readbreg(a))
        vm.writereg(a, rTmp)
        vm.writebreg(a, bTmp)
    }

    /**
     * load variable to r1 as pointer. If the variable does not exist, null pointer will be loaded instead.
     */
    fun LOADVARIABLE(identifier: String) { LOADPTR(1, vm.varTable[identifier]?.pointer?.memAddr ?: -1) }
    /**
     * save whatever on r1 (either an Immediate or Pointer) to variables table
     */
    fun SETVARIABLE(identifier: String) {
        val isPointer = vm.b1.and(0b10) != 0.toByte()
        val typeIndex = vm.b1.and(0b11100).toInt().ushr(2)

        if (!isPointer) {
            val byteSize = getByteSizeOfType(typeIndex)
            val varPtr = vm.malloc(byteSize)
            varPtr.type = getPointerTypeFromID(typeIndex)

            if (byteSize == 8)
                varPtr.write(vm.r1)
            else if (byteSize == 4) // just in case
                varPtr.write(java.lang.Double.doubleToRawLongBits(vm.r1).and(0xFFFFFFFF).toInt())
            else if (byteSize == 1)
                varPtr.write(java.lang.Double.doubleToRawLongBits(vm.r1).and(0xFF).toByte())


            val tbasValue: TBASValue = when(typeIndex) {
                TYPE_NIL -> TBASNil(varPtr)
                TYPE_NUMBER -> TBASNumber(varPtr)
                TYPE_BOOLEAN -> TBASBoolean(varPtr)
                else -> throw InternalError("String is Pointer!")
            }

            vm.varTable[identifier] = tbasValue
        }
        else {
            // String
            val ptr = VM.Pointer(vm, java.lang.Double.doubleToRawLongBits(vm.r1).toInt())
            vm.varTable[identifier] = TBASString(ptr)
        }
    }


    fun SLP(millisec: Number) {
        Thread.sleep(millisec.toLong())
    }

    /** r1 <- VM memory size in bytes */
    fun MEM() { vm.r1 = vm.memory.size.toDouble() }
    /** r1 <- System uptime in milliseconds */
    fun UPTIME() { vm.r1 = vm.uptime.toDouble() }





    private fun getByteSizeOfType(typeID: Int): Int = when(typeID) {
        TYPE_NIL -> 1
        TYPE_BOOLEAN -> 1
        TYPE_BYTES -> 1
        TYPE_NUMBER -> 8
        else -> throw IllegalArgumentException()
    }
    private fun getPointerTypeFromID(typeID: Int): VM.Pointer.PointerType = when(typeID) {
        TYPE_NIL -> VM.Pointer.PointerType.VOID
        TYPE_BOOLEAN -> VM.Pointer.PointerType.BOOLEAN
        TYPE_BYTES -> VM.Pointer.PointerType.BYTE
        TYPE_NUMBER -> VM.Pointer.PointerType.INT64
        else -> throw IllegalArgumentException()
    }

    private val TYPE_NIL = 0b000
    private val TYPE_BOOLEAN = 0b001
    private val TYPE_NUMBER = 0b010
    private val TYPE_BYTES = 0b011
    private val TYPE_STRING = 0b100 // implies "pointer: true"

    /*

    10 A = 4 * 10 + 2
    >>>  AST: setvariable["A", 4 * 10 + 2]
    >>>  LOADNUM 4.0, 2
         LOADNUM 10.0, 3
         MUL
         MOV 1, 2
         LOADNUM 2.0, 3
         ADD
         SETVARIABLE A
     */

    private fun Byte.toUint() = java.lang.Byte.toUnsignedInt(this)




    val SIZEOF_BYTE = VM.Pointer.sizeOf(VM.Pointer.PointerType.BYTE)
    //val SIZEOF_POINTER = VM.Pointer.sizeOf(VM.Pointer.PointerType.INT32)
    val SIZEOF_INT32 = VM.Pointer.sizeOf(VM.Pointer.PointerType.INT32)
    val SIZEOF_NUMBER = VM.Pointer.sizeOf(VM.Pointer.PointerType.INT64)
    val READ_UNTIL_ZERO = -2

    val opcodesList = hashMapOf<String, Byte>(
            "NOP" to 7.toByte(),

            "ADD" to 1.toByte(),
            "SUB" to 2.toByte(),
            "MUL" to 3.toByte(),
            "DIV" to 4.toByte(),
            "POW" to 5.toByte(),
            "MOD" to 6.toByte(),

            "HALT" to 0.toByte(),

            "JMP"   to 8.toByte(),
            "GOSUB"  to 9.toByte(),
            "RETURN" to 10.toByte(),
            "PUSH"   to 11.toByte(),
            "POP"    to 12.toByte(),
            "MOV"    to 13.toByte(),
            "POKE"   to 14.toByte(),
            "PEEK"   to 15.toByte(),

            "SHL"  to 16.toByte(),
            "SHR"  to 17.toByte(),
            "USHR" to 18.toByte(),
            "AND"  to 19.toByte(),
            "OR"   to 20.toByte(),
            "XOR"  to 21.toByte(),
            "NOT"  to 22.toByte(),

            "ABS"   to 23.toByte(),
            "SIN"   to 24.toByte(),
            "FLOOR" to 25.toByte(),
            "CEIL"  to 26.toByte(),
            "ROUND" to 27.toByte(),
            "LOG"   to 28.toByte(),
            "INT"   to 29.toByte(),
            "RND"   to 20.toByte(),
            "SGN"   to 31.toByte(),
            "SQRT"  to 32.toByte(),
            "CBRT"  to 33.toByte(),
            "INV"   to 34.toByte(),
            "RAD"   to 35.toByte(),

            "INTERRUPT" to 36.toByte(),

            "LOADNUM" to 37.toByte(),
            "LOADRAWNUM" to 38.toByte(),
            "LOADPTR" to 39.toByte(),
            "LOADVARIABLE" to 40.toByte(),
            "SETVARIABLE" to 41.toByte(),
            "LOADMNUM" to 42.toByte(),
            "LOADSTR" to 43.toByte(),

            "PUTCHAR" to 44.toByte(),
            "PRINTSTR" to 45.toByte(),
            "PRINTNUM" to 46.toByte(),

            "JZ" to 48.toByte(),
            "JNZ" to 49.toByte(),
            "JGT" to 50.toByte(),
            "JLS" to 51.toByte(),

            "CMP" to 52.toByte(),
            "XCHG" to 53.toByte(),

            "REGTOM" to 54.toByte(),

            "INC1" to 55.toByte(),
            "INC2" to 56.toByte(),
            "INC3" to 57.toByte(),
            "INC4" to 58.toByte(),
            "INC5" to 59.toByte(),
            "INC6" to 60.toByte(),
            "INC7" to 61.toByte(),
            "INC8" to 62.toByte(),
            "INCM" to 63.toByte(),

            "DEC1" to 64.toByte(),
            "DEC2" to 65.toByte(),
            "DEC3" to 66.toByte(),
            "DEC4" to 67.toByte(),
            "DEC5" to 68.toByte(),
            "DEC6" to 69.toByte(),
            "DEC7" to 70.toByte(),
            "DEC8" to 71.toByte(),
            "DECM" to 72.toByte(),

            "MEMCPY" to 73.toByte(),
            "MEMCPYPERI" to 74.toByte(),

            "POKEPERI" to 75.toByte(),
            "PEEKPERI" to 76.toByte(),

            "MEM" to 77.toByte(),

            "SLP" to 78.toByte(),

            "CLR" to 79.toByte(),

            "UPTIME" to 80.toByte()

    )

    val NOP = 7.toByte()

    val ADD = 1.toByte()
    val SUB = 2.toByte()
    val MUL = 3.toByte()
    val DIV = 4.toByte()
    val POW = 5.toByte()
    val MOD = 6.toByte()

    val HALT = 0.toByte()

    val JMP   = 8.toByte()
    val GOSUB  = 9.toByte()
    val RETURN = 10.toByte()
    val PUSH   = 11.toByte()
    val POP    = 12.toByte()
    val MOV    = 13.toByte()
    val POKE   = 14.toByte()
    val PEEK   = 15.toByte()

    val SHL  = 16.toByte()
    val SHR  = 17.toByte()
    val USHR = 18.toByte()
    val AND  = 19.toByte()
    val OR   = 20.toByte()
    val XOR  = 21.toByte()
    val NOT  = 22.toByte()

    val ABS   = 23.toByte()
    val SIN   = 24.toByte()
    val FLOOR = 25.toByte()
    val CEIL  = 26.toByte()
    val ROUND = 27.toByte()
    val LOG   = 28.toByte()
    val INT   = 29.toByte()
    val RND   = 20.toByte()
    val SGN   = 31.toByte()
    val SQRT  = 32.toByte()
    val CBRT  = 33.toByte()
    val INV   = 34.toByte()
    val RAD   = 35.toByte()

    val INTERRUPT = 36.toByte()

    val LOADNUM = 37.toByte()
    val LOADRAWNUM = 38.toByte()
    val LOADPTR = 39.toByte()
    val LOADVARIABLE = 40.toByte()
    val SETVARIABLE = 41.toByte()
    val LOADMNUM = 42.toByte()
    val LOADSTR = 43.toByte()

    val PUTCHAR = 44.toByte()
    val PRINTSTR = 45.toByte()
    val PRINTNUM = 46.toByte()

    val JZ = 48.toByte()
    val JNZ = 49.toByte()
    val JGT = 50.toByte()
    val JLS = 51.toByte()

    val CMP = 52.toByte()
    val XCHG = 53.toByte()

    val REGTOM = 54.toByte() // m1 <- r1.toInt()

    val INC1 = 55.toByte()
    val INC2 = 56.toByte()
    val INC3 = 57.toByte()
    val INC4 = 58.toByte()
    val INC5 = 59.toByte()
    val INC6 = 60.toByte()
    val INC7 = 61.toByte()
    val INC8 = 62.toByte()
    val INCM = 63.toByte()

    val DEC1 = 64.toByte()
    val DEC2 = 65.toByte()
    val DEC3 = 66.toByte()
    val DEC4 = 67.toByte()
    val DEC5 = 68.toByte()
    val DEC6 = 69.toByte()
    val DEC7 = 70.toByte()
    val DEC8 = 71.toByte()
    val DECM = 72.toByte()

    val MEMCPY = 73.toByte()
    val MEMCPYPERI = 74.toByte()

    val POKEPERI = 75.toByte()
    val PEEKPERI = 76.toByte()

    val MEM = 77.toByte()

    val SLP = 78.toByte()

    val CLR = 79.toByte()

    val UPTIME = 80.toByte()

    val opcodesListInverse = HashMap<Byte, String>()
    init {
        opcodesList.keys.forEach { opcodesListInverse.put(opcodesList[it]!!, it) }
    }
    val opcodesFunList = hashMapOf<String, (List<ByteArray>) -> Unit>(
            "NOP" to fun(_) { NOP() },

            "ADD" to fun(_) { ADD() },
            "SUB" to fun(_) { SUB() },
            "MUL" to fun(_) { MUL() },
            "DIV" to fun(_) { DIV() },
            "POW" to fun(_) { POW() },
            "MOD" to fun(_) { MOD() },

            "HALT" to fun(_) { HALT() },

            "JMP"   to fun(args: List<ByteArray>) { JMP(args[0].toLittleInt()) },
            "GOSUB"  to fun(args: List<ByteArray>) { GOSUB(args[0].toLittleInt()) },
            "RETURN" to fun(_) { RETURN() },
            "PUSH"   to fun(args: List<ByteArray>) { PUSH(args[0].toLittleInt()) },
            "POP"    to fun(_) { POP() },
            "MOV"    to fun(args: List<ByteArray>) { MOV(args[0][0].toInt(), args[1][0].toInt()) },
            "POKE"   to fun(_) { POKE() },
            "PEEK"   to fun(_) { PEEK() },

            "SHL"  to fun(_) { SHL() },
            "SHR"  to fun(_) { SHR() },
            "USHR" to fun(_) { USHR() },
            "AND"  to fun(_) { AND() },
            "OR"   to fun(_) { OR() },
            "XOR"  to fun(_) { XOR() },
            "NOT"  to fun(_) { NOT() },

            "ABS"   to fun(_) { ABS() },
            "SIN"   to fun(_) { SIN() },
            "FLOOR" to fun(_) { COS() },
            "CEIL"  to fun(_) { TAN() },
            "ROUND" to fun(_) { FLOOR() },
            "LOG"   to fun(_) { CEIL() },
            "INT"   to fun(_) { ROUND() },
            "RND"   to fun(_) { LOG() },
            "SGN"   to fun(_) { INT() },
            "SQRT"  to fun(_) { RND() },
            "CBRT"  to fun(_) { SGN() },
            "INV"   to fun(_) { INV() },
            "RAD"   to fun(_) { RAD() },

            "INTERRUPT" to fun(args: List<ByteArray>) { INTERRUPT(args[0][0].toInt()) },

            "LOADNUM" to fun(args: List<ByteArray>) { LOADNUM(args[0][0].toInt(), args[1].toLittleDouble()) },
            "LOADRAWNUM" to fun(args: List<ByteArray>) { LOADRAWNUM(args[0][0].toInt(), args[1].toLittleLong()) },
            "LOADPTR" to fun(args: List<ByteArray>) { LOADPTR(args[0][0].toInt(), args[1].toLittleInt()) },
            "LOADVARIABLE" to fun(args: List<ByteArray>) { LOADVARIABLE(args[0].toString(VM.charset)) },
            "SETVARIABLE" to fun(args: List<ByteArray>) { SETVARIABLE(args[0].toString(VM.charset)) },
            "LOADMNUM" to fun(args: List<ByteArray>) { LOADMNUM(args[0].toLittleInt()) },
            "LOADSTR" to fun(args: List<ByteArray>) { LOADSTR(args[0][0].toInt(), args[1]) },

            "PUTCHAR" to fun(_) { PUTCHAR() },
            "PRINTSTR" to fun(_) { PRINTSTR() },
            "PRINTNUM" to fun(_) { PRINTNUM() },

            "JZ" to fun(args: List<ByteArray>) { JZ(args[0].toLittleInt()) },
            "JNZ" to fun(args: List<ByteArray>) { JNZ(args[0].toLittleInt()) },
            "JGT" to fun(args: List<ByteArray>) { JGT(args[0].toLittleInt()) },
            "JLS" to fun(args: List<ByteArray>) { JLS(args[0].toLittleInt()) },

            "CMP" to fun(_) { CMP() },
            "XCHG" to fun(args: List<ByteArray>) { XCHG(args[0][0].toInt(), args[1][0].toInt()) },

            "REGTOM" to fun(_) { REGTOM() },

            "INC1" to fun(_) { INC1() },
            "INC2" to fun(_) { INC2() },
            "INC3" to fun(_) { INC3() },
            "INC4" to fun(_) { INC4() },
            "INC5" to fun(_) { INC5() },
            "INC6" to fun(_) { INC6() },
            "INC7" to fun(_) { INC7() },
            "INC8" to fun(_) { INC8() },
            "INCM" to fun(_) { INCM() },

            "DEC1" to fun(_) { DEC1() },
            "DEC2" to fun(_) { DEC2() },
            "DEC3" to fun(_) { DEC3() },
            "DEC4" to fun(_) { DEC4() },
            "DEC5" to fun(_) { DEC5() },
            "DEC6" to fun(_) { DEC6() },
            "DEC7" to fun(_) { DEC7() },
            "DEC8" to fun(_) { DEC8() },
            "DECM" to fun(_) { DECM() },

            "MEMCPY" to fun(_) { MEMCPY() },
            "MEMCPYPERI" to fun(_) { MEMCPYPERI() },

            "POKEPERI" to fun(_) { POKEPERI () },
            "PEEKPERI" to fun(_) { PEEKPERI() },

            "MEM" to fun(_) { MEM() },

            "SLP" to fun(args: List<ByteArray>) { SLP(args[0].toLittleDouble()) },

            "CLR" to fun(_) { CLR() },

            "UPTIME" to fun(_) { UPTIME() }

    )
    val opcodeArgsList = hashMapOf<String, IntArray>( // null == 0 operands
            "MOV" to intArrayOf(SIZEOF_BYTE, SIZEOF_BYTE),
            "XCHG" to intArrayOf(SIZEOF_BYTE, SIZEOF_BYTE),
            "LOADNUM" to intArrayOf(SIZEOF_BYTE, SIZEOF_NUMBER),
            "LOADRAWNUM" to intArrayOf(SIZEOF_BYTE, SIZEOF_NUMBER),
            "LOADPTR" to intArrayOf(SIZEOF_BYTE, SIZEOF_INT32),
            "LOADVARIABLE" to intArrayOf(READ_UNTIL_ZERO),
            "SETVARIABLE" to intArrayOf(READ_UNTIL_ZERO),
            "PUSH" to intArrayOf(SIZEOF_INT32),
            "LOADMNUM" to intArrayOf(SIZEOF_INT32),
            "LOADSTR" to intArrayOf(SIZEOF_BYTE, READ_UNTIL_ZERO),
            "INTERRUPT" to intArrayOf(SIZEOF_BYTE),
            "JMP" to intArrayOf(SIZEOF_INT32),
            "JZ" to intArrayOf(SIZEOF_INT32),
            "JNZ" to intArrayOf(SIZEOF_INT32),
            "JGT" to intArrayOf(SIZEOF_INT32),
            "JLS" to intArrayOf(SIZEOF_INT32),
            "SLP" to intArrayOf(SIZEOF_NUMBER)
    )
}