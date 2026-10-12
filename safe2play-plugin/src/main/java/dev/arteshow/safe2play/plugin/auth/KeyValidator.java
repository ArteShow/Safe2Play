package dev.arteshow.safe2play.plugin.auth;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class KeyValidator {
    private KeyValidator() {}

    public static PublicKey decodeEd25519(String encoded)
            throws Exception {
        byte[] bytes = Base64.getDecoder().decode(encoded);

        if (bytes.length > 256) {
            throw new IllegalArgumentException("Public key is too large.");
        }

        return KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(bytes));
    }
}
