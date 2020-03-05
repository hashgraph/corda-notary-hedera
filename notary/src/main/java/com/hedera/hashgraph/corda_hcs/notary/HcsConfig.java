package com.hedera.hashgraph.corda_hcs.notary;

import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import org.apache.shiro.codec.Hex;
import org.bouncycastle.math.ec.rfc8032.Ed25519;

import javax.annotation.Nullable;

public final class HcsConfig {
    /**
     * The account ID that will be sending HCS messages (and will create the topic if it doesn't
     * exist)
     */
    public final AccountId accountId;

    /**
     * The private key for the account.
     */
    public final byte[] privateKey;

    /**
     * The topic ID to use for HCS. If not given it will be created.
     */
    @Nullable
    public final ConsensusTopicId topicId;

    /**
     * The submit key to use with the given topic, or if it doesn't exist, the submit key to use
     * with the new topic.
     */
    @Nullable
    public final byte[] submitKey;

    public final boolean testnet;

    HcsConfig(Config config) {
        ConfigValue accountId = config.getValue("hcs.accountId");

        if (accountId.valueType() == ConfigValueType.STRING) {
            this.accountId = AccountId.fromString((String) accountId.unwrapped());
        } else if (accountId.valueType() == ConfigValueType.NUMBER) {
            this.accountId = new AccountId(((Number) accountId.unwrapped()).longValue());
        } else {
            throw new ConfigException.WrongType(accountId.origin(),
                    "hcs.accountId",
                    "string or number",
                    accountId.valueType().toString());
        }

        String privateKey = config.getString("hcs.privateKey");

        this.privateKey = Hex.decode(privateKey);

        // FIXME: use `Ed25519PrivateKey.fromString()` except Corda needs to upgrade Bouncycastle
        if (this.privateKey.length != Ed25519.SECRET_KEY_SIZE) {
            throw new ConfigException.BadValue(
                    config.origin(),
                    "hcs.privateKey",
                    "must be 64 hex characters; if you have a 96-character private key from "
                            + "the SDK then you must trim the first 32 characters");
        }

        if (config.hasPath("hcs.topicId")) {
            ConfigValue topicId = config.getValue("hcs.topicId");

            if (topicId.valueType() == ConfigValueType.STRING) {
                this.topicId = ConsensusTopicId.fromString((String) topicId.unwrapped());
            } else if (topicId.valueType() == ConfigValueType.NUMBER) {
                this.topicId = new ConsensusTopicId(((Number) topicId.unwrapped()).longValue());
            } else {
                throw new ConfigException.WrongType(topicId.origin(),
                        "hcs.topicId",
                        "string or number",
                        topicId.valueType().toString());
            }
        } else {
            this.topicId = null;
        }

        String submitKey = null;

        try {
            submitKey = config.getString("hcs.submitKey");
        } catch (ConfigException.Missing e) {
            // ignored
        }

        if (submitKey != null) {
            this.submitKey = Hex.decode(submitKey);

            if (this.submitKey.length != Ed25519.SECRET_KEY_SIZE) {
                throw new ConfigException.BadValue(
                        config.origin(),
                        "hcs.submitKey",
                        "must be 64 hex characters; if you have a 96-character private key "
                                + "from the SDK then you must trim the first 32 characters");
            }
        } else {
            this.submitKey = null;
        }

        boolean testnet = false;

        try {
            testnet = config.getBoolean("hcs.testnet");
        } catch (ConfigException.Missing e) {
            // ignored
        }

        this.testnet = testnet;
    }
}
