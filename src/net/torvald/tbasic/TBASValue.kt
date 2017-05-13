package net.torvald.tbasic

import net.torvald.tbasic.runtime.VM
import java.util.*

/**
 * Type markers
 *
 * Created by minjaesong on 2017-05-09.
 */
interface TBASValue {
    val pointer: VM.Pointer

    fun toBytes(): ByteArray
    /** Size in bytes. Same as same-named function in C language. */
    fun sizeOf(): Int
    fun getValue(): Any
    fun getPointerType(): VM.Pointer.PointerType
}

class TBASNil(override val pointer: VM.Pointer) : TBASValue {
override fun toBytes() = byteArrayOf(0.toByte())
    override fun sizeOf() = VM.Pointer.sizeOf(getPointerType())
    override fun equals(other: Any?) = (other is TBASNil)
    override fun getValue() = this
    override fun getPointerType() = VM.Pointer.PointerType.VOID
}

class TBASBoolean(override val pointer: VM.Pointer) : TBASValue {
    override fun toBytes() = byteArrayOf(pointer.readData() as Byte)
    override fun sizeOf() = VM.Pointer.sizeOf(getPointerType())
    override fun equals(other: Any?) = !(pointer.readData() == 0 && 0 == (other as? TBASValue)?.pointer?.readData())
    override fun getValue() = pointer.readData() != 0
    override fun getPointerType() = VM.Pointer.PointerType.BOOLEAN
}

class TBASNumber(override val pointer: VM.Pointer) : TBASValue {
    override fun toBytes() = pointer.parent.memSlice(pointer.memAddr, sizeOf())
    override fun sizeOf() = VM.Pointer.sizeOf(getPointerType())
    override fun getValue() = pointer.readData() as Number
    override fun equals(other: Any?) = Arrays.equals(this.toBytes(), (other as? TBASValue)?.toBytes())
    override fun getPointerType() = VM.Pointer.PointerType.DOUBLE
}

class TBASNumberArray(override val pointer: VM.Pointer, val dimensional: IntArray) : TBASValue {
    /*
    Memory map
    n    +8  +16  +32
    | d1 | d2 | d3 | ... | data data data |
     */
    override fun toBytes() = pointer.parent.memSlice(pointer.memAddr, sizeOf())
    override fun sizeOf(): Int {
        var memSize = 1
        dimensional.forEach { memSize *= it * VM.Pointer.sizeOf(getPointerType()) }
        return memSize + dimensional.size * VM.Pointer.sizeOf(getPointerType())
    }
    override fun equals(other: Any?) = Arrays.equals(this.toBytes(), (other as? TBASValue)?.toBytes())
    override fun getValue(): Any {
        TODO("not available")
    }
    override fun getPointerType() = VM.Pointer.PointerType.DOUBLE
}

/**
 * Protip: String is just ByteArray
 */
class TBASString(override val pointer: VM.Pointer, val length: Int) : TBASValue {
    override fun toBytes() = pointer.parent.memSlice(pointer.memAddr, sizeOf())
    override fun sizeOf(): Int = length
    override fun getValue() = String(toBytes(), VM.charset)
    override fun equals(other: Any?) = Arrays.equals(this.toBytes(), (other as? TBASValue)?.toBytes())
    override fun toString(): String = getValue() as String
    override fun getPointerType() = VM.Pointer.PointerType.BYTE
}