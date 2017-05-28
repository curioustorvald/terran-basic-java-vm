package net.torvald.tbasic.runtime

import net.torvald.tbasic.TBASOpcodes

/**
 * - Space/Tab/comma is a delimiter.
 * - Any line starts with ; is comment
 * - Any text after ; is a comment
 * - Labelling: @label_name
 * - String is surrounded with double quote
 * - Zero or one instructions per line
 *
 * Example program
 *
 * LOADSTR 1, "Helvetti world!\n"
 * PRINTSTR
 *
 * This prints out 'Helvetti world!\n' on the standard output.
 *
 *
 * Created by minjaesong on 2017-05-28.
 */
object TBASOpcodeAssembler {

    private val delimeters = Regex("[ \t,]+")
    private val stringR = Regex("\"[^\n]*\"")
    private val comments = Regex(";[^\n]*")
    private val labelMarker = "@"

    private val labelTable = HashMap<String, Int>()


    operator fun invoke(userProgram: String): ByteArray {
        val ret = ArrayList<Byte>()

        userProgram.replace(comments, "").lines().forEach { line ->

            val lineSplitted = line.split(delimeters)


            if (line.isEmpty() || lineSplitted.isEmpty()) {
                // NOP
            }
            else if (!line.startsWith(labelMarker)) {

                println("[TBASASM] line: $line")
                lineSplitted.forEach {
                    println("    > $it")
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

                        println("[TBASASM] argsInfo index: $index, size: $it")

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

                            println("match: ${stringR.matchEntire(line)}")

                            val match = stringR.matchEntire(line)!!.groupValues[0]
                            match.substring(1..match.length - 2).toByteArray(VM.charset).forEach {
                                ret.add(it)
                            }
                            ret.add(0.toByte())
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