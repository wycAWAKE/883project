package com.example.springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.springboot.dto.repair.*;
import com.example.springboot.entity.DormManager;
import com.example.springboot.mapper.DormManagerMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.springboot.vo.repair.RepairStaffOption;
import com.example.springboot.entity.RepairTicket;
import com.example.springboot.entity.RepairTicketHistory;
import com.example.springboot.mapper.RepairTicketMapper;
import com.example.springboot.mapper.RepairTicketHistoryMapper;
import com.example.springboot.repair.*;
import com.example.springboot.service.RepairTicketService;
import com.example.springboot.vo.repair.RepairHistoryView;
import com.example.springboot.vo.repair.RepairTicketView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RepairTicketServiceImpl implements RepairTicketService {
    private final RepairTicketMapper tickets;
    private final RepairTicketHistoryMapper histories;

    private final DormManagerMapper staff;

    public RepairTicketServiceImpl(RepairTicketMapper tickets, RepairTicketHistoryMapper histories, DormManagerMapper staff) {
        this.tickets = tickets;
        this.histories = histories;
        this.staff = staff;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairTicketView create(M4Actor actor, CreateRepairTicketRequest request) {
        if (!actor.isStudent()) throw new M4ApiException(403, "Only students can submit tickets.");
        if (request == null) throw M4ApiException.badRequest("A ticket body is required.");
        RepairTicket ticket = new RepairTicket();
        ticket.setTitle(required(request.getTitle(), 100, "Title"));
        ticket.setDescription(required(request.getDescription(), 2000, "Description"));
        ticket.setLocationType(RepairLocationType.validate(request.getLocationType()));
        ticket.setLocationText(required(request.getLocationText(), 200, "Location"));
        ticket.setRequesterUsername(actor.getUsername());
        ticket.setStatus(RepairTicketStatus.SUBMITTED.name());
        ticket.setVersion(0);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        if (tickets.insert(ticket) != 1 || ticket.getId() == null) {
            throw new IllegalStateException("Ticket insert did not return a generated ID.");
        }
        RepairTicketHistory history = new RepairTicketHistory();
        history.setTicketId(ticket.getId());
        history.setAction("CREATED");
        history.setToStatus(ticket.getStatus());
        history.setActorRole(actor.getRole());
        history.setActorUsername(actor.getUsername());
        history.setNote("Ticket submitted.");
        history.setCreatedAt(now);
        history.setTicketVersion(0);
        if (histories.insert(history) != 1) {
            throw new IllegalStateException("Ticket history was not saved.");
        }
        return RepairTicketView.from(ticket);
    }

    @Override
    public Page<RepairTicketView> list(M4Actor actor, Integer pageNum, Integer pageSize,
                                       String keyword, String status, Boolean mine) {
        if (pageNum == null || pageNum < 1 || pageSize == null || pageSize < 1 || pageSize > 50) {
            throw M4ApiException.badRequest("Use pageNum >= 1 and pageSize between 1 and 50.");
        }
        String term = M4Input.strip(keyword);
        if (term.length() > 100) throw M4ApiException.badRequest("Keyword must not exceed 100 characters.");
        String filter = RepairTicketStatus.validateFilter(status);
        QueryWrapper<RepairTicket> query = new QueryWrapper<>();
        if (actor.isStudent()) {
            // A student's ownership filter cannot be disabled by mine=false.
            query.eq("requester_username", actor.getUsername());
        } else if (Boolean.TRUE.equals(mine)) {
            if (!actor.isStaff()) throw M4ApiException.badRequest("The mine filter is for staff accounts.");
            query.eq("assignee_username", actor.getUsername());
        }
        if (!term.isEmpty()) query.like("title", term);
        if (filter != null) query.eq("status", filter);
        query.orderByDesc("created_at", "id");
        Page<RepairTicket> result = tickets.selectPage(new Page<>(pageNum, pageSize), query);
        Page<RepairTicketView> view = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        view.setRecords(result.getRecords().stream().map(RepairTicketView::from).collect(Collectors.toList()));
        return view;
    }

    @Override
    public RepairTicketView detail(M4Actor actor, Long id) {
        return RepairTicketView.from(findReadable(actor, id));
    }

    @Override
    public List<RepairHistoryView> history(M4Actor actor, Long id) {
        findReadable(actor, id);
        return histories.selectList(new QueryWrapper<RepairTicketHistory>()
                .eq("ticket_id", id).orderByAsc("id"))
                .stream().map(RepairHistoryView::from).collect(Collectors.toList());
    }


    @Override
    public List<RepairStaffOption> staffOptions(M4Actor actor) {
        requireStaff(actor);
        // Fetch and return only public selection fields, never passwords or full accounts.
        return staff.selectList(new QueryWrapper<DormManager>().select("username", "name").orderByAsc("username"))
                .stream().map(account -> new RepairStaffOption(account.getUsername(),
                        M4Input.strip(account.getName()).isEmpty() ? account.getUsername() : account.getName()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairTicketView assign(M4Actor actor, Long id, AssignRepairTicketRequest request) {
        requireStaff(actor);
        if (request == null) throw M4ApiException.badRequest("An assignment body is required.");
        int version = M4Input.version(request.getVersion());
        String username = required(request.getAssigneeUsername(), 64, "Assignee");
        String note = M4Input.optional(request.getNote(), 1800, "Assignment note");
        RepairTicket ticket = findReadable(actor, id);
        checkVersion(ticket, version);
        RepairTicketTransitions.requireAssignable(ticket.getStatus());
        DormManager employee = staff.selectById(username);
        if (employee == null) throw M4ApiException.badRequest("Choose an existing staff account.");
        // Use the canonical key returned by the legacy account table's collation.
        String assignee = employee.getUsername();
        if (assignee.equals(ticket.getAssigneeUsername())) {
            throw new M4ApiException(409, "This staff member is already assigned.");
        }
        String action = ticket.getAssigneeUsername() == null ? "ASSIGNED" : "REASSIGNED";
        LocalDateTime now = now();
        UpdateWrapper<RepairTicket> update = updateFor(ticket, version)
                .set("assignee_username", assignee).set("status", "ASSIGNED")
                .set("updated_at", now).set("version", version + 1);
        apply(update);
        appendHistory(actor, ticket, action, "ASSIGNED", "Assigned to " + assignee + "." +
                (note.isEmpty() ? "" : " " + note), now, version + 1);
        ticket.setAssigneeUsername(assignee);
        ticket.setStatus("ASSIGNED"); ticket.setUpdatedAt(now); ticket.setVersion(version + 1);
        return RepairTicketView.from(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairTicketView changeStatus(M4Actor actor, Long id, ChangeRepairStatusRequest request) {
        requireStaff(actor);
        if (request == null) throw M4ApiException.badRequest("A status body is required.");
        int version = M4Input.version(request.getVersion());
        RepairTicket ticket = findReadable(actor, id);
        requireAssignee(actor, ticket);
        checkVersion(ticket, version);
        String action = RepairTicketTransitions.action(ticket.getStatus(), request.getTargetStatus());
        String target = RepairTicketStatus.validateFilter(request.getTargetStatus());
        String note = "STARTED".equals(action) ? M4Input.optional(request.getNote(), 2000, "Note")
                : required(request.getNote(), 2000, "Processing note");
        if (note.isEmpty()) note = "Work started.";
        LocalDateTime now = now();
        UpdateWrapper<RepairTicket> update = ownedUpdate(actor, ticket, version)
                .set("status", target).set("updated_at", now).set("version", version + 1);
        if ("RESOLVED".equals(target)) update.set("resolved_at", now);
        if ("CLOSED".equals(target)) update.set("closed_at", now);
        apply(update);
        appendHistory(actor, ticket, action, target, note, now, version + 1);
        ticket.setStatus(target); ticket.setUpdatedAt(now); ticket.setVersion(version + 1);
        if ("RESOLVED".equals(target)) ticket.setResolvedAt(now);
        if ("CLOSED".equals(target)) ticket.setClosedAt(now);
        return RepairTicketView.from(ticket);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairTicketView addProgress(M4Actor actor, Long id, AddRepairProgressRequest request) {
        requireStaff(actor);
        if (request == null) throw M4ApiException.badRequest("A progress body is required.");
        int version = M4Input.version(request.getVersion());
        String note = required(request.getNote(), 2000, "Progress note");
        RepairTicket ticket = findReadable(actor, id);
        requireAssignee(actor, ticket);
        checkVersion(ticket, version);
        RepairTicketTransitions.requireInProgress(ticket.getStatus());
        LocalDateTime now = now();
        apply(ownedUpdate(actor, ticket, version).set("version", version + 1).set("updated_at", now));
        appendHistory(actor, ticket, "PROGRESS_ADDED", ticket.getStatus(), note, now, version + 1);
        ticket.setVersion(version + 1); ticket.setUpdatedAt(now);
        return RepairTicketView.from(ticket);
    }

    private void requireStaff(M4Actor actor) {
        if (!actor.isStaff() && !actor.isAdmin()) throw new M4ApiException(403, "Staff access is required.");
    }
    private void requireAssignee(M4Actor actor, RepairTicket ticket) {
        if (!actor.isAdmin() && !actor.getUsername().equals(ticket.getAssigneeUsername())) {
            throw new M4ApiException(403, "Only the assigned staff member can process this ticket.");
        }
    }
    private void checkVersion(RepairTicket ticket, int version) {
        if (!Integer.valueOf(version).equals(ticket.getVersion())) throw conflict();
    }
    private UpdateWrapper<RepairTicket> updateFor(RepairTicket ticket, int version) {
        return new UpdateWrapper<RepairTicket>().eq("id", ticket.getId())
                .eq("status", ticket.getStatus()).eq("version", version);
    }
    private UpdateWrapper<RepairTicket> ownedUpdate(M4Actor actor, RepairTicket ticket, int version) {
        UpdateWrapper<RepairTicket> update = updateFor(ticket, version);
        if (!actor.isAdmin()) update.eq("assignee_username", actor.getUsername());
        return update;
    }
    private void apply(UpdateWrapper<RepairTicket> update) {
        if (tickets.update(null, update) != 1) throw conflict();
    }
    private M4ApiException conflict() {
        return new M4ApiException(409, "The ticket has changed. Refresh it before trying again.");
    }
    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);
    }
    private void appendHistory(M4Actor actor, RepairTicket ticket, String action, String target,
                               String note, LocalDateTime now, int version) {
        RepairTicketHistory history = new RepairTicketHistory();
        history.setTicketId(ticket.getId()); history.setAction(action);
        history.setFromStatus(ticket.getStatus()); history.setToStatus(target);
        history.setActorRole(actor.getRole()); history.setActorUsername(actor.getUsername());
        history.setNote(note); history.setCreatedAt(now); history.setTicketVersion(version);
        if (histories.insert(history) != 1) throw new IllegalStateException("Ticket history was not saved.");
    }

    private RepairTicket findReadable(M4Actor actor, Long id) {
        if (id == null || id < 1) throw M4ApiException.badRequest("Ticket ID must be positive.");
        QueryWrapper<RepairTicket> query = new QueryWrapper<RepairTicket>().eq("id", id);
        if (actor.isStudent()) query.eq("requester_username", actor.getUsername());
        RepairTicket ticket = tickets.selectOne(query);
        if (ticket == null) throw M4ApiException.notFound();
        return ticket;
    }

    private String required(String value, int max, String field) {
        String text = M4Input.strip(value);
        if (text.isEmpty() || text.length() > max) {
            throw M4ApiException.badRequest(field + " is required and must not exceed " + max + " characters.");
        }
        return text;
    }
}
