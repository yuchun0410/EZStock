package com.ezstock.repository;

import com.ezstock.model.MoneyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MoneyAccountRepository extends JpaRepository<MoneyAccount, Long> {

    List<MoneyAccount> findByMemberIdOrderByKindAscIdAsc(Long memberId);

    Optional<MoneyAccount> findByIdAndMemberId(Long id, Long memberId);

    long deleteByIdAndMemberId(Long id, Long memberId);
}
