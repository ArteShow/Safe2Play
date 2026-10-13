
package dev.arteshow.safe2play.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ChallengePayload(byte[] nonce)
        implements CustomPacketPayload {

    public static final Type<ChallengePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(
                    "safe2play", "challenge"
            ));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, ChallengePayload
            > CODEC = new StreamCodec<>() {
        @Override
        public ChallengePayload decode(RegistryFriendlyByteBuf buf) {
            byte[] nonce = new byte[32];
            buf.readBytes(nonce);
            return new ChallengePayload(nonce);
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buf,
                ChallengePayload payload
        ) {
            if (payload.nonce().length != 32) {
                throw new IllegalArgumentException(
                        "Challenge must be 32 bytes."
                );
            }
            buf.writeBytes(payload.nonce());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
