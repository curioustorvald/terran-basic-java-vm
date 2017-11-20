package net.torvald.terranvm.runtime

import net.torvald.terranvm.Opcodes
import net.torvald.terranvm.Opcodes.SIZEOF_POINTER

/**
 * ## Syntax
 * - Space/Tab/comma is a delimiter.
 * - Any line starts with # is comment
 * - Any text after # is a comment
 * - Labelling: @label_name
 * - String is surrounded with double quote
 * - Every line must end with ; (@TODO wow, such primitive!)
 *
 * Example program
 * ```
 * .code
 *
 * LOADSTRINLINE 1, Helvetti world!;
 * PRINTSTR;
 * ```
 * This prints out 'Helvetti world!' on the standard output.
 *
 *
 * ## Sections
 *
 * TBAS Assembly can be divided into _sections_ that the assembler supports. If no section is given, 'code' is assumed.
 *
 * Supported sections:
 * - data
 * - func
 * - code
 *
 * Indentation after section header is optional (and you probably don't want it anyway).
 *
 * ### Data
 * Data section has following syntax:
 * ```
 * type label_name payload
 * ```
 * Label names are case insensitive.
 * Available types:
 * - STRING
 * - NUMBER
 * - INT
 * - BYTES (byte literals, along with pointer label -- pointer label ONLY!)
 *
 * You use your label in code by '@label_name', just like line labels.
 *
 *
 * ### Label
 * Predefined labels:
 * - r1..r8
 * - m1..m4
 *
 * Defining labels:
 * :label_name;
 *
 * Referring labels:
 * @label_name
 *
 *
 * Created by minjaesong on 2017-05-28.
 */
object Assembler {

    private val delimiters = Regex("""[ \t,]+""")
    private val blankLines = Regex("""(?<=;)[\n ]+""")
    private val stringMarker = Regex("""\"[^\n]*\"""")
    private val labelMarker = '@'
    private val labelDefinitionMarker = ':'
    private val lineEndMarker = ';'
    private val commentMarker = '#'
    private val literalMarker = '"'
    private val sectionHeading = Regex("""\.[A-Za-z0-9_]+""")
    private val matchInteger = Regex("""[0-9]+""")
    private val regexWhitespaceNoSP = Regex("""[\t\r\n\v\f]""")
    private val prependedSpaces = Regex("""^[\s]+""")


    private val dataSectActualData = Regex("""^[A-Za-z]+[m \t]+[A-Za-z0-9_]+[m \t]+""")

    private val labelTable = HashMap<String, Int>() // valid name: @label_name_in_lower_case

    private var currentSection = ".CODE"

    val asmSections = hashSetOf<String>(".CODE", ".DATA", ".FUNC")


    private val ASM_JMP = Opcodes.opcodesList["JMP"]!!


    private fun debug(any: Any?) { if (false) { println(any) } }

    var flagSpecifyJMP = false


    private fun putLabel(name: String, pointer: Int) {
        val name = labelMarker + name.toLowerCase()
        if (labelTable[name] != null && labelTable[name] != pointer) {
            throw Error("Labeldef conflict for $name -- old: ${labelTable[name]}, new: $pointer")
        }
        else {
            if (labelTable[name] == null) debug("->> put label '$name' with pc $pointer")
            labelTable[name] = pointer
        }
    }

    private fun getLabel(marked_labelname: String): Int {
        val name = marked_labelname.toLowerCase()
        return labelTable[name] ?: throw Error("Label '$name' not defined")
    }

    private fun splitLines(program: String): List<String> {
        val lines = ArrayList<String>()
        val sb = StringBuilder()

        fun split() {
            var str = sb.toString()

            // preprocess some
            // remove prepending whitespace
            str = str.replace(prependedSpaces, "")

            lines.add(str)
            sb.setLength(0)
        }

        var literalMode = false
        var commentMode = false
        var charCtr = 0
        while (charCtr < program.length) {
            val char = program[charCtr]
            val charNext = if (charCtr < program.lastIndex) program[charCtr + 1] else null

            if (!literalMode && !commentMode) {
                if (char == literalMarker) {
                    sb.append(literalMarker)
                    literalMode = true
                }
                else if (char == commentMarker) {
                    commentMode = true
                }
                else if (char == lineEndMarker) {
                    split()
                }
                else if (!char.toString().matches(regexWhitespaceNoSP)) {
                    sb.append(char)
                }
            }
            else if (!commentMode) {
                if (char == literalMarker && charNext == lineEndMarker) { // quote end must be ";
                    sb.append(literalMarker)
                    split()
                    literalMode = false
                }
                else {
                    sb.append(char)
                }
            }
            else { // comment mode
                if (char == '\n') {
                    commentMode = false
                }
            }

            charCtr++
        }

        return lines
    }

    operator fun invoke(userProgram: String): ByteArray {
        val ret = ArrayList<Byte>()

        fun getPC() = TerranVM.interruptCount * 4 + ret.size


        // pass 1: pre-scan for labels
        debug("\n\n== Pass 1 ==\n\n")
        var virtualPC = TerranVM.interruptCount * 4
        splitLines(userProgram).forEach { lline ->

            var line = lline.replace(Regex("""^ ?[\n]+"""), "") // do not remove  ?, this takes care of spaces prepended on comment marker
            val words = line.split(delimiters)


            if (line.isEmpty() || words.isEmpty()) {
                // NOP
            }
            else if (!line.startsWith(labelMarker)) {

                debug("[TBASASM] line: '$line'")
                //words.forEach { debug("  $it") }


                val cmd = words[0].toUpperCase()


                if (asmSections.contains(cmd)) { // sectioning commands
                    currentSection = cmd
                    // will continue to next statements
                }
                else if (currentSection == ".DATA") { // setup DB

                    // insert JMP instruction that jumps to .code section
                    if (!flagSpecifyJMP) {
                        virtualPC += (SIZEOF_POINTER + 1)
                        flagSpecifyJMP = true
                    }


                    // data syntax:
                    //      type name payload (separated by any delimiters)
                    //      e.g.: string   hai   Hello, world!

                    val type = words[0].toUpperCase()
                    val name = words[1]

                    putLabel(name, virtualPC)
                    // putLabel must be followed by some bytes payload fed to the return array, no gaps in-between

                    when (type) {
                        "STRING" -> {
                            val start = line.indexOf('"') + 1
                            val end = line.lastIndexOf('"')
                            if (end <= start) throw IllegalArgumentException("malformed string declaration syntax -- must be surrounded with pair of \" (double quote)")

                            val data = line.substring(start, end)

                            virtualPC += data.toCString().size
                        }
                        "NUMBER" -> {
                            val number = words[2].toDouble()

                            //debug("--> Number payload: [$number]")

                            virtualPC += number.toLittle().size
                        }
                        "INT" -> {
                            val int = words[2].toInt()

                            //debug("--> Int payload: [$int]")

                            int.toLittle().forEach { virtualPC += 1 }
                        }
                        "BYTES" -> {
                            (2..words.lastIndex).forEach {
                                if (words[it].matches(matchInteger) && words[it].toInt() in 0..255) { // byte literal
                                    //debug("--> Byte literal payload: ${words[it].toInt()}")
                                    virtualPC += 1
                                }
                                else if (words[it].startsWith(labelMarker)) {
                                    //debug("--> Byte literal payload (label): ${words[it]}")
                                    virtualPC += SIZEOF_POINTER
                                }
                                else {
                                    throw IllegalArgumentException("Illegal byte literal ${words[it]}")
                                }
                            }
                        }
                        else -> throw IllegalArgumentException("Unsupported data type: '$type' (or you missed a semicolon?)")
                    }

                }
                else if (currentSection == ".CODE" || currentSection == ".FUNC") { // interpret codes

                    if (currentSection == ".FUNC" && !flagSpecifyJMP) {
                        // insert JMP instruction that jumps to .code section
                        virtualPC += (SIZEOF_POINTER + 1)
                        flagSpecifyJMP = true
                    }

                    if (cmd.startsWith(labelDefinitionMarker)) {
                        putLabel(cmd.drop(1).toLowerCase(), virtualPC)
                        // will continue to next statements
                    }
                    else {
                        // sanitise input
                        if (Opcodes.opcodesList[cmd] == null) {
                            throw Error("Invalid opcode: $cmd")
                        }
                        // filter arg count mismatch
                        val argCount = Opcodes.opcodeArgsList[cmd]?.size ?: 0
                        if (argCount + 1 != words.size && (!(Opcodes.opcodeArgsList[cmd]?.contains(Opcodes.ArgType.STRING) ?: false))) {
                            throw Error("Opcode $cmd -- Number of argument(s) are mismatched; requires $argCount, got ${words.size - 1}. Perhaps semicolon not placed?")
                        }

                        virtualPC += 1

                        val argumentInfo = Opcodes.opcodeArgsList[cmd] ?: arrayOf()

                        // By the definition, "string argument" is always the last, and only one should exist.
                        if (argumentInfo.isNotEmpty()) {
                            argumentInfo.forEachIndexed { index, it ->

                                //debug("[TBASASM] argsInfo index: $index, size: $it")

                                try {
                                    when (it) {
                                        Opcodes.ArgType.BYTE, Opcodes.ArgType.REGISTER -> {
                                            virtualPC += 1
                                        }
                                        Opcodes.ArgType.NUMBER -> {
                                            virtualPC += 8
                                        }
                                        Opcodes.ArgType.POINTER, Opcodes.ArgType.INT32 -> {
                                            virtualPC += 4
                                        }
                                        Opcodes.ArgType.STRING -> {
                                            if (words[index + 1].startsWith(labelMarker)) {
                                                throw Error("Labels are supposed to be used as Pointer, not substitute for in-line String\nIf you are using LOADSTRINLINE, what you will want to use is LOADPTR.")
                                            }
                                            else {
                                                val strStart = line.indexOf(words[index + 1], ignoreCase = false)
                                                val strEnd = line.length

                                                val strArg = line.substring(strStart, strEnd)

                                                //debug("--> strArg: $strArg")

                                                virtualPC += strArg.toCString().size
                                                // using toCString(): null terminator is still required as executor requires it (READ_UNTIL_ZERO, literally)
                                            }
                                        }
                                        else -> throw IllegalArgumentException("Unknown argument type/size")
                                    }
                                }
                                catch (e: ArrayIndexOutOfBoundsException) {
                                    e.printStackTrace()
                                    throw Error("Argument #${index + 1} is missing for $cmd")
                                }
                            }
                        }
                    }
                }
            }
            else {
                TODO("ASM label 1")
            }

        }



        // pass 2: program
        // --> reset flags
        flagSpecifyJMP = false
        // <-- end of reset flags
        debug("\n\n== Pass 2 ==\n\n")
        splitLines(userProgram).forEach { lline ->

            var line = lline.replace(Regex("""^ ?[\n]+"""), "") // do not remove  ?, this takes care of spaces prepended on comment marker
            val words = line.split(delimiters)


            if (line.isEmpty() || words.isEmpty()) {
                // NOP
            }
            else if (!line.startsWith(labelMarker)) {

                debug("[TBASASM] line: '$line'")
                words.forEach { debug("  $it") }


                val cmd = words[0].toUpperCase()


                if (asmSections.contains(cmd)) { // sectioning commands
                    currentSection = cmd
                    // will continue to next statements
                }
                else if (currentSection == ".DATA") { // setup DB

                    // insert JMP instruction that jumps to .code section
                    if (!flagSpecifyJMP) {
                        ret.add(ASM_JMP)
                        repeat(SIZEOF_POINTER) { ret.add(0xFF.toByte()) } // temporary values, must be specified by upcoming .code section
                        flagSpecifyJMP = true
                    }


                    // data syntax:
                    //      type name payload (separated by any delimiters)
                    //      e.g.: string   hai   Hello, world!

                    val type = words[0].toUpperCase()
                    val name = words[1]

                    putLabel(name, getPC())
                    // putLabel must be followed by some bytes payload fed to the return array, no gaps in-between

                    when (type) {
                        "STRING" -> {
                            debug("->> line: $line")

                            val start = line.indexOf('"') + 1
                            val end = line.lastIndexOf('"')
                            if (end <= start) throw IllegalArgumentException("malformed string declaration syntax -- must be surrounded with pair of \" (double quote)")

                            val data = line.substring(start, end)

                            debug("--> strStart: $start")
                            debug("--> String payload: '$data'")

                            data.toCString().forEach { ret.add(it) }
                            // using toCString(): null terminator is still required as executor requires it (READ_UNTIL_ZERO, literally)
                        }
                        "NUMBER" -> {
                            val number = words[2].toDouble()

                            debug("--> Number payload: '$number'")

                            number.toLittle().forEach { ret.add(it) }
                        }
                        "INT" -> {
                            val int = words[2].toInt()

                            debug("--> Int payload: '$int'")

                            int.toLittle().forEach { ret.add(it) }
                        }
                        "BYTES" -> {
                            (2..words.lastIndex).forEach {
                                if (words[it].matches(matchInteger) && words[it].toInt() in 0..255) { // byte literal
                                    debug("--> Byte literal payload: ${words[it].toInt()}")
                                    ret.add(words[it].toInt().toByte())
                                }
                                else if (words[it].startsWith(labelMarker)) {
                                    debug("--> Byte literal payload (label): ${words[it]}")
                                    getLabel(words[it]).toLittle().forEach {
                                        ret.add(it)
                                    }
                                }
                                else {
                                    throw IllegalArgumentException("Illegal byte literal ${words[it]}")
                                }
                            }
                        }
                        else -> throw IllegalArgumentException("Unsupported data type: '$type' (or you missed a semicolon?)")
                    }

                }
                else if (currentSection == ".CODE" || currentSection == ".FUNC") { // interpret codes
                    if (flagSpecifyJMP && currentSection == ".CODE") {
                        val pcLittle = getPC().toLittle()
                        pcLittle.forEachIndexed { index, byte -> ret[1 + index] = byte }
                        flagSpecifyJMP = false
                    }
                    else if (!flagSpecifyJMP && currentSection == ".FUNC") {
                        // insert JMP instruction that jumps to .code section
                        ret.add(ASM_JMP)
                        repeat(SIZEOF_POINTER) { ret.add(0xFF.toByte()) } // temporary values, must be specified by upcoming .code section
                        flagSpecifyJMP = true
                    }


                    if (cmd.startsWith(labelDefinitionMarker)) {
                        putLabel(cmd.drop(1).toLowerCase(), getPC())
                        // will continue to next statements
                    }
                    else {
                        if (Opcodes.opcodesList[cmd] == null) {
                            throw Error("Invalid assembly: $cmd")
                        }

                        ret.add(Opcodes.opcodesList[cmd]!!)

                        val argumentInfo = Opcodes.opcodeArgsList[cmd] ?: arrayOf()

                        // By the definition, "string argument" is always the last, and only one should exist.
                        if (argumentInfo.isNotEmpty()) {
                            argumentInfo.forEachIndexed { index, it ->

                                debug("[TBASASM] argsInfo index: $index, size: $it")

                                try {
                                    when (it) {
                                        Opcodes.ArgType.REGISTER -> {
                                            if (words[index + 1].startsWith(labelMarker)) {
                                                Error("label that points to Register (${words[index + 1]})")
                                            }
                                            else {
                                                if (words[index + 1].startsWith("r", ignoreCase = true)) {
                                                    ret.add(words[index + 1].drop(1).toInt().toByte())
                                                }
                                                else {
                                                    throw Error("Register expected, got something else (${words[index + 1]})")
                                                }
                                            }
                                        }
                                        Opcodes.ArgType.BYTE -> {
                                            if (words[index + 1].startsWith(labelMarker)) {
                                                TODO("label that points to Byte (${words[index + 1]})")
                                            }
                                            else {
                                                ret.add(words[index + 1].toInt().toByte())
                                            }
                                        }
                                        Opcodes.ArgType.NUMBER -> {
                                            if (words[index + 1].startsWith(labelMarker)) {
                                                TODO("label that points to Number (${words[index + 1]})")
                                            }
                                            else {
                                                words[index + 1].toDouble().toLittle().forEach {
                                                    ret.add(it)
                                                }
                                            }
                                        }
                                        Opcodes.ArgType.POINTER, Opcodes.ArgType.INT32 -> {
                                            if (words[index + 1].startsWith(labelMarker)) { // label for PC or Pointer number
                                                getLabel(words[index + 1]).toLittle().forEach {
                                                    ret.add(it)
                                                }
                                            }
                                            else {
                                                words[index + 1].toInt().toLittle().forEach {
                                                    ret.add(it)
                                                }
                                            }
                                        }
                                        Opcodes.ArgType.STRING -> {
                                            if (words[index + 1].startsWith(labelMarker)) {
                                                throw Error("Labels are supposed to be used as Pointer, not substitute for in-line String\nIf you are using LOADSTRINLINE, what you will want to use is LOADPTR.")
                                            }
                                            else {
                                                val strStart = line.indexOf(words[index + 1], ignoreCase = false)
                                                val strEnd = line.length

                                                val strArg = line.substring(strStart, strEnd)

                                                debug("--> strArg: $strArg")

                                                strArg.toCString().forEach { ret.add(it) }
                                                // using toCString(): null terminator is still required as executor requires it (READ_UNTIL_ZERO, literally)
                                            }
                                        }
                                        else -> throw IllegalArgumentException("Unknown argument type/size")
                                    }
                                }
                                catch (e: ArrayIndexOutOfBoundsException) {
                                    e.printStackTrace()
                                    throw Error("Argument #${index + 1} is missing for $cmd")
                                }
                            }
                        }
                    }
                }
            }
            else {
                TODO("ASM label")
            }

        }


        debug("======== Assembler: Done! ========")

        return ret.toByteArray()
    }

}