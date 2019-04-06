package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import net.torvald.terranvm.runtime.GdxPeripheralWrapper
import net.torvald.terranvm.runtime.to8HexString
import net.torvald.terranvm.runtime.toUint
import java.io.OutputStream
import java.io.PrintStream
import java.util.*

/**
 * Created by minjaesong on 2017-11-18.
 */
open class PeriMDA(val W: Int = 80, val H: Int = 25, 
                   val vmExecDelay: Int? = null,
                   newMemSize: Int? = null,
                   val font: BitmapFont? = LCDFont()
) : GdxPeripheralWrapper(newMemSize ?: (W * H)) {

    var cursorBlink = true
    var cursorX = 0
    var cursorY = 0
    // will wrap around; think of how NES's graphic work
    var scrollX = 0
    var scrollY = 0

    /**
     * 0x000t: change "page"/framebuffer
     * 0x00bb: cursorblink; 00: false, ff: true
     * 0x01bb: change graphics mode (if supported)
     * 0x02bb: text cursor X position 0-255
     * 0x03bb: text cursor Y position 0-255
     * 0x04bb: text scroll X (-128..127)
     * 0x05bb: text scroll Y (-128..127)
     * 0x06bb: change foreground colour (if supported)
     * 0x07bb: change background colour (if supported)
     */
    override fun call(arg: Int) {
        //println("MDAcall ${arg.to8HexString().drop(4)}")


        when (arg) {
            0x0000 -> { cursorBlink = false }
            0x00ff -> { cursorBlink = true }
        }

        when (arg.ushr(8)) {
            0x01 -> { /* do nothing */ }
            0x02 -> { cursorX = arg.and(0xff) }
            0x03 -> { cursorY = arg.and(0xff) }
            0x04 -> { scrollX += arg.and(0xff).toByte().toInt() } // DON'T use toUint()
            0x05 -> { scrollY += arg.and(0xff).toByte().toInt() } // DON'T use toUint()

            0x08 -> { printStream.write(arg.and(0xff)) }
        }
    }


    private val height: Int; get() = Gdx.graphics.height

    private var blinkTimer = 0f
    private val blinkTime = 0.2f
    private var blinkOn = false

    override fun render(batch: SpriteBatch, delta: Float, offsetX: Float, offsetY: Float) {
        if (cursorBlink && blinkTimer > blinkTime) {
            blinkTimer -= blinkTime
            blinkOn = !blinkOn
        }

        blinkTimer += delta


        batch.color = Color(0x141414ff)
        for (y in 0 until H) {
            for (x in 0 until W) {
                val char = getByte(x, y).toChar()
                font?.draw(batch, "$char", offsetX + 12 * x, height - 16 - (offsetY + 16 * y))
            }
        }

        if (cursorBlink && blinkOn) {
            font?.draw(batch, "${0xdb.toChar()}", offsetX + 12 * (cursorX), height - 16 - (offsetY + 16 * minOf(cursorY, H - 1)))
        }
    }

    private fun clear() {
        Arrays.fill(this.memory, 0.toByte())
    }

    private fun clearLine(line: Int = cursorY) {
        for (i in 0 until W) setByte(i, line, 0.toByte())
    }

    private fun setByte(x: Int, y: Int, byte: Byte) {
        memory[x.plus(scrollX).rem(W) + y.plus(scrollY).rem(H) * W] = byte
    }

    private fun getByte(x: Int, y: Int): Int =
            memory[x.plus(scrollX).rem(W) + y.plus(scrollY).rem(H) * W].toUint()

    // will make display adapter run as if it was a dumb terminal
    val printStream = object : PrintStream(object : OutputStream() {
        override fun write(b: Int) {
            var delCalled = false
            var controlCharacterUsed = false

            when (b) {
            //ASCII_BEL -> bell(".")
                ASCII_BS, ASCII_DEL -> {
                    cursorX -= 1
                    delCalled = true
                }
                ASCII_TAB -> {
                    cursorX = (cursorX).div(TABSIZE).times(TABSIZE) + TABSIZE
                    controlCharacterUsed = true
                }
                ASCII_LF, ASCII_CR -> {
                    cursorY += 1; cursorX = 0
                    controlCharacterUsed = true
                }
                ASCII_FF -> {
                    clear()
                    controlCharacterUsed = true
                }
            }



            if (cursorX == W) {
                cursorX = 0
                cursorY++
            }

            if (cursorY == H) {
                // scroll
                //System.arraycopy(memory, W, memory, 0, W * (H - 1))
                scrollY++
                clearLine(H - 1)


                cursorY--
            }

            // blit text
            if (!controlCharacterUsed) {
                if (delCalled) {
                    setByte(cursorX, cursorY, 0.toByte())
                }
                else {
                    setByte(cursorX, cursorY, b.and(0xFF).toByte())
                    cursorX++ // advance cursor
                }
            }


            if (vmExecDelay != null) {
                Thread.sleep(vmExecDelay.toLong())
            }
        }
    } ) { }


    override fun keyTyped(char: Char): Boolean {
        return false
    }

    override fun keyDown(keycode: Int): Boolean {
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun dispose() {
        font?.dispose()
    }

    val TABSIZE = 4

    val ASCII_NUL = 0
    val ASCII_BEL = 7   // *BEEP!*
    val ASCII_BS = 8    // x = x - 1
    val ASCII_TAB = 9   // move cursor to next (TABSIZE * yy) pos (5 -> 8, 3- > 4, 4 -> 8)
    val ASCII_LF = 10   // new line
    val ASCII_FF = 12   // new page
    val ASCII_CR = 13   // x <- 0
    val ASCII_DEL = 127 // backspace and delete char
    val ASCII_DC1 = 17  // foreground colour 0
    val ASCII_DC2 = 18  // foreground colour 1
    val ASCII_DC3 = 19  // foreground colour 2
    val ASCII_DC4 = 20  // foreground colour 3
    val ASCII_DLE = 16  // error message colour


    override fun inquireBootstrapper(): ByteArray? {
        return null
    }
}