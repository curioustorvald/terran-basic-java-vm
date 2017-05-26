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

    lateinit var vm: VM

    fun invoke(vm: VM) {
        this.vm = vm

        //initTBasicEnv()
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


    // variable control //


    // flow control //

    fun PUSH(addr: Int) { vm.callStack[vm.sp++] = addr }
    fun POP() { vm.lr = vm.callStack[vm.sp--] }

    fun RETURN() { POP(); vm.pc = vm.lr }
    fun GOSUB(addr: Int) { PUSH(vm.pc); vm.pc = addr }
    fun GOTO(addr: Int) { vm.pc = addr }

    fun JZ(addr: Int) { if (vm.m1 == 0) GOTO(addr) }
    fun JNZ(addr: Int) { if (vm.m1 != 0) GOTO(addr) }
    fun JGT(addr: Int) { if (vm.m1 > 0) GOTO(addr) }
    fun JLE(addr: Int) { if (vm.m1 < 0) GOTO(addr) }

    fun HALT() { vm.terminate = true }

    
    // stdIO //
    fun PUTCHAR() { vm.stdout.write(java.lang.Double.doubleToRawLongBits(vm.r1).toInt()); vm.stdout.flush() }
    /**
     * print a string. String is prepared to r1 as pointer.
     */
    fun PRINTSTR() {
        val string = TBASString(VM.Pointer(vm, java.lang.Double.doubleToRawLongBits(vm.r1).toInt()))
        vm.m1 = 0 // string counter

        while (true) {
            vm.r1 = java.lang.Double.longBitsToDouble(vm.memory[string.pointer.memAddr + vm.m1].toUint().toLong())

            if (vm.r1 == 0.0) break

            PUTCHAR()
            vm.m1++
        }
    }

    /**
     * prints out whatever number in r1 register
     */
    fun PRINTNUM() {
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
        vm.m1 = 0 // string counter

        while (true) {
            vm.r1 = java.lang.Double.longBitsToDouble(vm.memory[string.pointer.memAddr + vm.m1].toUint().toLong())

            if (vm.r1 == 0.0) break

            PUTCHAR()
            vm.m1++
        }

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
    fun SQRT() { LOADNUM(2.0, 3); POW() }
    fun CBRT() { LOADNUM(3.0, 3); POW() }
    fun INV() { MOV(2, 3); LOADNUM(1.0, 2); DIV() }
    fun RAD() { LOADRAWNUM(0x4081ABE4B73FEFB5L, 3); DIV() } // r1 <- r2 / (180.0 * PI)

    fun NOT() { vm.r1 = vm.r2.toInt().inv().toDouble() }

    
    fun INC1() { vm.r1 += 1.0 }
    fun INC2() { vm.r1 += 1.0 }
    fun INC3() { vm.r1 += 1.0 }
    fun INC4() { vm.r1 += 1.0 }
    fun INCM() { vm.m1 += 1 }

    fun DEC1() { vm.r1 -= 1.0 }
    fun DEC2() { vm.r1 -= 1.0 }
    fun DEC3() { vm.r1 -= 1.0 }
    fun DEC4() { vm.r1 -= 1.0 }
    fun DECM() { vm.m1 -= 1 }
    
    

    // INTERNAL //

    fun NOP() { }

    fun INTERRUPT(interrupt: Int) {
        GOTO(interrupt * 4)
    }

    /** memory(r2) <- r1 */
    fun POKE() { vm.memory[vm.r2.toInt()] = vm.r1.toByte() }
    /** r1 <- data in memory addr r2 */
    fun PEEK() { vm.r1 = vm.memory[vm.r2.toInt()].toUint().toDouble() }


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
    fun LOADNUM(number: Double, register: Register) {
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
    fun LOADRAWNUM(num_as_bytes: Long, register: Register) {
        vm.writereg(register, java.lang.Double.longBitsToDouble(num_as_bytes))
        vm.writebreg(register, 1.toByte())
    }

    /**
     * Loads pointer's pointing address to r1, along with the marker that states r1 now holds memory address
     */
    fun LOADPTR(addr: Int, register: Register) {
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
            LOADPTR(strPtr.memAddr, register)
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
    fun LOADVARIABLE(identifier: String) { LOADPTR(vm.varTable[identifier]?.pointer?.memAddr ?: -1, 1) }
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

            "GOTO"   to 8.toByte(),
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
            "JLE" to 51.toByte(),

            "CMP" to 52.toByte(),
            "XCHG" to 53.toByte(),

            "REGTOM" to 54.toByte(),

            "INC1" to 55.toByte(),
            "INC2" to 56.toByte(),
            "INC3" to 57.toByte(),
            "INC4" to 58.toByte(),
            "INCM" to 59.toByte(),

            "DEC1" to 55.toByte(),
            "DEC2" to 56.toByte(),
            "DEC3" to 57.toByte(),
            "DEC4" to 58.toByte(),
            "DECM" to 59.toByte()

    )

    val NOP = byteArrayOf(7)

    val ADD = byteArrayOf(1)
    val SUB = byteArrayOf(2)
    val MUL = byteArrayOf(3)
    val DIV = byteArrayOf(4)
    val POW = byteArrayOf(5)
    val MOD = byteArrayOf(6)

    val HALT = byteArrayOf(0)

    val GOTO   = byteArrayOf(8)
    val GOSUB  = byteArrayOf(9)
    val RETURN = byteArrayOf(10)
    val PUSH   = byteArrayOf(11)
    val POP    = byteArrayOf(12)
    val MOV    = byteArrayOf(13)
    val POKE   = byteArrayOf(14)
    val PEEK   = byteArrayOf(15)

    val SHL  = byteArrayOf(16)
    val SHR  = byteArrayOf(17)
    val USHR = byteArrayOf(18)
    val AND  = byteArrayOf(19)
    val OR   = byteArrayOf(20)
    val XOR  = byteArrayOf(21)
    val NOT  = byteArrayOf(22)

    val ABS   = byteArrayOf(23)
    val SIN   = byteArrayOf(24)
    val FLOOR = byteArrayOf(25)
    val CEIL  = byteArrayOf(26)
    val ROUND = byteArrayOf(27)
    val LOG   = byteArrayOf(28)
    val INT   = byteArrayOf(29)
    val RND   = byteArrayOf(20)
    val SGN   = byteArrayOf(31)
    val SQRT  = byteArrayOf(32)
    val CBRT  = byteArrayOf(33)
    val INV   = byteArrayOf(34)
    val RAD   = byteArrayOf(35)

    val INTERRUPT = byteArrayOf(36)

    val LOADNUM = byteArrayOf(37)
    val LOADRAWNUM = byteArrayOf(38)
    val LOADPTR = byteArrayOf(39)
    val LOADVARIABLE = byteArrayOf(40)
    val SETVARIABLE = byteArrayOf(41)
    val LOADMNUM = byteArrayOf(42)
    val LOADSTR = byteArrayOf(43)

    val PUTCHAR = byteArrayOf(44)
    val PRINTSTR = byteArrayOf(45)
    val PRINTNUM = byteArrayOf(46)

    val JZ = byteArrayOf(48)
    val JNZ = byteArrayOf(49)
    val JGT = byteArrayOf(50)
    val JLE = byteArrayOf(51)

    val CMP = byteArrayOf(52)
    val XCHG = byteArrayOf(53)

    val REGTOM = byteArrayOf(54)

    val INC1 = byteArrayOf(55)
    val INC2 = byteArrayOf(56)
    val INC3 = byteArrayOf(57)
    val INC4 = byteArrayOf(58)
    val INCM = byteArrayOf(59)

    val DEC1 = byteArrayOf(55)
    val DEC2 = byteArrayOf(56)
    val DEC3 = byteArrayOf(57)
    val DEC4 = byteArrayOf(58)
    val DECM = byteArrayOf(59)

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

            "GOTO"   to fun(args: List<ByteArray>) { GOTO(args[0].toLittleInt()) },
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
            "INV"   to fun(_) { SQRT() },
            "RAD"   to fun(_) { CBRT() },

            "INTERRUPT" to fun(args: List<ByteArray>) { INTERRUPT(args[0][0].toInt()) },

            "LOADNUM" to fun(args: List<ByteArray>) { LOADNUM(args[0].toLittleDouble(), args[1][0].toInt()) },
            "LOADRAWNUM" to fun(args: List<ByteArray>) { LOADRAWNUM(args[0].toLittleLong(), args[1][0].toInt()) },
            "LOADPTR" to fun(args: List<ByteArray>) { LOADPTR(args[0].toLittleInt(), args[1][0].toInt()) },
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
            "JLE" to fun(args: List<ByteArray>) { JLE(args[0].toLittleInt()) },

            "CMP" to fun(_) { CMP() },
            "XCHG" to fun(args: List<ByteArray>) { XCHG(args[0][0].toInt(), args[1][0].toInt()) },

            "REGTOM" to fun(_) { REGTOM() },

            "INC1" to fun(_) { INC1() },
            "INC2" to fun(_) { INC2() },
            "INC3" to fun(_) { INC3() },
            "INC4" to fun(_) { INC4() },
            "INCM" to fun(_) { INCM() },

            "DEC1" to fun(_) { DEC1() },
            "DEC2" to fun(_) { DEC2() },
            "DEC3" to fun(_) { DEC3() },
            "DEC4" to fun(_) { DEC4() },
            "DECM" to fun(_) { DECM() }

    )
    val opcodeArgsList = hashMapOf<String, IntArray>( // null == 0 operands
            "MOV" to intArrayOf(SIZEOF_BYTE, SIZEOF_BYTE),
            "XCHG" to intArrayOf(SIZEOF_BYTE, SIZEOF_BYTE),
            "LOADNUM" to intArrayOf(SIZEOF_NUMBER, SIZEOF_BYTE),
            "LOADRAWNUM" to intArrayOf(SIZEOF_NUMBER, SIZEOF_BYTE),
            "LOADPTR" to intArrayOf(SIZEOF_POINTER, SIZEOF_BYTE),
            "LOADVARIABLE" to intArrayOf(READ_UNTIL_ZERO),
            "SETVARIABLE" to intArrayOf(READ_UNTIL_ZERO),
            "PUSH" to intArrayOf(SIZEOF_POINTER),
            "LOADMNUM" to intArrayOf(SIZEOF_INT32),
            "LOADSTR" to intArrayOf(SIZEOF_BYTE, READ_UNTIL_ZERO),
            "INTERRUPT" to intArrayOf(SIZEOF_BYTE),
            "JZ" to intArrayOf(SIZEOF_INT32),
            "JNZ" to intArrayOf(SIZEOF_INT32),
            "JGT" to intArrayOf(SIZEOF_INT32),
            "JLE" to intArrayOf(SIZEOF_INT32)
    )
}