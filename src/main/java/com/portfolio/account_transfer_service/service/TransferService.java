package com.portfolio.account_transfer_service.service;

import com.portfolio.account_transfer_service.entity.Account;
import com.portfolio.account_transfer_service.entity.Transfer;
import com.portfolio.account_transfer_service.entity.TransferStatus;
import com.portfolio.account_transfer_service.repository.TransferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountService accountService;

    @Autowired
    public TransferService(
            TransferRepository transferRepository,
            AccountService accountService) {
        this.transferRepository = transferRepository;
        this.accountService = accountService;
    }
    /*
        Transfer rules:
        - sender does not exist/not valid sender
           - try/catch if sender exist
        - Receiver does not exist/not valid receiver
             - Try/catch check if recover exist
        - sender is the same as receiver
            - Check account IDs to see if they match
     */

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Transfer executeTransfer(Long senderId, Long receiverId, BigDecimal amount){
        /*Check if sender and receiver account exist
            Expected behavior:
                - Check that sender is not sending to themselves
                -
            Input failure:
                - Utilizing fail-fast principal:
                    - throw exception that sender/receiver not valid or amount
                    Pros:
                        -  These specific validation exceptions can be captured and
                         routed to a downstream monitoring service via telemetry data to
                          detect and alert on unusual or potentially fraudulent transfer patterns.
                    Cons:
                        - Can leak sensitive data in api response if we don't sanitize your response
                        correctly
                        - Generic exceptions can cause issues for developer,
                        for example: a generic NullPointerException does not give the developer
                        enough information if the sender ran out of money or  if  we're pointing
                        to the wrong sender account bug.
                        - Can bloat logs

        */

        if(accountService.senderAndReceiverAccountsTheSame(senderId, receiverId)){
            throw new IllegalArgumentException(("Not a valid sender and/or receiver, sender and receiver the same ID"));
        }

        Account senderAccount = accountService.getAccountById(senderId);

        Account receieverAccount = accountService.getAccountById(receiverId);


        if(amount == null || amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException(("Invalid amount: must be greater than 0"));
        }
        if(amount.scale() < 0|| amount.scale() > 2){
            throw new IllegalArgumentException(("Invalid amount: \n valid amounts: 10.50, 10.5, 10"));
        }
        if(amount.precision() > 19){
            throw new IllegalArgumentException(("Invalid amount: precision must be less than 19"));
        }


        //FAILURE: CONCURRENCY IF SENDER IS BEING DEBITED MULTIPLE TIMES
        if(!senderHasValidFunds(senderAccount, amount)){
            throw new IllegalArgumentException(("Invalid amount: Sender does not have enough funds"));
        }

        /*
            Design Decision:
                - Let the Transfer entity act as the relationship owner to manage fund movements and account associations.

            Pros:
                - Single Point of update: Unidirectional updates ensure the Transfer entity is the sole source of truth,
                  making it easier to track and audit when changes are made to the sender, receiver, and transfer records.
                - Decoupled Domain Models: Maintains the Many-to-One relationship on both sender and receiver without
                  bloating the Account entity with collection fields.
                - Encapsulation: The entire state of the operation is captured in a single database row, making execution
                  flows easier to trace.
                - No Synchronization Boilerplate: Eliminates the need for defensive helper functions to prevent data drift
                  between Account and Transfer objects in the persistence context.

            Cons:
                - Object Navigation Overhead: Working with associated entity graphs requires navigating managed references,
                  which can slightly increase debugging complexity.
                    -> Fix: Document this architecture for the team. Ensure developers do not introduce accidental bidirectional
                       mappings or side - effect updates to Accounts during testing or future service expansions.
                - Lacks Inverse Navigability: Unable to traverse directly back to a transaction history from an Account object instance.
                    -> Fix: Implement custom JPQL/derived query methods in the TransferRepository to fetch transfers by a given Account.
         */

        Transfer transfer = new Transfer(senderAccount,receieverAccount,amount);

        BigDecimal newSenderBalance = senderAccount.getBalance().subtract(amount);
        senderAccount.setBalance(newSenderBalance);

        BigDecimal newReceiverBalance = receieverAccount.getBalance().add(amount);
        receieverAccount.setBalance(newReceiverBalance);


        //TODO:
        //question: save the transfer as failed in db if check fails

        // Design flaw: checkIfDebitCreditSuccessful(senderAccount, receieverAccount, amount, oldSenderBalance, oldReceiverBalance,transfer);
        // Solution 1: flush for hibernate sends the updates to account to Db account to the database entityManager.flush();

        // solution 2:
        // design around concurrency situation: stale reads on credited account

        //TODO: retry queue here try to process the transfer with debit and credit after check fails

        transfer.setTransferStatus(TransferStatus.COMPLETE);

        //can fail here also we need to have a separate check that the transfer persisted
        return transferRepository.saveAndFlush(transfer);

        //if fail then add to retry queue
    }
    private boolean senderHasValidFunds(Account sender, BigDecimal amount){
        BigDecimal senderBalance = sender.getBalance();
        return (senderBalance.compareTo(amount)) >= 0;
    }

    /*
    * Todo:
    *  how will I handle retries of failed transfers?
    *Retry Queue?
    *
    *
    *  Future implementation:
    * DLQ:
    * Maybe some type of dead letter queue (KAFKA)
    *
    *
    *
    *
    *
    *
    *
    *
┌─────────────┐
│   Message   │
│   Arrives   │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│  Process Message│
└──────┬──────────┘
       │
       ├── Success ──▶ Continue
       │
       └── Failure ──▶ ┌──────────────┐
                        │  Retry Queue │
                        └──────┬───────┘
                               │
                    ┌──────────┼──────────┐
                    │          │          │
              Retry 1    Retry 2    Retry 3
                    │          │          │
                    └──────────┼──────────┘
                               │
                    ┌──────────┴──────────┐
                    │                     │
              Success              Max Retries
                    │                     │
                    │                     ▼
                    │              ┌─────────────┐
                    │              │     DLQ     │
                    │              └─────────────┘
                    │
                    ▼
              Continue
    * */

    /*
        design 1.0:
            Design flaw:
                - Redundant test that checks that the math is correct for the accounts but does not actually test if the accounts persistence took place
                    private void checkIfDebitCreditSuccessful(Account senderAccount, Account receiverAccount, BigDecimal amount,
                                                                BigDecimal oldSenderBalance, BigDecimal oldReceiverBalance, Transfer transfer){
                        if(!oldSenderBalance.subtract(senderAccount.getBalance()).equals(amount)){
                            transfer.setTransferStatus(TransferStatus.FAILED);
                            transferRepository.save(transfer);
                            throw new RuntimeException("Debit not successful");
                        }
                        if (!receiverAccount.getBalance().subtract(oldReceiverBalance).equals(amount)){
                            transfer.setTransferStatus(TransferStatus.FAILED);
                            transferRepository.save(transfer);
                            throw new RuntimeException("Credit not successful");
                        }
                }
         design 2.0:
           public void checkIfDebitCreditSuccessful(Long senderAccountId, Long receiverAccountId, BigDecimal amount,
                                              BigDecimal oldSenderBalance, BigDecimal oldReceiverBalance, Transfer transfer){
                entityManager.clear();
                Account senderAccount = accountService.getAccountById(senderAccountId);
                Account receiverAccount = accountService.getAccountById(receiverAccountId);

                if(!oldSenderBalance.subtract(senderAccount.getBalance()).equals(amount)){
                    transfer.setTransferStatus(TransferStatus.FAILED);
                    transferRepository.save(transfer);
                    throw new RuntimeException("Debit not successful");
                }
                if (!receiverAccount.getBalance().subtract(oldReceiverBalance).equals(amount)){
                    transfer.setTransferStatus(TransferStatus.FAILED);
                    transferRepository.save(transfer);
                    throw new RuntimeException("Credit not successful");
                }
            }

            # Report: Rollback test for TransferService.executeTransfer

            ## Expected test case
            **Name:** `failedDebitRollsBackBalances` (in `TransferRollbackIntegrationTest`)

            **Goal:** Prove that when a failure occurs after the sender debit and receiver credit have been
            flushed to the DB, the `@Transactional` boundary rolls everything back.

            **Setup:** Sender = 5.00, Receiver = 0.00, transfer amount = 5.00.

            **Expected result:**
            1. `executeTransfer` throws a `RuntimeException` ("Debit not successful").
            2. Re-fetching both accounts through `accountRepository` shows sender = 5.00 and receiver = 0.00.

            **Test rules:**
            - The test class is not `@Transactional` and does not use `@DataJpaTest`. Either would wrap the test in its own transaction, so the service's real commit/rollback would never happen.
            - Re-fetch through the repository after the exception. Objects held in the test still carry in-memory balances.
            - Stub only inside the test body so the stubbing doesn't leak to other tests.
            - Rollback tests live in their own class so the spy doesn't affect the happy-path and validation tests.

    ## Attempts and failures

    | # | Attempt | Result | Why |
    |---|---------|--------|-----|
    | 1 | `when(receiverAccount.getBalance().add(any())).thenReturn(...)` | Won't compile or work | The variables only exist inside the service. The account is a real entity and `BigDecimal` is final, so it can't be mocked. Faking the math also leaves nothing to roll back. |
    | 2 | Spy `transferRepository.saveAndFlush` to throw | Proves rollback only | It fires after `checkIfDebitCreditSuccessful`, so it can't test the check. |
    | 3 | Spy `accountService.getAccountById` to return a stale balance on the re-fetch | Valid in theory, fragile | Depends on the call order (4 calls) and stubbing real vs fake per call. |
    | 4 | `@Spy` on `TransferService` | Wrong | Plain Mockito `@Spy` creates an object outside the Spring container, so injected beans are unaffected. |
    | 5 | `@SpyBean` | Not available | Deprecated in Spring Boot 3.4 and removed in 4.0. Use `@MockitoSpyBean`. |
    | 6 | `doThrow(...).when(transferService.checkIfDebitCreditSuccessful(...))` | Wrong shape | The spy goes in `.when(spy)`, and the void method call comes after it. |
    | 7 | `@MockitoSpyBean TransferService` + `doThrow().when(transferService).checkIfDebitCreditSuccessful(...)` | `NotAMockException` | The injected field isn't recognized as a Mockito spy. Likely causes: `@Autowired` still on the field, the wrong import, Spring below 6.2, or the `@Transactional` proxy wrapping the spy. **Cause not yet confirmed.** |
    | 8 | Stubbing the check to throw | Proves rollback, not detection | The real `clear()`, re-fetch, and comparison never run, so it doesn't verify the flush/clear logic. |

    ## Root cause: a design flaw, not a testing problem
    - `checkIfDebitCreditSuccessful` compares values the service just computed itself, so it can't fail on real inputs.
    - It was an attempt to detect concurrency problems. A single-threaded check can't see another transaction's overwrite.
    - Re-reading inside the same transaction only shows uncommitted state. It can't prove the commit succeeded.
    - `flush()` already throws if a write fails, so the re-read adds nothing.
    - `clear()` detaches every managed entity, which can leave stale objects and break lazy loading.
    - It does three jobs in one method (verify, save FAILED, throw), and it was made public only for testing.

    ## Bugs found
    - **`BigDecimal.equals` compares scale.** `10.5` is not equal to `10.50`, so valid transfers could be rejected with "Debit not successful". Fix: `compareTo(...) != 0`.
    - **The FAILED transfer is rolled back too.** It's saved in the same transaction as the exception that undoes everything, so it records nothing. Persisting it needs `REQUIRES_NEW`.

    ## Concepts clarified
    - `save()` hands the entity to the persistence context. `flush()` sends SQL, but the transaction is still open. Commit makes it permanent.
    - Hibernate dirty checking compares against its in-memory snapshot, not the DB rows. The database enforces isolation.
    - Rollback happens when an unchecked exception leaves a `@Transactional` method that was called from another bean.
    - Mockito stubs method calls on mocks and spies, not `if` statements. Use `doThrow().when(spy)` for void methods.
    - Catch `DataAccessException`, not `RuntimeException`, so your own bugs aren't relabeled as save failures.

    ## Solutions and decisions
    1. Delete `checkIfDebitCreditSuccessful`, plus the `flush()` and `clear()` added for it.
    2. Move the funds check into `Account.debit()` and `credit()`. Unit test with no mocks.
    3. Add `@Version` on `Account` for optimistic locking.
    4. Write a two-thread concurrency integration test: same sender, two simultaneous transfers, assert final balances.
    5. Rewrite the rollback test to spy `transferRepository.saveAndFlush` and throw after the balances change. This avoids spying on the `@Transactional` service.
    6. Later: try `PESSIMISTIC_WRITE` (lock accounts in ID order) and compare it to `@Version`.
    7. Later: retry queue / DLQ for transfers that lose an optimistic lock.
     */



    /*
        Expected true :
            return true if the sender balance is not equal or greater than amount than
            the amount they send
        failure:
            return false
     */



}
