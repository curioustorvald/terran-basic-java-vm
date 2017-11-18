package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import net.torvald.terrarumsansbitmap.gdx.TextureRegionPack

/**
 * Created by minjaesong on 2017-11-18.
 */
class LCDFont: BitmapFont() {
    internal val W = 12
    internal val H = 16

    internal val fontSheet = TextureRegionPack(Gdx.files.internal("assets/lcd.tga"), W, H)

    init {
        setOwnsTexture(true)
    }

    override fun draw(batch: Batch, str: CharSequence, x: Float, y: Float): GlyphLayout? {

        str.forEachIndexed { index, c ->
            batch.draw(
                    fontSheet.get(c.toInt() % 16, c.toInt() / 16),
                    x + W * index, y
            )
        }


        return null
    }

    override fun getLineHeight() = H.toFloat()
    override fun getCapHeight() = getLineHeight()
    override fun getXHeight() = getLineHeight()

    override fun dispose() {
        fontSheet.dispose()
    }
}