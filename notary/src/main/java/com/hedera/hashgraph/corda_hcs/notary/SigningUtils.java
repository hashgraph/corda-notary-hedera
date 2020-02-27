package com.hedera.hashgraph.corda_hcs.notary;

import com.google.protobuf.ByteString;
import com.hedera.hashgraph.proto.Key;
import com.hedera.hashgraph.proto.SignaturePair;
import com.hedera.hashgraph.sdk.crypto.PublicKey;

import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.bouncycastle.util.encoders.Hex;

public class SigningUtils {

    // FIXME (abonander) these are my own testnet account keys
    static final byte[] privateKeyBytes = Hex.decode("bffc5bc38cae07f381a5d5baa24086eb189b6f59f407ed87d7e3010814359843");
    static final byte[] submitKeyBytes = Hex.decode("9ae9d9e62f6f31154eb2ea6e4257828ace0575d97c03fa2dbfad7c20f5aa31f9");

    private static final byte[] publicKeyBytes = new byte[Ed25519.PUBLIC_KEY_SIZE];
    private static final byte[] submitPublicKeyBytes = new byte[Ed25519.PUBLIC_KEY_SIZE];

    static {
        Ed25519.generatePublicKey(privateKeyBytes, 0, publicKeyBytes, 0);
        Ed25519.generatePublicKey(submitKeyBytes, 0, submitPublicKeyBytes, 0);
    }

    static PublicKey publicKey = new Ed25519PublicKey(publicKeyBytes);
    static PublicKey submitPublicKey = new Ed25519PublicKey(submitPublicKeyBytes);

    // fixme: we can't use the `Ed25519PrivateKey` type in the Hedera SDK
    // because that uses `Ed25519PrivateKeyParameters` which don't appear in the version of
    // BouncyCastle that Corda *insists* on using (1.60).
    static byte[] sign(byte[] privateKeyBytes, byte[] message) {
        byte[] signature = new byte[Ed25519.SIGNATURE_SIZE];
        Ed25519.sign(privateKeyBytes, 0, message, 0, message.length, signature, 0);
        return signature;
    }

    private static class Ed25519PublicKey extends PublicKey {
        private final byte[] publicKeyBytes;

        private Ed25519PublicKey(byte[] publicKeyBytes) {
            this.publicKeyBytes = publicKeyBytes;
        }

        @Override
        public Key toKeyProto() {
            return Key.newBuilder().setEd25519(ByteString.copyFrom(publicKeyBytes)).build();
        }

        @Override
        public byte[] toBytes() {
            return publicKeyBytes;
        }

        @Override
        public SignaturePair.SignatureCase getSignatureCase() {
            return SignaturePair.SignatureCase.ED25519;
        }
    }
}
