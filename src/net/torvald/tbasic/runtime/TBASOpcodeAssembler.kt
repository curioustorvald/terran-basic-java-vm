package net.torvald.tbasic.runtime

import net.torvald.tbasic.TBASOpcodes

/**
 * - Space/Tab/comma is a delimiter.
 * - Any line starts with # is comment
 * - Any text after # is a comment
 * - Labelling: @label_name
 * - String is surrounded with double quote
 * - A statement must end with ;
 *
 * Example program
 *
 * LOADSTR 1, Helvetti world!;
 * PRINTSTR;
 *
 * This prints out 'Helvetti world!' on the standard output.
 *
 *
 * Created by minjaesong on 2017-05-28.
 */
object TBASOpcodeAssembler {

    private val delimeters = Regex("[ \t,]+")
    private val comments = Regex("#[^\n]*")
    private val blankLines = Regex("(?<=;)[\n ]+")
    private val labelMarker = '@'
    private val lineEndMarker = ';'

    private val labelTable = HashMap<String, Int>()


    fun debug(any: Any?) { if (false) println(any) }

    operator fun invoke(userProgram: String): ByteArray {
        val ret = ArrayList<Byte>()

        userProgram
                .replace(comments, "")
                .replace(blankLines, "")
                .split(lineEndMarker).forEach { line ->

            val lineSplitted = line.split(delimeters)


            if (line.isEmpty() || lineSplitted.isEmpty()) {
                // NOP
            }
            else if (!line.startsWith(labelMarker)) {

                debug("[TBASASM] line: $line")
                lineSplitted.forEach {
                    debug("--> $it")
                }


                val cmd = lineSplitted[0].toUpperCase()

                if (TBASOpcodes.opcodesList[cmd] == null) {
                    throw Error("Invalid assembly: $cmd")
                }

                ret.add(TBASOpcodes.opcodesList[cmd]!!)

                val argumentInfo = TBASOpcodes.opcodeArgsList[cmd] ?: intArrayOf()

                // By the definition, "string argument" is always the last, and only one should exist.
                if (argumentInfo.isNotEmpty()) {
                    argumentInfo.forEachIndexed { index, it ->

                        debug("[TBASASM] argsInfo index: $index, size: $it")

                        if (it == TBASOpcodes.SIZEOF_BYTE) {
                            ret.add(lineSplitted[index + 1].toByte())
                        }
                        else if (it == TBASOpcodes.SIZEOF_NUMBER) {

                            lineSplitted[index + 1].toDouble().toLittle().forEach {
                                ret.add(it)
                            }
                        }
                        else if (it == TBASOpcodes.SIZEOF_INT32) {

                            lineSplitted[index + 1].toInt().toLittle().forEach {
                                ret.add(it)
                            }
                        }
                        else if (it == TBASOpcodes.READ_UNTIL_ZERO) {

                            val strStart = line.indexOf(lineSplitted[index + 1], ignoreCase = false)
                            val strEnd = line.length

                            val strArg = line.substring(strStart, strEnd)

                            debug("--> strArg: $strArg")

                            strArg.toCString().forEach { ret.add(it) }
                            // using toCString(): null terminator is still required as executor requires it (READ_UNTIL_ZERO, literally)
                        }
                        else {
                            throw IllegalArgumentException("Unknown argument type/size")
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