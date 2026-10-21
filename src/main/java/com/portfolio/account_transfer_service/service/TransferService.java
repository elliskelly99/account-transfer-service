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
import java.util.Objects;

@Service
public class TransferService {
    @Autowired
    TransferRepository transferRepository;

    @Autowired
    AccountRepository accountRepository;

    /*
        Transfer rules:
        - sender does not exist/not valid sender
           - try/catch if sender exist
        - Receiver does not exist/not valid receiver
             - Try/catch check if recover exist
        - sender is the same as receiver
            - Check account IDs to see if they match try/catch statement
     */

    @Transactional
    private void transfer(Long senderId, Long receieverId, BigDecimal amount){
        /*Check if sender and receiver account exist
            Expected behavior:
                - Check that sender is not sending to themselves
                -
            Input failure:
                - Utilizing fail-fast principal:
                    - throw exception that sender/receiver not valid or amount
                    Cons:
                        - Can leak sensitive data in api response if we don't sanitize your response
                        correctly
                        - Generic exceptions can cause issues for developer,
                        for example: a generic NullPointerExpection does not give the developer
                        enough information if the sender ran out of money or  if  we're pointing
                        to the wrong sender account bug.

        */

        if(senderAndReceiverAccountsValid(senderId, receieverId).equals(false)){
            throw new IllegalArgumentException(("Not a valid sender and/or receiverAccount"));
        }

        Account senderAccount = accountRepository.findById(senderId).orElseThrow(() ->
                new IllegalArgumentException(("Sender Not found")));

        Account receieverAccount = accountRepository.findById(receieverId).orElseThrow(() ->
                new IllegalArgumentException(("Receiver Not found")));

        if(senderHasValidFunds(senderAccount, amount).equals(false)){
            throw new IllegalArgumentException(("Invalid amount"));
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

        BigDecimal newSenderBalance = senderAccount.getBalance().subtract(amount);
        transfer.getSenderAccount().setBalance(newSenderBalance);


        BigDecimal newReceiverBalance = receieverAccount.getBalance().add(amount);
        transfer.getReceiverAccount().setBalance(newReceiverBalance);

        transfer.setTransferStatus(TransferStatus.COMPLETE);
        transferRepository.save(transfer);


    }

    private Boolean senderAndReceiverAccountsValid(Long senderId, Long receiverId){
        /*
            Trade offs:
            Objects.equals()
            over
            senderId.equals() so we get a true/false return and avoids nullPointerExpection
            so we have a readable log to send over request that the sender/receiver are not valid

        */
        return !Objects.equals(senderId, receiverId);
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
