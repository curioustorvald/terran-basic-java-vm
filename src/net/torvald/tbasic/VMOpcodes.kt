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
 * Basically, the opcodes are all single-cycle. We're doing high-level emulation for high performance on your main application.
 *
 * Created by minjaesong on 2017-05-10.
 */
object TBASOpcodes {

    private val DEBUG = false

    private fun dprintln(any: Any?) { if (DEBUG) println(any) }
    private fun dprint(any: Any?) { if (DEBUG) print(any) }
    
    val TBASVERSION = 0.4

    lateinit var vm: VM

    fun invoke(vm: VM) {
        this.vm = vm

        //initTBasicEnv()
        //resetTBASVarTable()
    }

    /*
    rudimentary Hello World:

    LOADSTRINLINE  "Hello, world!\n"   r1
    PRINTSTR

     */

    fun initTBasicEnv() {
        // load things for TBASIC

        // Compose Interrupt Vector Table to display errors //
        val writePointer = VM.Pointer(vm, 0, VM.Pointer.PointerType.INT32, true)

        run { // --> SYNTAX ERROR (invalid opcode??)
            val syntaxErrorPtr = vm.makeBytesDB(byteArrayOf(TBASOpcodes.opcodesList["LOADSTRINLINE"]!!) + 1 + "?SYNTAX\tERROR\n".toCString() + byteArrayOf(TBASOpcodes.opcodesList["PRINTSTR"]!!))
            // write INT32 using yet another pointer
            writePointer.memAddr = VM.INT_ILLEGAL_OP * 4
            writePointer.write(syntaxErrorPtr.memAddr)
        }

        run { // --> DIVISION BY ZERO ERROR (invalid opcode??)
            val div0Ptr = vm.makeBytesDB(byteArrayOf(TBASOpcodes.opcodesList["LOADSTRINLINE"]!!) + 1 + "?DIVISION BY ZERO\tERROR\n".toCString() + byteArrayOf(TBASOpcodes.opcodesList["PRINTSTR"]!!))
            // write INT32 using yet another pointer
            writePointer.memAddr = VM.INT_DIV_BY_ZERO * 4
            writePointer.write(div0Ptr.memAddr)
        }

        run { // --> OUT OF MEMORY ERROR
            val oomPtr = vm.makeBytesDB(byteArrayOf(TBASOpcodes.opcodesList["LOADSTRINLINE"]!!) + 1 + "?OUT OF MEMORY\tERROR\n".toCString() + byteArrayOf(TBASOpcodes.opcodesList["PRINTSTR"]!!))
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

        vm.setvar("M_PI", TBASNumber(ptrM_PI))
        vm.setvar("M_2PI", TBASNumber(ptrM_2PI))
        vm.setvar("M_E", TBASNumber(ptrM_E))
        vm.setvar("M_ROOT2", TBASNumber(ptrM_ROOT2))
        vm.setvar("TRUE", TBASBoolean(ptrTRUE))
        vm.setvar("FALSE", TBASBoolean(ptrFALSE))
        vm.setvar("NIL", TBASNil(ptrNIL))
        vm.setvar("_VERSION", TBASNumber(ptr_VERSION))
    }


    // variable control //

    fun CLR() { resetTBASVarTable() }

    // flow control //

    fun PUSH(addr: Int) { vm.callStack[vm.sp++] = addr }
    fun POP() { vm.lr = vm.callStack[--vm.sp] }

    fun JMP(addr: Int) { vm.pc = addr }
    fun JFW(offset: Int) { vm.pc = Math.floorMod(vm.pc + offset, 0x7FFFFFFF) }
    fun JBW(offset: Int) { JFW(-offset) }

    fun JZ(addr: Int) { if (vm.m1 == 0) JMP(addr) }
    fun JNZ(addr: Int) { if (vm.m1 != 0) JMP(addr) }
    fun JGT(addr: Int) { if (vm.m1 > 0) JMP(addr) }
    fun JLS(addr: Int) { if (vm.m1 < 0) JMP(addr) }

    fun RETURN() { POP(); vm.pc = vm.lr }
    fun GOSUB(addr: Int) { PUSH(vm.pc); JMP(addr) }

    fun HALT() { vm.terminate = true }

    
    // stdIO //
    /**
     * prints any byte (stored as Number) on r1 as a character. If r1 has a number of 33.0, '!' will be printed.
     */
    fun PUTCHAR() { vm.stdout.write(vm.r1.toInt()); vm.stdout.flush() }
    /**
     * print a string. String should be prepared to r1 as pointer. (r1 will be garbled afterwards!)
     */
    fun PRINTSTR() {
        val string = TBASString(VM.Pointer(vm, vm.r1.toInt()))
        vm.strCntr = 0 // string counter

        while (true) {
            vm.r1 = vm.memory[string.pointer.memAddr + vm.strCntr].toUint().toDouble()

            if (vm.r1 == 0.0) break

            PUTCHAR()
            vm.strCntr++
        }

        vm.freeBlock(string)
    }
    fun GETCHAR() { LOADNUM(1, vm.stdin.read().toDouble()) }
    /** vm.r1 <- pointer to the string */
    fun READSTR() {
        val maxStrLen = 255 // plus null terminator
        val readTerminator = '\n'.toInt().toDouble()

        val strPtr = vm.calloc(maxStrLen + 1)
        val strPtrInitPos = strPtr.memAddr
        LOADNUM(1, -1.0)

        while (vm.r1 != readTerminator && strPtr.memAddr - strPtrInitPos <= maxStrLen) {
            GETCHAR()
            strPtr.write(vm.r1.toByte())
            strPtr.inc()
            PUTCHAR() // print out what the hell the user has just hit
        }

        // truncate and free remaining bytes
        vm.reduceAllocatedBlock(strPtrInitPos..strPtrInitPos + maxStrLen, strPtrInitPos + maxStrLen - strPtr.memAddr)


        LOADPTR(1, strPtr.memAddr)
    }

    /**
     * prints out whatever number in r1 register (r1 will be garbled afterwards!)
     */
    fun PRINTNUM() {
        var str = vm.r1.toString()
        // filter number string
        if (str.endsWith(".0")) str = str.dropLast(2)


        LOADSTRINLINE(1, str.toCString())

        val string = TBASString(VM.Pointer(vm, vm.r1.toInt()))
        vm.strCntr = 0 // string counter

        while (true) {
            vm.r1 = vm.memory[string.pointer.memAddr + vm.strCntr].toUint().toDouble()

            if (vm.r1 == 0.0) break

            PUTCHAR()
            vm.strCntr++
        }

        vm.freeBlock(string)
    }



    // MATHEMATICAL OPERATORS //

    /**
     * r1 <- r2 op r3 (no vararg)
     */
    fun ADD() { vm.r1 = vm.r2 + vm.r3; vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun SUB() { vm.r1 = vm.r2 - vm.r3; vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun MUL() { vm.r1 = vm.r2 * vm.r3; vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun DIV() { vm.r1 = vm.r2 / vm.r3; vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun POW() { vm.r1 = Math.pow(vm.r2, vm.r3); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun MOD() { vm.r1 = Math.floorMod(vm.r2.toLong(), vm.r3.toLong()).toDouble(); vm.b1 = (TYPE_NUMBER shl 2).toByte() } // FMOD

    fun SHL()  { vm.r1 = (vm.r2.toInt() shl  vm.r3.toInt()).toDouble(); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun SHR()  { vm.r1 = (vm.r2.toInt() shr  vm.r3.toInt()).toDouble(); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun USHR() { vm.r1 = (vm.r2.toInt() ushr vm.r3.toInt()).toDouble(); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun AND()  { vm.r1 = (vm.r2.toInt() and  vm.r3.toInt()).toDouble(); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun OR()   { vm.r1 = (vm.r2.toInt() or   vm.r3.toInt()).toDouble(); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun XOR()  { vm.r1 = (vm.r2.toInt() xor  vm.r3.toInt()).toDouble(); vm.b1 = (TYPE_NUMBER shl 2).toByte() }

    fun CMP()  { vm.m1 = if (vm.r2 == vm.r3) 0 else if (vm.r2 > vm.r3) 1 else -1 }

    /**
     * r1 <- r2 (no vararg)
     */
    fun ABS()   { vm.r1 = Math.abs  (vm.r2); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun SIN()   { vm.r1 = Math.sin  (vm.r2); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun COS()   { vm.r1 = Math.cos  (vm.r2); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun TAN()   { vm.r1 = Math.tan  (vm.r2); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun FLOOR() { vm.r1 = Math.floor(vm.r2); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun CEIL()  { vm.r1 = Math.ceil (vm.r2); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun ROUND() { vm.r1 = Math.round(vm.r2).toDouble(); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun LOG()   { vm.r1 = Math.log  (vm.r2); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun INT()   { if (vm.r2 >= 0.0) FLOOR() else CEIL() }
    fun RND()   { vm.r1 = Math.random(); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun SGN()   { vm.r1 = Math.signum(vm.r2); vm.b1 = (TYPE_NUMBER shl 2).toByte() }
    fun SQRT() { LOADNUM(3, 2.0); POW() }
    fun CBRT() { LOADNUM(3, 3.0); POW() }
    fun INV() { MOV(2, 3); LOADNUM(2, 1.0); DIV() }
    fun RAD() { LOADRAWNUM(3, 0x4081ABE4B73FEFB5L); DIV() } // r1 <- r2 / (180.0 * PI)

    fun NOT() { vm.r1 = vm.r2.toInt().inv().toDouble(); vm.b1 = (TYPE_NUMBER shl 2).toByte() }

    
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
    /** r1 <- data in memory addr r1 */
    fun PEEK() {
        LOADNUM(1, vm.memory[vm.r1.toInt()].toUint().toDouble())
    }

    /** memory(r2) <- r1 */
    fun POKEINT() { vm.r1.toInt().toLittle().forEachIndexed { index, byte -> vm.memory[vm.r2.toInt() + index] = byte } }
    /** r1 <- data in memory addr r1 */
    fun PEEKINT() {
        LOADNUM(1, byteArrayOf(
                vm.memory[vm.r1.toInt()],
                vm.memory[vm.r1.toInt() + 1],
                vm.memory[vm.r1.toInt() + 2],
                vm.memory[vm.r1.toInt() + 3]
        ).toLittleInt().toDouble())
    }

    /** memory(r2) <- r1 */
    fun POKENUM() { vm.r1.toLittle().forEachIndexed { index, byte -> vm.memory[vm.r2.toInt() + index] = byte } }
    /** r1 <- data in memory addr r1 (aka pointer dereference) */
    fun PEEKNUM() {
        dprintln("=== peeknum memmap at ${vm.r1}: ")

        (0..7).forEach { dprint("${vm.memory[vm.r1.toInt() + it]} ") }
        dprintln("")

        LOADNUM(1, byteArrayOf(
                vm.memory[vm.r1.toInt()],
                vm.memory[vm.r1.toInt() + 1],
                vm.memory[vm.r1.toInt() + 2],
                vm.memory[vm.r1.toInt() + 3],
                vm.memory[vm.r1.toInt() + 4],
                vm.memory[vm.r1.toInt() + 5],
                vm.memory[vm.r1.toInt() + 6],
                vm.memory[vm.r1.toInt() + 7]
        ).toLittleDouble())

        dprintln("=== peeknum r1: ${vm.r1}")
    }

    /** Peripheral(r3).memory(r2) <- r1 */
    fun STOREPERI() { vm.peripherals[vm.r3.toInt()].memory[vm.r2.toInt()] = vm.r1.toByte() }
    /** r1 <- data in memory addr r2 of peripheral r3 */
    fun LOADPERI() {
        LOADNUM(1, vm.peripherals[vm.r3.toInt()].memory[vm.r2.toInt()].toUint().toDouble())
    }


    /** Memory copy - source: r2, destination: r3, length: r4 */
    fun MEMCPY() { System.arraycopy(vm.memory, vm.r2.toInt(), vm.memory, vm.r3.toInt(), vm.r4.toInt()) }
    /** Memory copy - peripheral index: r5, source (machine): r2, destination (peripheral): r3, length: r4 */
    fun MEMCPYPERI() { System.arraycopy(vm.memory, vm.r2.toInt(), vm.peripherals[vm.r5.toInt()], vm.r3.toInt(), vm.r4.toInt()) }

    /** r1 <- allocated memory pointer, r2: size in bytes */
    fun MALLOC() { LOADNUM(1, vm.malloc(vm.r2.toInt()).memAddr.toDouble()) }

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
        vm.writebreg(register, (TYPE_NUMBER shl 2).toByte())
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
        vm.writebreg(register, (TYPE_NUMBER shl 2 or 1).toByte())
    }

    /**
     * Loads pointer's pointing address to r1, along with the marker that states r1 now holds memory address
     */
    fun LOADPTR(register: Register, addr: Int) {
        dprintln("=== loadptr r$register, address: $addr")

        try {
            vm.writereg(register, addr.toDouble())
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

    fun LOADSTRINLINE(register: Register, string: ByteArray) { // made the name longer to avoid confusion with LOADPTR
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
    fun LOADVARIABLE(identifier: String) {
        dprintln("=== loadvariable '$identifier'")
        LOADPTR(1, vm.getvar(identifier)?.pointer?.memAddr ?: -1)
    }
    /**
     * save whatever on r1 (either an Immediate or Pointer) to variables table
     */
    fun SETVARIABLE(identifier: String) {

        val isPointer = vm.b1.and(0b10) != 0.toByte()
        val typeIndex = vm.b1.and(0b11100).toInt().ushr(2)

        if (!isPointer) {
            val byteSize = getByteSizeOfType(typeIndex)
            val varPtr = if (!vm.hasvar(identifier)) vm.malloc(byteSize) // create new var
                         else vm.getvar(identifier)!!.pointer            // renew existing var

            varPtr.type = getPointerTypeFromID(typeIndex)

            dprintln("=== setvariable pointer addr: ${varPtr.memAddr}")
            dprintln("=== setvariable byteSize: $byteSize")

            if (byteSize == 8)
                varPtr.write(vm.r1)
            else if (byteSize == 4) // just in case
                varPtr.write(java.lang.Double.doubleToRawLongBits(vm.r1).and(0xFFFFFFFF).toInt())
            else if (byteSize == 1)
                varPtr.write(java.lang.Double.doubleToRawLongBits(vm.r1).and(0xFF).toByte())


            val tbasValue: TBASValue = when (typeIndex) {
                TYPE_NIL -> TBASNil(varPtr)
                TYPE_NUMBER -> TBASNumber(varPtr)
                TYPE_BOOLEAN -> TBASBoolean(varPtr)
                else -> throw InternalError("String is Pointer!")
            }

            vm.setvar(identifier, tbasValue)



            dprintln("=== setvariable memmap: wrote at ${varPtr.memAddr}: ")

            (0..7).forEach { dprint("${vm.memory[varPtr.memAddr + it]} ") }
            dprintln("")
        }
        else {
            // String
            val ptr = VM.Pointer(vm, java.lang.Double.doubleToRawLongBits(vm.r1).toInt())
            vm.setvar(identifier, TBASString(ptr))
        }
    }


    fun SLP(millisec: Number) {
        Thread.sleep(millisec.toLong())
    }

    /** r1 <- VM memory size in bytes */
    fun MEM() { LOADNUM(1, vm.memory.size.toDouble()) }
    /** r1 <- System uptime in milliseconds */
    fun UPTIME() { LOADNUM(1, vm.uptime.toDouble()) }



    fun CALL(peripheral: Byte, arg: Int) {
        if (peripheral == 0xFF.toByte())
            vm.bios.call(arg)
        else
            vm.peripherals[peripheral.toUint()].call(arg)
    }






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
    val SIZEOF_POINTER = VM.Pointer.sizeOf(VM.Pointer.PointerType.INT32)
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
            "LOADSTRINLINE" to 43.toByte(),

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

            "STOREPERI" to 75.toByte(),
            "LOADPERI" to 76.toByte(),

            "MEM" to 77.toByte(),

            "SLP" to 78.toByte(),

            "CLR" to 79.toByte(),

            "UPTIME" to 80.toByte(),

            "JFW" to 81.toByte(),
            "JBW" to 82.toByte(),

            "POKEINT" to 83.toByte(),
            "PEEKINT" to 84.toByte(),

            "CALL" to 85.toByte(),

            "GETCHAR" to 86.toByte(),
            "READSTR" to 87.toByte(),

            "POKENUM" to 88.toByte(),
            "PEEKNUM" to 89.toByte(),

            "MALLOC" to 90.toByte()

    )

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
            "LOADSTRINLINE" to fun(args: List<ByteArray>) { LOADSTRINLINE(args[0][0].toInt(), args[1]) },

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

            "STOREPERI" to fun(_) { STOREPERI() },
            "LOADPERI" to fun(_) { LOADPERI() },

            "MEM" to fun(_) { MEM() },

            "SLP" to fun(args: List<ByteArray>) { SLP(args[0].toLittleDouble()) },

            "CLR" to fun(_) { CLR() },

            "UPTIME" to fun(_) { UPTIME() },

            "JFW" to fun(args: List<ByteArray>) { JFW(args[0].toLittleInt()) },
            "JBW" to fun(args: List<ByteArray>) { JFW(args[0].toLittleInt()) },

            "POKEINT" to fun(_) { POKEINT() },
            "PEEKINT" to fun(_) { PEEKINT() },

            "CALL" to fun(args: List<ByteArray>) { CALL(args[0][0], args[1].toLittleInt()) },

            "GETCHAR" to fun(_) { GETCHAR() },
            "READSTR" to fun(_) { READSTR() },

            "POKENUM" to fun(_) { POKENUM() },
            "PEEKNUM" to fun(_) { PEEKNUM() },

            "MALLOC" to fun(_) { MALLOC() }


    )
    val opcodeArgsList = hashMapOf<String, IntArray>( // null == 0 operands
            "MOV" to intArrayOf(SIZEOF_BYTE, SIZEOF_BYTE),
            "XCHG" to intArrayOf(SIZEOF_BYTE, SIZEOF_BYTE),
            "LOADNUM" to intArrayOf(SIZEOF_BYTE, SIZEOF_NUMBER),
            "LOADRAWNUM" to intArrayOf(SIZEOF_BYTE, SIZEOF_NUMBER),
            "LOADPTR" to intArrayOf(SIZEOF_BYTE, SIZEOF_POINTER),
            "LOADVARIABLE" to intArrayOf(READ_UNTIL_ZERO),
            "SETVARIABLE" to intArrayOf(READ_UNTIL_ZERO),
            "PUSH" to intArrayOf(SIZEOF_INT32),
            "LOADMNUM" to intArrayOf(SIZEOF_INT32),
            "LOADSTRINLINE" to intArrayOf(SIZEOF_BYTE, READ_UNTIL_ZERO),
            "INTERRUPT" to intArrayOf(SIZEOF_BYTE),
            "JMP" to intArrayOf(SIZEOF_POINTER),
            "JZ" to intArrayOf(SIZEOF_POINTER),
            "JNZ" to intArrayOf(SIZEOF_POINTER),
            "JFW" to intArrayOf(SIZEOF_POINTER),
            "JBW" to intArrayOf(SIZEOF_POINTER),
            "JGT" to intArrayOf(SIZEOF_POINTER),
            "JLS" to intArrayOf(SIZEOF_POINTER),
            "SLP" to intArrayOf(SIZEOF_NUMBER),
            "GOSUB" to intArrayOf(SIZEOF_POINTER),
            "PUSH" to intArrayOf(SIZEOF_POINTER),
            "CALL" to intArrayOf(SIZEOF_BYTE, SIZEOF_INT32)
    )
}