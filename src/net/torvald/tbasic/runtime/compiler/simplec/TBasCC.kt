package net.torvald.tbasic.runtime.compiler.simplec

import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * A compiler for SimpleC language that compiles into TBASOpcode.
 *
 * # About SimpleC Language
 *
 * ## New Features
 *
 * - New data type ```boolean```
 * - Infinite loop using ```forever``` block. You can still use ```for (;;)```, ```while (true)``` or ```while (1)```
 * - Counted simple loop (without loop counter ref) using ```repeat``` block
 *
 *
 * ## Important Changes from C
 *
 * - All function definition must specify return type, even if the type is ```void```.
 * - Unary pre- and post- increments/decrements are considered _evil_ and thus prohibited.
 * - Unsigned types are also considered _evil_ and thus prohibited.
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
            Regex("""while\(true\)"""), // whitespaces are filtered on preprocess
            Regex("""for\([\s]*;[\s]*;[\s]*\)""")
    ) // more types of infinite loops are must be dealt with (e.g. while (0xFFFFFFFF < 0x7FFFFFFF))

    private val regexBooleanWhole = Regex("""^(true|false)$""")
    private val regexHexWhole = Regex("""^(0[Xx][0-9A-Fa-f_]+)$""")
    private val regexOctWhole = Regex("""^(0[0-7_]+)$""")
    private val regexBinWhole = Regex("""^(0[Bb][01_]+)$""")
    private val regexFPWhole =  Regex("""^([0-9]*\.[0-9]+[Ff]?|[0-9]+[Ff])$""")
    private val regexIntWhole = Regex("""^([0-9_]+)$""")

    private val regexVarNameWhole = Regex("""^([A-Za-z_][A-Za-z0-9_]*)$""")

    private val regexWhitespaceNoSP = Regex("""[\t\r\n\v\f]""")
    private val regexIndents = Regex("""^ +|^\t+|(?<=\n) +|(?<=\n)\t+""")

    private val digraphs = hashMapOf(
            "<:" to '[',
            ":>" to ']',
            "<%" to '{',
            "%>" to '}',
            "%:" to '#'
    )
    private val trigraphs = hashMapOf(
            "??=" to "#",
            "??/" to "'",
            "??'" to "^",
            "??(" to "[",
            "??)" to "]",
            "??!" to "|",
            "??<" to "{",
            "??>" to "}",
            "??-" to "~"
    )
    private val keywords = hashSetOf(
            // classic C
            "auto","break","case","char","const","continue","default","do","double","else","enum","extern","float",
            "for","goto","if","int","long","register","return","short","signed","static","struct","switch",//"sizeof" // is an operator
            "typedef","union","unsigned","void","volatile","while",
            // SimpleC boolean
            "boolean","true","false",
            // SimpleC code blocks
            "forever","repeat"

            // SimpleC dropped keywords (keywords that won't do anything/behave differently than C95, etc.):
            //  - auto, register, signed, unsigned, volatile: not implemented; WILL THROW ERROR
            //  - float: will act same as double
            //  - extern: everthing is global, anyway; WILL THROW ERROR

            // SimpleC exclusive keywords:
            //  - boolean, true, false: boolean algebra
    )
    private val operatorsHierarchyInternal = arrayOf( // opirator precedence in internal format (#_nameinlowercase)
            // most important
            hashSetOf("#_postinc","#_postdec","[", "]",".","->"),
            hashSetOf("#_preinc","#_postinc","#_unaryplus","#_unaryminus","!", "~","#_pointer","#_addressof","sizeof","(char *)","(short *)","(int *)","(long *)","(float *)","(double *)","(boolean *)","(void *)", "(char)","(short)","(int)","(long)","(float)","(double)","(boolean)"),
            hashSetOf("*","/","%"),
            hashSetOf("+","-"),
            hashSetOf("<<",">>"),
            hashSetOf("<","<=",">",">="),
            hashSetOf("==","!="),
            hashSetOf("&"),
            hashSetOf("^"),
            hashSetOf("|"),
            hashSetOf("&&"),
            hashSetOf("||"),
            hashSetOf("?",":"),
            hashSetOf("=","+=","-=","*=","/=","%=","<<=",">>=","&=","^=","|="),
            hashSetOf(",")
            // least important
    )
    private val operatorsHierarchyRTL = arrayOf(
            false,
            true,
            false,false,false,false,false,false,false,false,false,false,
            true,true,
            false
    )
    private val operatorLiterals = hashSetOf( // contains symbols with no order
            "(char*)","(short*)","(int*)","(long*)","(float*)","(double*)","(boolean*)","(void*)",
            "(char)","(short)","(int)","(long)","(float)","(double)","(boolean)",
            "++","--","[","]",".","->","+","-","!","~","*","&","sizeof","/","%","<<",">>","<","<=",">",">=","==","!=",
            "^","|","&&","||","?",":","=","+=","-=","*=","/=","%=","<<=",">>=","&=","^=","|=",","
    )
    private val operatorSanitiseList = arrayOf( // order is important!
            "<<=",">>=","\\+\\+","--","\\[","\\]","->","/","%","<<",">>","<=",">=","==","!=","\\+=","-=","\\*=","/=","%=","&=","\\^=","\\|=",
            "\\^","&&","\\|\\|","&","\\|","\\?",":","=","sizeof","\\+","-","!","~","\\*","<",">" // NOTE: '.' and ',' won't get whitespace
    )
    private val splittableTokens = arrayOf( // order is important!
            "<<=",">>=",
            "++","--","&&","||","<<",">>","->","<=",">=","==","!=","+=","-=","*=","/=","%=","&=","^=","|=",
            "<",">","^","|","?",":","=",",",".","+","-","!","~","*","&","/","%","(",")",
            " "
    )
    private val evilOperators = hashSetOf(
            "++","--"
    )
    private val funcAnnotations = hashSetOf(
            "auto", // does nothing; useless even in C (it's derived from B language, actually)
            "extern" // not used in SimpleC
    )
    private val funcTypes = hashSetOf(
            "char", "short", "int", "long", "float", "double", "boolean", "void"
    )
    private val varAnnotations = hashSetOf(
            "auto", // does nothing; useless even in C (it's derived from B language, actually)
            "extern", // not used in SimpleC
            "const",
            "register" // not used in SimpleC
    )
    private val varTypes = hashSetOf(
            "struct", "char", "short", "int", "long", "float", "double", "boolean"
    )
    private val validFuncPreword = (funcAnnotations + funcTypes).toHashSet()
    private val validVariablePreword = (varAnnotations + varTypes).toHashSet()
    private val codeBlockKeywords = hashSetOf(
            "do", "else", "enum", "for", "if", "struct", "switch", "union", "while", "forever", "repeat"
    )
    private val functionalKeywordsWithOneArg = hashSetOf(
            "goto", "return"
    )
    private val functionalKeywordsNoArg = hashSetOf(
            "break", "continue",
            "return" // return nothing
    )
    private val preprocessorKeywords = hashSetOf(
            "#include","#ifndef","#ifdef","#define","#if","#else","#elif","#endif","#undef","#pragma"
    )
    private val escapeSequences = hashMapOf<String, Char>(
            """\a""" to 0x07.toChar(), // Alert (Beep, Bell)
            """\b""" to 0x08.toChar(), // Backspace
            """\f""" to 0x0C.toChar(), // Formfeed
            """\n""" to 0x0A.toChar(), // Newline (Line Feed)
            """\r""" to 0x0D.toChar(), // Carriage Return
            """\t""" to 0x09.toChar(), // Horizontal Tab
            """\v""" to 0x0B.toChar(), // Vertical Tab
            """\\""" to 0x5C.toChar(), // Backslash
            """\'""" to 0x27.toChar(), // Single quotation mark
            """\"""" to 0x22.toChar(), // Double quotation mark
            """\?""" to 0x3F.toChar()  // uestion mark (used to avoid trigraphs)
    )
    private val builtinFunctions = hashSetOf(
            "assignvar", "plusassignvar"
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

    // compiler options
    private var useDigraph = false
    private var useTrigraph = false

    operator fun invoke(program: String, useDigraph: Boolean = false, useTrigraph: Boolean = false) {
        this.useDigraph = useDigraph
        this.useTrigraph = useTrigraph


        val tree = tokenise(preprocess(program))
        TODO()
    }


    private val structNames = HashSet<String>()
    private val structDict = ArrayList<CStruct>()

    private val funcNames = HashSet<String>()
    private val funcDict = ArrayList<CFunction>()

    private val includesUser = HashSet<String>()
    private val includesLib = HashSet<String>()

    fun preprocess(program: String): String {
        var program = program
                //.replace(regexIndents, "") // must come before regexWhitespaceNoSP
                //.replace(regexWhitespaceNoSP, "")

        if (useTrigraph) {
            trigraphs.forEach { from, to ->
                program = program.replace(from, to)
            }
        }


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



        //  // ADD whitespaces //
        //  // make sure operators HAVE a whitespace around (eventually, just between) them
        //  var program = program
        //  operatorSanitiseList.forEach {
        //      // I know it's stupid but at least I don't have to deal with the complexity
        //      val regexHeadTail = """[\s]*"""
        //      val regexBody = it
        //      val regex = Regex("$regexHeadTail$regexBody$regexHeadTail")
//
        //      program = program.replace(regex, " $it ")
        //  } // NOTE : whitespaces added for [ and ] will be removed again by 'KILL whitespaces' below
//
//
        //  // KILL whitespaces //
        //  // FIXME TODO strings that matches these conditions are reck'd
        //  program = program
        //          .replace(Regex("""[\s]*//[^\n]*"""), "") // line comment killer
        //          .replace(Regex("""[\s]*/\**\*/[\s]*"""), "") // comment blocks killer
        //          .replace(Regex("""[\s]*;[\s]*"""), ";") // kill whitespace around ;
        //          //.replace(Regex("""[\s]*:[\s]*"""), ":") // kill whitespace around label marker  COMMENTED: ternary op
        //          .replace(Regex("""[\s]*\{[\s]*"""), "{") // kill whitespace around {
        //          .replace(Regex("""[\s]*\}[\s]*"""), "}") // kill whitespace around }
        //          .replace(Regex("""[\s]*\([\s]*"""), "(") // kill whitespace around (
        //          .replace(Regex("""[\s]*\)[\s]*"""), ")") // kill whitespace around )
        //          .replace(Regex("""[\s]*\[[\s]*"""), "[") // kill whitespace around [
        //          .replace(Regex("""[\s]*\][\s]*"""), "]") // kill whitespace around ]
        //          .replace(Regex("""[\s]*,[\s]*"""), ",") // kill whitespace around ,
//
//
//
        //  infiniteLoops.forEach { // replace classic infinite loops
        //      program = program.replace(it, "forever")
        //  }
//
        //  //println(program)


        return program
    }

    /** No preprocessor should exist at this stage! */
    private fun tokenise(program: String): SyntaxTreeNode {
        ///////////////////////////////////
        // STEP 0. Divide things cleanly //
        ///////////////////////////////////
        // a.k.a. tokenise properly e.g. {extern int foo ( int initSize , SomeStruct strut , )} or {int foo = getch ( ) * ( ( num1 + num3 % 16 ) - 1 )}

        val lineStructures = ArrayList<LineStructure>()
        var currentProgramLineNumber = 1
        var currentLine = LineStructure(currentProgramLineNumber, 0, ArrayList<String>())


        // put things to lineStructure, kill any whitespace
        val sb = StringBuilder()
        var charCtr = 0
        var structureDepth = 0
        fun splitAndMoveAlong() {
            if (sb.isNotEmpty()) {
                currentLine.tokens.add(sb.toString())
                sb.setLength(0)
            }
        }
        fun gotoNewline() {
            if (currentLine.tokens.isNotEmpty()) {
                lineStructures.add(currentLine)
                sb.setLength(0)
                currentLine = LineStructure(currentProgramLineNumber, structureDepth, ArrayList<String>())
            }
        }
        var forStatementEngaged = false // to filter FOR range semicolon from statement-end semicolon
        var isLiteralMode = false // ""  ''
        var isCharLiteral = false
        while (charCtr < program.length) {
            var char = program[charCtr]
            var lookahead4 = program.substring(charCtr, minOf(charCtr + 4, program.length)) // charOfIndex {0, 1, 2, 3}
            var lookahead3 = program.substring(charCtr, minOf(charCtr + 3, program.length)) // charOfIndex {0, 1, 2}
            var lookahead2 = program.substring(charCtr, minOf(charCtr + 2, program.length)) // charOfIndex {0, 1}
            var lookbehind2 = program.substring(maxOf(charCtr - 1, 0), charCtr + 1) // charOfIndex {-1, 0}


            // count up line num
            if (char == '\n' && !isCharLiteral && !isLiteralMode) {
                currentProgramLineNumber += 1
                currentLine.lineNum = currentProgramLineNumber
            }
            else if (char == '\n' && isLiteralMode) {
                throw SyntaxError("at line $currentProgramLineNumber -- line break used inside of string literal")
            }
            else if (char.toString().matches(regexWhitespaceNoSP)) {
                // do nothing
            }
            else if (!isLiteralMode && !isCharLiteral) {
                // replace digraphs
                if (useDigraph && digraphs.containsKey(lookahead2)) { // replace digraphs
                    char = program[charCtr]
                    lookahead4 = char + lookahead4.substring(0..lookahead4.lastIndex)
                    lookahead3 = char + lookahead3.substring(0..lookahead3.lastIndex)
                    lookahead2 = char + lookahead2.substring(0..lookahead2.lastIndex)
                    lookbehind2 = lookbehind2.substring(0..lookahead2.lastIndex - 1) + char
                    charCtr++
                }


                // do the real jobs
                if (char == structOpen) {
                    splitAndMoveAlong()
                    gotoNewline()
                    structureDepth += 1
                }
                else if (char == structClose) {
                    splitAndMoveAlong()
                    gotoNewline()
                    structureDepth -= 1
                }
                // double quotes
                else if (char == '"' && lookbehind2[0] != '\\') {
                    isLiteralMode = !isLiteralMode
                    sb.append(char)
                }
                // char literal
                else if (!isCharLiteral && char == '\'' && lookbehind2[0] != '\'') {
                    if ((lookahead4[1] == '\\' && lookahead4[3] != '\'') || (lookahead4[1] != '\\' && lookahead4[2] != '\''))
                        throw SyntaxError("Illegal usage of char literal")
                    isCharLiteral = !isCharLiteral
                }
                else if (char == ')' && forStatementEngaged) {
                    forStatementEngaged = false
                    TODO()
                }
                else if (lookahead3 == "for") {
                    if (forStatementEngaged) throw SyntaxError("keyword 'for' used inside of 'for' statement")
                    forStatementEngaged = true
                    TODO()
                }
                else if (!forStatementEngaged && char == ';') {
                    splitAndMoveAlong()
                    gotoNewline()
                }
                else {
                    if (splittableTokens.contains(lookahead3)) { // three-char operator
                        splitAndMoveAlong() // split previously accumulated word

                        sb.append(lookahead3)
                        splitAndMoveAlong()
                        charCtr += 2
                    }
                    else if (splittableTokens.contains(lookahead2)) { // two-char operator
                        splitAndMoveAlong() // split previously accumulated word

                        if (evilOperators.contains(lookahead2)) {
                            throw IllegalTokenException("at line $currentProgramLineNumber -- evil operator '$lookahead2'")
                        }
                        sb.append(lookahead2)
                        splitAndMoveAlong()
                        charCtr += 1
                    }
                    else if (splittableTokens.contains(char.toString())) { // operator and ' '
                        if (char != ' ') {
                            splitAndMoveAlong() // split previously accumulated word

                            println("splittable token: $char, on line $currentProgramLineNumber")
                            sb.append(char)
                            splitAndMoveAlong()
                        }
                        else { // space detected, split only
                            splitAndMoveAlong()
                        }
                    }
                    else {
                      sb.append(char)
                    }
                }
            }
            else if (isCharLiteral && !isLiteralMode) {
                if (char == '\\') { // escape sequence of char literal
                    sb.append(escapeSequences[lookahead2]!!.toInt())
                    charCtr++
                }
                else {
                    sb.append(char.toInt())
                }
            }
            else if (isLiteralMode && !isCharLiteral) {
                if (char == '"' && lookbehind2[0] != '\\') {
                    isLiteralMode = !isLiteralMode
                }

                sb.append(char)
            }
            else {
                TODO("isLiteral && isCharLiteral")
            }


            charCtr++
        }



        // test print
        lineStructures.forEach {
            println("l${it.lineNum} ${">\t".repeat(it.depth)}${it.tokens}")
        }


        throw Exception()


        ///////////////////////////
        // STEP 1. Create a tree //
        ///////////////////////////

        /*val ASTroot = SyntaxTreeNode(ExpressionType.FUNCTION_DEF, ReturnType.VOID, name = null, isRoot = true)
        val middleNodes = Stack<SyntaxTreeNode>()
        var currentDepth = 0
        middleNodes.push(ASTroot)

        fun getCurrentNode() = middleNodes.peek()
        fun pushNode(node: SyntaxTreeNode) {
            middleNodes.push(node)
            currentDepth += 1
        }

        lineStructures.forEach { val (lineNum, depth, tokens) = it
            if (depth > currentDepth) throw SyntaxError("Unexpected code block")
            if (depth < currentDepth) middleNodes.pop()

            val treeNode = asTreeNode(lineNum, tokens)

            if (treeNode.expressionType == ExpressionType.FUNCTION_DEF) {
                getCurrentNode().addStatement(treeNode)
                pushNode(treeNode) // go one level deeper
            }
            else {
                getCurrentNode().addStatement(treeNode)
            }
        }



        throw Exception()*/
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



    /*fun asTreeNode(lineNumber: Int, tokens: List<String>): SyntaxTreeNode {
        fun splitContainsValidVariablePreword(split: List<String>): Int {
            var ret = -1
            for (stage in 0..minOf(3, split.lastIndex)) {
                if (validVariablePreword.contains(split[stage])) ret += 1
            }
            return ret
        }



        // TODO rework this


        // get return type
        // search for line "[optional-annotation]  type  name  [optional empty list elem]"
        // beware of multiple-spaces-as-separators and tab-separators!
        var slices_funcType = line.substring(0, whereToCutForFuncType).split(genericTokenSeparator)
        // kill appendix
        if (slices_funcType.last().isBlank()) slices_funcType = slices_funcType.dropLast(1)


        // contradiction: auto AND extern


        ////////////////////////////
        // as Function Definition //
        ////////////////////////////
        if (slices_funcType.size >= 2 && line.endsWith(')') && validFuncPreword.contains(slices_funcType[0])) {
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
                if (argTokens.size == 1 && argTokens[0] != "..." || (argTokens.size == 2 && argTokens[1] == "*")) {
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
                            if (argTokens[0] == "...")
                                ReturnType.VARARG
                            else
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
                else if (!isString && parenDepth == 0 && char == ',') {
                    if (sb.isNotEmpty()) {
                        args.add(sb.toString())
                        sb.setLength(0)
                    }
                    // else, ignore
                }
                else {
                    if (isString || (!isString && char != ' ')) {
                        sb.append(char)
                    }
                    // else, ignore
                }


                if (index == argsLine.lastIndex && !isString && sb.isNotEmpty()) {
                    // deal with final paren
                    if (char == ')') { parenDepth -= 1 }
                    else if (char == '(') { parenDepth += 1 } // just in case...

                    args.add(sb.toString())
                    sb.setLength(0)
                }
            }

            if (parenDepth > 0) {
                throw SyntaxError("at line $line -- unclosed '('")
            }


            val funcCallNode = SyntaxTreeNode(ExpressionType.FUNCTION_CALL, null, funcName)

            println("!!argsLine: $argsLine")
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

            // filtered String literals
            if (line.startsWith('"')) {
                val leafNode = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.CHAR_PTR, null)
                leafNode.literalValue = (line.substring(1) + nullchar)
                return leafNode
            }
            // boolean literals
            else if (line.matches(regexBooleanWhole)) {
                val leafNode = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.BOOL, null)
                leafNode.literalValue = line == "true"
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

            //////////////////////////////////////
            // variable literal (VARIABLE_LEAF) // usually function call arguments
            //////////////////////////////////////
            else if (line.matches(regexVarNameWhole)) {
                val leafNode = SyntaxTreeNode(ExpressionType.VARIABLE_LEAF, null, line)
                return leafNode
            }

            else {



                /////////////////////////////////////////////////
                // return something; goto somewhere (keywords) //
                /////////////////////////////////////////////////
                if (lineSplit.size == 2 && functionalKeywordsWithOneArg.contains(lineSplit[0])) {
                    if (lineSplit[0] == "goto") {
                        val node = SyntaxTreeNode(ExpressionType.FUNCTION_CALL, null, "goto")
                        val gotoLabel = SyntaxTreeNode(ExpressionType.GOTO_LABEL_LEAF, null, null)
                        gotoLabel.literalValue = lineSplit[1]

                        node.addArgument(gotoLabel)
                        return node
                    }
                    else {
                        val node = SyntaxTreeNode(ExpressionType.FUNCTION_CALL, null, lineSplit[0])
                        node.addArgument(asTreeNode(parentLine, lineSplit[1]))
                        return node
                    }
                }

                //////////////////////////
                // variable declaration //
                //////////////////////////
                // extern auto struct STRUCTID foobarbaz
                // extern auto int foobarlulz
                else if (splitContainsValidVariablePreword(lineSplit) != -1) {
                    val prewordIndex = splitContainsValidVariablePreword(lineSplit)
                    val realType = lineSplit[prewordIndex]

                    try {
                        var structID: String? = null
                        val varname: String
                        val hasAssignment: Boolean

                        if (realType == "struct") {
                            structID = lineSplit[prewordIndex + 1]
                            varname = lineSplit[prewordIndex + 2]
                            hasAssignment = lineSplit.lastIndex > prewordIndex + 2
                        }
                        else {
                            varname = lineSplit[prewordIndex + 1]
                            hasAssignment = lineSplit.lastIndex > prewordIndex + 1
                        }

                        // deal with assignment
                        if (hasAssignment) {
                            TODO("variable declaration and assign")
                        }
                        else {
                            if (structID != null) {
                                TODO("Struct variable def")
                            }
                            else {
                                val leafNode = SyntaxTreeNode(ExpressionType.VARIABLE_DEF, resolveTypeString(realType), null)
                                leafNode.addArgument(varname.toRawTreeNode())
                                return leafNode
                            }
                        }
                    }
                    catch (syntaxFuck: ArrayIndexOutOfBoundsException) {
                        throw SyntaxError("at line $parentLine -- missing statement(s)")
                    }
                }
                else {
                    // turn infix notation into prefix
                    val operatorStack = Stack<String>()
                    val nodeStack = Stack<Any>()


                    TODO()
                }
            }

        }
    } */




    private data class LineStructure(var lineNum: Int, val depth: Int, val tokens: MutableList<String>)

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
            get() = expressionType.toString().endsWith("_LEAF") ||
                    (arguments.isEmpty() && statements.isEmpty())

        override fun toString() = toStringRepresentation(0)

        private fun toStringRepresentation(depth: Int): String {
            val header = "│ ".repeat(depth) + if (isRoot) "⧫AST (name: $name)" else "AST$depth (name: $name)"
            val lines = arrayListOf(
                    header,
                    "│ ".repeat(depth+1) + "ExprType : $expressionType",
                    "│ ".repeat(depth+1) + "RetnType : $returnType",
                    "│ ".repeat(depth+1) + "LiteralV : [$literalValue]",
                    "│ ".repeat(depth+1) + "isRoot ? $isRoot",
                    "│ ".repeat(depth+1) + "isLeaf ? $isLeaf"
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

    private fun String.toRawTreeNode(): SyntaxTreeNode {
        val node = SyntaxTreeNode(ExpressionType.LITERAL_LEAF, ReturnType.CHAR_PTR, null)
        node.literalValue = this
        return node
    }

    enum class ExpressionType {
        FUNCTION_DEF, FUNC_ARGUMENT_DEF, // expect Arguments and Statements

        VARIABLE_DEF, // see OPERATOR_CALL

        FUNCTION_CALL, // expect Arguments and Statements
        CODE_BLOCK, // special case of function call; expect Arguments and Statements
        OPERATOR_CALL, // special case of function call; name: operator symbol/converted literal (e.g. ->  #_plusassign  (listed in operatorsHierarchyInternal))
        // the case of VARIABLE DEF //
        // returnType: variable type; name: "="; TODO add description for STRUCT
        // arg0: name of the variable (String)
        // arg1: (optional) assigned value, either LITERAL or FUNCTION_CALL or another OPERATOR CALL
        // arg2: (if STRUCT) struct identifier (String)

        //STATEMENT, // equations that needs to be eval'd; has returnType of null?; things like "(3 + 4) * 12" are leaves; "(i + 1) % 16" is not (it calls variable 'i', which _is_ a leaf)

        LITERAL_LEAF, // literals, also act as a leaf of the tree; has returnType of null
        VARIABLE_LEAF, // has returnType of null; typical use case: somefunction(somevariable) e.g. println(message)
        GOTO_LABEL_LEAF // self-explanatory
    }
    enum class ReturnType {
        VOID, BOOL, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE, STRUCT,
        VOID_PTR, BOOL_PTR, CHAR_PTR, SHORT_PTR, INT_PTR, LONG_PTR, FLOAT_PTR, DOUBLE_PTR, STRUCT_PTR,

        VARARG
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