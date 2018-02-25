package net.torvald.terrarum.virtualcomputer.terranvmadapter

import net.torvald.terranvm.runtime.TerranVM
import net.torvald.terranvm.runtime.to8HexString
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
        r1: 00000000h; 0; 0.0f
        rCMP: -1
        000000 : 00 00 00 00 00 00 00 48 00 00 00 50 00 00 00 58 | .......H...P...X
         */

        // registers
        for (r in 1..8) {
            val rI = vm.readregInt(r)
            val rF = vm.readregFloat(r)

            sb.append("r$r: " +
                    "${rI.to8HexString()}; " +
                    "$rI; ${rF}f\n"
            )
        }

        sb.append("rCMP: " +
                "${vm.rCMP.to8HexString()}; " +
                "${vm.rCMP}\n"
        )


        sb.append("uptime: ${vm.uptime} ms\n")



        sb.append("ADRESS :  0  1  2  3  4  5  6  7  8  9  A  B  C  D  E  F\n")


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
            sb.append("s${i.toString().padStart(4, '0')} : ${vm.callStack[i].to8HexString()}\n")
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