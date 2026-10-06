package com.portfolio.account_transfer_service.repository;

import com.portfolio.account_transfer_service.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AccountRepository extends JpaRepository<Account,Long> {
    /*
        By extending jpaRepository, we inherit these common methods:
            - Save()
            - findById()
            - findAll()
            - deleteById()

            <Account,Long>: Account Entity with Long primary key
     */
}
