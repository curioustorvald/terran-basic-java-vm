package net.torvald.terranvm

import net.torvald.terranvm.runtime.TerranVM
import java.util.*

/**
 * Type markers
 *
 * Created by minjaesong on 2017-05-09.
 */
interface TBASValue {
    val pointer: TerranVM.Pointer

    fun toBytes(): ByteArray
    /** Size in bytes. Same as same-named function in C language. */
    fun sizeOf(): Int
    fun getValue(): Any
    fun getPointerType(): TerranVM.Pointer.PointerType
}

class TBASNil(override val pointer: TerranVM.Pointer) : TBASValue {
override fun toBytes() = byteArrayOf(0.toByte())
    override fun sizeOf() = TerranVM.Pointer.sizeOf(getPointerType())
    override fun equals(other: Any?) = (other is TBASNil)
    override fun getValue() = this
    override fun getPointerType() = TerranVM.Pointer.PointerType.VOID
}

class TBASBoolean(override val pointer: TerranVM.Pointer) : TBASValue {
    override fun toBytes() = byteArrayOf(pointer.readData() as Byte)
    override fun sizeOf() = TerranVM.Pointer.sizeOf(getPointerType())
    override fun equals(other: Any?) = !(pointer.readData() == 0 && 0 == (other as? TBASValue)?.pointer?.readData())
    override fun getValue() = pointer.readData() != 0
    override fun getPointerType() = TerranVM.Pointer.PointerType.BOOLEAN
}

class TBASNumber(override val pointer: TerranVM.Pointer) : TBASValue {
    override fun toBytes() = pointer.parent.memSliceBySize(pointer.memAddr, sizeOf())
    override fun sizeOf() = TerranVM.Pointer.sizeOf(getPointerType())
    override fun getValue() = pointer.readData() as Number
    override fun equals(other: Any?) = Arrays.equals(this.toBytes(), (other as? TBASValue)?.toBytes())
    override fun getPointerType() = TerranVM.Pointer.PointerType.DOUBLE
}

class TBASNumberArray(override val pointer: TerranVM.Pointer, val dimensional: IntArray) : TBASValue {
    /*
    Memory map
    n    +8  +16  +32
    | d1 | d2 | d3 | ... | data data data |
     */
    override fun toBytes() = pointer.parent.memSliceBySize(pointer.memAddr, sizeOf())
    override fun sizeOf(): Int {
        var memSize = 1
        dimensional.forEach { memSize *= it * TerranVM.Pointer.sizeOf(getPointerType()) }
        return memSize + dimensional.size * TerranVM.Pointer.sizeOf(getPointerType())
    }
    override fun equals(other: Any?) = Arrays.equals(this.toBytes(), (other as? TBASValue)?.toBytes())
    override fun getValue(): Any {
        TODO("not available")
    }
    override fun getPointerType() = TerranVM.Pointer.PointerType.DOUBLE
}

/**
 * Protip: String is just ByteArray
 *
 * Memory map
 * | data | (terminated by NULL (0))
 */
class TBASString(override val pointer: TerranVM.Pointer) : TBASValue {
    override fun toBytes() = pointer.parent.memSliceBySize(pointer.memAddr, sizeOf())
    override fun sizeOf(): Int {
        var l = 0
        var b = pointer.parent.memory[pointer.memAddr]
        while (b != 0.toByte()) {
            b = pointer.parent.memory[pointer.memAddr + l]
            l++
        }
        return l
    }
    override fun getValue() = String(pointer.parent.memSliceBySize(pointer.memAddr, sizeOf()), TerranVM.charset)
    override fun equals(other: Any?) = Arrays.equals(this.toBytes(), (other as? TBASValue)?.toBytes())
    override fun toString(): String = getValue() as String
    override fun getPointerType() = TerranVM.Pointer.PointerType.BYTE
}


