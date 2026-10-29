package com.portfolio.account_transfer_service.Service;

import com.portfolio.account_transfer_service.entity.Account;
import com.portfolio.account_transfer_service.entity.Transfer;
import com.portfolio.account_transfer_service.repository.AccountRepository;
import com.portfolio.account_transfer_service.repository.TransferRepository;
import com.portfolio.account_transfer_service.service.AccountService;
import com.portfolio.account_transfer_service.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

//UNIT TESTING
@ExtendWith(MockitoExtension.class)
public class TransferServiceTest {
   @InjectMocks
    TransferService transferService;
   @Mock
    TransferRepository transferRepository;
   @Mock
    AccountRepository accountRepository;
   @Mock
   AccountService accountService;


   /*
        Debugging story:
        Initially used @Spy on AccountService to test transfer behavior,
        but encountered a NullPointerException because the repository dependency wasn't initialized.

        Switched to @Mock to isolate TransferService and explicitly
        stubbed account retrieval behavior. Also corrected exception stubbing to include the expected exception message.
        Key takeaway: Isolate unit tests by mocking dependencies and explicitly defining their expected behavior.
    */

   @Test
    public void senderReceiverAccountTheSameTransfer(){
       Long senderId = 1L;
       Long receiverId = 1L;

       when(accountService.senderAndReceiverAccountsTheSame(senderId, receiverId)).thenReturn(true);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.transfer(senderId , receiverId , new BigDecimal("10.00"))
        );

        assertEquals("Not a valid sender and/or receiver, sender and receiver the same ID", exception.getMessage());
    }

    @Test
    public void senderNotFound(){
        Long senderId = 1L;
        Long receiverId = 2L;

        when(accountService.getAccountById(senderId)).thenThrow(new IllegalArgumentException("Account not found"));
        when(accountService.senderAndReceiverAccountsTheSame(senderId, receiverId)).thenReturn(false);

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                ()-> transferService.transfer(senderId,receiverId, new BigDecimal("50.0")));

        assertEquals("Account not found", illegalArgumentException.getMessage());

    }

    @Test
    public void receiverNotFound(){
        Long senderId = 1L;
        Long receiverId = 2L;

        when(accountService.getAccountById(senderId)).thenThrow(new IllegalArgumentException("Account not found"));
        when(accountService.senderAndReceiverAccountsTheSame(senderId, receiverId)).thenReturn(false);

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                ()-> transferService.transfer(senderId,receiverId, new BigDecimal("50.0")));

        assertEquals("Account not found", illegalArgumentException.getMessage());
    }

    @Test void invalidAmountLessThanZero(){
        Account sender = new Account();
        sender.setBalance(new BigDecimal("-5.00"));

        when(accountService.getAccountById(1L)).thenReturn(sender);
        when(accountService.getAccountById(2L)).thenReturn(new Account());

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                ()-> transferService.transfer(1L,2L, new BigDecimal("-5.00")));

        assertEquals("Invalid amount: must be greater than 0", illegalArgumentException.getMessage());
    }

    @Test void invalidAmountSenderNotEnoughFunds(){
        Account sender = new Account();
        sender.setBalance(new BigDecimal("-5.00"));

        when(accountService.getAccountById(1L)).thenReturn(sender);
        when(accountService.getAccountById(2L)).thenReturn(new Account());

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                ()-> transferService.transfer(1L,2L, new BigDecimal("5.00")));

        assertEquals("Invalid amount: Sender does not have enough funds", illegalArgumentException.getMessage());
    }
    @Test void happyPathTransfer(){
        Account sender = new Account();
        sender.setBalance(new BigDecimal("100.00"));

        Account receiver = new Account();
        receiver.setBalance(new BigDecimal("0.00"));

        when(accountService.getAccountById(1L)).thenReturn(sender);
        when(accountService.getAccountById(2L)).thenReturn(receiver);

        transferService.transfer(1L,2L, new BigDecimal("5.00"));

        verify(transferRepository).save(any(Transfer.class));
        assertEquals(new BigDecimal("95.00"), sender.getBalance());
        assertEquals(new BigDecimal("5.00"), receiver.getBalance());

    }

}
