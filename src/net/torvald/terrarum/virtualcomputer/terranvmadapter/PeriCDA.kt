package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import net.torvald.terranvm.runtime.GdxPeripheralWrapper
import net.torvald.terranvm.runtime.toLittle
import net.torvald.terranvm.runtime.toUint
import net.torvald.terrarumsansbitmap.gdx.TextureRegionPack
import kotlin.experimental.and
import kotlin.experimental.or

/**
 * Colour Display Adapter
 *
 * (name chosen to avoid confusion with real-life IBM CGA)
 *
 * Specs:
 * - 768x448 framebuffer, of which 512x224 is being displayed; 64 colours per pixel -> 258 048 bytes (252 kB)
 * - hardware scrollable
 *      + scroll informations are stored in the last 4 bytes of the memory, Y scroll is at the end, in little endian
 * - 107 fixed-size 3-colour sprites, scalable up to 4x in horizontally and vertically separated
 *      + Colour index of 0 is always transparent. You can change the value programatically but it will have no effect whatsoever
 *
 * ## Main Memory Map
 * ```
 * pixels data | sprites data | (empty space) | x scroll | y scroll
 *
 * Pixels data are stored as 6 bitplanes with the size of 768x448 each. Firstmost bitplane is LSB of colour.
 * For each byte (i.e. a pixel octet), LSB is leftmost pixel.
 *
 * Scrolling: only positive values are being accepted, which is fine because:
 *            the edges should be repeated
 *
 *    Entry    |  Size
 * ------------|--------
 * pixels data | 258 048
 * sprites data|   4 066
 * empty space |      26
 * x scroll    |       2
 * y scroll    |       2
 * ------------|--------
 * Total       | 262 144
 * ```
 *
 *
 * ## Sprite Memory Map
 * ```
 * pixels | palette | parametres (little endian)
 * parametres:
 *|       2       |       1       |       0       |
 * 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 (3 bytes)
 * | · · · · · · · | | · · · · · · · | | | ^^^^^^^ scaling (yy, xx)
 * | · · · · · · · | | · · · · · · · | | ^ on/off
 * | · · · · · · · | | · · · · · · · | ^ horizontal mirroring
 * | · · · · · · · | ^^^^^^^^^^^^^^^^^ X-position [0..1023]
 * ^^^^^^^^^^^^^^^^^ Y-position [0..1023]
 *
 * scaling: 00 for 1x, 01 for 2x, 10 for 3x, 11 for 4x
 * ```
 *
 *
 * Created by minjaesong on 2018-07-22.
 */
class PeriCDA : GdxPeripheralWrapper(262144), ColourDisplayBase {

    // CDA has 256 kB of internal memory, of which 26 bytes are unused

    companion object {
        private const val W = 768
        private const val H = 448

        private const val MAX_SPRITES = 107
        private const val MAX_SPRITE_COLS = 4

        private const val SPRITE_DIM = 16
    }

    override val screenWidth: Int = 512
    override val screenHeight: Int = 224
    override val maxColours: Int = 64
    override val displayModeCount: Int = 0
    override val maxSpriteCount: Int = MAX_SPRITES
    override val maxSpriteColours: Int = MAX_SPRITE_COLS
    override val maxSpriteScaleX: Int = 4
    override val maxSpriteScaleY: Int = 4
    override val spriteWidth: Int = SPRITE_DIM
    override val spriteHeight: Int = SPRITE_DIM
    override val scrollable: Boolean = true

    private val spriteMemStart = W * H / 8 * 6
    private val spriteMemEndExl = spriteMemStart + 38 * MAX_SPRITES

    // memory's last 4 bytes are used to store these
    override var scrollX: Int
        get() = memory[memory.size - 3].toInt().shl(8) + memory[memory.size - 4]
        set(value) {
            val value = Math.floorMod(value, W)
            memory[memory.size - 3] = value.ushr(8).toByte()
            memory[memory.size - 4] = value.and(0xFF).toByte()
        }
    override var scrollY: Int
        get() = memory[memory.size - 1].toInt().shl(8) + memory[memory.size - 2]
        set(value) {
            val value = Math.floorMod(value, H)
            memory[memory.size - 1] = value.ushr(8).toByte()
            memory[memory.size - 2] = value.and(0xFF).toByte()
        }

    private val frameBuffer = Pixmap(W, H, Pixmap.Format.RGBA8888)
    private val spritesSpriteSheet = Pixmap(256, 128, Pixmap.Format.RGBA8888) // 16 x 8 sprites
    private val spriteParams = Array(MAX_SPRITES, { SpriteParametres(false, 0f, 0f, 0f, 0f, false) })

    /**
     *
     */
    override fun render(batch: SpriteBatch, delta: Float, offsetX: Float, offsetY: Float) {
        // main framebuffer (peripheral memory) into pixmap
        val fbSize = W * H


        for (yb in 0 until H) {
            for (xb in 0 until (W / 8)) {
                val bitplanes = ByteArray(6, { bitPlaneNumber -> memory[
                        (fbSize * bitPlaneNumber) * // address bitplane
                                (yb * W + xb) // address pixel octets
                ] })

                // we read 8 pixels each, thus we process 8 simultaneous horizontal pixels

                for (bitmask in 0 until 8) {
                    val bitMaskCombined: Int = bitplanes.foldIndexed(0) { bitplaneNumber, acc, byte ->
                        acc or byte.toUint().ushr(bitmask).and(1).shl(bitplaneNumber)
                    }

                    // at this point, bitMaskCombined must sit in range of 0..63

                    val pixelColour = PrefferedPaletteGDX.colourIndices64[bitMaskCombined]

                    // actually plot a pixel
                    frameBuffer.setColor(pixelColour)
                    frameBuffer.drawPixel(xb * 8 + bitmask, yb)
                }
            }
        }


        // pixmap into GL draw
        val tex = Texture(frameBuffer)
        // TODO does this method of scrolling actually work?
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest) // pixellated effects
        tex.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat) // how scrolling should work
        val texReg = TextureRegion(tex)
        texReg.setRegion(scrollX, scrollY, screenWidth, screenHeight)

        // don't forget to stretch Y to 2 !
        batch.draw(texReg, offsetX, offsetY, screenWidth * 1f, screenHeight * 2f)

        // draw sprites //

        // construct texture data for spritesSpriteSheet
        for (i in 0 until MAX_SPRITES) {
            val spriteMem = ByteArray(CDASprite.SERIALISE_LENGTH)
            System.arraycopy(memory, spriteMemStart + i * CDASprite.SERIALISE_LENGTH, spriteMem, 0, CDASprite.SERIALISE_LENGTH)

            val spriteObj = CDASprite.deserialise(spriteMem)

            spriteParams[i].onOff = spriteObj.onOff
            spriteParams[i].posX = spriteObj.posX.toFloat()
            spriteParams[i].posY = spriteObj.posY.toFloat()
            spriteParams[i].scaleX = spriteObj.scaling.and(0b11).toFloat()
            spriteParams[i].scaleY = spriteObj.scaling.ushr(2).and(0b11).toFloat()
            spriteParams[1].mirrorH = spriteObj.mirrorH


            val spritePaletteGDX = arrayOf(
                    Color(0),
                    PrefferedPaletteGDX.colourIndices64[spriteObj.palette[0].toUint()],
                    PrefferedPaletteGDX.colourIndices64[spriteObj.palette[1].toUint()],
                    PrefferedPaletteGDX.colourIndices64[spriteObj.palette[2].toUint()]
            )

            // we read 8 pixels each, thus we process 8 simultaneous horizontal pixels

            for (yb in 0 until SPRITE_DIM) {
                for (xb in 0 until (SPRITE_DIM / 8)) {
                    val bitplanes = ByteArray(2, { bitPlaneNumber -> spriteMem[
                            (SPRITE_DIM * SPRITE_DIM * bitPlaneNumber) * // address bitplane
                                    (yb * SPRITE_DIM + xb) // address pixel octets
                    ] })

                    // we read 8 pixels each, thus we process 8 simultaneous horizontal pixels

                    for (bitmask in 0 until 8) {
                        val bitMaskCombined: Int = bitplanes.foldIndexed(0) { bitplaneNumber, acc, byte ->
                            acc or byte.toUint().ushr(bitmask).and(1).shl(bitplaneNumber)
                        }

                        // at this point, bitMaskCombined must sit in range of 0..3

                        val pixelColour = spritePaletteGDX[bitMaskCombined]

                        // actually plot a pixel
                        spritesSpriteSheet.setColor(pixelColour)
                        spritesSpriteSheet.drawPixel(
                                (i % 16) * 16 + xb * 8 + bitmask,
                                (i / 16) * 16 + yb
                        )
                    }
                }
            }
        }

        // actually draw sprites using spritesheet
        val spriteSpriteShit = TextureRegionPack(Texture(spritesSpriteSheet), 16, 16)
        for (i in 0 until MAX_SPRITES) {
            val texRegion = spriteSpriteShit.get(i % 16, i / 16)
            val param = spriteParams[i]

            texRegion.flip(param.mirrorH, false)

            if (param.onOff) {
                batch.draw(texRegion, param.posX, param.posY, SPRITE_DIM * param.scaleX, SPRITE_DIM * param.scaleY)
            }
        }
    }

    override fun keyDown(keycode: Int): Boolean {
        return false
    }

    override fun keyTyped(char: Char): Boolean {
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun dispose() {
        frameBuffer.dispose()
    }

    override fun call(arg: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun inquireBootstrapper(): ByteArray? = null

    private class CDASprite {
        var onOff: Boolean = false
        var posX: Int = 0 // 0..1023
        var posY: Int = 0 // 0..1023
        var scaling: Int = 0b00_00 // 00: 1x, 11: 4x
        var mirrorH: Boolean = false
        var palette = ByteArray(PALETTE_SIZE) // for Color 1, 2, 3; two MSBs must be zero, only lower 6 bits are legal
        var pixels = ByteArray(PIXELS_SIZE) // 256x of 2-bit numbers, bit-planed
        // --> 38 bytes

        companion object {
            val PIXELS_SIZE = SPRITE_DIM * SPRITE_DIM / 8
            val PALETTE_SIZE = 3
            val PARAMS_SIZE = 3

            val SERIALISE_LENGTH = PIXELS_SIZE + PALETTE_SIZE + PARAMS_SIZE

            fun serialise(sprite: CDASprite): ByteArray {
                fun Boolean.toInt() = if (this) 1 else 0


                val ba = ByteArray(sprite.pixels.size + sprite.palette.size + 4)

                // memory map: pixels | palette | parametres (little endian)


                System.arraycopy(sprite.pixels, 0, ba, 0, sprite.pixels.size)
                System.arraycopy(sprite.palette, 0, ba, sprite.pixels.size, sprite.palette.size)
                val p = sprite.scaling or sprite.mirrorH.toInt().shl(5) or sprite.onOff.toInt().shl(4) or sprite.posY.and(0x1FF).shl(15) or sprite.posX.and((0x1FF).shl(6))
                val param = p.toLittle()
                System.arraycopy(param, 0, ba, sprite.pixels.size + sprite.palette.size, 3)

                return ba
            }

            fun deserialise(bytes: ByteArray): CDASprite {
                if (bytes.size != SERIALISE_LENGTH) throw IllegalArgumentException("Byte array not a CDASprite")

                val newSprite = CDASprite()

                newSprite.pixels = bytes.sliceArray(0 until PIXELS_SIZE)
                newSprite.palette = bytes.sliceArray(PIXELS_SIZE until PIXELS_SIZE + PALETTE_SIZE)
                val paramsBits = bytes.sliceArray(PIXELS_SIZE + PALETTE_SIZE until bytes.size)

                if (paramsBits.size != PARAMS_SIZE) throw InternalError()

                newSprite.scaling = paramsBits[0].toUint().and(0b1111)
                newSprite.onOff = paramsBits[0].toUint().and(0x10) != 0
                newSprite.mirrorH = paramsBits[0].toUint().and(0x20) != 0
                newSprite.posY = paramsBits[2].toUint().shl(1) or paramsBits[1].toUint().ushr(7)
                newSprite.posX = paramsBits[1].toUint().and(0x7F) or paramsBits[0].toUint().ushr(6)

                return newSprite
            }
        }
    }

    private data class SpriteParametres(var onOff: Boolean, var posX: Float, var posY: Float, var scaleX: Float, var scaleY: Float, var mirrorH: Boolean)
}