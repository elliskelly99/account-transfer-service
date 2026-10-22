package com.portfolio.account_transfer_service.Service;

import com.portfolio.account_transfer_service.entity.Account;
import com.portfolio.account_transfer_service.entity.Transfer;
import com.portfolio.account_transfer_service.repository.AccountRepository;
import com.portfolio.account_transfer_service.repository.TransferRepository;
import com.portfolio.account_transfer_service.service.TransferService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransferServiceTest {
   @InjectMocks
    TransferService transferService;

   @Mock
    AccountRepository accountRepository;
   @Mock
    TransferRepository transferRepository;
    @Test
    public void senderReceiverAccountTheSameTransfer(){
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transferService.transfer(1L, 1L, new BigDecimal("10.00"))
        );

        assertEquals("Not a valid sender and/or receiverAccount", exception.getMessage());
    }

    @Test
    public void senderNotFound(){
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                ()-> transferService.transfer(1L,2L, new BigDecimal("50.0")));

        assertEquals("Sender Not found", illegalArgumentException.getMessage());

    }

    @Test
    public void receiverNotFound(){
        when(accountRepository.findById(1L)).thenReturn(Optional.of(new Account()));

        when(accountRepository.findById(2L)).thenReturn(Optional.empty());

        IllegalArgumentException illegalArgumentException= assertThrows(IllegalArgumentException.class,
                ()-> transferService.transfer(1L,2L,BigDecimal.TWO));

        assertEquals("Receiver Not found", illegalArgumentException.getMessage());
    }

    @Test void invalidAmountLessThanZero(){
        Account sender = new Account();
        sender.setBalance(new BigDecimal("-5.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(new Account()));

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                ()-> transferService.transfer(1L,2L, new BigDecimal("-5.00")));

        assertEquals("Invalid amount: must be greater than 0", illegalArgumentException.getMessage());
    }

    @Test void invalidAmountSenderNotEnoughFunds(){
        Account sender = new Account();
        sender.setBalance(new BigDecimal("-5.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(new Account()));

        IllegalArgumentException illegalArgumentException = assertThrows(IllegalArgumentException.class,
                ()-> transferService.transfer(1L,2L, new BigDecimal("5.00")));

        assertEquals("Invalid amount: Sender does not have enough funds", illegalArgumentException.getMessage());
    }
    @Test void happyPathTransfer(){
        Account sender = new Account();
        sender.setBalance(new BigDecimal("100.00"));

        Account receiver = new Account();
        receiver.setBalance(new BigDecimal("0.00"));

        when(accountRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(receiver));

        transferService.transfer(1L,2L, new BigDecimal("5.00"));
        verify(transferRepository).save(any(Transfer.class));
    }

}
