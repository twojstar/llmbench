package com.twojstar.llmbench.data.tokenizer

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType

/** Android/JVM-backed exact local counter using the same o200k family as Docbench. */
object LocalTokenCounter : TokenCounter {
    const val ENCODING_LABEL = "o200k"

    override val encodingLabel: String = ENCODING_LABEL

    private val encoding by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.O200K_BASE)
    }

    override fun count(text: String): Int =
        if (text.isEmpty()) 0 else encoding.countTokensOrdinary(text)
}
