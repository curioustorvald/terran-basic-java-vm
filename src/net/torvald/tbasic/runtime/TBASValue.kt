package net.torvald.tbasic.runtime

/**
 * Created by minjaesong on 2017-05-09.
 */
interface TBASValue {
    fun toBytes(): ByteArray
}

class TBASNil : TBASValue {
    override fun toBytes() = byteArrayOf(0.toByte())
}

class TBASBoolean(val value: Boolean) : TBASValue {
    override fun toBytes() = byteArrayOf((if (value) 0xFF else 0x00).toByte())
}