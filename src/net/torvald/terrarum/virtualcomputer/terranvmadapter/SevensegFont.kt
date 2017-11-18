package net.torvald.terrarum.virtualcomputer.terranvmadapter

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import net.torvald.terrarumsansbitmap.gdx.TextureRegionPack

/**
 * Created by minjaesong on 2017-11-18.
 */
class SevensegFont: BitmapFont() {
    val charMapping = ('0'..'9') + ('a'..'f')

    internal val W = 11
    internal val H = 18

    internal val fontSheet = TextureRegionPack(Gdx.files.internal("assets/7segnum.tga"), W, H)

    init {
        setOwnsTexture(true)
    }

    override fun draw(batch: Batch, str: CharSequence, x: Float, y: Float): GlyphLayout? {

        str.forEachIndexed { index, c ->
            val inCharMapping = charMapping.indexOf(c)

            batch.draw(
                    if (c != ' ' && inCharMapping != -1)
                        fontSheet.get(1 + inCharMapping, 0)
                    else
                        fontSheet.get(0, 0)
                    , x + W * index, y)
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