package com.hedera.hashgraph.corda_hcs.notary;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicCreateTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.mirror.MirrorClient;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicQuery;
import com.hedera.hashgraph.sdk.mirror.MirrorConsensusTopicResponse;
import com.hedera.hashgraph.sdk.mirror.MirrorSubscriptionHandle;
import com.typesafe.config.Config;

import net.corda.core.contracts.StateRef;
import net.corda.core.crypto.Crypto;
import net.corda.core.crypto.SecureHash;
import net.corda.core.crypto.SignableData;
import net.corda.core.crypto.SignatureMetadata;
import net.corda.core.crypto.TransactionSignature;
import net.corda.core.flows.FlowLogic;
import net.corda.core.flows.FlowSession;
import net.corda.core.flows.NotaryError;
import net.corda.core.flows.NotaryException;
import net.corda.core.flows.StateConsumptionDetails;
import net.corda.core.internal.notary.NotaryService;
import net.corda.core.node.ServiceHub;
import net.corda.core.transactions.CoreTransaction;
import net.corda.node.services.api.ServiceHubInternal;
import net.corda.node.services.config.NotaryConfig;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

public abstract class HcsNotaryService extends NotaryService {
    private static Logger logger = LoggerFactory.getLogger(HcsNotaryService.class);

    private final ServiceHubInternal serviceHubInternal;
    private final PublicKey publicKey;
    private final NotaryConfig notaryConfig;

    private final Client sdkClient;
    private final MirrorClient mirrorClient;

    private final AccountId operatorAccountId;

    @Nullable
    private ConsensusTopicId topicId;

    private final byte[] privateKeyBytes;

    @Nullable
    private final byte[] submitKeyBytes;

    @Nullable
    private final Ed25519PublicKey submitPublicKey;


    private final ConcurrentHashMap<StateRef, StateDestruction> stateDestructions = new ConcurrentHashMap<>();

    private long sequenceNumber = -1;

    @Nullable
    private MirrorSubscriptionHandle subscriptionHandle;

    public HcsNotaryService(ServiceHubInternal serviceHubInternal, PublicKey publicKey) {
        super();
        this.serviceHubInternal = serviceHubInternal;
        this.publicKey = publicKey;
        this.notaryConfig = Objects.requireNonNull(serviceHubInternal.getConfiguration().getNotary());

        Config extraConfig = Objects.requireNonNull(
                this.notaryConfig.getExtraConfig(),
                "required `extraConfig.hcs` key in notary config");

        final HcsConfig hcsConfig = new HcsConfig(extraConfig);

        this.operatorAccountId = hcsConfig.accountId;
        this.topicId = hcsConfig.topicId;

        this.privateKeyBytes = hcsConfig.privateKey;
        this.submitKeyBytes = hcsConfig.submitKey;

        this.submitPublicKey = submitKeyBytes != null ? Ed25519PublicKey.fromPrivateKey(submitKeyBytes) : null;

        sdkClient = (hcsConfig.testnet ? Client.forTestnet() : Client.forMainnet())
                .setOperatorWith(
                        operatorAccountId,
                        Ed25519PublicKey.fromPrivateKey(privateKeyBytes),
                        message -> SigningUtils.sign(privateKeyBytes, message));

        mirrorClient = new MirrorClient(
                hcsConfig.testnet
                        ? "hcs.testnet.mirrornode.hedera.com:5600"
                        : "hcs.mainnet.mirrornode.hedera.com:5600");
    }

    @NotNull
    @Override
    public PublicKey getNotaryIdentityKey() {
        return publicKey;
    }

    @NotNull
    @Override
    public ServiceHub getServices() {
        return serviceHubInternal;
    }

    @NotNull
    @Override
    public final FlowLogic<Void> createServiceFlow(@NotNull FlowSession otherPartySession) {
        return createNotaryServiceFlow(otherPartySession);
    }

    public abstract HcsNotaryServiceFlow createNotaryServiceFlow(@NotNull FlowSession otherSession);

    long submitTransactionSpends(CoreTransaction transaction) throws HederaStatusException {
        logger.trace("submitting transaction spends");

        ConsensusMessageSubmitTransaction msgTxn = new ConsensusMessageSubmitTransaction()
                .setTopicId(Objects.requireNonNull(topicId, "topic ID not set or created"));

        logger.trace("serializing corda transaction");

        msgTxn.setMessage(new SerializeTransaction(transaction).serialize());

        logger.trace("building transaction");

        Transaction hederaTxn = msgTxn.build(sdkClient);

        logger.trace("submitting transaction to Hedera");

        if (submitKeyBytes != null && submitPublicKey != null) {
            hederaTxn.signWith(submitPublicKey, m -> SigningUtils.sign(submitKeyBytes, m));
        }

        TransactionId txnId = hederaTxn.execute(sdkClient);

        logger.trace("transaction ID" + txnId);

        return txnId.getReceipt(sdkClient)
                .getConsensusTopicSequenceNumber();
    }

    boolean checkTransaction(CoreTransaction txn, long sequenceNumber) throws NotaryException {
        if (this.sequenceNumber < sequenceNumber) {
            return false;
        }

        HashMap<StateRef, StateConsumptionDetails> consumedStates = new HashMap<>();

        for (StateRef input : txn.getInputs()) {
            StateDestruction destruction = stateDestructions.get(input);

            if (destruction != null && !destruction.txnId.equals(txn.getId())) {
                consumedStates.put(input,
                        new StateConsumptionDetails(destruction.txnId, StateConsumptionDetails.ConsumedStateType.INPUT_STATE));
            }
        }

        for (StateRef ref : txn.getReferences()) {
            StateDestruction destruction = stateDestructions.get(ref);

            if (destruction != null) {
                consumedStates.put(ref,
                        new StateConsumptionDetails(destruction.txnId, StateConsumptionDetails.ConsumedStateType.REFERENCE_INPUT_STATE));
            }
        }

        if (!consumedStates.isEmpty()) {
            logger.debug("throwing error, consumed states: " + consumedStates);
            throw new NotaryException(new NotaryError.Conflict(txn.getId(), consumedStates), txn.getId());
        }

        return true;
    }

    TransactionSignature signTransaction(SecureHash txId) {
        SignableData signableData = new SignableData(txId, new SignatureMetadata(serviceHubInternal.getMyInfo().getPlatformVersion(), Crypto.findSignatureScheme(publicKey).getSchemeNumberID()));
        return serviceHubInternal.getKeyManagementService().sign(signableData, publicKey);
    }


    private void onMessage(MirrorConsensusTopicResponse msg) {
        logger.trace("received consensus message " + msg);

        SerializeTransaction txn = SerializeTransaction.deserialize(msg.message);

        logger.trace("received transaction " + txn);

        for (StateRef input : txn.inputs) {
            // don't overwrite with duplicate destructions
            stateDestructions.computeIfAbsent(input, key -> new StateDestruction(txn.txnId, msg.sequenceNumber));
        }

        sequenceNumber = msg.sequenceNumber;
    }

    @Override
    public void start() {
        if (topicId == null) {
            ConsensusTopicCreateTransaction txn = new ConsensusTopicCreateTransaction()
                    .setTopicMemo("Corda HCS Notary");

            if (submitPublicKey != null) {
                txn.setSubmitKey(submitPublicKey);
            }

            try {
                TransactionId txnId = txn.execute(sdkClient);
                topicId = txnId.getReceipt(sdkClient).getConsensusTopicId();
            } catch (HederaStatusException e) {
                throw new RuntimeException("failed to create topic", e);
            }
        }

        subscriptionHandle = new MirrorConsensusTopicQuery()
                .setTopicId(topicId)
                // for demo purposes we don't care about any states before the notary started
                .setStartTime(Instant.now())
                .subscribe(
                        mirrorClient,
                        this::onMessage,
                        e -> logger.error("error on HCS subscribe", e)
                );
    }

    @Override
    public void stop() {
        if (subscriptionHandle != null) {
            subscriptionHandle.unsubscribe();
        }
    }
}
