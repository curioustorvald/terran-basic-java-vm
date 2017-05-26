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

    LOADSTRING  "Hello, world!\n"   r1
    PRINTSTR

     */

    fun initTBasicEnv() {
        // load things for TBASIC

        // Compose Interrupt Vector Table to display errors //
        val writePointer = VM.Pointer(vm, 0, VM.Pointer.PointerType.INT32, true)

        run { // --> SYNTAX ERROR (invalid opcode??)
            val syntaxErrorPtr = vm.makeBytesDB(byteArrayOf(TBASOpcodes.opcodesList["LOADSTRING"]!!) + 1 + "?SYNTAX\tERROR\n".toCString() + byteArrayOf(TBASOpcodes.opcodesList["PRINTSTR"]!!))
            // write INT32 using yet another pointer
            writePointer.memAddr = VM.INT_ILLEGAL_OP * 4
            writePointer.write(syntaxErrorPtr.memAddr)
        }

        run { // --> DIVISION BY ZERO ERROR (invalid opcode??)
            val div0Ptr = vm.makeBytesDB(byteArrayOf(TBASOpcodes.opcodesList["LOADSTRING"]!!) + 1 + "?DIVISION BY ZERO\tERROR\n".toCString() + byteArrayOf(TBASOpcodes.opcodesList["PRINTSTR"]!!))
            // write INT32 using yet another pointer
            writePointer.memAddr = VM.INT_DIV_BY_ZERO * 4
            writePointer.write(div0Ptr.memAddr)
        }

        run { // --> OUT OF MEMORY ERROR
            val oomPtr = vm.makeBytesDB(byteArrayOf(TBASOpcodes.opcodesList["LOADSTRING"]!!) + 1 + "?OUT OF MEMORY\tERROR\n".toCString() + byteArrayOf(TBASOpcodes.opcodesList["PRINTSTR"]!!))
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

    fun END() { vm.terminate = true }

    
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
        val strBytes = str.length.toLittle() + str.toCString()
        val numStrPtr = vm.malloc(strBytes.size)

        val string = TBASString(VM.Pointer(vm, java.lang.Double.doubleToRawLongBits(vm.r1).toInt()))
        vm.m1 = 0 // string counter
        while (vm.r1 != 0.0) {
            vm.r1 = java.lang.Double.longBitsToDouble(vm.memory[string.pointer.memAddr + vm.m1].toUint().toLong())
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
    fun LOADPOINTER(addr: Int, register: Register) {
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

    fun LOADFLAG(int: Int) {
        vm.m1 = int
    }

    fun LOADSTRING(register: Register, string: ByteArray) {
        try {
            val strPtr = vm.makeStringDB(string)
            LOADPOINTER(strPtr.memAddr, register)
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

    /**
     * load variable to r1 as pointer. If the variable does not exist, null pointer will be loaded instead.
     */
    fun LOADVARIABLE(identifier: String) { LOADPOINTER(vm.varTable[identifier]?.pointer?.memAddr ?: -1, 1) }
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

            "END" to 0.toByte(),

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
            "LOADPOINTER" to 39.toByte(),
            "LOADVARIABLE" to 40.toByte(),
            "SETVARIABLE" to 41.toByte(),
            "LOADFLAG" to 42.toByte(),
            "LOADSTRING" to 43.toByte(),

            "PUTCHAR" to 44.toByte(),
            "PRINTSTR" to 45.toByte(),
            "PRINTNUM" to 46.toByte(),

            "JZ" to 48.toByte(),
            "JNZ" to 49.toByte(),
            "JGT" to 50.toByte(),
            "JLE" to 51.toByte(),

            "CMP" to 52.toByte()

    )
    val opcodesListInverse = HashMap<Byte, String>()
    init {
        opcodesList.keys.forEach { opcodesListInverse.put(opcodesList[it]!!, it) }
    }
    val opcodesFunList = hashMapOf<String, (List<ByteArray>) -> Unit>(
            "NOP" to fun(args: List<ByteArray>) { NOP() },

            "ADD" to fun(args: List<ByteArray>) { ADD() },
            "SUB" to fun(args: List<ByteArray>) { SUB() },
            "MUL" to fun(args: List<ByteArray>) { MUL() },
            "DIV" to fun(args: List<ByteArray>) { DIV() },
            "POW" to fun(args: List<ByteArray>) { POW() },
            "MOD" to fun(args: List<ByteArray>) { MOD() },

            "END" to fun(args: List<ByteArray>) { END() },

            "GOTO"   to fun(args: List<ByteArray>) { GOTO(args[0].toLittleInt()) },
            "GOSUB"  to fun(args: List<ByteArray>) { GOSUB(args[0].toLittleInt()) },
            "RETURN" to fun(args: List<ByteArray>) { RETURN() },
            "PUSH"   to fun(args: List<ByteArray>) { PUSH(args[0].toLittleInt()) },
            "POP"    to fun(args: List<ByteArray>) { POP() },
            "MOV"    to fun(args: List<ByteArray>) { MOV(args[0][0].toInt(), args[1][0].toInt()) },
            "POKE"   to fun(args: List<ByteArray>) { POKE() },
            "PEEK"   to fun(args: List<ByteArray>) { PEEK() },

            "SHL"  to fun(args: List<ByteArray>) { SHL() },
            "SHR"  to fun(args: List<ByteArray>) { SHR() },
            "USHR" to fun(args: List<ByteArray>) { USHR() },
            "AND"  to fun(args: List<ByteArray>) { AND() },
            "OR"   to fun(args: List<ByteArray>) { OR() },
            "XOR"  to fun(args: List<ByteArray>) { XOR() },
            "NOT"  to fun(args: List<ByteArray>) { NOT() },

            "ABS"   to fun(args: List<ByteArray>) { ABS() },
            "SIN"   to fun(args: List<ByteArray>) { SIN() },
            "FLOOR" to fun(args: List<ByteArray>) { COS() },
            "CEIL"  to fun(args: List<ByteArray>) { TAN() },
            "ROUND" to fun(args: List<ByteArray>) { FLOOR() },
            "LOG"   to fun(args: List<ByteArray>) { CEIL() },
            "INT"   to fun(args: List<ByteArray>) { ROUND() },
            "RND"   to fun(args: List<ByteArray>) { LOG() },
            "SGN"   to fun(args: List<ByteArray>) { INT() },
            "SQRT"  to fun(args: List<ByteArray>) { RND() },
            "CBRT"  to fun(args: List<ByteArray>) { SGN() },
            "INV"   to fun(args: List<ByteArray>) { SQRT() },
            "RAD"   to fun(args: List<ByteArray>) { CBRT() },

            "INTERRUPT" to fun(args: List<ByteArray>) { INTERRUPT(args[0][0].toInt()) },

            "LOADNUM" to fun(args: List<ByteArray>) { LOADNUM(args[0].toLittleDouble(), args[1][0].toInt()) },
            "LOADRAWNUM" to fun(args: List<ByteArray>) { LOADRAWNUM(args[0].toLittleLong(), args[1][0].toInt()) },
            "LOADPOINTER" to fun(args: List<ByteArray>) { LOADPOINTER(args[0].toLittleInt(), args[1][0].toInt()) },
            "LOADVARIABLE" to fun(args: List<ByteArray>) { LOADVARIABLE(args[0].toString(VM.charset)) },
            "SETVARIABLE" to fun(args: List<ByteArray>) { SETVARIABLE(args[0].toString(VM.charset)) },
            "LOADFLAG" to fun(args: List<ByteArray>) { LOADFLAG(args[0].toLittleInt()) },
            "LOADSTRING" to fun(args: List<ByteArray>) { LOADSTRING(args[0][0].toInt(), args[1]) },

            "PUTCHAR" to fun(args: List<ByteArray>) { PUTCHAR() },
            "PRINTSTR" to fun(args: List<ByteArray>) { PRINTSTR() },
            "PRINTNUM" to fun(args: List<ByteArray>) { PRINTNUM() },

            "JZ" to fun(args: List<ByteArray>) { JZ(args[0].toLittleInt()) },
            "JNZ" to fun(args: List<ByteArray>) { JNZ(args[0].toLittleInt()) },
            "JGT" to fun(args: List<ByteArray>) { JGT(args[0].toLittleInt()) },
            "JLE" to fun(args: List<ByteArray>) { JLE(args[0].toLittleInt()) },

            "CMP" to fun(args: List<ByteArray>) { CMP() }

    )
    val opcodesFunListTwoArgs = hashMapOf<String, (ByteArray, ByteArray) -> Unit>(

    )
    val opcodeArgsList = hashMapOf<String, IntArray>( // null == 0 operands
            "MOV" to intArrayOf(SIZEOF_BYTE, SIZEOF_BYTE),
            "LOADNUM" to intArrayOf(SIZEOF_NUMBER, SIZEOF_BYTE),
            "LOADRAWNUM" to intArrayOf(SIZEOF_NUMBER, SIZEOF_BYTE),
            "LOADPOINTER" to intArrayOf(SIZEOF_POINTER, SIZEOF_BYTE),
            "LOADVARIABLE" to intArrayOf(READ_UNTIL_ZERO),
            "SETVARIABLE" to intArrayOf(READ_UNTIL_ZERO),
            "PUSH" to intArrayOf(SIZEOF_POINTER),
            "LOADFLAG" to intArrayOf(SIZEOF_INT32),
            "LOADSTRING" to intArrayOf(SIZEOF_BYTE, READ_UNTIL_ZERO),
            "INTERRUPT" to intArrayOf(SIZEOF_BYTE),
            "JZ" to intArrayOf(SIZEOF_INT32),
            "JNZ" to intArrayOf(SIZEOF_INT32),
            "JGT" to intArrayOf(SIZEOF_INT32),
            "JLE" to intArrayOf(SIZEOF_INT32)
    )
}