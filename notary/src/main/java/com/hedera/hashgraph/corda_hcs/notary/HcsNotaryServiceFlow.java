package com.hedera.hashgraph.corda_hcs.notary;

import com.hedera.hashgraph.sdk.HederaStatusException;

import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.NotarisationPayload;
import net.corda.core.flows.NotarisationResponse;
import net.corda.core.transactions.CoreTransaction;

import java.time.Duration;
import java.util.Collections;

import co.paralleluniverse.fibers.Suspendable;

public class HcsNotaryServiceFlow extends FlowLogic<Void> {

    private final HcsNotaryService notaryService;
    private final FlowSession otherPartySession;

    public HcsNotaryServiceFlow(HcsNotaryService notaryService, FlowSession otherPartySession) {
        this.notaryService = notaryService;
        this.otherPartySession = otherPartySession;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        NotarisationPayload payload = otherPartySession.receive(NotarisationPayload.class)
                .unwrap(p -> p);

        CoreTransaction txn = payload.getCoreTransaction();

        System.out.println("received core txn: " + txn);

        long seqNumber;

        try {
            seqNumber = notaryService.submitTransactionSpends(txn);
        } catch (HederaStatusException e) {
            System.out.println("error trying to submit transaction" + e);
            throw new FlowException(e);
        }

        System.out.println("sequence number: " + seqNumber);

        while (!notaryService.checkTransaction(txn, seqNumber)) {
            FlowLogic.sleep(Duration.ofSeconds(5));
        }

        System.out.println("notarizing transaction " + txn.getId());
        otherPartySession.send(new NotarisationResponse(Collections.singletonList(notaryService.signTransaction(txn.getId()))));

        return null;
    }
}
