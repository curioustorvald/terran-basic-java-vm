package net.torvald.terrarum.virtualcomputer.terranvmadapter

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.*
import javax.swing.*

/**
 * Created by minjaesong on 2018-05-11.
 */
class PoketchDotHelper(val pictW: Int, val pictH: Int) : JFrame() {


    val pixelsCheckBox = Array<JCheckBox>(pictW * pictH) { JCheckBox() }

    val pixelsGrid = JPanel(GridLayout(pictH, pictW))
    val textBox = JTextArea()
    val textBoxPanel = JPanel(BorderLayout())

    val pixelAreaSize = 18

    val buttonUpdate = JButton("Update")
    val buttonReset = JButton("Reset")
    val buttonInvert = JButton("Invert")

    val mainPanel = JPanel(GridLayout(1, 2))
    val menuPanel = JPanel()

    init {
        if (pictW % 8 != 0) {
            throw IllegalArgumentException("Picture width must be multiple of 8")
        }


        pixelsCheckBox.forEach {
            pixelsGrid.add(it)
        }
        pixelsGrid.isVisible = true

        textBox.text = "Check anywhere to begin. Corresponding bytes will be displayed here."
        textBoxPanel.add(textBox)
        textBoxPanel.isVisible = true


        mainPanel.add(pixelsGrid)
        mainPanel.add(textBoxPanel)


        buttonUpdate.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                updateText()
            }
        })

        buttonReset.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                pixelsCheckBox.forEach { it.isSelected = false }
            }
        })

        buttonInvert.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                pixelsCheckBox.forEach { it.isSelected = !it.isSelected }
            }
        })


        menuPanel.add(buttonUpdate)
        menuPanel.add(buttonReset)
        menuPanel.add(buttonInvert)


        this.layout = BorderLayout()
        this.defaultCloseOperation = EXIT_ON_CLOSE
        this.add(mainPanel, BorderLayout.CENTER)
        this.add(menuPanel, BorderLayout.SOUTH)
        this.isVisible = true
        this.setSize(pictW * pixelAreaSize * 2, pictH * pixelAreaSize + 20)
    }


    private fun updateText() {
        val sb = StringBuilder()
        var akku = 0
        var appended = 0
        pixelsCheckBox.forEachIndexed { index, jCheckBox ->
            val bitmask = index % 8
            akku = akku or if (jCheckBox.isSelected) 1 shl bitmask else 0
            
            if (bitmask == 7 || index == pixelsCheckBox.lastIndex) {
                sb.append("${akku.toString(16).padStart(2, '0').toUpperCase()}h,")
                akku = 0
                appended++


                if (appended == pictW / 4) {
                    appended = 0
                    sb.append('\n')
                }
            }
        }
        
        
        textBox.text = sb.substring(0, sb.lastIndex - 1) + ";"
    }
}

fun main(args: Array<String>) {
    PoketchDotHelper(24,10)
}