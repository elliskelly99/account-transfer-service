package com.portfolio.account_transfer_service.Entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.aspectj.lang.annotation.RequiredTypes;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts" )
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)//Generation Auto for portability before DB decision made
    Long id;
    //require
    @NotBlank(message = "Owner name required")//application level
    @Column(name="ownerName", nullable = false)//Database level, can allow empty Strings, handles the literal Null
    String ownerName;
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
