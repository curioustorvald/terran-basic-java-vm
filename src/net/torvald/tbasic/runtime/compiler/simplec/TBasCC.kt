package net.torvald.tbasic.runtime.compiler.simplec

import java.util.*
import kotlin.collections.ArrayList

/**
 * A compiler for SimpleC language that compiles into TBASOpcode.
 *
 * ## New Features
 *
 * - new data type ```boolean```
 * - infinite loop using ```forever``` block. You can still use ```for (;;)```, ```while (true)``` or ```while (1)```
 * - counted simple loop (without loop counter ref) using ```repeat``` block
 *
 *
 * Created by minjaesong on 2017-06-04.
 */
object TBasCC {

    private val structOpen = '{'
    private val structClose = '}'

    private val parenOpen = '('
    private val parenClose = ')'

    private val genericTokenSeparator = Regex("""[ \t]+""")

    private val infiniteLoops = arrayListOf<Regex>(
            Regex("""while\(true\)"""),
            Regex("""while\([1-9][0-9]*\)"""),
            Regex("""for\(;;\)""")
    )

    private val keywords = hashSetOf(
            // classic C
            "auto","break","case","char","const","continue","default","do","double","else","enum","extern","float",
            "for","goto","if","int","long","register","return","short","signed","static","struct","switch",//"sizeof" // is an operator
            "typedef","union","unsigned","void","volatile","while",
            // SimpleC boolean
            "boolean","true","false",
            // SimpleC blocks
            "forever","repeat"

            // SimpleC dropped keywords (keywords that won't do anything/behave differently than C99, etc.):
            //  - auto, register, signed, unsigned, volatile: not implemented
            //  - float: will act same as double
            //  - extern: everthing is global, anyway

            // SimpleC exclusive keywords:
            //  - boolean, true, false: boolean algebra
    )
    private val operatorsWhole = hashSetOf(
            "sizeof","!","=","==","<",">","+","-","*","/","%" //...
    )
    private val funcAnnotations = hashSetOf(
            "auto", // does nothing; useless even in C (it's derived from B language, actually)
            "extern" // not used in SimpleC
    )
    private val funcTypes = hashSetOf(
            "char", "short", "int", "long", "float", "double", "boolean", "void"
    )
    private val codeBlockKeywords = hashSetOf(
            "do", "else", "enum", "for", "if", "struct", "switch", "union", "while", "forever", "repeat"
    )
    private val preprocessorKeywords = hashSetOf(
            "#include","#ifndef","#ifdef","#define","#if","#else","#elif","#endif","#undef","#pragma"
    )


    operator fun invoke(program: String) {
        val tree = tokenise(program)
    }


    private val structNames = HashSet<String>()
    private val structDict = ArrayList<CStruct>()

    private val funcNames = HashSet<String>()
    private val funcDict = ArrayList<CFunction>()


    /* Test program

void main() {
    fprintf(stdout, "Hello, world!\n");
}

     */
    /** No preprocessor should exist at this stage! */
    private fun tokenise(program: String): SyntaxTreeNode {

        var program = program
                .replace(Regex("""[\s]*//[^\n]*"""), "") // line comment killer
                .replace(Regex("""[\s]*/\**\*/[\s]*"""), "") // comment blocks killer
                .replace(Regex("""[\s]*;[\s]*"""), ";") // whitespace around ;
                .replace(Regex("""[\s]*:[\s]*"""), ":") // whitespace around label marker
                .replace(Regex("""[\s]*\{[\s]*"""), "{") // whitespace around {
                .replace(Regex("""[\s]*\}[\s]*"""), "}") // whitespace around }
                .replace(Regex("""[\s]*\([\s]*"""), "(") // whitespace around (
                .replace(Regex("""[\s]*\)[\s]*"""), ")") // whitespace around )
                .replace(Regex("""[\s]*,[\s]*"""), ",") // whitespace around ,
                .replace(Regex("""[\s]*->[\s]*"""), "->") // whitespace around ->


        infiniteLoops.forEach { // replace classic infinite loops
            program = program.replace(it, "forever")
        }

        //println(program)


        ///////////////////////////////////
        // STEP 0. Divide things cleanly //
        ///////////////////////////////////

        val lineStructures = ArrayList<LineStructure>()


        val sb = StringBuilder()
        var charCtr = 0
        var structureDepth = 0
        fun flushToLineStructure() {
            if (sb.isNotEmpty()) {
                lineStructures.add(LineStructure(structureDepth, sb.toString()))
                sb.setLength(0)
            }
        }
        var forStatementEngaged = false // to filter FOR range semicolon from statement-end semicolon
        while (charCtr < program.length) {
            val char = program[charCtr]


            if (char == structOpen) {
                flushToLineStructure()
                structureDepth += 1
            }
            else if (char == structClose) {
                flushToLineStructure()
                structureDepth -= 1
            }
            else if (char == ')' && forStatementEngaged) {
                forStatementEngaged = false
                TODO()
            }
            else if (charCtr < program.length - 3 && program.slice(charCtr..charCtr + 3) == "for(") {
                forStatementEngaged = true
                TODO()
            }
            else if (!forStatementEngaged && char == ';') {
                flushToLineStructure()
            }
            else {
                sb.append(char)
            }


            charCtr++
        }



        // test print
        lineStructures.forEach {
            repeat(it.depth) {
                print(">\t")
            }
            println(it.line)
        }

        //throw Exception()





        ///////////////////////////
        // STEP n. Create a tree //
        ///////////////////////////

        val ASThead = SyntaxTreeNode(ExpressionType.FUNCTION_DEF, ReturnType.VOID, name = null, isRoot = true)
        val middleNodes = Stack<SyntaxTreeNode>()
        var currentDepth = 0
        middleNodes.push(ASThead)

        fun getCurrentNode() = middleNodes.peek()
        fun pushNode(node: SyntaxTreeNode) {
            middleNodes.push(node)
            currentDepth += 1
        }

        lineStructures.forEach { val (depth, line) = it
            if (depth > currentDepth) throw SyntaxError("Unexpected code block")
            if (depth < currentDepth) middleNodes.pop()

            val asFuncDef = asFuncDef(line)
            val asFuncCall = asFuncCall(line)

            if (asFuncDef != null) {
                getCurrentNode().addStatement(asFuncDef)
                pushNode(asFuncDef) // go one level deeper
            }
            else if (asFuncCall != null) {
                getCurrentNode().addStatement(asFuncCall)
            }
            else if (false) {

            }
        }



        throw Exception()
    }



    private fun traverseAST() {

    }




    ///////////////////////////////////////////////////
    // publicising things so that they can be tested //
    ///////////////////////////////////////////////////

    fun resolveTypeString(type: String, isPointer: Boolean = false): ReturnType {
        val isPointer = type.endsWith('*') or isPointer

        return if (structNames.contains(type))
            if (isPointer) ReturnType.STRUCT_PTR else ReturnType.STRUCT
        else
            when (type) {
                "void"    -> if (isPointer) ReturnType.VOID_PTR else ReturnType.VOID
                "char"    -> if (isPointer) ReturnType.CHAR_PTR else ReturnType.CHAR
                "short"   -> if (isPointer) ReturnType.SHORT_PTR else ReturnType.SHORT
                "int"     -> if (isPointer) ReturnType.INT_PTR else ReturnType.INT
                "long"    -> if (isPointer) ReturnType.LONG_PTR else ReturnType.LONG
                "float"   -> if (isPointer) ReturnType.FLOAT_PTR else ReturnType.FLOAT
                "double"  -> if (isPointer) ReturnType.DOUBLE_PTR else ReturnType.DOUBLE
                "boolean" -> if (isPointer) ReturnType.BOOL_PTR else ReturnType.BOOL
                else -> throw IllegalTokenException("Unknown return type: $type")
            }
    }

    fun asFuncDef(line: String): SyntaxTreeNode? {
        // [optional_annotation]   type   name(type   argname,type   argname)

        val whereToCutForFuncType = line.indexOf('(')


        // get return type
        // search for line "[optional-annotation]  type  name("
        // function name is always have '(' appended
        // beware of multiple-spaces-as-separators and tab-separators!
        val slices_funcType = line.slice(0..whereToCutForFuncType).split(genericTokenSeparator)


        if (slices_funcType.size < 2) return null

        val actualFuncType = slices_funcType[slices_funcType.lastIndex - 1]
        val returnType = resolveTypeString(actualFuncType)

        val funcName = slices_funcType.last().dropLast(1)

        val funcDefNode = SyntaxTreeNode(ExpressionType.FUNCTION_DEF, returnType, funcName)


        // get arguments
        val lastParen = line.lastIndexOf(')')
        if (lastParen < 0) throw IllegalTokenException("at line $line -- ')' expected")
        // "int arg1,boolean arg2,double arg3"
        val argumentsDef = line.slice(whereToCutForFuncType + 1..lastParen - 1).split(',')

        argumentsDef.forEach { iit ->
            val it = iit.replace(Regex("""[ \t]*\*[\t]*"""), " * ") // separate pointer marker

            val argTokens = it.split(genericTokenSeparator) // {"type", ("*",) "name"}

            // function prototype
            if (argTokens.size == 1 || (argTokens.size == 2 && argTokens[1] == "*")) {
                funcDefNode.addArgument(SyntaxTreeNode(
                        ExpressionType.FUNC_ARGUMENT_DEF,
                        resolveTypeString(argTokens[0], argTokens.size == 2),
                        null
                ))
            }
            // the "right" way (with double-scan ofc)
            else {
                funcDefNode.addArgument(SyntaxTreeNode(
                        ExpressionType.FUNC_ARGUMENT_DEF,
                        resolveTypeString(argTokens[0], argTokens.size == 3),
                        argTokens.last()
                ))
            }
        }

        return funcDefNode
    }

    /** TODO currently function calls/equations INSIDE of argument is not supported (needs recursion) */
    fun asFuncCall(line: String): SyntaxTreeNode? {
        // name(   arg   ,   arg   ,   "lol, wut"   )

        val whereToCutForFuncType = line.indexOf('(')


        // get function name
        // search for line "name("
        // function name is always have '(' appended
        // beware of multiple-spaces-as-separators and tab-separators!
        val slices_funcType = line.slice(0..whereToCutForFuncType).split(genericTokenSeparator)

        if (slices_funcType.size != 1) return null
        val funcName = slices_funcType.last().dropLast(1)


        // get arguments
        val lastParen = line.lastIndexOf(')')
        if (lastParen < 0) throw IllegalTokenException("at line $line -- ')' expected")
        val argsLine = line.slice(whereToCutForFuncType + 1..lastParen - 1)

        val args = ArrayList<String>()

        val sb = StringBuilder()
        var isString = false
        argsLine.forEachIndexed { index, it ->
            if (index > 0 && it == '"' && argsLine[index - 1] != '\\') {
                // pop strings
                if (isString) {
                    args.add(sb.toString())
                    sb.setLength(0)
                }

                isString = !isString
            }
            else if (!isString && (it == ',') && sb.isNotEmpty()) {
                args.add(sb.toString())
                sb.setLength(0)
            }
            else {
                sb.append(it)
            }
        }


        val funcCallNode = SyntaxTreeNode(ExpressionType.FUNCTION_CALL, null, funcName)


        // set all the arguments right
        // TODO currently function calls/equations INSIDE of argument is not supported (needs recursion)
        args.forEach {
            val argNodeLeaf = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, null, null)
            argNodeLeaf.literalValue = it // TODO it is string
            funcCallNode.addArgument(argNodeLeaf)
        }


        return funcCallNode
    }





    private data class LineStructure(val depth: Int, val line: String)

    class SyntaxTreeNode(
            val expressionType: ExpressionType,
            val returnType: ReturnType?,
            val name: String?,
            val isRoot: Boolean = false
    ) {

        var literalValue: Any? = null // for LITERALs only

        val arguments = ArrayList<SyntaxTreeNode>() // for FUNCTION, CODE_BLOCK
        val statements = ArrayList<SyntaxTreeNode>()

        fun addArgument(node: SyntaxTreeNode) {
            arguments.add(node)
        }
        fun addStatement(node: SyntaxTreeNode) {
            statements.add(node)
        }


        val isLeaf: Boolean
            get() = expressionType == ExpressionType.LITERAL_LEAF || expressionType == ExpressionType.VARIABLE_LEAF ||
                    (arguments.isEmpty() && statements.isEmpty())

        override fun toString() = toStringRepresentation(0)

        private fun toStringRepresentation(depth: Int): String {
            val header = if (isRoot) "⧫AST (name: $name)" else "AST (name: $name)"
            val lines = arrayListOf(
                    header,
                    "│ ExprType : $expressionType",
                    "│ Ret Type : $returnType",
                    "│ Literal  : $literalValue",
                    "│ isRoot ?? $isRoot",
                    "│ isLeaf ?? $isLeaf"
            )

            if (!isLeaf) {
                lines.add("│ # of statements: ${statements.size}")
                arguments.forEach { lines.add(it.toStringRepresentation(depth + 1)) }
                lines.add("│ # of statements: ${statements.size}")
                statements.forEach { lines.add(it.toStringRepresentation(depth + 1)) }
            }

            lines.add("╘" + "═".repeat(header.length - 1))

            val sb = StringBuilder()
            lines.forEachIndexed { index, it ->
                repeat(depth) { sb.append("│ ") }
                sb.append(it)
                if (index < lines.lastIndex) {
                    sb.append("\n")
                }
            }

            return sb.toString()
        }
    }

    enum class ExpressionType {
        FUNCTION_DEF, FUNC_ARGUMENT_DEF, // expect Arguments and Statements
        FUNCTION_CALL, CODE_BLOCK, // expect Arguments and Statements
        STATEMENT, // equations that needs to be eval'd; has returnType of null?; things like "(3 + 4) * 12" are leaves; "(i + 1) % 16" is not (it calls variable 'i', which _is_ a leaf)
        LITERAL_LEAF, // literals, also act as a leaf of the tree; has returnType of null
        VARIABLE_LEAF // has returnType of null; typical use case: somefunction(somevariable) e.g. println(message)
    }
    enum class ReturnType {
        VOID, BOOL, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE, STRUCT,
        VOID_PTR, BOOL_PTR, CHAR_PTR, SHORT_PTR, INT_PTR, LONG_PTR, FLOAT_PTR, DOUBLE_PTR, STRUCT_PTR
    }

    class CStruct(val name: String) {
        data class CStructTypeNamePair(val type: ReturnType, val name: String)
        val members = ArrayList<CStructTypeNamePair>()
        fun addMember(type: ReturnType, name: String) {
            members.add(CStructTypeNamePair(type, name))
        }
    }

    abstract class CFunction(val name: String, val returnType: ReturnType) {
        abstract fun generateOpcode(vararg args: Any)
    }

    open class SyntaxError(msg: String? = null) : Exception(msg)
    class IllegalTokenException(msg: String? = null) : SyntaxError(msg)
}