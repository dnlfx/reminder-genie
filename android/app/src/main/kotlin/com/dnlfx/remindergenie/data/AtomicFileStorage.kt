package com.dnlfx.remindergenie.data

import android.util.AtomicFile
import com.dnlfx.remindergenie.core.ItemStorage
import java.io.File
import java.io.FileNotFoundException

/** Saves the items file so a crash mid-write can never leave it half written. */
class AtomicFileStorage(file: File) : ItemStorage {
    private val atomic = AtomicFile(file)

    override fun read(): String? = try {
        atomic.readFully().toString(Charsets.UTF_8)
    } catch (e: FileNotFoundException) {
        null
    }

    override fun write(text: String) {
        val stream = atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            atomic.failWrite(stream)
            throw e
        }
    }
}
