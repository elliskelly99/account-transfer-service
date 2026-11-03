
package com.portfolio.account_transfer_service.integration;

import com.portfolio.account_transfer_service.entity.Account;
import com.portfolio.account_transfer_service.repository.AccountRepository;
import com.portfolio.account_transfer_service.repository.TransferRepository;
import com.portfolio.account_transfer_service.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class TransferRollbackIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @MockitoSpyBean
    private TransferRepository transferRepository;

    @Test
    void transferRollsBackWhenPersistenceFailsAfterAccountUpdates() {

        Account sender = new Account();
        sender.setOwnerName("Sender");
        sender.setBalance(new BigDecimal("5.00"));

        Account receiver = new Account();
        receiver.setOwnerName("Receiver");
        receiver.setBalance(new BigDecimal("0.00"));

        Long senderId = accountRepository.save(sender).getId();
        Long receiverId = accountRepository.save(receiver).getId();

        // Force transfer persistence to fail
        doThrow(new RuntimeException("Transfer save failed"))
                .when(transferRepository)
                .saveAndFlush(any());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> transferService.executeTransfer(
                        senderId,
                        receiverId,
                        new BigDecimal("5.00")
                )
        );

        // Assert: verify exception
        assertEquals("Transfer save failed", exception.getMessage());

        // Re-fetch accounts from database
        Account senderAfter = accountRepository.findById(senderId).orElseThrow();
        Account receiverAfter = accountRepository.findById(receiverId).orElseThrow();

        // Verify original balances were preserved
        assertEquals(0, senderAfter.getBalance()
                .compareTo(new BigDecimal("5.00")));
        assertEquals(0, receiverAfter.getBalance()
                .compareTo(new BigDecimal("0.00")));
    }
}
