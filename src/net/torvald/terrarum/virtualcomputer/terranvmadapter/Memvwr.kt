package net.torvald.terrarum.virtualcomputer.terranvmadapter

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
class Memvwr(val vm: TerranVM) : JFrame() {

    val memArea = JTextArea()

    var columns = 16

    fun composeMemText() {
        val sb = StringBuilder()

        /*
        000000 : 00 00 00 00 00 00 00 48 00 00 00 50 00 00 00 58 | .......H...P...X
         */

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


        memArea.text = sb.toString()
    }


    fun update() {
        composeMemText()
    }



    init {
        memArea.font = Font("Monospaced", Font.PLAIN, 12)
        memArea.highlighter = null
        memArea.name = "TerranVM Memory Viewer"

        this.layout = BorderLayout()
        this.isVisible = true
        this.add(javax.swing.JScrollPane(memArea), BorderLayout.CENTER)
        this.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        this.size = Dimension(600, 960)
    }

}