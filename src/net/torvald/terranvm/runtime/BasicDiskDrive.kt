package net.torvald.terranvm.runtime

import net.torvald.terrarum.virtualcomputer.tvd.*
import java.nio.charset.Charset

/**
 * Created by minjaesong on 2018-05-19.
 */
abstract class BasicDiskDrive(val vm: TerranVM, val disk: VirtualDisk) : VMPeripheralWrapper(0) {

    internal var currentFile: Int? = null
    internal var currentFilePosition = 0

    private val charset = TerranVM.charset

    internal val fileBuffer = ArrayList<Byte>()
    internal var fileBufferInUse = false
    internal var fileBufferType: Int = 0

    private val TRUE = -1
    private val FALSE = 0
    private val SUCCESS = 0
    private val FAILED = 1

    private val FILE = 1
    private val DIRECTORY = 2
    private val SYMLINK = 3


    private fun openFile(fileID: Int) {
        currentFile = fileID
        currentFilePosition = 0
        val entry = disk.entries[fileID]
        entry?.let {
            fileBuffer.clear()

            if (entry.contents is EntryFile) {
                (entry.contents as EntryFile).bytes.forEachBanks {
                    fileBuffer.addAll(it.asList())

                }

                fileBufferInUse = true
                fileBufferType = FILE
            }
            else if (entry.contents is EntryDirectory) {
                (entry.contents as EntryDirectory).forEach {
                    it.toLittle().forEach {
                        fileBuffer.add(it)
                    }
                }

                fileBufferInUse = true
                fileBufferType = DIRECTORY
            }
            else if (entry.contents is EntryFile) {
                openFile((entry.contents as EntrySymlink).target)
            }
        }
    }

    private fun flushFile() {
        val entry = disk.entries[currentFile]
        entry?.let {
            val oldEntry = VDUtil.getAsNormalFile(disk, currentFile!!)
            val newByteArray64 = ByteArray64(fileBuffer.size.toLong())
            fileBuffer.forEachIndexed { index, byte -> newByteArray64[index.toLong()] = byte }
            oldEntry.bytes = newByteArray64
        }
    }


    /**
     * Bytes 0..23: argument
     * Bytes 24..31: operation, see File Operations in the ReferenceBIOS
     */
    fun callInternal(arg: Int): Int? {
        when (arg.ushr(24)) {
            0 -> {
                val fileID = vm.readregInt(arg.and(0b111))
                if (currentFile == fileID) return null
                openFile(fileID)
            }
            1 -> {
                fileBufferInUse = false
                fileBufferType = 0
            }
            2 -> {
                flushFile()
            }
            3 -> rewindFully()
            4 -> {
                if (currentFile == null) return null
                else {
                    vm.writeregInt(arg.and(7), fileBuffer[currentFilePosition].toUint())
                    currentFilePosition++
                }
            }
            5 -> if (currentFile == null) return null
            else {
                if (currentFile == null) return null
                else {
                    fileBuffer[currentFilePosition] = arg.and(0xFF).toByte()
                    currentFilePosition++
                }
            }
            6 -> currentFilePosition += arg.and(0xFFFFFF)
            7 -> return disk.isReadOnly.toInt() // readOnly to individual file is not supported
            8 -> return (disk.entries[currentFile] != null).toInt()
            9 -> {
                if (currentFile == null) return null
                else {
                    val entry = disk.entries[currentFile!!]
                    if (entry == null) return FAILED
                    else {
                        try {
                            VDUtil.deleteFile(disk, currentFile!!)
                            return SUCCESS
                        }
                        catch (e: Exception) {
                            return FAILED
                        }
                    }
                }
            }
            10 -> {
                if (currentFile == null || currentFile == 0) return null
                else {
                    val entry = disk.entries[currentFile!!]
                    return entry?.parentEntryID // returns null if the entry does not exist
                }
            }
            11 -> {
                val entry = disk.entries[currentFile]
                if (entry?.contents is EntryFile) return FILE
                else if (entry?.contents is EntryDirectory) return DIRECTORY
                else if (entry?.contents is EntrySymlink) return SYMLINK
                else return null
            }
            12 -> {
                val entry = disk.entries[currentFile]
                if (entry?.contents !is EntryFile) return null
                else {
                    return entry.contents.getSizePure().toInt() // works up to 2 GB
                }
            }
            13 -> {
                val newFile = VDUtil.createNewBlankFile(disk, currentFile!!, 0L, "", charset)
                fileBufferInUse = true
                fileBufferType = FILE
                currentFilePosition = 0
                fileBuffer.clear()
                return newFile
            }
            14 -> {
                try {
                    val newFile = VDUtil.addDir(disk, currentFile!!, "".toByteArray(charset))
                    return newFile
                }
                catch (e: Exception) {
                    return null
                }
            }
            15 -> {
                val entry = disk.entries[currentFile]
                if (entry != null) {
                    entry.modificationDate = TODO("game date")
                    return SUCCESS
                }

                return null
            }
            16 -> {
                val entry = disk.entries[currentFile]
                if (entry != null) {
                    val stringPayload = entry.filename
                    val stringPointer = vm.malloc(stringPayload.size)

                    System.arraycopy(stringPayload, 0, vm.memory, stringPointer.memAddr, stringPayload.size)

                    return stringPointer.memAddr
                }

                return null
            }
            17 -> {
                val entry = disk.entries[currentFile]
                if (entry != null) {
                    val stringPointer = vm.readregInt(arg.and(0b111))
                    val sb = StringBuilder()
                    while (true) {
                        val byte = vm.memory[stringPointer]
                        sb.append(byte.toChar())
                        if (byte == 0.toByte()) break
                    }

                    entry.filename = sb.toString().toByteArray(charset)

                    return SUCCESS
                }

                return null
            }
        }


        return null
    }


    override fun call(arg: Int) {
        // write to registry #r, call(some other arg)
    }

    override fun inquireBootstrapper(): ByteArray? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun Boolean.toInt() = if (this) TRUE else FALSE

    abstract fun rewindBy(amount: Int)
    abstract fun rewindFully()
    abstract fun fastForwardBy(amount: Int)
    abstract fun writeOneByte(byte: Int)
}