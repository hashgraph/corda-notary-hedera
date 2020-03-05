package com.hedera.hashgraph.corda_hcs.notary_demo;

import com.hedera.hashgraph.corda_hcs.notary.HcsNotaryService;
import com.hedera.hashgraph.corda_hcs.notary.HcsNotaryServiceFlow;

import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.NotarisationPayload;
import net.corda.core.flows.NotaryError;
import net.corda.core.flows.NotaryException;
import net.corda.node.services.api.ServiceHubInternal;

import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;

public class ObligationNotaryService extends HcsNotaryService {
    public ObligationNotaryService(ServiceHubInternal serviceHubInternal, PublicKey publicKey) {
        super(serviceHubInternal, publicKey);
    }

    @Override
    public HcsNotaryServiceFlow createNotaryServiceFlow(@NotNull FlowSession otherSession) {
        return new HcsNotaryServiceFlow(this, otherSession) {
            @Override
            protected void validateTransaction(NotarisationPayload payload) throws FlowException {
                System.out.println("received transaction " + payload.getCoreTransaction());
            }
        };
    }
}
