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
       r2, r3, r4, r5: arguments for operators
       r6: flags for r2-r5; 16 flags for each (0b555..55_444..44_333..33_222..22)
            arguments flag:
            0   false: constant; true: pointer
            1   true: vararg (Number array)


       r7, r8: TBA
     */

    // MATHEMATICAL OPERATORS //

    /**
     * r1 <- r2 op r3 (no vararg)
     */
    fun ADD() { vm.r1.write(vm.r2.readAsDouble() + vm.r3.readAsDouble()) }
    fun SUB() { vm.r1.write(vm.r2.readAsDouble() + vm.r3.readAsDouble()) }
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
    fun INV() { MOV(3, 2); LOADNUM(1.0, 2); DIV() }
    fun RAD() { LOADINT(0x4081ABE4B73FEFB5, 3); DIV() } // r1 <- r2 / (180.0 * PI)

    // END OF MATHE //


    // INTERNAL //

    /**
     * r1 <- Number (Double)
     *
     * @param register 1-8 for r1-r8
     */
    fun LOADNUM(number: Double, register: Register) {
        vm.registers[register - 1].write(number)
        if (register in 2..5) {
            // flag that says this is a constant
            val bits = 0L
            val bitMask = 1L.shl(16 * (register - 2))
            vm.r6.write(vm.r6.readAsLong() and bitMask or bits)
        }
    }
    fun LOADINT(num_as_bytes: Long, register: Register) {
        vm.registers[register - 1].write(num_as_bytes)
        if (register in 2..5) {
            // flag that says this is a constant
            val bits = 0L
            val bitMask = 1L.shl(16 * (register - 2))
            vm.r6.write(vm.r6.readAsLong() and bitMask or bits)
        }
    }
    fun LOADPOINTER(addr: Int, register: Register) {
        vm.registers[register - 1].write(addr.toLong())
        if (register in 2..5) {
            // flag that says this is a pointer
            val bits = 1L
            val bitMask = 1L.shl(16 * (register - 2))
            vm.r6.write(vm.r6.readAsLong() and bitMask or bits)
        }
    }

    fun SETVARIABLE(identifier: String) {}

    /**
     * r(to) <- r(from)
     *
     * @param to 1-8 for r1-r8
     * @param from 1-8 for r1-r8
     */
    fun MOV(to: Register, from: Register) {
        vm.registers[to - 1].write(vm.registers[from - 1].readData())

        // move args flag
        if (from in 2..5 && to in 2..5) {
            val bits = vm.r6.readAsLong().ushr(16 * (from - 2)).and(0xFFFF).shl(16 * (to - 2))
            val bitMask = 0xFFFFL.shl(16 * (to - 2))
            vm.r6.write(vm.r6.readAsLong() and bitMask or bits)
        }
    }

    /**
     * NIL must be stored as TBASNil; null means there's no such variable
     */
    fun READVAR(identifier: String): Any? = vm.varTable[identifier]?.getValue()
    fun SETVAR(identifier: String, value: TBASValue) {
        val ptr = vm.malloc(value.sizeOf())
        ptr.write(value.toBytes())
        ptr.type = value.getPointerType()
    }


    /*

    10 A = 4 * 10 + 2
    >>>  4 10 * 2 + setvariable("A")

     */

}