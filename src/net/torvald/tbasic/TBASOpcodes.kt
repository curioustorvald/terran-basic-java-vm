package net.torvald.tbasic

import net.torvald.tbasic.runtime.VM

/**
 * Created by minjaesong on 2017-05-10.
 */

typealias Register = Int

class TBASOpcodes(val vm: VM) {

    // variable control //


    // flow control //

    fun RETURN() { vm.callStack.pop() }
    fun GOSUB() { vm.callStack.push(vm.pc) }
    fun GOTO() { vm.pc = vm.r1.readData() as Int } // must be LOADINT'd first

    fun END() { vm.terminate = true }

    
    // stdIO //
    

    /* Registers map

       r1: the main value
       r2, r3, r4: arguments for operators
       r5: flags for r1-r4; 16 flags for each (0b444..44_333..33_222..22_111..11)
            arguments flag:
            0   false: constant; true: pointer
            1   true: vararg (Number array)
            2-4 type (000: nil, 001: Boolean, 010: Number, 011: ByteArray -- String, also should be a pointer)


       r6, r7, r8: TBA
     */

    // MATHEMATICAL OPERATORS //

    /**
     * r1 <- r2 op r3 (no vararg)
     */
    fun ADD() { vm.r1.write(vm.r2.readAsDouble() + vm.r3.readAsDouble()) }
    fun SUB() { vm.r1.write(vm.r2.readAsDouble() - vm.r3.readAsDouble()) }
    fun MUL() { vm.r1.write(vm.r2.readAsDouble() * vm.r3.readAsDouble()) }
    fun DIV() { vm.r1.write(vm.r2.readAsDouble() / vm.r3.readAsDouble()) }
    fun POW() { vm.r1.write(Math.pow(vm.r2.readAsDouble(), vm.r3.readAsDouble())) }
    fun MOD() { vm.r1.write(Math.floorMod((vm.r2.readAsDouble()).toLong(), (vm.r3.readAsDouble()).toLong()).toDouble()) } // FMOD

    /**
     * r1 <- r2 (no vararg)
     */
    fun ABS() { vm.r1.write(Math.abs(vm.r2.readAsDouble())) }
    fun SIN() { vm.r1.write(Math.sin(vm.r2.readAsDouble())) }
    fun COS() { vm.r1.write(Math.cos(vm.r2.readAsDouble())) }
    fun TAN() { vm.r1.write(Math.tan(vm.r2.readAsDouble())) }
    fun FLOOR() { vm.r1.write(Math.floor(vm.r2.readAsDouble())) }
    fun CEIL()  { vm.r1.write(Math.ceil(vm.r2.readAsDouble())) }
    fun ROUND() { vm.r1.write(Math.round(vm.r2.readAsDouble()).toDouble()) }
    fun LOG() { vm.r1.write(Math.log(vm.r2.readAsDouble())) }
    fun INT() { if (vm.r2.readAsDouble() >= 0) FLOOR() else CEIL() }
    fun RND() { vm.r1.write(Math.random()) }
    fun SGN() { vm.r1.write(Math.signum(vm.r2.readAsDouble())) }
    fun SQRT() { LOADNUM(2.0, 3); POW() }
    fun CBRT() { LOADNUM(3.0, 3); POW() }
    fun INV() { MOV(2, 3); LOADNUM(1.0, 2); DIV() }
    fun RAD() { LOADINT(0x4081ABE4B73FEFB5, 3); DIV() } // r1 <- r2 / (180.0 * PI)

    // INTERNAL //

    /**
     * r1 <- Number (Double)
     *
     * @param register 1-8 for r1-r8
     */
    fun LOADNUM(number: Double, register: Register) {
        vm.registers[register - 1].write(number)
        if (register in 1..4) {
            // flag that says this is a constant
            val bits = 0L
            val bitMask = 1L.shl(16 * (register - 1))
            vm.r5.write(vm.r5.readAsLong() and bitMask or bits)
        }
    }
    /**
     * r1 <- Int (raw Double value) transformed to Double
     *
     * @param register 1-8 for r1-r8
     */
    fun LOADINT(num_as_bytes: Long, register: Register) {
        vm.registers[register - 1].write(num_as_bytes)
        if (register in 1..4) {
            // flag that says this is a constant
            val bits = 0L
            val bitMask = 1L.shl(16 * (register - 1))
            vm.r5.write(vm.r5.readAsLong() and bitMask or bits)
        }
    }

    /**
     * Loads pointer's pointing address to r1, along with the marker that states r1 now holds memory address
     */
    fun LOADPOINTER(addr: Int, register: Register) {
        vm.registers[register - 1].write(addr.toLong())
        if (register in 1..4) {
            // flag that says this is a pointer
            val bits = 1L
            val bitMask = 1L.shl(16 * (register - 1))
            vm.r5.write(vm.r5.readAsLong() and bitMask or bits)
        }
    }

    /**
     * r(to) <- r(from)
     *
     * @param from 1-8 for r1-r8
     * @param to 1-8 for r1-r8
     */
    fun MOV(from: Register, to: Register) {
        vm.registers[to - 1].write(vm.registers[from - 1].readData())

        // move args flag
        if (from in 1..4 && to in 1..4) {
            setRegisterFlags(to, getRegisterFlags(from))
        }
    }

    /**
     * NIL must be stored as TBASNil; null means there's no such variable
     */
    fun READVARIABLE(identifier: String): TBASValue? = vm.varTable[identifier]
    /**
     * save whatever on r1 to variables table
     */
    fun SETVARIABLE(identifier: String) {
        val typeIndex = getRegisterFlags(1).shr(2).and(0b111)
        val isPointer = getRegisterFlags(1).and(1) == 1L

        if (!isPointer) {
            val byteSize = getByteSizeOfType(typeIndex)
            val varPtr = vm.malloc(byteSize)
            varPtr.type = getPointerTypeFromID(typeIndex)

            if (byteSize == 8)
                varPtr.write(vm.r1.readAsDouble())
            else if (byteSize == 4) // just in case
                varPtr.write(vm.r1.readAsLong().and(0xFFFFFFFF).toInt())
            else if (byteSize == 1)
                varPtr.write(vm.r1.readAsLong().and(0xFF).toByte())


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


    private fun getRegisterFlags(register: Register): Long {
        if (register in 1..4) {
            return vm.r5.readAsLong().ushr(16 * (register - 1)).and(0xFFFF)
        }
        else {
            throw IllegalArgumentException()
        }
    }
    private fun setRegisterFlags(register: Register, bits: Long) {
        if (register in 1..4) {
            val bits = bits.shl(16 * (register - 1))
            val bitMask = 0xFFFFL.shl(16 * (register - 1))
            vm.r5.write(vm.r5.readAsLong() and bitMask or bits)
        }
        else {
            throw IllegalArgumentException()
        }
    }

    private fun getByteSizeOfType(typeID: Long): Int = when(typeID) {
        TYPE_NIL -> 1
        TYPE_BOOLEAN -> 1
        TYPE_BYTES -> 1
        TYPE_NUMBER -> 8
        else -> throw IllegalArgumentException()
    }
    private fun getPointerTypeFromID(typeID: Long): VM.Pointer.PointerType = when(typeID) {
        TYPE_NIL -> VM.Pointer.PointerType.VOID
        TYPE_BOOLEAN -> VM.Pointer.PointerType.BOOLEAN
        TYPE_BYTES -> VM.Pointer.PointerType.BYTE
        TYPE_NUMBER -> VM.Pointer.PointerType.INT64
        else -> throw IllegalArgumentException()
    }

    private val TYPE_NIL = 0L
    private val TYPE_BOOLEAN = 1L
    private val TYPE_NUMBER = 2L
    private val TYPE_BYTES = 3L

    /*

    10 A = 4 * 10 + 2
    >>>  4 10 * 2 + setvariable("A")

     */

}