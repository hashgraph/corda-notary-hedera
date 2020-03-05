package com.hedera.hashgraph.corda_hcs.notary;

import com.hedera.hashgraph.sdk.crypto.PublicKey;

import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.bouncycastle.util.encoders.Hex;

public class SigningUtils {
    // fixme: we can't use the `Ed25519PrivateKey` type in the Hedera SDK
    // because that uses `Ed25519PrivateKeyParameters` which don't appear in the version of
    // BouncyCastle that Corda *insists* on using (1.60).
    static byte[] sign(byte[] privateKeyBytes, byte[] message) {
        byte[] signature = new byte[Ed25519.SIGNATURE_SIZE];
        Ed25519.sign(privateKeyBytes, 0, message, 0, message.length, signature, 0);
        return signature;
    }
}
