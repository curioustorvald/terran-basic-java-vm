package net.torvald.terranvm.runtime.compiler.cflat

/**
 * Created by minjaesong on 2017-06-15.
 */

class CFunction(val name: String, val returnType: Cflat.ReturnType) {
    fun call(args: Array<Cflat.SyntaxTreeNode>) {

    }
}


/*abstract class CBuiltinFunction(name: String, argCount: Int) : CFunction(name, argCount) {
    abstract fun toASM(args: Array<Cflat.SyntaxTreeNode>): ByteArray // C's return == push to stack
}

abstract class CDerivativeFunction(name: String, argCount: Int) : CFunction(name, argCount) {
    abstract val statements: List<Cflat.SyntaxTreeNode>
}
*/