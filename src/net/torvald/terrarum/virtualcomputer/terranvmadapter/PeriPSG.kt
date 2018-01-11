package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.graphics.g2d.SpriteBatch
import net.torvald.terranvm.runtime.GdxPeripheralWrapper

/**
 * Memory map:
 *
 * For each voice:
 * - Frequency: Int32 (16.16 fixed precision, first byte is smallest number)
 *      + e.g. 0x00_80_01_00 == 1 + (32768/65536) == 1.5
 * - Volume: Uint8
 *      + Volume: 0 to 256 (0 means no sound output)
 * - Waveform: Uint8
 *      + Waveform - 000..111 : (8 - bits) / 16
 *          - e.g. 000: 50%, 111: 6.25%
 *      + 1000..1111 : Triangle..Sawtooth
 *      + 10000 : Noise
 *      + 11111110 : Lowest voltage (-1.0 if signed, 0.0 if unsigned)
 *      + 11111111 : Highest voltage (1.0)
 *
 * For compatibility, extra voices and parametres (e.g. ADSM) must be placed AFTER PSG's usual memory map
 * ____
 *
 * Full memory map:
 *
 * ff ff ff ff vv ww
 * ff ff ff ff vv ww
 * ff ff ff ff vv ww
 * ff ff ff ff vv ww
 *
 * Size: 24 bytes
 *
 *
 * Created by minjaesong on 2017-12-17.
 */
class PeriPSG : GdxPeripheralWrapper(24) {

    override fun render(batch: SpriteBatch, delta: Float, offsetX: Float, offsetY: Float) { }

    override fun keyDown(keycode: Int) = false

    override fun keyTyped(char: Char) = false

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int) = false

    override fun dispose() {
    }

    override fun call(arg: Int) {
    }
}