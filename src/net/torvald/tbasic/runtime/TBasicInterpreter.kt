package net.torvald.tbasic.runtime

/**
 * Created by minjaesong on 2017-06-04.
 */
object TBasicInterpreter {

    private val stringMarker = '"'
    private val tokenSeparators = arrayOf(' ', ',', '(', ')')

    fun invokeREPL(line: String) {
        println(parseLine(line))
    }


    fun parseLine(line: String): List<String> {
        // allowed lines:
        //  TOKEN one_arg_only
        //  TOKEN(args, args,args , args )
        // "quoted line" is a thing

        val tokens = ArrayList<String>()

        var lineCtr = 0
        var lineCtrMax = line.lastIndex
        val tokenBuffer = StringBuffer()
        var isQuote = false

        while (lineCtr <= lineCtrMax) {
            val char = line[lineCtr]

            if (char == stringMarker) {
                isQuote = !isQuote

                // end quote; flush it
                if (!isQuote) {
                    tokens.add(tokenBuffer.toString())
                    tokenBuffer.setLength(0)
                    lineCtr++
                }
            }
            else {
                if (isQuote) {
                    tokenBuffer.append(char)
                }
                else {
                    if ((tokenSeparators.contains(char) || lineCtr == lineCtrMax)) {
                        if (lineCtr == line.lastIndex) { tokenBuffer.append(char) }

                        if (tokenBuffer.isNotEmpty()) {
                            tokens.add(tokenBuffer.toString())
                            tokenBuffer.setLength(0)
                            lineCtr++
                        }
                    }
                    else {
                        tokenBuffer.append(char)
                    }
                }
            }


            lineCtr++
        }

        return tokens
    }

}