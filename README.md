# hedera-hcs-corda

This library provides an implementation of a Notary Service for the [Corda] distributed ledger
platform using the [Hedera Consensus Service (HCS)][HCS] to globally order transaction spends, 
providing Byzantine Fault Tolerant (BFT) protection against double-spends without running your own 
network like BFTSmart.

[Corda]: https://www.corda.net/
[HCS]: https://www.hedera.com/consensus-service/

### Functional Overview

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

