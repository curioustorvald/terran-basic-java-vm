package net.torvald.tbasic

import net.torvald.tbasic.runtime.compiler.simplec.TBasCC
import java.util.*

/**
 * Created by minjaesong on 2017-06-04.
 */

fun main(args: Array<String>) {
    val prg = """
int globalnum = 1<<10;
double floatnum = 3.14342f;
double scientificfloat = 1.e-2;

extern void testfuncdef (int *index, boolean* isSomething , double * someNumber, ...);

int main    () {
    if(true) {
        boolean s = true;
    }

    fprintf(stdout, "Hello, world!\n");
    return 0;
}
"""
    /*try {
        TBasCC(prg)
    }
    catch (gottaCatchEmAll: Exception) {

    }*/

    //val node = TBasCC.asTreeNode("""void testfn(int* index,boolean *isSomething,double * someNumber)""")
    //println(node)

    //val line = """return dup(foo("bar",0x24),null)"""
    //val line = """int foo"""
    //val node2 = TBasCC.asTreeNode(line, line)
    //println(node2)




    /*val equations = """int harambe = burr - argone * - argtwo + 42"""
    val eqNorm = """assignint(harambe,+(-(burr,*(argone,unaryminus(argtwo))),42))"""
    //println(TBasCC.preprocess(equations))
    val atre = """int printf(...)"""
    val node2 = TBasCC.asTreeNode(atre, atre)
    println(node2)*/




    //val line = """println( (a - b) / c * (d + e - f / g) );"""
    val line = /*"""
bool clack = true;

int main() {
    if (42 == x) {
        printf("arst");
    }
    while (42 == x) {
        printf("arst");
    }
}
"""*/"""
int main() {

    int bob_bailey = 43;

    forever {
        goto hell;

        if (true) {
            return 1;
        }

        return 2;
    }

    return 0;
}
"""

    val preprocessedLine = TBasCC.preprocess(line)

    println(preprocessedLine)

    val lineStructures = TBasCC.tokenise(preprocessedLine)

    lineStructures.forEach { println(it) }

    val tree = TBasCC.buildTree(lineStructures)

    println(tree)



}

