package com.ezstock.repository;

import com.ezstock.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    Optional<Member> findByGoogleSub(String googleSub);

    Optional<Member> findByLineUserId(String lineUserId);

    List<Member> findByLineUserIdIsNotNullOrderByIdAsc();
}
