package com.myfun.funclass.ui.components

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decodes raw file bytes with strict UTF-8 first, automatically falling back to GB18030 (superset of GBK/GB2312)
 * so Windows Excel-exported `.csv` and legacy Chinese academic `.html` files import without garbled text.
 */
fun decodeTextSmart(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""
    // Strip UTF-8 BOM if present
    val slice = if (bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    ) {
        bytes.copyOfRange(3, bytes.size)
    } else {
        bytes
    }

    val strictUtf8 = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)

    return runCatching {
        strictUtf8.decode(ByteBuffer.wrap(slice)).toString()
    }.getOrElse {
        runCatching {
            String(slice, Charset.forName("GB18030"))
        }.getOrElse {
            String(slice, Charsets.UTF_8)
        }
    }
}
