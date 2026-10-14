package com.portfolio.account_transfer_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;


    /*
        Transfer entity mapping to account entity
            - senderAccount and RecieverAccount

        ensured two foreign key constraint on account
            for example: Case when account is deleted(archived), we want the transfer history to persist

            TODO:
               implement a job that archives all deleted(disabled) account/transfer into an archived table

        Traded offs during implementation:
            Summary:
                - Not storing our offset for our transfer creation time:
                    - OffsetDateTime over Instant
                - perks:
                    -backend model and db simplicity;
                    clients handle time zone preferences, db creates a source of truth

            Transfer creation timestamps represent an authoritative global instant and are normalized to UTC.
            The backend exposes timestamps with an explicit UTC indicator,
            while clients convert them to the user's local time zone for display.
            We will not persist a separate original-offset column
            unless a concrete audit requirement justifies preserving the initiating user's local offset or time zone.
    */

@Entity
@Table(name = "transfer")
public class Transfer {

    protected Transfer(){};
    public Transfer(Account senderAccount, Account receiverAccount, BigDecimal amount){
        this.senderAccount = senderAccount;
        this.receiverAccount = receiverAccount;
        this.createdAt = Instant.now();
        this.transferAmount = amount;
        this.transferStatus = TransferStatus.NEW;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long transferId;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_account_id", nullable = false)
    private Account senderAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_account_id", nullable = false)
    private Account receiverAccount;

    //Guard checks at the java and database level to ensure we never pass any null values
    @Column(nullable = false)//sql level check
    @Positive(message = "Transaction amount must be greater than 0")//Adds validation at the persistence boundary when Bean Validation is triggered
    @NotNull(message = "Transaction amount is required") //Adds validation at the persistence boundary when Bean Validation is triggered
    private BigDecimal transferAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)//Needed so our DB will show our status as a string and not an index
    private TransferStatus transferStatus;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    /*
    TODO:
        eventually will add logic to give us the settledAtTime on coniditon that the Status is completed
        will create method on the logic flow of a transfer

     */

    public Long getId(){ return transferId;}

    public Account getSenderAccount(){return senderAccount;}
    public Account getReceiverAccount(){return receiverAccount;}

    public BigDecimal getTransferAmount(){return transferAmount;}

    public TransferStatus getTransferStatus(){return transferStatus;}
    public Instant getCreatedAt(){return createdAt;}


}
