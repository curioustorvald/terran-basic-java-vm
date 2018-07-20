package net.torvald.terranvm

import net.torvald.terranvm.runtime.*
import java.util.*


//typealias Register = Int


fun Int.toReadableBin() =
        this.ushr(29).toString(2).padStart(3, '0') + "_" +
        this.ushr(26).and(0b111).toString(2).padStart(3, '0') + "_" +
        this.ushr(22).and(0b1111).toString(2).padStart(4, '0') + "_" +
        this.ushr(18).and(0b1111).toString(2).padStart(4, '0') + "_" +
        this.ushr(16).and(0b11).toString(2).padStart(2, '0') + "_" +
        this.and(0xFFFF).toString(2).padStart(16, '0')

fun Int.toBytesBin(): String {
    val sb = StringBuilder()
    this.toString(2).padStart(32, '0').forEachIndexed { index, c ->
        sb.append(c)
        if (index != 0 && index in intArrayOf(7, 15, 23)) {
            sb.append('_')
        }
    }

    return sb.toString()
}

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
    val Rd = this.and(0b000_000_1111_0000_0000_00000000000000).ushr(22)
    val Rs = this.and(0b000_000_0000_1111_0000_00000000000000).ushr(18)
    val Rm = this.and(0b000_000_0000_0000_1111_00000000000000).ushr(14)
    val regArray = arrayOf(Rd, Rs, Rm)
    val cond = when (this.ushr(29)) {
        0 -> ""
        1 -> "Z"
        2 -> "NZ"
        3 -> "GT"
        4 -> "LS"
        // JMP only
        5 -> "FW"
        6 -> "BW"
        else -> return "(data or unknown opcode)"
    }

    val mode = this.and(0b000_111_0000_0000_0000_00000000000000).ushr(26)

    var opString = when (mode) {
        0 -> {
            val zeroMode = this.and(0x3FFF)
            Assembler.opcodes.searchFor(zeroMode) ?: return "(data or unknown opcode)"
        }
        1 -> {
            val oneMode = this.ushr(16).and(0b11)

            when (oneMode) {
                0 -> "LOADWORDILO"
                1 -> "LOADWORDIHI"
                2 -> "STOREHWORDI"
                else -> "(data or unknown opcode)"
            }
        }
        2 -> "LOADWORDIMEM"
        3 -> "STOREWORDIMEM"
        4 -> if (cond.isEmpty()) "JMP" else "J"
        5 -> "JSRI"
        6 -> {
            val sixMode = this.ushr(8).and(0x3FFF)

            when (sixMode) {
                in 0x0000..0x0FFF -> "MEMBANK"
                0x1000 -> "FEATURE"
                0x1001 ->"CALL"
                0x1002 -> if (this.and(0xFF) == 255) "UPTIME" else "MEMSIZE"
                else -> return "(data or unknown opcode)"
            }
        }
        7 -> if (this.and(0x1FFFFF_00).ushr(8) == 0x1FFF00) "INT" else return "(data or unknown opcode)"
        else -> throw InternalError("Gah, another ionised particle!")
    }

    // manual replace
    if (opString == "LOADWORDI") opString = "LOADWORDILO"
    else if (opString == "CMP") opString = "CMP" + when (this.and(0b11)) {
        0b00 -> "II"
        0b01 -> "IF"
        0b10 -> "FI"
        0b11 -> "FF"
        else -> throw InternalError()
    }


    try {
        Assembler.getOpArgs(this)
    }
    catch (e: UnknownOpcodeExpection) {
        return "(data or unknown opcode)"
    }

    val argInfo = Assembler.getOpArgs(this)

    if (argInfo == null) {
        return "(data or unknown opcode)"
    }

    val args = Array(3, { "" })
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


    return "$opString$cond$argStr"
}


fun Int.byte1() = this.and(0xff).toByte()
fun Int.byte2() = this.ushr(8).and(0xff).toByte()
fun Int.byte3() = this.ushr(16).and(0xff).toByte()
fun Int.byte4() = this.ushr(24).and(0xff).toByte()

fun Float.byte1() = this.toRawBits().byte1()
fun Float.byte2() = this.toRawBits().byte2()
fun Float.byte3() = this.toRawBits().byte3()
fun Float.byte4() = this.toRawBits().byte4()



/**
 * NOTE: what we say OFFSET is a memory address divided by four (and word-aligned)
 *
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
        val t = vm.readregInt(src)
        vm.writeregInt(src, vm.readregInt(dest))
        vm.writeregInt(dest, t)
    }
    fun SRR(dest: Register, src: Register) {
        vm.writeregInt(dest, when (src) {
            1 -> vm.pc
            2 -> vm.sp
            3 -> vm.lr
            else -> throw IllegalArgumentException("Unknown special register index: $src")
        })
    }
    fun SRW(dest: Register, src: Register) {
        val reg = vm.readregInt(src)
        when (dest) {
            1 -> vm.pc = reg
            2 -> throw Exception("Security violation") //vm.sp = reg
            3 -> throw Exception("Security violation") //vm.lr = reg
            else -> throw IllegalArgumentException("Unknown special register index: $dest")
        }
    }
    fun SXCHG(dest: Register, src: Register) {
        throw Exception("Security violation")

        /*val destVal = vm.readregInt(dest)
        val srcVal = when (src) {
            1 -> vm.pc
            2 -> vm.sp
            3 -> vm.lr
            else -> throw IllegalArgumentException("Unknown special register index: $src")
        }
        if (destVal != srcVal) {
            when (src) {
                1 -> vm.pc = destVal
                2 -> vm.sp = destVal
                3 -> vm.lr = destVal
                else -> throw IllegalArgumentException("Unknown special register index: $src")
            }
            vm.writeregInt(dest, srcVal) // srcVal is a copy; no tempvar needed
        }*/
    }
    fun INC(dest: Register) {
        vm.writeregInt(dest, vm.readregInt(dest) + 1)
    }
    fun DEC(dest: Register) {
        vm.writeregInt(dest, vm.readregInt(dest) - 1)
    }

    fun MALLOC(dest: Register, size: Register) { vm.writeregInt(dest, vm.malloc(vm.readregInt(size)).memAddr) }
    fun RETURN() { POP(0); vm.pc = vm.lr shl 2 /* LR must contain OFFSET, not actual address */ }
    /**
     * Register must contain address for program counter, must be aligned (0x..0, 0x..4, 0x..8, 0x..C) but not pre-divided
     */
    fun JSR(register: Register) {
        val addr = vm.readregInt(register)
        JSRI(addr)
    }

    fun JSRI(offset: Int) {
        _PUSHWORDI(vm.pc ushr 2) // PC is incremented by 4 right before any opcode is executed  @see TerranVM.kt
        JMP(offset)
    }

    fun HALT() { vm.terminate = true }
    fun YIELD() {
        if (vm.isPaused) {
            throw Error("VM is already paused")
        }
        vm.yieldFlagged = true
    }
    fun PAUSE() {
        if (vm.isPaused) {
            throw Error("VM is already paused")
        }
        vm.pauseExec()
    }

    @Strictfp fun FTOI(dest: Register, src: Register) { vm.writeregInt(dest, vm.readregFloat(src).toInt()) }
    @Strictfp fun ITOF(dest: Register, src: Register) { vm.writeregFloat(dest, vm.readregInt(src).toFloat()) }

    fun ITOS(dest: Register, src: Register) {
        val value = vm.readregInt(src)
        val outString = value.toString()
        val strLen = outString.length + 1 // incl. null terminator
        val strPtr = vm.malloc(strLen).memAddr
        outString.forEachIndexed { index, c ->
            vm.memory[strPtr + index] = c.toByte()
        }
        vm.memory[strPtr + strLen - 1] = 0.toByte()
        vm.writeregInt(dest, strPtr ushr 2)
        vm.freeBlock(strPtr..(strPtr + strLen).roundToFour())
    }

    @Strictfp fun FTOS(dest: Register, src: Register) {
        val value = vm.readregFloat(src)
        val outString = value.toString()
        val strLen = outString.length + 1 // incl. null terminator
        val strPtr = vm.malloc(strLen).memAddr
        outString.forEachIndexed { index, c ->
            vm.memory[strPtr + index] = c.toByte()
        }
        vm.memory[strPtr + strLen - 1] = 0.toByte()
        vm.writeregInt(dest, strPtr ushr 2)
        vm.freeBlock(strPtr..(strPtr + strLen).roundToFour())
    }

    fun ITOX(dest: Register, src: Register) {
        val value = vm.readregInt(src)
        var outString = value.toString(16).toUpperCase()

        if (outString.length % 2 == 1) outString = "0$outString" // prepend 0 if length of outstring is in odd number

        val strLen = outString.length + 1 // incl. null terminator
        val strPtr = vm.malloc(strLen).memAddr
        outString.forEachIndexed { index, c ->
            vm.memory[strPtr + index] = c.toByte()
        }
        vm.memory[strPtr + strLen - 1] = 0.toByte()
        vm.writeregInt(dest, strPtr ushr 2)
        vm.freeBlock(strPtr..(strPtr + strLen).roundToFour())
    }

    fun STOI(dest: Register, src: Register) {
        var strPtr = vm.readregInt(src) shl 2
        val sb = StringBuilder()

        var char = 0xff.toChar()
        while (char != 0.toChar()) {
            char = vm.memory[strPtr].toUint().toChar()
            sb.append(char)

            strPtr++
        }

        val convertedInt = sb.toString().toLong().and(0xFFFFFFFF).toInt()
        vm.writeregInt(dest, convertedInt)
    }

    fun STOF(dest: Register, src: Register) {
        var strPtr = vm.readregInt(src) shl 2
        val sb = StringBuilder()

        var char = 0xff.toChar()
        while (char != 0.toChar()) {
            char = vm.memory[strPtr].toUint().toChar()
            sb.append(char)

            strPtr++
        }

        val convertedInt = sb.toString().toLong().and(0xFFFFFFFF).toInt()
        vm.writeregFloat(dest, Float.fromBits(convertedInt))
    }

    fun XTOI(dest: Register, src: Register) {
        var strPtr = vm.readregInt(src) shl 2
        val sb = StringBuilder()

        var char = 0xff.toChar()
        while (char != 0.toChar()) {
            char = vm.memory[strPtr].toUint().toChar()
            sb.append(char)

            strPtr++
        }

        val convertedInt = java.lang.Long.parseLong(sb.toString(), 16).and(0xFFFFFFFF).toInt()
        vm.writeregInt(dest, convertedInt)
    }

    @Strictfp fun ADD(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) + vm.readregFloat(m)) }
    @Strictfp fun SUB(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) - vm.readregFloat(m)) }
    @Strictfp fun MUL(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) * vm.readregFloat(m)) }
    @Strictfp fun DIV(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) / vm.readregFloat(m)) }
    @Strictfp fun POW(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) pow vm.readregFloat(m)) }
    @Strictfp fun MOD(dest: Register, src: Register, m: Register) { vm.writeregFloat(dest, vm.readregFloat(src) fmod vm.readregFloat(m)) }

    fun ADDINT(dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) + vm.readregInt(m)) }
    fun SUBINT(dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) - vm.readregInt(m)) }
    fun MULINT(dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) * vm.readregInt(m)) }
    fun DIVINT(dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) / vm.readregInt(m)) }
    fun POWINT(dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) pow vm.readregInt(m)) }
    fun MODINT(dest: Register, src: Register, m: Register) { vm.writeregInt(dest, vm.readregInt(src) fmod vm.readregInt(m)) }

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
        vm.writeregInt(dest, memspace[index].toUint() or memspace[index + 1].toUint().shl(8) or memspace[index + 2].toUint().shl(16) or memspace[index + 3].toUint().shl(24))
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
        val destValue = vm.readregInt(dest)
        memspace[index]     = destValue.byte1()
        memspace[index + 1] = destValue.byte2()
        memspace[index + 2] = destValue.byte3()
        memspace[index + 3] = destValue.byte4()
    }



    fun CMP(dest: Register, src: Register, lr: Int) {
        val lhand = if (lr and 0b10 != 0) vm.readregFloat(dest).toDouble() else vm.readregInt(dest).toDouble()
        val rhand = if (lr and 0b01 != 0) vm.readregFloat(src).toDouble() else vm.readregInt(src).toDouble()
        vm.cp = if (lhand == rhand) 0 else if (lhand > rhand) 1 else -1
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
    fun STOREHWORDI(dest: Register, peri: Register, halfword: Int) {
        val memspace = if (vm.readregInt(peri) == 0) vm.memory else vm.peripherals[vm.readregInt(peri)]!!.memory

        memspace[vm.readregInt(dest)] = halfword.and(0xFF).toByte()
        memspace[vm.readregInt(dest) + 1] = halfword.ushr(8).and(0xFF).toByte()
    }
    fun LOADWORDIMEM(dest: Register, offset: Int) {
        val index = offset shl 2
        vm.writeregInt(dest, vm.memory[index].toUint() or vm.memory[index + 1].toUint().shl(8) or vm.memory[index + 2].toUint().shl(16) or vm.memory[index + 3].toUint().shl(24))
    }
    fun STOREWORDIMEM(dest: Register, offset: Int) {
        val index = offset shl 2
        val destValue = vm.readregInt(dest)
        vm.memory[index]     = destValue.byte1()
        vm.memory[index + 1] = destValue.byte2()
        vm.memory[index + 2] = destValue.byte3()
        vm.memory[index + 3] = destValue.byte4()
    }

    /**
     * Push whatever value in the dest register into the stack
     */
    fun PUSH(dest: Register) {
        if (vm.sp < vm.stackSize!!) {
            val value = vm.readregInt(dest)
            vm.memory[vm.ivtSize + 4 * vm.sp    ] = value.byte1()
            vm.memory[vm.ivtSize + 4 * vm.sp + 1] = value.byte2()
            vm.memory[vm.ivtSize + 4 * vm.sp + 2] = value.byte3()
            vm.memory[vm.ivtSize + 4 * vm.sp + 3] = value.byte4()
            vm.sp++

            // old code
            //vm.callStack[vm.sp++] = vm.readregInt(dest)
        }
        else
            vm.interruptStackOverflow()
    }

    /**
     * Push memory address offset immediate into the stack
     */
    private fun _PUSHWORDI(offset: Int) {
        if (vm.sp < vm.stackSize!!) {
            vm.memory[vm.ivtSize + 4 * vm.sp    ] = offset.byte1()
            vm.memory[vm.ivtSize + 4 * vm.sp + 1] = offset.byte2()
            vm.memory[vm.ivtSize + 4 * vm.sp + 2] = offset.byte3()
            vm.memory[vm.ivtSize + 4 * vm.sp + 3] = offset.byte4()
            vm.sp++

            // old code
            //vm.callStack[vm.sp++] = offset shl 2
        }
        else
            vm.interruptStackOverflow()
    }

    /**
     * Pop whatever value in the stack and write the value to the register
     */
    fun POP(dest: Register) {
        vm.sp--

        val value = ByteArray(4)
        value[0] = vm.memory[vm.ivtSize + 4 * vm.sp]
        value[1] = vm.memory[vm.ivtSize + 4 * vm.sp + 1]
        value[2] = vm.memory[vm.ivtSize + 4 * vm.sp + 2]
        value[3] = vm.memory[vm.ivtSize + 4 * vm.sp + 3]

        // fill with FF for security
        System.arraycopy(vm.bytes_ffffffff, 0, vm.memory, vm.ivtSize + 4 * vm.sp, 4)

        if (dest == 0) {
            vm.lr = value.toLittleInt()
        }
        else {
            vm.writeregInt(dest, value.toLittleInt())
        }

        // old code
        //vm.writeregInt(dest, vm.callStack[--vm.sp])
    }

    /**
     * Pop whatever value in the stack and write the value to the Link Register
     */
    /*fun POPWORDI() {
        vm.sp--

        val value = ByteArray(4)
        value[0] = vm.memory[vm.ivtSize + 4 * vm.sp]
        value[1] = vm.memory[vm.ivtSize + 4 * vm.sp + 1]
        value[2] = vm.memory[vm.ivtSize + 4 * vm.sp + 2]
        value[3] = vm.memory[vm.ivtSize + 4 * vm.sp + 3]

        // fill with FF for security
        System.arraycopy(vm.bytes_ffffffff, 0, vm.memory, vm.ivtSize + 4 * vm.sp, 4)

        vm.lr = value.toLittleInt()

        // old code
        // vm.lr = vm.callStack[--vm.sp]
    }*/



    fun JMP(offset: Int) { vm.pc = offset shl 2 }
    fun JZ (offset: Int) { if (vm.cp == 0) JMP(offset) }
    fun JNZ(offset: Int) { if (vm.cp != 0) JMP(offset) }
    fun JGT(offset: Int) { if (vm.cp >  0) JMP(offset) }
    fun JLS(offset: Int) { if (vm.cp <  0) JMP(offset) }
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
        val Rd = opcode.and(0b000_000_1111_0000_0000_00000000000000).ushr(22)
        val Rs = opcode.and(0b000_000_0000_1111_0000_00000000000000).ushr(18)
        val Rm = opcode.and(0b000_000_0000_0000_1111_00000000000000).ushr(14)
        val cond = opcode.ushr(29)
        val offset = opcode.and(0x3F_FFFF)


        fun execInCond(action: () -> Unit) {
            when (cond) {
                0 -> action()
                1 -> if (vm.cp == 0) action()
                2 -> if (vm.cp != 0) action()
                3 -> if (vm.cp >  0) action()
                4 -> if (vm.cp <  0) action()
                else -> throw UnknownOpcodeExpection(opcode)
            }
        }


        execInCond { when (opcode.ushr(26).and(0b111)) {
            0b000 -> {
                // Mathematical and Register data transfer
                if (opcode.and(0x3F00) == 0) {
                    when (opcode.and(0xFF)) {
                        0 -> HALT()
                        32 -> YIELD()
                        42 -> PAUSE()

                        1 -> ADD(Rd, Rs, Rm)
                        2 -> SUB(Rd, Rs, Rm)
                        3 -> MUL(Rd, Rs, Rm)
                        4 -> DIV(Rd, Rs, Rm)
                        5 -> POW(Rd, Rs, Rm)
                        6 -> MOD(Rd, Rs, Rm)

                        33 -> ADDINT(Rd, Rs, Rm)
                        34 -> SUBINT(Rd, Rs, Rm)
                        35 -> MULINT(Rd, Rs, Rm)
                        36 -> DIVINT(Rd, Rs, Rm)
                        37 -> POWINT(Rd, Rs, Rm)
                        38 -> MODINT(Rd, Rs, Rm)

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

                        48 -> MOV(Rd, Rs)
                        49 -> XCHG(Rd, Rs)
                        50 -> INC(Rd)
                        51 -> DEC(Rd)
                        52 -> MALLOC(Rd, Rs)

                        54 -> FTOI(Rd, Rs)
                        55 -> ITOF(Rd, Rs)
                        56 -> ITOS(Rd, Rs)
                        57 -> STOI(Rd, Rs)
                        58 -> FTOS(Rd, Rs)
                        59 -> STOF(Rd, Rs)
                        60 -> ITOX(Rd, Rs)
                        61 -> XTOI(Rd, Rs)

                        62 -> JSR(Rd)
                        63 -> RETURN()

                        // Load and Store to memory
                        in 0b1_000_000..0b1_000_111 -> {
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

                        // SRR/SXCHG
                        0b10_000000 -> SRR(Rd, Rs)
                        0b10_000001 -> SXCHG(Rd, Rs)
                        0b11_000000 -> SRW(Rd, Rs)

                        // not a scope
                        else -> throw UnknownOpcodeExpection(opcode)
                    }
                }
                // compare
                else if (opcode.and(0x3F00) in 0x100..0x103) {
                    val lr = opcode.and(0b11)

                    CMP(Rd, Rs, lr)
                }
                // push and pop
                else if (opcode.and(0x3F00) == 0x200 || opcode.and(0x3F00) == 0x300) {
                    val c = opcode.ushr(8).and(1)

                    if (c == 0) {
                        PUSH(Rd)
                    }
                    else {
                        POP(Rd)
                    }
                }
                else {
                    throw UnknownOpcodeExpection(opcode)
                }
            }
            // load word immediate
            0b001 -> {
                val C = opcode.ushr(17).and(1)
                val M = opcode.ushr(16).and(1)
                val halfword = opcode.and(0xFFFF)

                if (C == 0)
                    LOADWORDI(Rd, halfword, M == 1)
                else
                    STOREHWORDI(Rd, Rs, halfword)
            }
            // Load and Store a word from register to memory
            0b010 -> {
                 LOADWORDIMEM(Rd, offset)
            }
            0b011 -> {
                 STOREWORDIMEM(Rd, offset)
            }
            // Conditional jump
            0b100 -> {
                when (cond) {
                    0 -> JMP(offset)
                    1 -> JZ (offset)
                    2 -> JNZ(offset)
                    3 -> JGT(offset)
                    4 -> JLS(offset)
                    5 -> JFW(offset)
                    6 -> JBW(offset)
                    else -> throw UnknownOpcodeExpection(opcode)
                }
            }
            // Jump to Subroutine Immediate
            0b101 -> {
                JSRI(offset)
            }
            // Call peripheral; Get memory size; Get uptime; Interrupt
            0b110 -> {
                val irq = opcode.and(0xFF)
                val cond2 = opcode.ushr(8).and(0x3FFF)

                if (cond2 == 0x1001) {
                    CALL(Rd, irq)
                }
                else if (cond2 == 0x1002) {
                    MEMSIZE(Rd, irq)
                }
                else if (cond2 == 0x3F00 && Rd == 0b1111) {
                    INT(irq)
                }
                else {
                    throw UnknownOpcodeExpection(opcode)
                }
            }
            else -> throw UnknownOpcodeExpection(opcode)
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
            "LOADBYTEI" to 2, // alias of LOADWORDILO
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
            "ADDINT" to 3,
            "SUBINT" to 3,
            "MULINT" to 3,
            "DIVINT" to 3,
            "POWINT" to 3,
            "MODINT" to 3,
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

            "ITOS" to 2,
            "STOI" to 2,
            "FTOS" to 2,
            "STOF" to 2,
            "ITOX" to 2,
            "XTOI" to 2,

            "INC" to 1,
            "DEC" to 1,

            //"MEMCPY" to 3,
            "CMP" to 2,
            "CMPII" to 2,
            "CMPIF" to 2,
            "CMPFI" to 2,
            "CMPFF" to 2,

            "PUSH" to 1,
            //"PUSHWORDI" to 1,
            "POP" to 1,
            //"POPWORDI" to 0,

            "JMP" to 1,
            "JZ" to 1,
            "JNZ" to 1,
            "JGT" to 1,
            "JLS" to 1,
            "JFW" to 1,
            "JBW" to 1,

            "JSRI" to 1,

            "CALL" to 2,
            "MEMSIZE" to 2,
            "UPTIME" to 1,
            "INT" to 1,

            "SRR" to 2,
            "SXCHG" to 2,
            "SRW" to 2
    )

    private val returnType = hashMapOf(
            "MOV" to "Int32",
            "XCHG" to "Int32",
            "INC" to "Int32",
            "DEC" to "Int32",
            "FTOI" to "Float",
            "ITOF" to "Int32",
            "ITOS" to "Int32",
            "STOI" to "Int32",
            "FTOS" to "Int32",
            "STOF" to "Float",
            "ITOX" to "Int32",
            "XTOI" to "Int32",
            "ADD" to "Float",
            "SUB" to "Float",
            "MUL" to "Float",
            "DIV" to "Float",
            "POW" to "Float",
            "MOD" to "Float",
            "ADDINT" to "Int32",
            "SUBINT" to "Int32",
            "MULINT" to "Int32",
            "DIVINT" to "Int32",
            "POWINT" to "Int32",
            "MODINT" to "Int32",
            "SHL" to "Int32",
            "SHR" to "Int32",
            "USHR" to "Int32",
            "AND" to "Int32",
            "OR" to "Int32",
            "XOR" to "Int32",
            "ABS" to "Float",
            "SIN" to "Float",
            "COS" to "Float",
            "TAN" to "Float",
            "FLOOR" to "Float",
            "CEIL" to "Float",
            "ROUND" to "Float",
            "LOG" to "Float",
            "RNDI" to "Float",
            "RND" to "Float",
            "SGN" to "Float",
            "SQRT" to "Float",
            "CBRT" to "Float",
            "INV" to "Float",
            "RAD" to "Float",
            "NOT" to "Int32"
    )

    val threeArgsCmd = hashSetOf<String>()
    val twoArgsCmd = hashSetOf<String>()
    val returnsIntegerCmd = hashSetOf<String>()
    val returnsFloatCmd = hashSetOf<String>()

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

        returnType.forEach { t, u ->
            if ("Float" == u) {
                returnsFloatCmd.add(t)
                returnsFloatCmd.add(t + "Z")
                returnsFloatCmd.add(t + "NZ")
                returnsFloatCmd.add(t + "GT")
                returnsFloatCmd.add(t + "LS")
            }
            else if ("Int32" == u) {
                returnsIntegerCmd.add(t)
                returnsIntegerCmd.add(t + "Z")
                returnsIntegerCmd.add(t + "NZ")
                returnsIntegerCmd.add(t + "GT")
                returnsIntegerCmd.add(t + "LS")
            }
        }
    }

    fun getReturnType(cmd: String): String? =
            when (cmd) {
                in returnsIntegerCmd -> "int"
                in returnsFloatCmd -> "float"
                else -> null
            }


    private infix fun Float.pow(other: Float) = Math.pow(this.toDouble(), other.toDouble()).toFloat()
    private infix fun Float.fmod(other: Float) = Math.floorMod(this.toInt(), other.toInt()).toFloat()
    private infix fun Int.pow(other: Int) = Math.pow(this.toDouble(), other.toDouble()).toInt()
    private infix fun Int.fmod(other: Int) = Math.floorMod(this.toInt(), other.toInt()).toInt()

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