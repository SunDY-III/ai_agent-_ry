package com.zhishu.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Optional<Ticket> findByTicketNo(String ticketNo);
    List<Ticket> findByUserIdOrderByIdDesc(Long userId);
}
