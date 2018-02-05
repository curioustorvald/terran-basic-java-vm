package net.torvald.terranvm

import net.torvald.terranvm.runtime.*
import java.util.*


//typealias Register = Int


fun Int.toReadableBin() =
        this.ushr(29).toString(2).padStart(3, '0') + "_" +
        this.ushr(25).and(0b1111).toString(2).padStart(4, '0') + "_" +
                this.ushr(22).and(0b111).toString(2).padStart(3, '0') + "_" +
                this.ushr(16).and(0x3F).toString(2).padStart(6, '0') + "_" +
                this.and(0xFFFF).toString(2).padStart(16, '0')


/**
 * Conducts linear search for the hashmap for a value, returns matching key if found, null otherwise.
 *
 * Will not work if multiple keys are pointing to the same value.
 *
 * @param value value to search for in the hashmap
 * @return matching key for the value parameter, `null` if not found
 */
fun HashMap<out Any, out Any>.searchFor(value: Any): Any? {
    var ret: Any? = null

    this.forEach { k, v ->
        if (v == value) ret = k
    }

    return ret
}


fun Int.toReadableOpcode(): String {
    val Rd = this.and(0b00000001110000000000000000000000).ushr(22) + 1
    val Rs = this.and(0b00000000001110000000000000000000).ushr(19) + 1
    val Rm = this.and(0b00000000000001110000000000000000).ushr(16) + 1
    val R4 = this.and(0b00000000000000001110000000000000).ushr(13) + 1
    val R5 = this.and(0b00000000000000000001110000000000).ushr(10) + 1
    val regArray = arrayOf(Rd, Rs, Rm, R4, R5)
    val cond = when (this.ushr(29)) {
        0 -> ""
        1 -> "Z"
        2 -> "NZ"
        3 -> "GT"
        4 -> "LS"
        // JMP only
        5 -> "FW"
        6 -> "BW"
        else -> throw NullPointerException("Illegal condition: ${this.ushr(29)}")
    }

    val mode = this.and(0b00011110000000000000000000000000).ushr(25)

    var opString = when (mode) {
        0 -> {
            Assembler.opcodes.searchFor(this.and(0b1111111)) ?: throw NullPointerException("Unknown opcode: ${this.toReadableBin()}")
        }
        1 -> {
            Assembler.opcodes.searchFor(1.shl(25) or this.ushr(16).and(0b111).shl(16)) ?: throw NullPointerException("Unknown opcode: ${this.toReadableBin()}")
        }
        2 -> "LOADWORDIMEM"
        3 -> "STOREWORDIMEM"
        4 -> "PUSH"
        5 -> "POP"
        6 -> "PUSHWORDI"
        7 -> "POPWORDI"
        8 -> if (cond.isEmpty()) "JMP" else "J"
        9 -> if (Rs == 0) "SETBANK" else "INQFEATURE"
        15 -> {
            if (this.and(0x100) == 0) {
                "CALL"
            }
            else {
                if (this.and(0x1FFFFF00) == 0x1FFFFF00) {
                    "INT"
                }
                else if (this.and(0xFF) == 0xFF) {
                    "UPTIME"
                }
                else {
                    "MEMSIZE"
                }
            }
        }
        else -> throw NullPointerException("Unknown opcode: ${this.toReadableBin()}")
    }

    // manual replace
    if (opString == "LOADWORDI") opString = "LOADWORDILO"


    val argInfo = Assembler.getOpArgs(this)
    val args = Array(5, { "" })
    val argStr = StringBuilder()

    argInfo.forEachIndexed { index, c ->
        when (c) {
            'r' -> args[index] = "r${regArray[index]}"
            'a' -> args[index] = this.and(0x3FFFFF).to8HexString()
            'b' -> args[index] = this.and(0xFF).to8HexString().drop(6)
            'w', 'f' -> args[index] = this.and(0xFFFF).to8HexString().drop(4)

        }
    }


    args.forEachIndexed { index, s ->
        if (index == 0) {
            argStr.append(" $s")
        }
        else if (s.isNotEmpty()) {
            argStr.append(", $s")
        }
    }


    return "$opString$argStr"
}


                /**
 * Created by minjaesong on 2017-12-27.
 */
object VMOpcodesRISC {

    lateinit var vm: TerranVM

    fun invoke(vm: TerranVM) {
        this.vm = vm
    }



    fun MOV(dest: Register, src: Register) {
        vm.writeregInt(dest, vm.readregInt(src))
    }
    fun XCHG(dest: Register, src: Register) {
        /*val t = vm.readregInt(src)
        vm.writeregInt(src, vm.readregInt(dest))
        vm.writeregInt(dest, t)*/

        // xorswap: to slow down the execution
        if (vm.readregInt(dest) != vm.readregInt(src)) {
            XOR(dest, dest, src)
            XOR(src, src, dest)
            XOR(dest, dest, src)
        }
    }
    fun INC(dest: Register) {
        vm.writeregInt(dest, vm.readregInt(dest) + 1)
    }
    fun DEC(dest: Register) {
        vm.writeregInt(dest, vm.readregInt(dest) - 1)
    }

    fun MALLOC(dest: Register, size: Register) { vm.writeregInt(dest, vm.malloc(vm.readregInt(size)).memAddr) }
    fun RETURN() { POPWORDI(); vm.pc = vm.lr }
    /**
     * @param addr Address for program counter, must be aligned (0x..0, 0x..4, 0x..8, 0x..C)
     */
    fun GOSUB(addr: Int) {
        PUSHWORDI(addr ushr 2)
        JMP(addr ushr 2)
    }

    fun HALT() { vm.terminate = true }

    @Strictfp fun FTOI(dest: Register, src: Register) { vm.writeregInt(dest, vm.readregFloat(src).toInt()) }
    @Strictfp fun ITOF(dest: Register, src: Register) { vm.writeregFloat(dest, vm.readregInt(src).toFloat()) }

    @Strictfp fun ADD(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) + vm.readregFloat(m)) }
    @Strictfp fun SUB(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) - vm.readregFloat(m)) }
    @Strictfp fun MUL(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) * vm.readregFloat(m)) }
    @Strictfp fun DIV(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) / vm.readregFloat(m)) }
    @Strictfp fun POW(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) pow vm.readregFloat(m)) }
    @Strictfp fun MOD(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) fmod vm.readregFloat(m)) }

    fun SHL (dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) shl  vm.readregInt(m)) }
    fun SHR (dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) shr  vm.readregInt(m)) }
    fun USHR(dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) ushr vm.readregInt(m)) }
    fun AND (dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) and  vm.readregInt(m)) }
    fun OR  (dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) or   vm.readregInt(m)) }
    fun XOR (dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) xor  vm.readregInt(m)) }

    @Strictfp fun ABS(dest: Register, src: Register) { vm.writeregFloat(dest, Math.abs(vm.readregFloat(src))) }
    @Strictfp fun SIN(dest: Register, src: Register) { vm.writeregFloat(dest, Math.sin(vm.readregFloat(src).toDouble()).toFloat()) }
    @Strictfp fun COS(dest: Register, src: Register) { vm.writeregFloat(dest, Math.cos(vm.readregFloat(src).toDouble()).toFloat()) }
    @Strictfp fun TAN(dest: Register, src: Register) { vm.writeregFloat(dest, Math.tan(vm.readregFloat(src).toDouble()).toFloat()) }
    @Strictfp fun FLOOR(dest: Register, src: Register) { vm.writeregFloat(dest, Math.floor(vm.readregFloat(src).toDouble()).toFloat()) }
    @Strictfp fun CEIL (dest: Register, src: Register) { vm.writeregFloat(dest, Math.ceil (vm.readregFloat(src).toDouble()).toFloat()) }
    @Strictfp fun ROUND(dest: Register, src: Register) { vm.writeregFloat(dest, Math.round(vm.readregFloat(src)).toFloat()) }
    @Strictfp fun LOG(dest: Register, src: Register) { vm.writeregFloat(dest, Math.log(vm.readregFloat(src).toDouble()).toFloat()) }
    @Strictfp fun RNDI(dest: Register) { vm.writeregInt(dest, VMRNG.nextInt()) }
    @Strictfp fun RND(dest: Register) { vm.writeregFloat(dest, VMRNG.nextFloat()) }
    @Strictfp fun SGN(dest: Register, src: Register) { vm.writeregFloat(dest, Math.signum(vm.readregFloat(src))) }
    @Strictfp fun SQRT(dest: Register, src: Register) { vm.writeregFloat(dest, Math.pow(vm.readregFloat(src).toDouble(), 2.0).toFloat()) }
    @Strictfp fun CBRT(dest: Register, src: Register) { vm.writeregFloat(dest, Math.pow(vm.readregFloat(src).toDouble(), 3.0).toFloat()) }
    @Strictfp fun INV(dest: Register, src: Register) { vm.writeregFloat(dest, 1f / vm.readregFloat(src)) }
    @Strictfp fun RAD(dest: Register, src: Register) { vm.writeregFloat(dest, vm.readregFloat(src) / (180f * 3.14159265358979323f)) }

    fun NOT (dest: Register, src: Register) { vm.writeregInt(dest, vm.readregInt(src).inv()) }


    /**
     * @param dest register where the read value goes
     * @param src  memory address to be read
     * @param peri device ID (0 for main memory)
     */
    fun LOADBYTE(dest: Register, src: Register, peri: Register) {
        val memspace = if (vm.readregInt(peri) == 0) vm.memory else vm.peripherals[vm.readregInt(peri)]!!.memory
        val index = vm.readregInt(src)
        vm.writeregInt(dest, memspace[index].toUint())
    }
    fun LOADHWORD(dest: Register, src: Register, peri: Register) {
        val memspace = if (vm.readregInt(peri) == 0) vm.memory else vm.peripherals[vm.readregInt(peri)]!!.memory
        val index = vm.readregInt(src)
        vm.writeregInt(dest, memspace[index].toUint() or memspace[index + 1].toUint().shl(8))
    }
    fun LOADWORD(dest: Register, src: Register, peri: Register) {
        val memspace = if (vm.readregInt(peri) == 0) vm.memory else vm.peripherals[vm.readregInt(peri)]!!.memory
        val index = vm.readregInt(src)
        vm.writeregInt(dest, memspace[index].toUint() or memspace[index + 1].toUint().shl(8) or memspace[index + 1].toUint().shl(16) or memspace[index + 1].toUint().shl(24))
    }
    /**
     * @param dest register that holds the memory address where the read value goes
     * @param src  memory address to be written
     * @param peri device ID (0 for main memory)
     */
    fun STOREBYTE(dest: Register, src: Register, peri: Register) {
        val memspace = if (vm.readregInt(peri) == 0) vm.memory else vm.peripherals[vm.readregInt(peri)]!!.memory
        val index = vm.readregInt(src)
        memspace[index] = vm.readregInt(dest).and(0xFF).toByte()
    }
    fun STOREHWORD(dest: Register, src: Register, peri: Register) {
        val memspace = if (vm.readregInt(peri) == 0) vm.memory else vm.peripherals[vm.readregInt(peri)]!!.memory
        val index = vm.readregInt(src)
        memspace[index] = vm.readregInt(dest).and(0xFF).toByte()
        memspace[index + 1] = vm.readregInt(dest).ushr(8).and(0xFF).toByte()
    }
    fun STOREWORD(dest: Register, src: Register, peri: Register) {
        val memspace = if (vm.readregInt(peri) == 0) vm.memory else vm.peripherals[vm.readregInt(peri)]!!.memory
        val index = vm.readregInt(src)
        memspace[index] = vm.readregInt(dest).and(0xFF).toByte()
        memspace[index + 1] = vm.readregInt(dest).ushr(8).and(0xFF).toByte()
        memspace[index + 2] = vm.readregInt(dest).ushr(16).and(0xFF).toByte()
        memspace[index + 3] = vm.readregInt(dest).ushr(24).and(0xFF).toByte()
    }
    /**
     * @param dest device ID (0 for main memory)
     * @param src  device ID (0 for main memory)
     * @param len  size of the memory to copy
     * @param fromOff memory address to read
     * @param toOff   memory address to write
     */
    fun MEMCPY(dest: Register, src: Register, len: Register, fromAddr: Register, toAddr: Register) {
        val srcMem = if (vm.readregInt(src) == 0) vm.memory else vm.peripherals[vm.readregInt(src)]!!.memory
        val destMem = if (vm.readregInt(dest) == 0) vm.memory else vm.peripherals[vm.readregInt(dest)]!!.memory
        val cpyLen = vm.readregInt(len)
        val fromAddr = vm.readregInt(fromAddr)
        val toAddr = vm.readregInt(toAddr)

        System.arraycopy(srcMem, fromAddr, destMem, toAddr, cpyLen)
    }



    fun CMP(dest: Register, src: Register, lr: Int) {
        val lhand = if (lr and 0b10 != 0) vm.readregFloat(dest) else vm.readregInt(dest).toFloat()
        val rhand = if (lr and 0b01 != 0) vm.readregFloat(src) else vm.readregInt(src).toFloat()
        vm.rCMP = if (lhand == rhand) 0 else if (lhand > rhand) 1 else -1
    }



    fun LOADBYTEI(dest: Register, byte: Int) {
        vm.writeregInt(dest, byte.and(0xFF))
    }
    fun LOADHWORDI(dest: Register, halfword: Int) {
        vm.writeregInt(dest, halfword.and(0xFFFF))
    }
    fun LOADWORDI(dest: Register, word: Int, isHigh: Boolean) {
        val originalRegisterContents = vm.readregInt(dest)
        vm.writeregInt(dest,
                if (isHigh)
                    word.and(0xFFFF).shl(16) or originalRegisterContents.and(0xFFFF)
                else
                    word.and(0xFFFF) or originalRegisterContents.toLong().and(0xFFFF0000L).toInt()
        )
    }
    fun STOREBYTEI(dest: Register, peri: Register, byte: Int) {
        vm.memory[vm.readregInt(dest)] = byte.toByte()
    }
    fun STOREHWORDI(dest: Register, peri: Register, halfword: Int) {
        vm.memory[vm.readregInt(dest)] = halfword.and(0xFF).toByte()
        vm.memory[vm.readregInt(dest) + 1] = halfword.ushr(8).and(0xFF).toByte()
    }
    fun LOADWORDIMEM(dest: Register, offset: Int) {
        val index = offset shl 2
        vm.writeregInt(dest, vm.memory[index].toUint() or vm.memory[index + 1].toUint().shl(8) or vm.memory[index + 1].toUint().shl(16) or vm.memory[index + 1].toUint().shl(24))
    }
    fun STOREWORDIMEM(dest: Register, offset: Int) {
        val index = offset shl 2
        vm.memory[index] = vm.readregInt(dest).and(0xFF).toByte()
        vm.memory[index + 1] = vm.readregInt(dest).ushr(8).and(0xFF00).toByte()
        vm.memory[index + 2] = vm.readregInt(dest).ushr(16).and(0xFF00).toByte()
        vm.memory[index + 3] = vm.readregInt(dest).ushr(24).and(0xFF00).toByte()
    }
    fun PUSH(dest: Register) {
        if (vm.sp < vm.callStack.size)
            vm.callStack[vm.sp++] = vm.readregInt(dest)
        else
            vm.interruptStackOverflow()
    }
    fun PUSHWORDI(offset: Int) {
        if (vm.sp < vm.callStack.size)
            vm.callStack[vm.sp++] = offset shl 2
        else
            vm.interruptStackOverflow()
    }
    fun POP(dest: Register) {
        vm.writeregInt(dest, vm.callStack[--vm.sp])
    }
    fun POPWORDI() {
        vm.lr = vm.callStack[--vm.sp]
    }



    fun JMP(offset: Int) { vm.pc = offset shl 2 }
    fun JZ (offset: Int) { if (vm.rCMP == 0) JMP(offset) }
    fun JNZ(offset: Int) { if (vm.rCMP != 0) JMP(offset) }
    fun JGT(offset: Int) { if (vm.rCMP >  0) JMP(offset) }
    fun JLS(offset: Int) { if (vm.rCMP <  0) JMP(offset) }
    fun JFW(offset: Int) { vm.pc = Math.floorMod(vm.pc + (offset shl 2), 0xFFFFFF) }
    fun JBW(offset: Int) { vm.pc = Math.floorMod(vm.pc - (offset shl 2), 0xFFFFFF) }



    fun CALL(dest: Register, irq: Int) {
        if (irq == 255)
            vm.bios.call(vm.readregInt(dest))
        else
            vm.peripherals[irq]!!.call(vm.readregInt(dest))
    }



    fun MEMSIZE(dest: Register, irq: Int) {
        if (irq == 0)
            vm.writeregInt(dest, vm.memory.size)
        else if (irq == 255)
            vm.writeregInt(dest, vm.uptime)
        else
            vm.writeregInt(dest, vm.peripherals[irq]!!.memory.size)
    }
    fun UPTIME(dest: Register) = MEMSIZE(dest, 255)



    fun INT(interrupt: Int) {
        JMP(interrupt * 4)
    }





    fun decodeAndExecute(opcode: Int) {
        val Rd = opcode.and(0b00000001110000000000000000000000).ushr(22) + 1
        val Rs = opcode.and(0b00000000001110000000000000000000).ushr(19) + 1
        val Rm = opcode.and(0b00000000000001110000000000000000).ushr(16) + 1
        val R4 = opcode.and(0b00000000000000001110000000000000).ushr(13) + 1
        val R5 = opcode.and(0b00000000000000000001110000000000).ushr(10) + 1
        val cond = opcode.ushr(29)


        fun execInCond(action: () -> Unit) {
            when (cond) {
                0 -> action()
                1 -> if (vm.rCMP == 0) action()
                2 -> if (vm.rCMP != 0) action()
                3 -> if (vm.rCMP >  0) action()
                4 -> if (vm.rCMP <  0) action()
                else -> throw NullPointerException()
            }
        }


        execInCond { when (opcode.ushr(25).and(0b1111)) {
            0b0000 -> {
                // Mathematical and Register data transfer
                if (opcode.and(0b1111111111000000) == 0) {
                    when (opcode.and(0b111111)) {
                        0 -> HALT()
                        1 -> ADD(Rd, Rs, Rm)
                        2 -> SUB(Rd, Rs, Rm)
                        3 -> MUL(Rd, Rs, Rm)
                        4 -> DIV(Rd, Rs, Rm)
                        5 -> POW(Rd, Rs, Rm)
                        6 -> MOD(Rd, Rs, Rm)
                        7 -> SHL(Rd, Rs, Rm)
                        8 -> SHR(Rd, Rs, Rm)
                        9 -> USHR(Rd, Rs, Rm)
                        10 -> AND(Rd, Rs, Rm)
                        11 -> OR(Rd, Rs, Rm)
                        12 -> XOR(Rd, Rs, Rm)

                        16 -> ABS(Rd, Rs)
                        17 -> SIN(Rd, Rs)
                        18 -> COS(Rd, Rs)
                        19 -> TAN(Rd, Rs)
                        20 -> FLOOR(Rd, Rs)
                        21 -> CEIL(Rd, Rs)
                        22 -> ROUND(Rd, Rs)
                        23 -> LOG(Rd, Rs)
                        24 -> RNDI(Rd)
                        25 -> RND(Rd)
                        26 -> SGN(Rd, Rs)
                        27 -> SQRT(Rd, Rs)
                        28 -> CBRT(Rd, Rs)
                        29 -> INV(Rd, Rs)
                        30 -> RAD(Rd, Rs)
                        31 -> NOT(Rd, Rs)

                        32 -> MOV(Rd, Rs)
                        33 -> XCHG(Rd, Rs)
                        34 -> INC(Rd)
                        35 -> DEC(Rd)
                        36 -> MALLOC(Rd, Rs)
                        37 -> FTOI(Rd, Rs)
                        38 -> ITOF(Rd, Rs)

                        62 -> GOSUB(Rd)
                        63 -> RETURN()

                        else -> throw NullPointerException()
                    }
                }
                // Load and Store to memory
                else if (opcode.and(0xFFFF).ushr(3) == 0b1000) {
                    val loadStoreOpcode = opcode.and(0b111)
                    when (loadStoreOpcode) {
                        0 -> LOADBYTE(Rd, Rs, Rm)
                        1 -> STOREBYTE(Rd, Rs, Rm)
                        2 -> LOADHWORD(Rd, Rs, Rm)
                        3 -> STOREHWORD(Rd, Rs, Rm)
                        4 -> LOADWORD(Rd, Rs, Rm)
                        5 -> STOREWORD(Rd, Rs, Rm)
                        else -> throw NullPointerException()
                    }
                }
                // Memory copy
                else if (opcode.and(0b1111111111) == 0b0001001000) {
                    MEMCPY(Rd, Rs, Rm, R4, R5)
                }
                else {
                    throw NullPointerException()
                }
            }
            0b0001 -> {
                val M = opcode.ushr(16).and(1)
                val byte = opcode.and(0xFF)
                val halfword = opcode.and(0xFFFF)

                when (opcode.and(0b1100000000000000000).ushr(17)) {
                    // Compare
                    0 -> CMP(Rd, Rs, opcode.and(0b11))
                    // Load and Store byte immediate
                    1 -> if (M == 0) LOADBYTEI(Rd, byte) else STOREBYTEI(Rd, Rs, byte)
                    // Load and Store halfword immediate
                    2 -> if (M == 0) LOADHWORDI(Rd, halfword) else STOREHWORDI(Rd, Rs, halfword)
                    // Load word immediate
                    3 -> LOADWORDI(Rd, halfword, M == 1)
                    else -> throw NullPointerException()
                }
            }
            // Load and Store a word from register to memory
            0b0010 -> {
                 LOADWORDIMEM(Rd, opcode.and(0x3F_FFFF))
            }
            0b0011 -> {
                 STOREWORDIMEM(Rd, opcode.and(0x3F_FFFF))
            }
            // Push and Pop
            0b0100 -> {
                 PUSH(Rd)
            }
            0b0101 -> {
                 POP(Rd)
            }
            0b0110 -> {
                 val offset = opcode.and(0x3F_FFFF); PUSHWORDI(offset)
            }
            0b0111 -> {
                 POPWORDI()
            }
            // Conditional jump
            0b1000 -> {
                val offset = opcode.and(0x3F_FFFF)
                when (cond) {
                    0 -> JMP(offset)
                    1 -> JZ (offset)
                    2 -> JNZ(offset)
                    3 -> JGT(offset)
                    4 -> JLS(offset)
                    5 -> JFW(offset)
                    6 -> JBW(offset)
                    else -> throw NullPointerException()
                }
            }
            // Call peripheral; Get memory size; Get uptime; Interrupt
            0b1111 -> {
                val irq = opcode.and(0xFF)
                val cond2 = opcode.ushr(8).and(0x3FFF)

                if (cond2 == 0) {
                    CALL(Rd, irq)
                }
                else if (cond2 == 1) {
                    MEMSIZE(Rd, irq)
                }
                else if (cond2 == 0x3FFF && Rd == 0b111) {
                    INT(irq)
                }
                else {
                    throw NullPointerException()
                }
            }
            else -> throw NullPointerException()
        } }
    }


    fun getArgumentCount(command: String): Int {
        return if (command.endsWith("NZ") || command.endsWith("GT") || command.endsWith("LS")) {
            argumentCount[command.dropLast(2)] ?: 0
        }
        else if (command.endsWith("Z")) {
            argumentCount[command.dropLast(1)] ?: 0
        }
        else {
            argumentCount[command] ?: 0
        }
    }

    private val argumentCount = hashMapOf(
            "LOADBYTE" to 3,
            "LOADHWORD" to 3,
            "LOADWORD" to 3,
            "LOADBYTEI" to 2,
            "LOADHWORDI" to 2,
            "LOADWORDI" to 2,
            "LOADWORDIMEM" to 2,

            "STOREBYTE" to 3,
            "STOREHWORD" to 3,
            "STOREWORD" to 3,
            "STOREBYTEI" to 2,
            "STOREHWORDI" to 2,
            "STOREWORDIMEM" to 2,

            "ADD" to 3,
            "SUB" to 3,
            "MUL" to 3,
            "DIV" to 3,
            "POW" to 3,
            "MOD" to 3,
            "SHL" to 3,
            "SHR" to 3,
            "USHR" to 3,
            "AND" to 3,
            "OR" to 3,
            "XOR" to 3,

            "ABS" to 2,
            "SIN" to 2,
            "COS" to 2,
            "TAN" to 2,
            "FLOOR" to 2,
            "CEIL" to 2,
            "ROUND" to 2,
            "LOG" to 2,
            "SGN" to 2,
            "SQRT" to 2,
            "CBRT" to 2,
            "INV" to 2,
            "RAD" to 2,
            "NOT" to 2,

            "MOV" to 2,
            "XCHG" to 2,
            "MALLOC" to 2,
            "FTOI" to 2,
            "ITOF" to 2,

            "INC" to 1,
            "DEC" to 1,

            "MEMCPY" to 5,
            "CMP" to 2,
            "CMPII" to 2,
            "CMPIF" to 2,
            "CMPFI" to 2,
            "CMPFF" to 2,

            "PUSH" to 1,
            "PUSHWORDI" to 1,
            "POP" to 1,
            "POPWORDI" to 0,

            "JMP" to 1,
            "JZ" to 1,
            "JNZ" to 1,
            "JGT" to 1,
            "JLS" to 1,
            "JFW" to 1,
            "JBW" to 1,

            "CALL" to 2,
            "MEMSIZE" to 2,
            "UPTIME" to 1,
            "INT" to 1
    )

    val threeArgsCmd = hashSetOf<String>()
    val twoArgsCmd = hashSetOf<String>()

    init {
        argumentCount.forEach { t, u ->
            if (3 == u) {
                threeArgsCmd.add(t)
                threeArgsCmd.add(t + "Z")
                threeArgsCmd.add(t + "NZ")
                threeArgsCmd.add(t + "GT")
                threeArgsCmd.add(t + "LS")
            }
            else if (2 == u) {
                twoArgsCmd.add(t)
                twoArgsCmd.add(t + "Z")
                twoArgsCmd.add(t + "NZ")
                twoArgsCmd.add(t + "GT")
                twoArgsCmd.add(t + "LS")
            }
        }
    }


    private infix fun Float.pow(other: Float) = Math.pow(this.toDouble(), other.toDouble()).toFloat()
    private infix fun Float.fmod(other: Float) = Math.floorMod(this.toInt(), other.toInt()).toFloat()

    /**
     * Will seed itself using RTC or uptime
     */
    object VMRNG : Random(vm.peripherals[TerranVM.IRQ_RTC]?.memory?.toLittleLong() ?: vm.uptime.toLong()) {
        var s = -2208269211404306670L

        override fun nextLong(): Long {
            s = (s * 6364136223846793005) + 1442695040888963407 // knuth is the man
            return s
        }
    }





}