package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import net.torvald.terranvm.runtime.toUint

/**
 * Created by minjaesong on 2018-05-11.
 */
class Poketch : PeriMDA(11, 10, 25, null, 1760, null) {

    val testPatternBytes = byteArrayOf(
            -1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,-1,-1,-1,-1,85,85,0,0,0,-1,-1,-1,-1,-1,-1,-86,-86,0,0,0,-1,-1,0,0,0,0,0,0,-1,-1,0,-1,-1,0,0,0,0,0,0,-1,-1,0,-1,-1,0,0,0,0,0,0,-1,-1,0,-1,-1,0,0,0,0,0,0,-1,-1,0,-1,-1,0,0,0,0,0,0,-1,-1,0,-1,-1,0,0,0,0,0,0,-1,-1,0,-1,-1,0,0,0,0,0,0,-1,-1,0,-1,-1,0,0,0,0,0,0,-1,-1,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,0,0,-86,-86,-1,-1,-1,-1,-1,-1,-86,0,0,85,85,-1,-1,-1,-1,-1,-1,85,-1,-1,0,0,-1,-1,0,0,-1,-1,0,-1,-1,0,0,-1,-1,0,0,-1,-1,0,-1,-1,0,0,-1,-1,0,0,-1,-1,0,-1,-1,0,0,-1,-1,0,0,-1,-1,0,-1,-1,0,0,-1,-1,0,0,-1,-1,0,-1,-1,0,0,-1,-1,0,0,-1,-1,0,-1,-1,0,0,-1,-1,0,0,-1,-1,0,-1,-1,0,0,-1,-1,0,0,-1,-1,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0,-1,-1,0,0,0,0,0,0,0,0,0

    )

    /**
     * 0x00bb: cursorblink; 00: false, ff: true
     * 0x01bb: change graphics mode (if supported)
     * 0x02bb: text cursor X position 0-255
     * 0x03bb: text cursor Y position 0-255
     * 0x04bb: text scroll X (-128..127)
     * 0x05bb: text scroll Y (-128..127)
     * 0x06bb: change foreground colour (if supported)
     * 0x07bb: change background colour (if supported)
     *
     * Mandatory for bit-mapped graphics card:
     * 0x10bb: fill screen with colour
     */
    override fun call(arg: Int) {
        when (arg) {
            0x1000 -> for (i in 0 until memory.size) { memory[i] = 0 }
            0x1001 -> for (i in 0 until memory.size) { memory[i] = if (i < 880) -1 else 0 }
            0x1002 -> for (i in 0 until memory.size) { memory[i] = if (i < 880) 0 else -1 }
            0x1003 -> for (i in 0 until memory.size) { memory[i] = -1 }
        }
    }

    /*
     * Memory map:
     * Dots: the area is further divided by two:
     *
     * Each dot is two bits, for 4 colours
     * 0..879: least significant bits (black, white)
     * 880..1759: most significant bits (dark grey, light grey)
     *
     * First byte == Leftmost 8 dots
     * Most significant bit in a byte == Rightmost dot
     */

    val framebuffer = Pixmap(88, 80, Pixmap.Format.RGB888)
    val colourPalette = arrayOf(
            0x373d1fff,          // bit 00; black
            0xb0c364ff.toInt(),  // bit 01; white
            0x68733bff,          // bit 10; dark grey
            0x98a856ff.toInt()   // bit 11; light grey
    )

    init {
        // init: fill with test pattern
        // to clear the screen, call 1001h
        System.arraycopy(testPatternBytes, 0, memory, 0, testPatternBytes.size)
    }

    fun renderToFrameBuffer() {
        for (k in 0 until 880) { // 88 * 80 / 8 (nuber of bits in a byte)
            val lsb = memory[k].toUint()
            val msb = memory[880 + k].toUint()
            for (bitmask in 0..7) {
                val bit = lsb.ushr(bitmask).and(1) or msb.ushr(bitmask).and(1).shl(1)
                val colour = colourPalette[bit]

                val x = (k * 8 + bitmask) % framebuffer.width
                val y = k / (framebuffer.width / 8)

                framebuffer.drawPixel(x, y, colour)
            }
        }
    }


    override fun render(batch: SpriteBatch, delta: Float, offsetX: Float, offsetY: Float) {

    }

    override fun keyTyped(char: Char): Boolean {
        return super.keyTyped(char)
    }

    override fun keyDown(keycode: Int): Boolean {
        return super.keyDown(keycode)
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return super.touchDown(screenX, screenY, pointer, button)
    }

    override fun dispose() {
        framebuffer.dispose()
        super.dispose()
    }

}