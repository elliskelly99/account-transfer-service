package com.portfolio.account_transfer_service.repository;

import com.portfolio.account_transfer_service.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface TransferRepository extends JpaRepository <Transfer, Long> {


}
