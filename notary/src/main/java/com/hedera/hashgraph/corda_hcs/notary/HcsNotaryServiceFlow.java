package com.hedera.hashgraph.corda_hcs.notary;

import com.hedera.hashgraph.sdk.HederaStatusException;

import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.NotarisationPayload;
import net.corda.core.flows.NotarisationResponse;
import net.corda.core.transactions.CoreTransaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;

import co.paralleluniverse.fibers.Suspendable;

public abstract class HcsNotaryServiceFlow extends FlowLogic<Void> {
    private static final Logger logger = LoggerFactory.getLogger(HcsNotaryServiceFlow.class);

    protected final HcsNotaryService notaryService;
    protected final FlowSession otherPartySession;

    protected HcsNotaryServiceFlow(HcsNotaryService notaryService, FlowSession otherPartySession) {
        this.notaryService = notaryService;
        this.otherPartySession = otherPartySession;
    }

    @Suspendable
    @Override
    public Void call() throws FlowException {
        NotarisationPayload payload = otherPartySession.receive(NotarisationPayload.class)
                .unwrap(p -> p);

        validateTransaction(payload);

        CoreTransaction txn = payload.getCoreTransaction();

        logger.trace("received core txn: " + txn);

        long seqNumber;

        try {
            seqNumber = notaryService.submitTransactionSpends(txn);
        } catch (HederaStatusException e) {
            logger.error("error trying to submit transaction", e);
            throw new FlowException("an error occured while submitting an HCS message", e);
        }

        logger.trace("sequence number: " + seqNumber);

        while (!notaryService.checkTransaction(txn, seqNumber)) {
            FlowLogic.sleep(Duration.ofSeconds(5));
        }

        logger.trace("notarizing transaction " + txn.getId());
        otherPartySession.send(new NotarisationResponse(
                Collections.singletonList(notaryService.signTransaction(txn.getId()))));

        return null;
    }

    /**
     * Validate that the transaction in the given payload is valid for the current contract.
     *
     * @param payload
     * @throws FlowException
     */
    protected abstract void validateTransaction(NotarisationPayload payload) throws FlowException;
}
