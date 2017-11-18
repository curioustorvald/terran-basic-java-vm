package net.torvald.terranvm.runtime.compiler.simplec

import net.torvald.terranvm.runtime.Assembler

/**
 * Created by minjaesong on 2017-06-14.
 */

internal fun asm(assembly: String) = Assembler(assembly)

/*class Assignvar : CBuiltinFunction("#_assignvar", 3) {
    override fun toASM(args: Array<SimpleC.SyntaxTreeNode>): ByteArray {
        val varname = args[0].literalValue!! as String
        val vartype = args[1].literalValue!! as String
        val value = args[2].literalValue!!

        return asm((if (vartype == "CHAR_PTR")
            """.data; string $varname "$value"; .code; loadptr 1, @$varname;"""
        else
            """loadnum 1, $value;""") + """setvariable $varname; push 1;""") // 'push 1' acts as 'return something;'
    }
}


///////////////
// MATHE OPS //
///////////////

class Plus : CBuiltinFunction("+", 2) {
    override fun toASM(args: Array<SimpleC.SyntaxTreeNode>): ByteArray {
        val lh = args[0].literalValue!! as Double
        val rh = args[1].literalValue!! as Double

        return asm("""loadnum 2, $lh; loadnum 3, $rh; add; push 1;""") // 'push 1' acts as 'return something;'
    }
}

class Minus : CBuiltinFunction("-", 2) {
    override fun toASM(args: Array<SimpleC.SyntaxTreeNode>): ByteArray {
        val lh = args[0].literalValue!! as Double
        val rh = args[1].literalValue!! as Double

        return asm("""loadnum 2, $lh; loadnum 3, $rh; sub; push 1;""")
    }
}


/////////////////
// OTHER SHITS //
/////////////////

class Malloc : CBuiltinFunction("malloc", 1) {
    override fun toASM(args: Array<SimpleC.SyntaxTreeNode>): ByteArray {
        val size = args[0].literalValue!! as Double

        return asm("""loadnum 2, $size; malloc; push 1;""") // 'push 1' acts as 'return something;'
    }
}*/