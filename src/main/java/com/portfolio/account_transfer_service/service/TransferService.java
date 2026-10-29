package com.portfolio.account_transfer_service.service;

import com.portfolio.account_transfer_service.entity.Account;
import com.portfolio.account_transfer_service.entity.Transfer;
import com.portfolio.account_transfer_service.entity.TransferStatus;
import com.portfolio.account_transfer_service.repository.AccountRepository;
import com.portfolio.account_transfer_service.repository.TransferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferService {
    @Autowired
    TransferRepository transferRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    AccountService accountService;

    /*
        Transfer rules:
        - sender does not exist/not valid sender
           - try/catch if sender exist
        - Receiver does not exist/not valid receiver
             - Try/catch check if recover exist
        - sender is the same as receiver
            - Check account IDs to see if they match
     */

    @Transactional
    public void transfer(Long senderId, Long receiverId, BigDecimal amount){
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
                        for example: a generic NullPointerExpection does not give the developer
                        enough information if the sender ran out of money or  if  we're pointing
                        to the wrong sender account bug.
                        - Can bloat logs

        */
        Long sendertest =senderId;
        Long receiverTest = receiverId;

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
        if(senderHasValidFunds(senderAccount, amount).equals(false)){
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

        //Scenario 1: Happy path transfer
        Transfer transfer = new Transfer(senderAccount,receieverAccount,amount);

        BigDecimal oldSenderBalance = senderAccount.getBalance();
        BigDecimal newSenderBalance = senderAccount.getBalance().subtract(amount);
        transfer.getSenderAccount().setBalance(newSenderBalance);

        BigDecimal oldReceiverBalance = receieverAccount.getBalance();
        BigDecimal newReceiverBalance = receieverAccount.getBalance().add(amount);
        transfer.getReceiverAccount().setBalance(newReceiverBalance);

        //TODO:
        //question: save the transfer as failed in db if check fails
        checkIfDebitCreditSuccessful(senderAccount, receieverAccount, amount, oldSenderBalance, oldReceiverBalance,transfer);
        //retry queue here try to process the transfer with debit and credit after check fails

        transfer.setTransferStatus(TransferStatus.COMPLETE);
        //can fail here also we need to have a seperate check that the transfer persisted
        transferRepository.save(transfer);
        //if fail then add to retry queue
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
    private void checkIfDebitCreditSuccessful(Account senderAccount, Account receiverAccount, BigDecimal amount,
                                                BigDecimal oldSenderBalance, BigDecimal oldReceiverBalance, Transfer transfer){
        if(!oldSenderBalance.subtract(senderAccount.getBalance()).equals(amount)){
            transfer.setTransferStatus(TransferStatus.FAILED);
            throw new RuntimeException("Debit not successful");
        };
        if (!receiverAccount.getBalance().subtract(oldReceiverBalance).equals(amount)){
            transfer.setTransferStatus(TransferStatus.FAILED);
            throw new RuntimeException("Credit not successful");
        }
    }


    /*
        Expected true :
            return true if the sender balance is not equal or greater than amount than
            the amount they send
        failure:
            return false
     */

    private Boolean senderHasValidFunds(Account sender, BigDecimal amount){
        BigDecimal senderBalance = sender.getBalance();
        return (senderBalance.compareTo(amount)) >= 0;
    }

}
