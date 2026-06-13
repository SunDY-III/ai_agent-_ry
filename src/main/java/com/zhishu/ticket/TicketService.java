package com.zhishu.ticket;

import com.zhishu.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private static final Set<String> VALID_CATEGORIES = Set.of("FAULT", "CONSULT", "PERMISSION", "PURCHASE");

    private final TicketRepository ticketRepository;
    private final AssignService assignService;

    @Transactional
    public Ticket create(Long userId, String title, String description) {
        Ticket t = new Ticket();
        t.setTicketNo("TK" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)));
        t.setUserId(userId);
        t.setTitle(title);
        t.setDescription(description);
        t.setStatus(TicketStatus.PENDING);
        return ticketRepository.save(t);
    }

    /** 分类落库：LLM 抽取的分类必须过白名单校验（工具调用可靠性兜底） */
    @Transactional
    public Ticket classify(String ticketNo, String category) {
        if (!VALID_CATEGORIES.contains(category)) {
            throw new BizException("非法分类: " + category + "，必须是 " + VALID_CATEGORIES);
        }
        Ticket t = mustGet(ticketNo);
        t.setCategory(category);
        return ticketRepository.save(t);
    }

    /** 派单：状态机校验 + 最小负载处理人 */
    @Transactional
    public Ticket assign(String ticketNo) {
        Ticket t = mustGet(ticketNo);
        t.getStatus().assertCanTransitTo(TicketStatus.PROCESSING);
        Long handlerId = assignService.pickLeastLoaded();
        if (handlerId == null) throw new BizException("当前无可用处理人");
        t.setAssigneeId(handlerId);
        t.setStatus(TicketStatus.PROCESSING);
        return ticketRepository.save(t);   // 并发冲突由 @Version 乐观锁兜住
    }

    @Transactional
    public Ticket transit(String ticketNo, TicketStatus target) {
        Ticket t = mustGet(ticketNo);
        t.getStatus().assertCanTransitTo(target);
        if (target == TicketStatus.RESOLVED || target == TicketStatus.CLOSED) {
            assignService.release(t.getAssigneeId());
        }
        t.setStatus(target);
        return ticketRepository.save(t);
    }

    public Ticket mustGet(String ticketNo) {
        return ticketRepository.findByTicketNo(ticketNo)
                .orElseThrow(() -> new BizException("工单不存在: " + ticketNo));
    }

    public List<Ticket> listByUser(Long userId) {
        return ticketRepository.findByUserIdOrderByIdDesc(userId);
    }
}
