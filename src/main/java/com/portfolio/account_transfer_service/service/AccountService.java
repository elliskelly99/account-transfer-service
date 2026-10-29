package com.portfolio.account_transfer_service.service;

import com.portfolio.account_transfer_service.entity.Account;
import com.portfolio.account_transfer_service.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AccountService {
    /*

        Input validation will be handled in the DTO to ensure no client sends invalid input and make unnecesary calls
        to the services and DB
     */
    @Autowired
    AccountRepository accountRepository;

    public Account getAccountById(Long id){
        return accountRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException(("Account not found")));
    }

    public Boolean senderAndReceiverAccountsTheSame(Long senderId, Long receiverId){
        /*
            Trade offs:
            Objects.equals()
            over
            senderId.equals() so we get a true/false return and avoids nullPointerExpection
            so we have a readable log to send over request that the sender/receiver are not valid

        */
        return Objects.equals(senderId, receiverId);
    }



}
