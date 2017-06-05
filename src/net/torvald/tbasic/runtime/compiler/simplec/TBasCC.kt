package net.torvald.tbasic.runtime.compiler.simplec

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

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

    private val nullchar = 0.toChar()

    private val infiniteLoops = arrayListOf<Regex>(
            Regex("""while\(true\)"""),
            Regex("""while\([1-9][0-9]*\)"""),
            Regex("""for\(;;\)""")
    )

    private val regexBooleanWhole = Regex("""^(true|false|TRUE|FALSE)$""")
    private val regexHexWhole = Regex("""^(0[Xx][0-9A-Fa-f_]+)$""")
    private val regexOctWhole = Regex("""^(0[0-7_]+)$""")
    private val regexBinWhole = Regex("""^(0[Bb][01_]+)$""")
    private val regexFPWhole =  Regex("""^([0-9]*\.[0-9]+[Ff]?|[0-9]+[Ff])$""")
    private val regexIntWhole = Regex("""^([0-9_]+)$""")

    private val regexVarNameWhole = Regex("""^([A-Za-z_][A-Za-z0-9_]*)$""")

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


    fun sizeofPrimitive(type: String) = when (type) {
        "char" -> 1
        "short" -> 2
        "int" -> 4
        "long" -> 8
        "float" -> 8 // in SimpleC, float is same as double
        "double" -> 8
        "boolean" -> 1
        "void" -> 1 // GCC feature
        else -> throw IllegalArgumentException("Unknown primitive type: $type")
    }


    operator fun invoke(program: String) {
        val tree = tokenise(preprocess(program))
    }


    private val structNames = HashSet<String>()
    private val structDict = ArrayList<CStruct>()

    private val funcNames = HashSet<String>()
    private val funcDict = ArrayList<CFunction>()

    private val includesUser = HashSet<String>()
    private val includesLib = HashSet<String>()

    private fun preprocess(program: String): String {
        program.lines().forEach {
            if (it.startsWith('#')) {
                val tokens = it.split(genericTokenSeparator)
                val cmd = tokens[0]

                if (!preprocessorKeywords.contains(cmd)) {
                    throw UndefinedStatement("Preprocessor macro $cmd is not supported.")
                }
                else {
                    TODO()
                }
            }
        }

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


        return program
    }

    /** No preprocessor should exist at this stage! */
    private fun tokenise(program: String): SyntaxTreeNode {

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

            val treeNode = asTreeNode(line, line)

            if (treeNode.expressionType == ExpressionType.FUNCTION_DEF) {
                getCurrentNode().addStatement(treeNode)
                pushNode(treeNode) // go one level deeper
            }
            else {
                getCurrentNode().addStatement(treeNode)
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



    fun asTreeNode(parentLine: String, line: String): SyntaxTreeNode {
        val whereToCutForFuncType = line.indexOf('(')

        // splitted line; will be used by many lines of code
        val lineSplit = line.split(genericTokenSeparator)


        // get return type
        // search for line "[optional-annotation]  type  name("
        // function name is always have '(' appended
        // beware of multiple-spaces-as-separators and tab-separators!
        val slices_funcType = line.slice(0..whereToCutForFuncType).split(genericTokenSeparator)


        ////////////////////////////
        // as Function Definition //
        ////////////////////////////
        if (slices_funcType.size >= 2 && line.endsWith(')')) {
            val actualFuncType = slices_funcType[slices_funcType.lastIndex - 1]
            val returnType = resolveTypeString(actualFuncType)

            val funcName = slices_funcType.last().dropLast(1)

            val funcDefNode = SyntaxTreeNode(ExpressionType.FUNCTION_DEF, returnType, funcName)


            // get arguments
            // "int arg1,boolean arg2,double arg3"
            val argumentsDef = line.slice(whereToCutForFuncType + 1..line.lastIndex - 1).split(',')

            argumentsDef.forEach { argDefRaw ->
                val argDef = argDefRaw.replace(Regex("""[ \t]*\*[\t]*"""), " * ") // separate pointer marker

                val argTokens = argDef.split(genericTokenSeparator) // {"type", ("*",) "name"}

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
        //////////////////////
        // as Function Call // (also works as keyworded code block (e.g. if, for, while))
        //////////////////////
        else if (slices_funcType.size == 1 && line.endsWith(')')) {
            val funcName = slices_funcType.last().dropLast(1)


            // get arguments
            val argsLine = line.slice(whereToCutForFuncType + 1..line.lastIndex - 1)

            val args = ArrayList<String>()

            val sb = StringBuilder()
            var isString = false
            var parenDepth = 0
            argsLine.forEachIndexed { index, char ->
                // error catching
                if (parenDepth < 0) {
                    throw SyntaxError("at line $line -- misplaced ')'")
                }
                // normal stuffs
                else if (char == '(') {
                    parenDepth += 1
                    sb.append(char)
                }
                else if (char == ')') {
                    parenDepth -= 1
                    sb.append(char)
                }
                // --> quotes
                else if (parenDepth == 0 && char == '"' &&
                        (index == 0 || (index > 0 && argsLine[index - 1] != '\\')) // \" is not counted
                             ) {
                    // pop strings
                    if (isString) {
                        args.add('"' + sb.toString()) // "string_contents (" appended as String marker)
                        sb.setLength(0)
                    }

                    isString = !isString
                }
                else if (!isString && parenDepth == 0 && (char == ',' || index == argsLine.lastIndex)) {
                    // deal with final paren
                    if (char == ')') { parenDepth -= 1 }
                    else if (char == '(') { parenDepth += 1 } // just in case...


                    if (index == argsLine.lastIndex) { sb.append(char) }

                    if (sb.isNotEmpty()) {
                        args.add(sb.toString())
                        sb.setLength(0)
                    }
                }
                else {
                    sb.append(char)
                }
            }

            if (parenDepth > 0) {
                throw SyntaxError("at line $line -- unclosed '('")
            }


            val funcCallNode = SyntaxTreeNode(ExpressionType.FUNCTION_CALL, null, funcName)

            println("!!argsLine $argsLine")
            args.forEach { println("!!args: $it") }
            println("==========================")

            // set all the arguments right
            args.forEach {
                val argNodeLeaf = asTreeNode(parentLine, it)
                funcCallNode.addArgument(argNodeLeaf)
            }


            return funcCallNode
        }
        ////////////////////////
        // as Var Call / etc. //
        ////////////////////////
        else {
            // filter illegal lines (absurd keyword usage)
            if (codeBlockKeywords.contains(line) || funcAnnotations.contains(line)) {
                throw IllegalTokenException("in line $line -- Unexpected token: $line")
            }

            ///////////////////////
            // Bunch of literals //
            ///////////////////////

            // String literals
            if (line.startsWith('"')) {
                val leafNode = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.CHAR_PTR, null)
                leafNode.literalValue = (line.substring(1) + nullchar)
                return leafNode
            }
            // boolean literals
            else if (line.matches(regexBooleanWhole)) {
                val leafNode = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.BOOL, null)
                leafNode.literalValue = (line == "true" || line == "TRUE")
                return leafNode
            }
            // hexadecimal literals
            else if (line.matches(regexHexWhole)) {
                val isLong = line.endsWith('L', true)
                val leafNode = SyntaxTreeNode(
                        ExpressionType.LITERAL_LEAF,
                        if (isLong) ReturnType.LONG else ReturnType.INT,
                        null
                )
                leafNode.literalValue = if (isLong)
                    line.slice(2..line.lastIndex - 1).toLong(16)
                else
                    line.slice(2..line.lastIndex).toInt(16)

                return leafNode
            }
            // octal literals
            else if (line.matches(regexOctWhole)) {
                val isLong = line.endsWith('L', true)
                val leafNode = SyntaxTreeNode(
                        ExpressionType.LITERAL_LEAF,
                        if (isLong) ReturnType.LONG else ReturnType.INT,
                        null
                )
                leafNode.literalValue = if (isLong)
                    line.slice(1..line.lastIndex - 1).toLong(8)
                else
                    line.slice(1..line.lastIndex).toInt(8)

                return leafNode
            }
            // binary literals
            else if (line.matches(regexBinWhole)) {
                val isLong = line.endsWith('L', true)
                val leafNode = SyntaxTreeNode(
                        ExpressionType.LITERAL_LEAF,
                        if (isLong) ReturnType.LONG else ReturnType.INT,
                        null
                )
                leafNode.literalValue = if (isLong)
                    line.slice(2..line.lastIndex - 1).toLong(2)
                else
                    line.slice(2..line.lastIndex).toInt(2)

                return leafNode
            }
            // floating point literals
            else if (line.matches(regexFPWhole)) {
                val leafNode = SyntaxTreeNode(
                        ExpressionType.LITERAL_LEAF,
                        if (line.endsWith('F', true)) ReturnType.FLOAT else ReturnType.DOUBLE,
                        null
                )
                leafNode.literalValue = if (line.endsWith('F', true))
                    line.slice(0..line.lastIndex - 1).toFloat()
                else
                    line.toDouble()

                return leafNode
            }
            // int literals
            else if (line.matches(regexIntWhole)) {
                val isLong = line.endsWith('L', true)
                val leafNode = SyntaxTreeNode(
                        ExpressionType.LITERAL_LEAF,
                        if (isLong) ReturnType.LONG else ReturnType.INT,
                        null
                )
                leafNode.literalValue = if (isLong)
                    line.slice(0..line.lastIndex - 1).toLong()
                else
                    line.toInt()

                return leafNode
            }

            ///////////////
            // variables //
            ///////////////
            else if (line.matches(regexVarNameWhole)) {
                val leafNode = SyntaxTreeNode(ExpressionType.VARIABLE_LEAF, null, line)
                return leafNode
            }

            TODO()
        }
    }




    private data class LineStructure(val depth: Int, val line: String)

    class SyntaxTreeNode(
            val expressionType: ExpressionType,
            val returnType: ReturnType?, // STATEMENT, LITERAL_LEAF: valid ReturnType; VAREABLE_LEAF: always null
            val name: String?,
            val isRoot: Boolean = false
    ) {

        var literalValue: Any? = null // for LITERALs only | if returnType is Struct, it should hold the struct

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
            val header = "│ ".repeat(depth) + if (isRoot) "⧫AST (name: $name)" else "AST$depth (name: $name)"
            val lines = arrayListOf(
                    header,
                    "│ ".repeat(depth+1) + "ExprType : $expressionType",
                    "│ ".repeat(depth+1) + "Ret Type : $returnType",
                    "│ ".repeat(depth+1) + "Literal  : $literalValue",
                    "│ ".repeat(depth+1) + "isRoot ?? $isRoot",
                    "│ ".repeat(depth+1) + "isLeaf ?? $isLeaf"
            )

            if (!isLeaf) {
                lines.add("│ ".repeat(depth+1) + "# of arguments: ${arguments.size}")
                arguments.forEach { lines.add(it.toStringRepresentation(depth + 1)) }
                lines.add("│ ".repeat(depth+1) + "# of statements: ${statements.size}")
                statements.forEach { lines.add(it.toStringRepresentation(depth + 1)) }
            }

            lines.add("│ ".repeat(depth) + "╘" + "═".repeat(header.length - 1 - 2*depth))

            val sb = StringBuilder()
            lines.forEachIndexed { index, line ->
                sb.append(line)
                if (index < lines.lastIndex) { sb.append("\n") }
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

    class CPrimitive(name: String, val type: ReturnType, val value: Any): CData(name) {
        override fun sizeOf(): Int {
            var typestr = type.toString().toLowerCase()
            if (typestr.endsWith("_ptr")) typestr = typestr.drop(4)
            return sizeofPrimitive(typestr)
        }
    }

    abstract class CFunction(val name: String, val returnType: ReturnType) {
        abstract fun generateOpcode(vararg args: Any)
    }


    open class SyntaxError(msg: String? = null) : Exception(msg)
    class IllegalTokenException(msg: String? = null) : SyntaxError(msg)
    class UnresolvedReference(msg: String? = null) : SyntaxError(msg)
    class UndefinedStatement(msg: String? = null) : SyntaxError(msg)
    class PreprocessorErrorMessage(msg: String) : SyntaxError(msg)
}