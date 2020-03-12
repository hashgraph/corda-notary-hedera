package com.hedera.hashgraph.corda_hcs.notary_demo;

import com.hedera.hashgraph.corda_hcs.notary.HcsNotaryService;
import com.hedera.hashgraph.corda_hcs.notary.HcsNotaryServiceFlow;

import net.corda.core.flows.FlowException;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.NotarisationPayload;
import net.corda.node.services.api.ServiceHubInternal;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;

/**
 * A simple example of a non-validating notary service.
 *
 * You must subclass `HcsNotaryService` in your own code for Corda to include it in the CordApp
 * package.
 */
public class ObligationNotaryService extends HcsNotaryService {
    private static final Logger logger = LoggerFactory.getLogger(ObligationNotaryService.class);

    public ObligationNotaryService(ServiceHubInternal serviceHubInternal, PublicKey publicKey) {
        super(serviceHubInternal, publicKey);
    }
}
