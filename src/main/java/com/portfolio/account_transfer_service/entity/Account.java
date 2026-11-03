package com.portfolio.account_transfer_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Entity
@Table(name = "account" )
public class Account {

    //TODO: Id should be by the persons phone number so we can simply just pass the phone number for easy lookup in db and mapping on client end
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)//Generation Auto for portability before DB decision made
    Long id;
    //require
    @NotBlank(message = "Owner name required")//application level
    @Column(name="ownerName", nullable = false)//Database level, can allow empty Strings, handles the literal Null
    String ownerName;

    //TODO: ADD isActive flag for disabled accounts

    //default 0
    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    @NotNull(message = "Balance amount is required") //Adds validation at the persistence boundary when Bean Validation is triggered
    BigDecimal balance = new BigDecimal("0.0");

    @Version//Optimistic locking: Concurrency control, does not old strict database locking, conflicts are rare
    Long version;

    public Long getId(){ return id;}

    public String getOwnerName(){return ownerName;}
    public void setOwnerName(String ownerName){this.ownerName = ownerName;}

    /**
     * Sets the account balance directly.
     * <p>
     * Intended for use only by TransferService and tests. Other callers
     * should not change balances through this method.
     *
     * @param balance the new balance
     */
    public void setBalance(BigDecimal balance){this.balance = balance;}
    public BigDecimal getBalance(){return balance;}




}
