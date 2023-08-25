package com.sajjad.nfct3;

import java.io.UnsupportedEncodingException;

public class Common {

    public static String getText(byte[] payload, int languageCodeLength, String textEncoding) throws UnsupportedEncodingException {
        // Get the Text
        return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);

    }
}
