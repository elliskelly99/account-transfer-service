package com.portfolio.account_transfer_service.repository;

import com.portfolio.account_transfer_service.entity.Account;
import jakarta.persistence.EntityManager;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

//JPA persistance intergation test
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
    @Autowired
    EntityManager entityManager;

    @Test
    void saveAccount() {

        Account account = new Account();
        account.setOwnerName("Ellis");
        account.setBalance(new BigDecimal("500"));

        accountRepository.save(account);
        long id = account.getId();

        //Ensure we don't have persistence from in memory cache from hibernate and repository level
        accountRepository.flush();//force any pending db operations in memory cache to be completed
        entityManager.clear();//clear the hibernate layer for all entities

        Account savedAccount =  accountRepository.findById(id).orElseThrow(()->
                new AssertionError("ID not found"));


        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount.getOwnerName()).isEqualTo("Ellis");
        assertThat(savedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("500"));

    }

    @Test
    void balanceIsNegative() {

        Account account = new Account();
        account.setOwnerName("Ellis");
        account.setBalance(new BigDecimal("-500.00"));

        accountRepository.save(account);
        long id = account.getId();

        //Ensure we don't have persistence from in memory cache from hibernate and repository level
        accountRepository.flush();//force any pending db operations in memory cache to be completed
        entityManager.clear();//clear the hibernate layer for all entities

        Account savedAccount =  accountRepository.findById(id).orElseThrow(()->
                new AssertionError("ID not found"));


        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount.getOwnerName()).isEqualTo("Ellis");
        assertThat(savedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("-500"));
    }

    @Test
    void balanceIsNull() {

        Account account = new Account();
        account.setOwnerName("Ellis");
        account.setBalance(null);

        accountRepository.save(account);//at this level it should fell
        long id = account.getId();

        //Ensure we don't have persistence from in memory cache from hibernate and repository level
        Exception exceptionPersistanceFail = assertThrows(ValidationException.class,
                () -> accountRepository.flush());
        entityManager.clear();//clear the hibernate layer for all entities
       Optional<Account> failedSavedAccount = accountRepository.findById(id);
        assertThat(exceptionPersistanceFail).isNotNull();
    }
}
