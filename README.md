hedera-hcs-corda
================

This library provides an implementation of a Notary Service for the [Corda] distributed ledger
platform using the [Hedera Consensus Service (HCS)][HCS] to globally order transaction spends, 
providing Byzantine Fault Tolerant (BFT) protection against double-spends without running your own 
network like BFTSmart.

[Corda]: https://www.corda.net/
[HCS]: https://www.hedera.com/consensus-service/

Usage
---- 

### Installation

Add the following to your `build.gradle`: 
```groovy
repositories {
    mavenCentral()
}

dependencies {
    compile "com.hedera.hashgraph:corda-hcs-notary:0.0.1"
}
```

##### From Source

Follow the above step, additionally adding `mavenLocal()` to the `repositories {}` block of your 
`build.gradle` above `mavenCentral()`.

Clone this repository:
```bash
git clone https://github.com/hashgraph/hedera-hcs-corda
```

Enter the `notary` directory and install the package to your local Maven repository:
```bash
cd hedera-hcs-corda/notary
./gradlew install
```

### Creating a Notary

In your project, create a class extending `HcsNotaryService`:

```java
package com.mypackage;

public class MyNotaryService extends HcsNotaryService {
    public MyNotaryService(ServiceHubInternal serviceHubInternal, PublicKey publicKey) {
        super(serviceHubInternal, publicKey);
    }
}
```

Add the notary to your `task deployNodes(...) {}` configuration block and pass an 
Hedera account ID and private key to use:
```groovy
task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['jar']) {
    // ... 
    node {
        name "O=NotaryA,L=London,C=GB"
        notary = [
                validating: false,
                className: "com.my_package.MyNotaryService",
                extraConfig: [
                        hcs: [
                                // (required) the Hedera account ID that will pay for transactions
                                // can also be just the accountNum as an int
                                accountId: "0.0.####", 
                                
                                // (required) the hex-encoded Ed25519 private key for the given account
                                privateKey: "<private key string here>",

                                // (optional) the HCS topic ID to use
                                // if omitted, a new topic is created on startup
                                // can also be just the topicNum as an int
                                topicId: "0.0.####", 

                                // (optional) the Ed25519 private key for the HCS topic
                                // if a topic ID was not specified, this will be the submit key to 
                                // use with the one that will be created
                                // if not specified for an existing topic that requires one,
                                // flows will throw errors at runtime
                                submitKey: "<private key string here>",

                                // (optional) whether or not to connect to testnet instead of mainnet
                                // defaults to false
                                testnet: true | false
                        ]
                ]
        ]
    
        // these ports must be unique in the network configuration
        p2pPort 10002
        rpcSettings {
            address("localhost:10003")
            adminAddress("localhost:10043")
        }
    }
    // other nodes follow
}
```

That's it! Your network is now protected from double-spends with HCS.

### Creating a Validating Notary

Similar to the non-validating notary above, except you begin by setting `validating: true` in your 
notary node config and then override the appropriate methods:

```java
public class MyNotaryService extends HcsNotaryService {
    public MyNotaryService(ServiceHubInternal serviceHubInternal, PublicKey publicKey) {
        super(serviceHubInternal, publicKey);
    }
    
    @Override
    public HcsNotaryServiceFlow createNotaryServiceFlow(@NotNull FlowSession otherSession) {
        return new MyNotaryServiceFlow(this, otherSession);
    }
}
```

```java
public class MyNotaryServiceFlow extends HcsNotaryServiceFlow {
    @Override
    protected void validateTransaction(NotarisationPayload payload) throws FlowException {
        // validate the transaction in the context of the contract(s) running on your current network
        // throw a `NotaryError` if the validation fails
    }
}
```

More details on validating notary flows are available here: 
https://docs.corda.net/tutorial-custom-notary.html

Functional Overview
-------------------

This project provides an abstract base class extending `NotaryService` which by default functions
as a non-validating notary only, using HCS to prevent double-spends. However, the `HcsNotaryService`
class can also be extended to implement contract-specific validation to prevent
denial-of-state attacks from malicious parties on the network, as once states are recorded in HCS
as being consumed that record is permanent.

The notary flow proceeds as follows:

1. If implemented, the local validation routine is invoked for a transaction. This prevents faulty 
transactions from being recorded in HCS.

    * This step isn't strictly necessary if the network operator accepts having to resolve this 
    situation off-ledger: https://docs.corda.net/key-concepts-notaries.html#validation

2. The transaction hash and its state consumptions and references are serialized and submitted
as a single message to a preconfigured HCS topic (or one created on startup).

    * states are referenced only by their creating transaction hash and state index 
    (information that is useless to someone who was not a party to each referenced transaction) so 
    even though HCS messages are public, the privacy guarantees of Corda are preserved.
    
    * transactions are submitted wholesale to prevent a situation where two transactions
    are submitted at the same time with interleaved messages trying to consume the same states,
    which would prevent both transactions from executing and lock out all of their input states.
    
3. The consensus sequence number for the message is returned from Hedera in a transaction receipt
and stored.

4. Outside the DJVM, the notary service watches the HCS topic via a mirror node and records
the sequence number associated with every state destruction and also stores the sequence number
of the latest message.

5. Back inside the notary flow in the DJVM, the flow waits for the latest sequence number to reach 
that of the message it sent to HCS.

6. The flow then checks its input and reference states against the notary service's records.

    * If none of its input or reference states have been consumed, or if there are state 
    consumptions but the sequence numbers from those consumptions are greater than the sequence
    number of the submitted HCS message, it notarises the transaction.
    
    * Otherwise, it reports an error with the consumed states.

