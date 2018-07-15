package net.torvald.terranvm.assets

object FreshNewParametreRAM {

    private val newPRAM = ByteArray(8192)

    init {
        newPRAM[0] = 8.toByte()
        newPRAM[1] = 9.toByte()
        newPRAM[2] = 12.toByte()
        newPRAM[3] = 13.toByte()
    }

    operator fun invoke() = newPRAM.clone()

}