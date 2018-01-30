package net.torvald.terranvm.runtime.compiler.simplec

/**
 * Created by minjaesong on 2017-06-15.
 */

class CFunction(val name: String, val returnType: SimpleC.ReturnType) {
    fun call(args: Array<SimpleC.SyntaxTreeNode>) {

    }
}


/*abstract class CBuiltinFunction(name: String, argCount: Int) : CFunction(name, argCount) {
    abstract fun toASM(args: Array<SimpleC.SyntaxTreeNode>): ByteArray // C's return == push to stack
}

abstract class CDerivativeFunction(name: String, argCount: Int) : CFunction(name, argCount) {
    abstract val statements: List<SimpleC.SyntaxTreeNode>
}
*/