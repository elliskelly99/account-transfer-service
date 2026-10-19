package com.portfolio.account_transfer_service.repository;

import com.portfolio.account_transfer_service.entity.Account;
import com.portfolio.account_transfer_service.entity.Transfer;
import com.portfolio.account_transfer_service.entity.TransferStatus;
import org.hibernate.AssertionFailure;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
public class TransferRepositoryTest {
    @Autowired
    TestEntityManager entityManager;
    @Autowired
    TransferRepository transferRepository;

    @Autowired
    AccountRepository accountRepository;

    @Test
    void saveTransfer(){
        Account senderAccount = new Account();
        senderAccount.setOwnerName("testSender");

        Account recieverAccount = new Account();
        recieverAccount.setOwnerName("testReceiver");

        BigDecimal amount = new BigDecimal("5.00");

         Transfer transfer = new Transfer(senderAccount,
                recieverAccount,
                amount);

        accountRepository.save(senderAccount);
        accountRepository.save(recieverAccount);
        transferRepository.save(transfer);

        Long id = transfer.getId();
        Instant createdAt = transfer.getCreatedAt();

        transferRepository.flush();
        entityManager.clear();

        Transfer savedTransfer = transferRepository.findById(id).orElseThrow(()->
                new AssertionError("ID not found"));

        assertThat(savedTransfer.getId()).isEqualTo(id);
        assertThat(savedTransfer.getCreatedAt())
                .isCloseTo(createdAt, within(1, ChronoUnit.MICROS));        assertThat(savedTransfer.getTransferAmount()).isEqualTo(new BigDecimal("5.00"));
        assertThat(savedTransfer.getTransferStatus()).isEqualTo(TransferStatus.NEW);
        assertThat(savedTransfer.getSenderAccount().getOwnerName()).isEqualTo("testSender");
        assertThat(savedTransfer.getReceiverAccount().getOwnerName()).isEqualTo("testReceiver");
    }
}
