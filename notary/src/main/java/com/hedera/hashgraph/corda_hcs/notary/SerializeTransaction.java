package com.hedera.hashgraph.corda_hcs.notary;

import net.corda.core.contracts.StateRef;
import net.corda.core.crypto.SecureHash;
import net.corda.core.transactions.CoreTransaction;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class SerializeTransaction {
    public final SecureHash txnId;
    public final List<StateRef> inputs;
    public final List<StateRef> refs;

    public SerializeTransaction(CoreTransaction txn) {
        this.txnId = txn.getId();
        this.inputs = txn.getInputs();
        this.refs = txn.getReferences();
    }

    public SerializeTransaction(SecureHash txnId, List<StateRef> inputs, List<StateRef> refs) {
        this.txnId = txnId;
        this.inputs = inputs;
        this.refs = refs;
    }

    // SERIALIZED FORMAT:
    // byte[32] transaction ID
    // int4 inputsLen
    // int4 refsLen
    //
    // for 0 .. inputsLen:
    //   byte[32] input transaction ID
    //   int4 state index
    //
    // for 0 .. refsLen:
    //   byte[32] ref transaction ID
    //   int4 state index

    public byte[] serialize() {
        // txn ID + lengths + (txn ID + index) * inputsLen + (txn ID + index) * refsLen
        int capacity = 32 + 8 + inputs.size() * 36 + refs.size() * 36;

        ByteBuffer out = ByteBuffer.allocate(capacity);

        System.out.println("allocated buffer with " + capacity + " bytes");

        txnId.putTo(out);
        out.putInt(inputs.size());
        out.putInt(refs.size());

        for (StateRef input : inputs) {
            input.getTxhash().putTo(out);
            out.putInt(input.getIndex());
        }

        System.out.println("serialized inputs");

        for (StateRef ref : refs) {
            ref.getTxhash().putTo(out);
            out.putInt(ref.getIndex());
        }

        System.out.println("serialized outputs");

        return out.array();
    }

    public static SerializeTransaction deserialize(byte[] data) {
        ByteBuffer in = ByteBuffer.wrap(data).asReadOnlyBuffer();

        byte[] txnIdBytes = new byte[32];
        in.get(txnIdBytes);

        SecureHash txnId = new SecureHash.SHA256(txnIdBytes);

        int inputsLen = in.getInt();
        int refsLen = in.getInt();

        List<StateRef> inputs = new ArrayList<>(inputsLen);
        List<StateRef> refs = new ArrayList<>(refsLen);

        for (int i = 0; i < inputsLen; i++) {
            inputs.add(readStateRef(in));
        }

        for (int i = 0; i < refsLen; i++) {
            refs.add(readStateRef(in));
        }

        return new SerializeTransaction(txnId, inputs, refs);
    }

    private static StateRef readStateRef(ByteBuffer in) {
        byte[] hash = new byte[32];
        in.get(hash);
        int index = in.getInt();

        return new StateRef(new SecureHash.SHA256(hash), index);
    }

    @Override
    public String toString() {
        return "SerializeTransaction{" +
                "txnId=" + txnId +
                ", inputs=" + inputs +
                ", refs=" + refs +
                '}';
    }
}
