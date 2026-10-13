
package dev.arteshow.safe2play.mod.auth;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;

public final class KeyManager {
    private static final String ALGORITHM = "Ed25519";

    private final KeyPair keyPair;
    private final String publicKeyBase64;

    private KeyManager(KeyPair keyPair) {
        this.keyPair = keyPair;
        this.publicKeyBase64 = Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded());
    }

    public static KeyManager loadOrCreate() throws Exception {
        Path directory = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("safe2play");

        Files.createDirectories(directory);

        Path privateFile = directory.resolve("private.key");
        Path publicFile = directory.resolve("public.key");

        boolean hasPrivate = Files.exists(privateFile);
        boolean hasPublic = Files.exists(publicFile);

        if (hasPrivate != hasPublic) {
            throw new IOException(
                    "Only one Safe2Play key exists. Refusing to replace it."
            );
        }

        KeyPair pair;

        if (hasPrivate) {
            byte[] privateBytes = Base64.getDecoder().decode(
                    Files.readString(privateFile, StandardCharsets.UTF_8).trim()
            );
            byte[] publicBytes = Base64.getDecoder().decode(
                    Files.readString(publicFile, StandardCharsets.UTF_8).trim()
            );

            KeyFactory factory = KeyFactory.getInstance(ALGORITHM);

            pair = new KeyPair(
                    factory.generatePublic(
                            new X509EncodedKeySpec(publicBytes)
                    ),
                    factory.generatePrivate(
                            new PKCS8EncodedKeySpec(privateBytes)
                    )
            );
        } else {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance(ALGORITHM);

            pair = generator.generateKeyPair();

            String privateText = Base64.getEncoder()
                    .encodeToString(pair.getPrivate().getEncoded());
            String publicText = Base64.getEncoder()
                    .encodeToString(pair.getPublic().getEncoded());

            Files.writeString(
                    privateFile, privateText, StandardCharsets.UTF_8
            );

            try {
                setOwnerOnly(privateFile);
                Files.writeString(
                        publicFile, publicText, StandardCharsets.UTF_8
                );
            } catch (Exception exception) {
                Files.deleteIfExists(privateFile);
                Files.deleteIfExists(publicFile);
                throw exception;
            }

            setOwnerOnly(privateFile);
        }

        return new KeyManager(pair);
    }

    private static void setOwnerOnly(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(
                    file,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE
                    )
            );
        } catch (UnsupportedOperationException ignored) {
            // POSIX permissions are not supported on every operating system.
        }
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }
}
