package com.portfolio.account_transfer_service.repository;

import com.portfolio.account_transfer_service.entity.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class AccountRepositoryTest {
    /*
        JPA sliced testing to test persistence between AccountRepository and Account Entity

        Trade off:
        Scoped testing to the Hibernate,JPA, Repository layers over spinning up the full application context.

        findById is slightly more expensive than using getByReferenceId() because it explicitly retrieves the
           entity from the database. However, this test needs to test that the entity was actually persisted
        and can be retrieved from the database

        Perks:
            - Leaner (Scoped to the persistence layer)
            - Faster (No need to spin up full application context)
            - Explicitly verifies the persisted entity can be retrieved

    */
    @Autowired
    AccountRepository accountRepository;

    @Test
    void saveAccount() {
        Account account = new Account();
        account.setOwnerName("Ellis");
        account.setBalance(new BigDecimal("500"));

        accountRepository.save(account);
        long id = account.getId();

        Account savedAccount =  accountRepository.findById(id).orElseThrow(()->
                new AssertionError("ID not found"));


        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount.getOwnerName()).isEqualTo("Ellis");
        assertThat(savedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("500"));

    }
}
