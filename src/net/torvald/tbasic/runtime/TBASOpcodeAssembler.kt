package net.torvald.tbasic.runtime

import net.torvald.tbasic.TBASOpcodes
import net.torvald.tbasic.TBASOpcodes.SIZEOF_POINTER

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
 *
 * Created by minjaesong on 2017-05-28.
 */
object TBASOpcodeAssembler {

    private val delimiters = Regex("""[ \t,]+""")
    private val comments = Regex("""#[^\n]*""")
    private val blankLines = Regex("""(?<=;)[\n ]+""")
    private val stringMarker = Regex("""\"[^\n]*\"""")
    private val labelMarker = '@'
    private val labelDefinitionMarker = ':'
    private val lineEndMarker = ';'
    private val sectionHeading = Regex("""\.[A-Za-z0-9_]+""")
    private val matchInteger = Regex("""[0-9]+""")

    private val labelTable = HashMap<String, Int>() // valid name: @label_name_in_lower_case

    private var currentSection = ".CODE"

    val asmSections = hashSetOf<String>(".CODE", ".DATA", ".FUNC")


    private fun debug(any: Any?) { if (true) { println(any) } }

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

    operator fun invoke(userProgram: String): ByteArray {
        val ret = ArrayList<Byte>()

        fun getPC() = VM.interruptCount * 4 + ret.size


        // pass 1: pre-scan for labels
        debug("\n\n== Pass 1 ==\n\n")
        var virtualPC = VM.interruptCount * 4
        userProgram
                .replace(comments, "")
                .replace(blankLines, "")
                .split(lineEndMarker).forEach { lline ->

            var line = lline.replace(Regex("""^ ?[\n]+"""), "") // do not remove  ?, this takes care of spaces prepended on comment marker
            val words = line.split(delimiters)
            val cmd = words[0].toUpperCase()


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
                            val strStart = line.indexOf(words[2], ignoreCase = false)
                            val strEnd = line.length
                            val data = line.substring(strStart, strEnd)

                            //debug("--> String payload: [$data]")

                            data.toCString().forEach { virtualPC += 1 }
                            // using toCString(): null terminator is still required as executor requires it (READ_UNTIL_ZERO, literally)
                        }
                        "NUMBER" -> {
                            val number = words[2].toDouble()

                            //debug("--> Number payload: [$number]")

                            number.toLittle().forEach { virtualPC += 1 }
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
                        if (TBASOpcodes.opcodesList[cmd] == null) {
                            throw Error("Invalid assembly: $cmd")
                        }

                        virtualPC += 1

                        val argumentInfo = TBASOpcodes.opcodeArgsList[cmd] ?: intArrayOf()

                        // By the definition, "string argument" is always the last, and only one should exist.
                        if (argumentInfo.isNotEmpty()) {
                            argumentInfo.forEachIndexed { index, it ->

                                //debug("[TBASASM] argsInfo index: $index, size: $it")

                                try {
                                    when (it) {
                                        TBASOpcodes.SIZEOF_BYTE -> {
                                            virtualPC += 1
                                        }
                                        TBASOpcodes.SIZEOF_NUMBER -> {
                                            virtualPC += 8
                                        }
                                        TBASOpcodes.SIZEOF_INT32 -> {
                                            virtualPC += 4
                                        }
                                        TBASOpcodes.READ_UNTIL_ZERO -> {
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
        userProgram
                .replace(comments, "")
                .replace(blankLines, "")
                .split(lineEndMarker).forEach { lline ->

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
                        ret.add(TBASOpcodes.JMP)
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
                            val strStart = line.indexOf(words[2], ignoreCase = false)
                            val strEnd = line.length
                            val data = line.substring(strStart, strEnd)

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
                        ret.add(TBASOpcodes.JMP)
                        repeat(SIZEOF_POINTER) { ret.add(0xFF.toByte()) } // temporary values, must be specified by upcoming .code section
                        flagSpecifyJMP = true
                    }


                    if (cmd.startsWith(labelDefinitionMarker)) {
                        putLabel(cmd.drop(1).toLowerCase(), getPC())
                        // will continue to next statements
                    }
                    else {
                        if (TBASOpcodes.opcodesList[cmd] == null) {
                            throw Error("Invalid assembly: $cmd")
                        }

                        ret.add(TBASOpcodes.opcodesList[cmd]!!)

                        val argumentInfo = TBASOpcodes.opcodeArgsList[cmd] ?: intArrayOf()

                        // By the definition, "string argument" is always the last, and only one should exist.
                        if (argumentInfo.isNotEmpty()) {
                            argumentInfo.forEachIndexed { index, it ->

                                debug("[TBASASM] argsInfo index: $index, size: $it")

                                try {
                                    when (it) {
                                        TBASOpcodes.SIZEOF_BYTE -> {
                                            ret.add(words[index + 1].toInt().toByte())
                                        }
                                        TBASOpcodes.SIZEOF_NUMBER -> {
                                            if (words[index + 1].startsWith(labelMarker)) {
                                                TODO("label that points to Number (${words[index + 1]})")
                                            }
                                            else {
                                                words[index + 1].toDouble().toLittle().forEach {
                                                    ret.add(it)
                                                }
                                            }
                                        }
                                        TBASOpcodes.SIZEOF_INT32 -> {
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
                                        TBASOpcodes.READ_UNTIL_ZERO -> {
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

        return ret.toByteArray()
    }

}