package net.torvald.tbasic.runtime.compiler.simplec

/**
 * Created by minjaesong on 2017-06-15.
 */
abstract class CData(val name: String) {
    abstract fun sizeOf(): Int
}

class CStruct(name: String, val identifier: String): CData(name) {
    val members = ArrayList<CData>()

    fun addMember(member: CData) {
        members.add(member)
    }

    override fun sizeOf(): Int {
        return members.map { it.sizeOf() }.sum()
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Struct $name: ")
        members.forEachIndexed { index, cData ->
            if (cData is CPrimitive) {
                sb.append(cData.type)
                sb.append(' ')
                sb.append(cData.name)
            }
            else if (cData is CStruct) {
                sb.append(cData.identifier)
                sb.append(' ')
                sb.append(cData.name)
            }
            else throw IllegalArgumentException("Unknown CData extension: ${cData.javaClass.simpleName}")
        }
        return sb.toString()
    }
}

class CPrimitive(name: String, val type: SimpleC.ReturnType, val value: Any?, val derefDepth: Int = 0): CData(name) {
    override fun sizeOf(): Int {
        var typestr = type.toString()
        if (typestr.endsWith("_PTR")) typestr = typestr.drop(4)
        return SimpleC.sizeofPrimitive(typestr)
    }
}