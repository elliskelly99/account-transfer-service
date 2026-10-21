package com.portfolio.account_transfer_service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

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
    @Column(name = "balance", nullable = false)

    BigDecimal balance = new BigDecimal("0.0");
    @Version//Optimistic locking: Concurrency control, does not old strict database locking, conflcits are rare
    Long version;

    public Long getId(){ return id;}

    public String getOwnerName(){return ownerName;}
    public void setOwnerName(String ownerName){this.ownerName = ownerName;}

    public void setBalance(BigDecimal balance){this.balance = balance;}
    public BigDecimal getBalance(){return balance;}




}
