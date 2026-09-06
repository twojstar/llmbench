package com.twojstar.llmbench.data.tokenizer

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType

/** Local, reusable token counter for prompt/document tooling. */
internal object LocalTokenCounter {
    const val ENCODING_LABEL = "o200k"

    private val encoding by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.O200K_BASE)
    }

    fun count(text: String): Int =
        if (text.isEmpty()) 0 else encoding.countTokensOrdinary(text)
}
