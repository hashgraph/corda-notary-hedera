package com.hedera.hashgraph.corda_hcs.notary;

import net.corda.core.crypto.SecureHash;

public class StateDestruction {
    public final SecureHash txnId;
    public final long sequenceNumber;

    public StateDestruction(SecureHash txnId, long sequenceNumber) {
        this.txnId = txnId;
        this.sequenceNumber = sequenceNumber;
    }
}
