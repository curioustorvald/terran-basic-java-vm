package net.torvald.tbasic.runtime

import net.torvald.tbasic.TBASOpcodes

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
 *      LOADSTR 1, Helvetti world!;
 *      PRINTSTR;
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
 * - code
 *
 * Indentation after section header is optional.
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
 *
 * You use your label in code by '@label_name', just like line labels.
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
    private val lineEndMarker = ';'
    private val sectionHeading = Regex("""\.[A-Za-z0-9_]+""")

    private val labelTable = HashMap<String, Int>() // valid name: @label_name_in_lower_case

    private var currentSection = ".CODE"

    val asmSections = hashSetOf<String>(".CODE", ".DATA")


    fun debug(any: Any?) { if (false) println(any) }


    var flagSpecifyJMP = false


    private fun putLabel(name: String, pointer: Int) {
        val name = labelMarker + name.toLowerCase()
        if (labelTable[name] != null) {
            throw Error("Label $name already defined")
        }
        else {
            debug(" ->> put label [$name] with pc $pointer")
            labelTable[name] = pointer
        }
    }

    private fun getLabel(marked_labelname: String): Int {
        val name = marked_labelname.toLowerCase()
        return labelTable[name] ?: throw Error("Label [$name] not defined")
    }

    operator fun invoke(userProgram: String): ByteArray {
        val ret = ArrayList<Byte>()

        fun getPC() = VM.interruptCount * 4 + ret.size


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

                debug("[TBASASM] line: [$line]")
                words.forEach { debug("--> $it") }


                val cmd = words[0].toUpperCase()


                if (asmSections.contains(cmd)) { // sectioning commands
                    currentSection = cmd
                    // will continue to next statements
                }
                else if (currentSection == ".DATA") { // setup DB

                    // insert JMP instruction that jumps to .code section
                    ret.add(TBASOpcodes.JMP)
                    repeat(4) { ret.add(0xFF.toByte()) } // temporary values, must be specified by upcoming .code section
                    flagSpecifyJMP = true


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

                            debug("--> String payload: [$data]")

                            data.toCString().forEach { ret.add(it) }
                            // using toCString(): null terminator is still required as executor requires it (READ_UNTIL_ZERO, literally)
                        }
                        "NUMBER" -> {
                            val number = words[2].toDouble()

                            debug("--> Number payload: [$number]")

                            number.toLittle().forEach { ret.add(it) }
                        }
                        "INT" -> {
                            val int = words[2].toInt()

                            debug("--> Int payload: [$int]")

                            int.toLittle().forEach { ret.add(it) }
                        }
                        else -> throw IllegalArgumentException("Unsupported data type: [$type]")
                    }

                }
                else if (currentSection == ".CODE") { // interpret codes
                    if (flagSpecifyJMP) {
                        val pcLittle = getPC().toLittle()
                        pcLittle.forEachIndexed { index, byte -> ret[1 + index] = byte }
                        flagSpecifyJMP = false
                    }


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
                                        ret.add(words[index + 1].toByte())
                                    }
                                    TBASOpcodes.SIZEOF_NUMBER -> {
                                        if (words[index + 1].startsWith(labelMarker)) {
                                            TODO("label that points to Number")
                                        }
                                        else {
                                            words[index + 1].toDouble().toLittle().forEach {
                                                ret.add(it)
                                            }
                                        }
                                    }
                                    TBASOpcodes.SIZEOF_INT32 -> {
                                        if (words[index + 1].startsWith(labelMarker)) {
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
                                            throw Error("Labels are supposed to be used as Pointer, not substitute for in-line String\nIf you are using LOADSTR, what you will want to use is LOADPTR.")
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
            else {
                TODO("ASM label")
            }

        }

        return ret.toByteArray()
    }

}