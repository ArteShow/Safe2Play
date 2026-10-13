
package dev.arteshow.safe2play.mod;

import dev.arteshow.safe2play.mod.network.AuthPayloads;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Safe2PlayMod implements ModInitializer {
    public static final String MOD_ID = "safe2play";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(
                AuthPayloads.Challenge.TYPE,
                AuthPayloads.Challenge.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(
                AuthPayloads.Response.TYPE,
                AuthPayloads.Response.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(
                AuthPayloads.Challenge.TYPE,
                (payload, context) -> {
                    try {
                        String signature = ClientIdentity.signBase64(payload.message());

                        String response = ClientIdentity.publicKeyBase64()
                                + "." + signature;

                        ClientPlayNetworking.send(
                                new AuthPayloads.Response(response));
                    } catch (Exception exception) {
                        LOGGER.error(
                                "Safe2Play could not answer the authentication challenge.",
                                exception);
                    }
                });

        try {
            ClientIdentity.getKeyPair();
            LOGGER.info("Safe2Play client identity is ready.");
        } catch (Exception exception) {
            LOGGER.error("Could not create or load the Safe2Play identity.", exception);
        }
    }
}
