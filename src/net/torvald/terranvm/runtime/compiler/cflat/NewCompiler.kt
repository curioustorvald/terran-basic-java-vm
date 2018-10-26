package net.torvald.terranvm.runtime.compiler.cflat

import net.torvald.terranvm.runtime.*
import net.torvald.terranvm.runtime.compiler.cflat.Cflat.ExpressionType.*
import net.torvald.terrarum.virtualcomputer.terranvmadapter.printASM
import java.lang.StringBuilder
import kotlin.UnsupportedOperationException
import kotlin.collections.HashMap


/**
 * Compilation step:
 *
 *     User Input -> Tree Representation -> IR1 (preorder?) -> IR2 (postorder) -> ASM => Fed into Assembler
 *
 * Reference:
 * - *Compiler Design: Virtual Machines*, Reinhard Wilhelm and Helmut Seidi, 2011, ISBN 3642149081
 */
object NewCompiler {


    val testProgram = """
        int c;
        int d;
        int shit;
        int plac;
        int e_ho;
        int lder;
        int r;
        //c = (3 + 4) * (7 - 2);

        c = -3;

        if (c > 42) {
            c = 0;
        }
        else {
            c = 1;
        }

        //void newfunction(int k) {
        //    dosomething(k, -k);
        //}

        //d = "Hello, world!";
        float dd;
    """.trimIndent()

    // TODO add issues here

    // lexer and parser goes here //

    /*
    /**
     * An example of (3 + 4) * (7 - 2).
     *
     * This is also precisely what **IR1** is supposed to be.
     * Invoke this "Function" and you will get a string, which is **IR2**.
     * IR2 is
     */
    val TEST_CODE: () -> CodeR = { { MUL(0, { ADD(0, { LIT(0, 3) }, { LIT(0, 4) }) }, { SUB(0, { LIT(0, 7) }, { LIT(0, 2) }) }) } }

    /**
     * If (3) 7200 Else 5415; 3 + 4; 7 * 2
     */
    val TEST_CODE2 = {
        {
            IFELSE(1,
                { LIT(1, 3) }, // cond
                { LIT(2, 7200) }, // true
                { LIT(4, 5415) }  // false
            )
        } bind { // AM I >>=ing right ?
            ADD(6, { LIT(6, 3) }, { LIT(6, 4) })
        } bind {
            MUL(7, { LIT(7, 7) }, { LIT(7, 2) })
        }

    }

    /**
     * var a = (b + (b * c)); // a |> 5; b |> 6; c |> 7
     *
     * Expected output: loadc 6; load; loadc 6; load; ... -- as per our reference book
     */
    val TEST_VARS = {
        // function for variable address
        val aTable = hashMapOf("a" to 5, "b" to 6, "c" to 7)
        val aenv: Rho = { name -> aTable[name]!! }

        // the actual code
        {
            ASSIGN(1, {
                VAR_L(1, "a", aenv)
            }, {
                ADD(1, {
                    VAR_R(1, "b", aenv)
                }, {
                    MUL(1, {
                        VAR_R(1, "b", aenv)
                    }, {
                        VAR_R(1, "c", aenv)
                    })
                })
            }, aenv
            )
        }
    }*/

    /** Shitty >>= (toilet plunger) */
    private infix fun (CodeR).bind(other: CodeR) = sequenceOf(this, other)
    private infix fun (Sequence<CodeR>).bind(other: CodeR) = this + other


    // TREE TO IR1

    fun toIR1(tree: Cflat.SyntaxTreeNode): Pair<CodeR, Rho> {
        //val parentCode: Sequence<CodeR> = sequenceOf( { "" } )
        val variableAddr = HashMap<String, Int>()
        var varCnt = 0
        fun String.toVarName() = "$$this"

        val aenv: Rho = { name ->
            if (!variableAddr.containsKey(name.toVarName()))
                throw UnresolvedReference("No such variable defined: $name")

            //println("Fetch var ${name.toVarName()} -> ${variableAddr[name.toVarName()]}")

            variableAddr[name.toVarName()]!!
        }
        fun addvar(name: String, ln: Int) {
            if (!variableAddr.containsKey(name.toVarName())) {
                variableAddr[name.toVarName()] = varCnt + 256 // index starts at 100h
                varCnt++
            }

            //println("Add var ${name.toVarName()} -> ${variableAddr[name.toVarName()]}")
        }


        // TODO recursion is a key
        // SyntaxTreeNode has its own traverse function, but our traverse is nothing trivial
        //      so let's just leave it this way.
        // Traverse order: Self -> Arguments (e1, e2, ...) -> Statements (s)
        fun traverse1(node: Cflat.SyntaxTreeNode) : CodeR {
            val l = node.lineNumber


            // termination conditions //

            if (node.expressionType == LITERAL_LEAF) {
                return when (node.returnType!!) {
                    Cflat.ReturnType.INT -> { CodeR { LIT(l, node.literalValue as Int) }}
                    Cflat.ReturnType.FLOAT -> { CodeR { LIT(l, node.literalValue as Float) }}
                    Cflat.ReturnType.DATABASE -> { CodeR { LIT(l, node.literalValue as String) }}
                    else -> { CodeR { _REM(l, "Unsupported literal with type: ${node.returnType}") }}
                }
            }
            else if (node.expressionType == VARIABLE_READ) {
                addvar(node.name!!, l)
                return CodeR { VAR_R(l, node.name!!, aenv) }
            }
            else if (node.expressionType == VARIABLE_WRITE) {
                if (!variableAddr.containsKey(node.name!!)) {
                    throw UnresolvedReference("No such variable defined: ${node.name!!}")
                }
                return CodeR { VAR_L(l, node.name!!, aenv) }
            }

            // recursion conditions //

            else {

                return if (node.expressionType == FUNCTION_CALL) {
                    when (node.name) {
                        "+" -> { CodeR { ADD(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "-" -> { CodeR { SUB(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "*" -> { CodeR { MUL(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "/" -> { CodeR { DIV(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "=" -> { CodeR { ASSIGN(l, CodeL { VAR_L(l, node.arguments[0].name!!, aenv) }, traverse1(node.arguments[1]), aenv) }}

                        "==" -> { CodeR { EQU(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "!=" -> { CodeR { NEQ(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        ">=" -> { CodeR { GEQ(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "<=" -> { CodeR { LEQ(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        ">" -> { CodeR { GT(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}
                        "<" -> { CodeR { LS(l, traverse1(node.arguments[0]), traverse1(node.arguments[1])) }}

                        "if" -> { CodeR { IF(l, traverse1(node.arguments[0]), traverse1(node.statements[0])) }}
                        "ifelse" -> { CodeR { IFELSE(l, traverse1(node.arguments[0]), traverse1(node.statements[0]), traverse1(node.statements[1])) }}

                        "#_unaryminus" -> { CodeR { NEG(l, traverse1(node.arguments[0])) } }

                        else -> { CodeR { _REM(l, "Unknown OP or func call is WIP: ${node.name}") }}
                    }
                }
                else if (node.expressionType == INTERNAL_FUNCTION_CALL) {
                    when (node.name) {
                        "#_declarevar" -> {
                            addvar(node.arguments[0].literalValue as String, l)
                            return CodeR { NEWVAR(l, node.arguments[1].literalValue as String, node.arguments[0].literalValue as String) }
                        }
                        else -> { return CodeR  { _REM(l, "Unknown internal function: ${node.name}") }}
                    }
                }
                else if (node.name == Cflat.rootNodeName) {
                    return CodeR { _PARSE_HEAD(node.statements.map { traverse1(it) }) }
                }
                else {
                    throw UnsupportedOperationException("Unsupported node:\n[NODE START]\n$node\n[NODE END]")
                }
            }
        }


        return traverse1(tree) to aenv
    }











    // These are the definition of IR1. Invoke this "function" and string will come out, which is IR2.
    // l: Int means Line Number

    private fun _REM(l: Int, message: String): IR2 = "\nREM Ln$l : ${message.replace('\n', '$')};\n"
    private fun _PARSE_HEAD(e: List<CodeR>): IR2 = e.fold("") { acc, it -> acc + it.invoke() } + "HALT;\n"
    /** e1 ; e2 ; add ; */
    fun ADD(l: Int, e1: CodeR, e2: CodeR): IR2 = e1() + e2() + "ADD;\n"
    fun SUB(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "SUB;\n"
    fun MUL(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "MUL;\n"
    fun DIV(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "DIV;\n"
    fun NEG(l: Int, e: CodeR) = e() + "NEG;\n"
    /** loadconst ; literal ; */
    fun LIT(l: Int, e: Int) = "LOADCONST ${e.toHexString()};-_-_-_ Literal\n" // always ends with 'h'
    fun LIT(l: Int, e: Float) = "LOADCONST ${e}f;-_-_-_ Literal\n" // always ends with 'f'
    fun LIT(l: Int, e: String) = LIT(l, e.toByteArray(Charsets.UTF_8))
    fun LIT(l: Int, e: ByteArray) = "LOADDATA ${e.fold("") { acc, byte -> acc + "${byte.to2Hex()} " }};"
    /** address(int) ; value(word) ; store ; */
    fun ASSIGN(l: Int, e1: CodeL, e2: CodeR, aenv: Rho) = e2() + e1(aenv) + "STORE;\n"
    /** ( address -- value stored in that address ) */ // this is Forth stack notation
    fun VAR_R(l: Int, varname: String, aenv: Rho) = "LOADCONST ${aenv(varname).toHexString()}; LOAD;-_-_-_ Read from variable\n" // NOT using 8hexstring is deliberate
    /** ( address -- ); memory gets changed */
    fun VAR_L(l: Int, varname: String, aenv: Rho) = "LOADCONST ${aenv(varname).toHexString()};-_-_-_ Write to variable, if following command is STORE\n" // NOT using 8hexstring is deliberate; no need for extra store; handled by ASSIGN
    fun NEWVAR(l: Int, type: String, varname: String) = "NEWVAR ${type.toUpperCase()} $varname;\n"
    fun JUMP(l: Int, newPC: CodeR) = "JUMP $newPC;\n"
    // FOR NON-COMPARISON OPS
    fun JUMPZ(l: Int, newPC: CodeR) = "JUMPZ $newPC;\n"
    fun JUMPNZ(l: Int, newPC: CodeR) = "JUMPNZ $newPC;\n"
    // FOR COMPARISON OPS ONLY !!
    fun JUMPFALSE(l: Int, newPC: CodeR) = "JUMPFALSE $newPC;\n" // zero means false
    fun IF(l: Int, cond: CodeR, invokeTrue: CodeR) =
            cond() + "JUMPFALSE ${labelFalse(l, cond)};\n" + invokeTrue() + "LABEL ${labelFalse(l, cond)};\n"
    fun IFELSE(l: Int, cond: CodeR, invokeTrue: CodeR, invokeFalse: CodeR) =
            cond() + "JUMPFALSE ${labelFalse(l, cond)};\n" + invokeTrue() + "JUMP ${labelThen(l, cond)};\n" +
                    "LABEL ${labelFalse(l, cond)};\n" + invokeFalse() + "LABEL ${labelThen(l, cond)};\n"
    fun WHILE(l: Int, cond: CodeR, invokeWhile: CodeR) =
            "LABEL ${labelWhile(l, cond)};\n" +
                    cond() + "JUMPFALSE ${labelThen(l, cond)};\n" +
                    invokeWhile() + "JUMP ${labelWhile(l, cond)};\n" +
                    "LABEL ${labelThen(l, cond)};\n"
    fun EQU(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "ISEQUAL;\n"
    fun NEQ(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "ISNOTEQUAL;\n"
    fun LEQ(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "ISLESSEQUAL;\n"
    fun GEQ(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "ISGREATEQUAL;\n"
    fun GT(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "ISGREATER;\n"
    fun LS(l: Int, e1: CodeR, e2: CodeR) = e1() + e2() + "ISLESSER;\n"

    //fun FOR
    // TODO  for ( e1 ; e2 ; e3 ) s' === e1 ; while ( e2 ) { s' ; e3 ; }


    private fun labelUnit(lineNum: Int, code: CodeR): IR2 {
        return "\$LN${lineNum}_${code.hashCode().toHexString().dropLast(1)}" // hopefully there'll be no hash collision...
    }
    private fun labelFalse(lineNum: Int, code: CodeR) = labelUnit(lineNum, code) + "_FALSE"
    private fun labelThen(lineNum: Int, code: CodeR) = labelUnit(lineNum, code) + "_THEN"
    private fun labelWhile(lineNum: Int, code: CodeR) = "\$WHILE_" + labelUnit(lineNum, code).drop(1)


    // IR2 to ASM

    /**
        - IR2 conditional:
        ```
        IS-Comp
        JUMPFALSE Lfalse
        λ. code s_true rho
        JUMP Lthen
        LABEL Lfalse
        λ. code s_false rho
        LABEL Lthen
        ...
        ```

        - ASM conditional:
        ```
        λ. <comparison> rho
        CMP;
        JZ/JNZ/... Lfalse
        (do.)
        ```

        IR2's JUMPFALSE needs to be converted equivalent ASM according to the IS-Comp,
        for example, ISEQUAL; JUMPZ false === CMP; JNZ false.

        If you do the calculation, ALL THE RETURNING LABEL TAKE FALSE-LABELS. (easy coding wohoo!)
         */
    private fun IR2toFalseJumps(compFun: IR2): Array<String> {
        return when(compFun) {
            "ISEQUAL" -> arrayOf("JNZ")
            "ISNOTEQUAL" -> arrayOf("JZ")
            "ISGREATER" -> arrayOf("JLS", "JZ")
            "ISLESSER" -> arrayOf("JGT, JZ")
            "ISGREATEQUAL" -> arrayOf("JLS")
            "ISLESSEQUAL" -> arrayOf("JGT")
            else -> throw UnsupportedOperationException("Unacceptable comparison operator: $compFun")
        }
    }

    fun toASM(ir2: IR2, addDebugComments: Boolean = true): String {
        val irList1 = ir2.replace(Regex("""-_-_-_[^\n]*\n"""), "") // get rid of debug comment
                        .replace(Regex("""; *"""), "\n") // get rid of traling spaces after semicolon
                        .replace(Regex("""\n+"""), "\n") // get rid of empty lines
                        .split('\n') // CAPTCHA Sarah Connor

        // put all the newvar into the top of the list
        fun filterNewvar(s: IR2) = s.startsWith("NEWVAR")
        val irList = listOf("SECT DATA") + irList1.filter { filterNewvar(it) } + listOf("SECT CODE") + irList1.filterNot { filterNewvar(it) }

        val asm = StringBuilder()
        var prevCompFun = ""

        println("[START irList]")
        println(irList.joinToString("\n")) // test return irList as one string
        println("[END irList]\n")

        for (c in 0..irList.lastIndex) {
            val it = irList[c]

            val tokens = it.split(Regex(" +"))
            val head = tokens[0]
            val arg1 = tokens.getOrNull(1)
            val arg2 = tokens.getOrNull(2)
            val arg3 = tokens.getOrNull(3)

            if (it.isEmpty())
                continue

            val stmt: List<String>? = when (head) {
                "HALT" -> { listOf("HALT;") }
                "LOADCONST" -> { // ( -- immediate )
                    val l = listOf(
                            "LOADWORDIHI r1, ${arg1!!.dropLast(5)}h;",
                            "LOADWORDILO r1, ${arg1.takeLast(5)};",
                            "PUSH r1;"
                    )

                    if (arg1.length >= 5) l else l.drop(1)
                }
                "LOAD" -> { // ( address -- value in the addr )
                    listOf("POP r1;",
                            "LOADWORD r1, r1, r0;"
                    )
                }
                "STORE" -> { // ( value, address -- )
                    // TODO 'address' is virtual one
                    listOf("POP r2;",
                            "POP r1;",
                            "STOREWORD r1, r2, r0;"
                    )
                }
                "LABEL" -> {
                    listOf(":$arg1;")
                }
                "JUMP" -> {
                    listOf("JMP @$arg1;")
                }
                "ISEQUAL", "ISNOTEQUAL", "ISGREATER", "ISLESSER", "ISGREATEQUAL", "ISLESSEQUAL" -> {
                    prevCompFun = head // it's guaranteed compfunction is followed by JUMPFALSE (see IF/IFELSE/WHILE)

                    listOf("POP r2;",
                            "POP r1;",
                            "CMP r1, r2;"
                    )
                }
                "JUMPFALSE" -> {
                    IR2toFalseJumps(prevCompFun).map { "$it @$arg1;" }
                }
                "NEG" -> {
                    listOf("POP r1;",
                            "SUB r1, r0, r1;",
                            "PUSH r1;"
                    )
                }
                "SECT" -> { listOf(".$arg1;") }
                "NEWVAR" -> { listOf("$arg1 $arg2;") }
                else -> {
                    listOf("# Unknown IR2: $it")
                }
            }

            // keep this as is; it puts \n and original IR2 as formatted comments
            val tabLen = 50
            if (addDebugComments) {
                stmt?.forEachIndexed { index, s ->
                    if (index == 0)
                        if (head == "JUMPFALSE")
                            asm.append(s.tabulate(tabLen) + "# ($prevCompFun) -> $it\n")
                        else
                            asm.append(s.tabulate(tabLen) + "# $it\n")
                    else if (index == stmt.lastIndex)
                        asm.append(s.tabulate(tabLen) + "#\n")
                    else
                        asm.append(s.tabulate(tabLen) + "#\n")
                }
            }
            else {
                stmt?.forEach { asm.append("$it ") } // ASMs grouped as their semantic meaning won't get \n'd
            }
            stmt?.let { asm.append("\n") }
        }

        return asm.toString()
    }

    private fun Byte.to2Hex() = this.toUint().toString(16).toUpperCase().padStart(2, '0') + 'h'
    private fun String.tabulate(columnSize: Int = 56) = this + " ".repeat(maxOf(1, columnSize - this.length))
}

// IR1 is the bunch of functions above
typealias IR2 = String
/**
 * Address Environment; a function to map a variable name to its memory address.
 *
 * The address is usually virtual. Can be converted to real address at either (IR2 -> ASM) or (ASM -> Machine) stage.
 */
typealias Rho = (String) -> Int
//typealias CodeL = (Rho) -> IR2
//typealias CodeR = () -> IR2
inline class CodeL(val function: (Rho) -> IR2) {
    operator fun invoke(rho: Rho): IR2 { return function(rho) }
}
inline class CodeR(val function: () -> IR2) {
    operator fun invoke(): IR2 { return function() }
}


fun main(args: Array<String>) {
    val testProg = NewCompiler.testProgram
    val tree = Cflat.buildTree(Cflat.tokenise(testProg))

    val vm = TerranVM(4096)
    val assembler = Assembler(vm)

    println(tree)

    //val code = NewCompiler.TEST_VARS
    val (ir1, aenv) = NewCompiler.toIR1(tree)
    val ir2 = ir1.invoke()

    //code.invoke().forEach { println(it.invoke()) } // this is probably not the best sequence comprehension

    println("## IR2: ##")
    println(ir2)

    val asm = NewCompiler.toASM(ir2)

    println("## ASM: ##")
    println(asm)

    val vmImage = assembler(asm)

    println("## OPCODE: ##")
    vmImage.bytes.printASM()
}