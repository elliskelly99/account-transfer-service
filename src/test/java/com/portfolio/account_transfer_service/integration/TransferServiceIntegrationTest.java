package com.portfolio.account_transfer_service.integration;

import com.portfolio.account_transfer_service.entity.Account;
import com.portfolio.account_transfer_service.repository.AccountRepository;
import com.portfolio.account_transfer_service.repository.TransferRepository;
import com.portfolio.account_transfer_service.service.AccountService;
import com.portfolio.account_transfer_service.service.TransferService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
public class TransferServiceIntegrationTest {
    @Autowired
    TransferService transferService;
    @Autowired
    TransferRepository transferRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    AccountService accountService;

    /*
        Integration testing issues:

        During my integration test to ensure that the happyPathTransfer persisted in the database
        with the correct account balances for the sender and receiver, I ran into an issue where I believed
        my business logic in the service was correct. My unit test supported this
        since it simulated that the transfer service should debit and credit the sender and receiver
        accounts on the happy path.

        I noticed that I was asserting against account references that reflected the balances before
        the transfer service executed and persisted the transfer.

        I resolved this by fetching updated references for the sender and receiver
        through findById(). This ensured I was asserting against newly fetched Java objects
        representing the persisted account balances rather than the outdated references.

        Design decisions:
            - Using in memory h2db
                 Cons:
                    - DB syntax differences from h2 db and other db providers can make our integration test at best
                    a guess since we can't ensure the transfer service will perform the same.
                 pros:
                    - portability, h2 db can run directly in memory meaning it can run directly from the jar file.




    */

    /*todo:
        Future implementations:
            - Integration with testContainers:
                pros:
                    -Allows for direct behavior expectations from the db we point to
                Cons:
                    - We will need docker installed any machine running our applicaiton
        Reference:
            https://medium.com/@mesfandiari77/goodbye-h2-the-ultimate-guide-to-integration-testing-in-spring-boot-with-testcontainers-d98278a83d2e
    */

    @Test
    public void happyPathTransfer(){
        Account sender = new Account();
        sender.setBalance(new BigDecimal("50.00"));
        sender.setOwnerName("Sender Account");

        Account receiver = new Account();
        receiver.setOwnerName("Receiver Account");
        receiver.setBalance(new BigDecimal("0.00"));

        accountRepository.save(sender);
        accountRepository.save(receiver);

       Long senderId = sender.getId();
       Long receiverId = receiver.getId();

        accountRepository.flush();
        entityManager.clear();

        sender = accountRepository.findById(senderId).orElseThrow(()->
                new IllegalArgumentException("senderId not found"));

        receiver= accountRepository.findById(receiverId).orElseThrow(() ->
                new IllegalArgumentException("receiverId not found"));


        transferService.executeTransfer(senderId, receiverId, new BigDecimal("5.00"));

        sender= accountRepository.findById(senderId).orElseThrow(()->
                new IllegalArgumentException("senderId not found"));

        receiver= accountRepository.findById(receiverId).orElseThrow(() ->
                new IllegalArgumentException("receiverId not found"));

        //ASSERT TRANSFER BALANCE
        assertTrue(sender.getBalance().compareTo(new BigDecimal("45.00")) == 0);
        assertTrue(receiver.getBalance().compareTo(new BigDecimal("5.00")) == 0);

    }

    @Test
    public void senderNotEnoughFundsTransfer(){
        Account sender = new Account();
        sender.setBalance(new BigDecimal("2.00"));
        sender.setOwnerName("Sender Account");

        Account receiver = new Account();
        receiver.setOwnerName("Receiver Account");
        receiver.setBalance(new BigDecimal("0.00"));

        accountRepository.save(sender);
        accountRepository.save(receiver);

        Long senderId = sender.getId();
        Long receiverId = receiver.getId();

        accountRepository.flush();
        entityManager.clear();

        sender= accountRepository.findById(senderId).orElseThrow(()->
                new IllegalArgumentException("senderId not found"));

        receiver= accountRepository.findById(receiverId).orElseThrow(() ->
                new IllegalArgumentException("receiverId not found"));



        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
                    transferService.executeTransfer(senderId, receiverId, new BigDecimal("5.00"));
                });

        sender= accountRepository.findById(senderId).orElseThrow(()->
                new IllegalArgumentException("senderId not found"));

        receiver= accountRepository.findById(receiverId).orElseThrow(() ->
                new IllegalArgumentException("receiverId not found"));

        assertTrue(sender.getBalance().compareTo(new BigDecimal("2.00")) == 0);
        assertTrue(receiver.getBalance().compareTo(new BigDecimal("0.00")) == 0);
        assertEquals("Invalid amount: Sender does not have enough funds",illegalArgumentException.getMessage());
    }

    @Test
    public void senderInputInvalidAmountTransfer(){
        Account sender = new Account();
        sender.setBalance(new BigDecimal("2.00"));
        sender.setOwnerName("Sender Account");

        Account receiver = new Account();
        receiver.setOwnerName("Receiver Account");
        receiver.setBalance(new BigDecimal("0.00"));

        accountRepository.save(sender);
        accountRepository.save(receiver);

        Long senderId = sender.getId();
        Long receiverId = receiver.getId();

        accountRepository.flush();
        entityManager.clear();

        sender= accountRepository.findById(senderId).orElseThrow(()->
                new IllegalArgumentException("senderId not found"));

        receiver= accountRepository.findById(receiverId).orElseThrow(() ->
                new IllegalArgumentException("receiverId not found"));



       IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            transferService.executeTransfer(senderId, receiverId, new BigDecimal("-5.00"));
        });

        sender= accountRepository.findById(senderId).orElseThrow(()->
                new IllegalArgumentException("senderId not found"));

        receiver= accountRepository.findById(receiverId).orElseThrow(() ->
                new IllegalArgumentException("receiverId not found"));

        assertTrue(sender.getBalance().compareTo(new BigDecimal("2.00")) == 0);
        assertTrue(receiver.getBalance().compareTo(new BigDecimal("0.00")) == 0);
        assertEquals("Invalid amount: must be greater than 0",illegalArgumentException.getMessage());
    }
}
