package dev.arteshow.safe2play.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public final class AuthPayloads {
    private AuthPayloads() {
    }

    public record Challenge(String message) implements CustomPacketPayload {
        public static final Type<Challenge> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
                "safe2play", "challenge"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Challenge> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                Challenge::message,
                Challenge::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Response(String message) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(Identifier.fromNamespaceAndPath(
                "safe2play", "response"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Response> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8,
                Response::message,
                Response::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
