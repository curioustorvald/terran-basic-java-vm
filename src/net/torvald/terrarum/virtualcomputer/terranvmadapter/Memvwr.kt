package net.torvald.terrarum.virtualcomputer.terranvmadapter

import net.torvald.terranvm.Opcodes
import net.torvald.terranvm.runtime.TerranVM
import net.torvald.terranvm.runtime.toUint
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.JFrame
import javax.swing.JTextArea
import javax.swing.WindowConstants

/**
 * Created by minjaesong on 2017-11-21.
 */
class Memvwr(val vm: TerranVM) : JFrame("TerranVM Memory Viewer - Core Memory") {

    val memArea = JTextArea()

    var columns = 16

    fun composeMemText() {
        val sb = StringBuilder()

        /*
        r1: 0.000000000 (Number)
        000000 : 00 00 00 00 00 00 00 48 00 00 00 50 00 00 00 58 | .......H...P...X
         */

        // registers
        for (r in 1..8) {
            val rreg = vm.readreg(r)
            val breg = vm.readbreg(r).toInt()

            val rregtype = breg.ushr(2).and(7)
            val rregtypeStr = when (rregtype) {
                0 -> "Nil"
                1 -> "Boolean"
                2 -> "Number"
                3 -> "Bytes"
                4 -> "String"
                else -> "Undefined--$rregtype"
            }
            val isInt = breg.and(1) != 0
            val isPtr = breg.and(2) != 0

            sb.append("r$r: ")

            if (!isInt && !isPtr) {
                if (rregtype == Opcodes.TYPE_NUMBER) {
                    sb.append("$rreg ($rregtypeStr)")
                }
                else {
                    sb.append("${java.lang.Double.doubleToRawLongBits(rreg).toString(16).toUpperCase()}h ($rregtypeStr)")
                }
            }
            else if (isInt && !isPtr) {
                sb.append("${java.lang.Double.doubleToRawLongBits(rreg).toString(16).toUpperCase()}h (Int)")
            }
            else if (isInt && isPtr) {
                sb.append("${rreg.toInt().toString(16).padStart(8, '0').toUpperCase()}h (Pointer)")
            }
            else {
                sb.append("${java.lang.Double.doubleToRawLongBits(rreg).toString(16).toUpperCase()}h (Null Pointer)")
            }

            sb.append('\n')
        }

        for (m in 1..4) {
            val mreg = when (m) {
                1 -> vm.m1
                2 -> vm.m2
                3 -> vm.m3
                4 -> vm.m4
                else -> throw InternalError("Your RAM just got hit by gamma radiation.")
            }

            sb.append("m$m: ${mreg.toLong().and(0xffffffff).toString(16).padStart(8, '0').toUpperCase()}h ($mreg)\n")
        }

        sb.append("uptime: ${vm.uptime} ms\n")



        // coremem
        for (i in 0..vm.memory.lastIndex) {
            if (i % columns == 0) {
                sb.append(i.toString(16).toUpperCase().padStart(6, '0')) // mem addr
                sb.append(" : ") // separator
            }


            sb.append(vm.memory[i].toUint().toString(16).toUpperCase().padStart(2, '0'))
            sb.append(' ') // mem value


            // ASCII viewer
            if (i % columns == 15) {
                sb.append("| ")

                for (x in -15..0) {
                    val mem = vm.memory[i + x].toUint()

                    if (mem < 32) {
                        sb.append('.')
                    }
                    else {
                        sb.append(mem.toChar())
                    }
                }

                sb.append('\n')
            }
        }


        // stack
        sb.append("stack size: ${vm.sp}/${vm.callStack.size}\n")
        for (i in 0 until vm.sp) {
            sb.append("s${i.toString().padStart(4, '0')} : ${vm.callStack[i].toInt().toString(16).padStart(8, '0').toUpperCase()}h\n")
        }


        // peripherals
        sb.append("peripherals:\n")
        for (i in 0 until vm.peripherals.size) {
            sb.append("peri[$i]: ${vm.peripherals[i]?.javaClass?.simpleName ?: "null"}\n")
        }


        memArea.text = sb.toString()
    }


    fun update() {
        composeMemText()
    }



    init {
        memArea.font = Font("Monospaced", Font.PLAIN, 12)
        memArea.highlighter = null

        this.layout = BorderLayout()
        this.isVisible = true
        this.add(javax.swing.JScrollPane(memArea), BorderLayout.CENTER)
        this.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        this.size = Dimension(600, 960)
    }

}