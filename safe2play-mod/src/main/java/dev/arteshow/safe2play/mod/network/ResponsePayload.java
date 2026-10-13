
package dev.arteshow.safe2play.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ResponsePayload(byte[] signature)
        implements CustomPacketPayload {

    public static final Type<ResponsePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    "safe2play", "response"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, ResponsePayload
            > CODEC = new StreamCodec<>() {
        @Override
        public ResponsePayload decode(RegistryFriendlyByteBuf buf) {
            byte[] signature = new byte[64];
            buf.readBytes(signature);
            return new ResponsePayload(signature);
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buf,
                ResponsePayload payload
        ) {
            if (payload.signature().length != 64) {
                throw new IllegalArgumentException(
                        "Ed25519 signature must be 64 bytes."
                );
            }
            buf.writeBytes(payload.signature());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
