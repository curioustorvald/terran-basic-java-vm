package net.torvald.tbasic

import net.torvald.tbasic.runtime.VM
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
class TBASOpcodes(val vm: VM) {

    // variable control //


    // flow control //

    fun PUSH(addr: Int) { vm.callStack[vm.sp++] = addr }
    fun POP() = vm.callStack[vm.sp--]

    fun RETURN() { vm.pc = POP() }
    fun GOSUB(addr: Int) { PUSH(vm.pc); vm.pc = addr }
    fun GOTO(addr: Int) { vm.pc = addr }

    fun END() { vm.terminate = true }

    
    // stdIO //
    


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
    fun RAD() { LOADINT(0x4081ABE4B73FEFB5L, 3); DIV() } // r1 <- r2 / (180.0 * PI)

    // INTERNAL //

    /*
     b registers:

     0 - true if INT, false if Number
     1 - true if Pointer
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
    fun LOADINT(num_as_bytes: Long, register: Register) {
        vm.writereg(register, java.lang.Double.longBitsToDouble(num_as_bytes))
        vm.writebreg(register, 1.toByte())
    }

    /**
     * Loads pointer's pointing address to r1, along with the marker that states r1 now holds memory address
     */
    fun LOADPOINTER(addr: Int, register: Register) {
        vm.writereg(register, java.lang.Double.longBitsToDouble(addr.toLong()))
        vm.writebreg(register, 3.toByte())
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
     * NIL must be stored as TBASNil; null means there's no such variable
     */
    fun READVARIABLE(identifier: String): TBASValue? = vm.varTable[identifier]
    /**
     * save whatever on r1 to variables table
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
            TODO("TBASString")
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

    private val TYPE_NIL = 0
    private val TYPE_BOOLEAN = 1
    private val TYPE_NUMBER = 2
    private val TYPE_BYTES = 3

    /*

    10 A = 4 * 10 + 2
    >>>  4 10 * 2 + setvariable("A")

     */

}