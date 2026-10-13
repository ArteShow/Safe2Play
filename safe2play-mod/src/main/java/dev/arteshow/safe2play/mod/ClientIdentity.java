
package dev.arteshow.safe2play.mod;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class ClientIdentity {
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("safe2play");

    private static final Path PRIVATE_KEY = DIRECTORY.resolve("identity.pk8");
    private static final Path PUBLIC_KEY = DIRECTORY.resolve("identity.x509");

    private static KeyPair keyPair;

    private ClientIdentity() {
    }

    public static synchronized KeyPair getKeyPair() throws Exception {
        if (keyPair != null) {
            return keyPair;
        }

        Files.createDirectories(DIRECTORY);
        KeyFactory factory = KeyFactory.getInstance("Ed25519");

        if (Files.exists(PRIVATE_KEY) && Files.exists(PUBLIC_KEY)) {
            byte[] privateBytes = Base64.getDecoder().decode(
                    Files.readString(PRIVATE_KEY).trim());
            byte[] publicBytes = Base64.getDecoder().decode(
                    Files.readString(PUBLIC_KEY).trim());

            keyPair = new KeyPair(
                    factory.generatePublic(new X509EncodedKeySpec(publicBytes)),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes)));
            return keyPair;
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        keyPair = generator.generateKeyPair();

        Files.writeString(
                PRIVATE_KEY,
                Base64.getEncoder().encodeToString(
                        keyPair.getPrivate().getEncoded()));

        Files.writeString(
                PUBLIC_KEY,
                Base64.getEncoder().encodeToString(
                        keyPair.getPublic().getEncoded()));

        return keyPair;
    }

    public static String publicKeyBase64() throws Exception {
        return Base64.getEncoder().encodeToString(
                getKeyPair().getPublic().getEncoded());
    }

    public static String signBase64(String challengeBase64) throws Exception {
        byte[] challenge = Base64.getDecoder().decode(challengeBase64);

        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(getKeyPair().getPrivate());
        signature.update(challenge);

        return Base64.getEncoder().encodeToString(signature.sign());
    }
}
