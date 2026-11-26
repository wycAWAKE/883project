package com.example.springboot.controller;

import com.example.springboot.common.Result;
import com.example.springboot.dto.repair.*;
import com.example.springboot.repair.M4SessionResolver;
import com.example.springboot.service.RepairTicketService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/repairs")
public class RepairTicketController {
    private final RepairTicketService service;
    private final M4SessionResolver sessions;
    public RepairTicketController(RepairTicketService service, M4SessionResolver sessions) {
        this.service = service;
        this.sessions = sessions;
    }
    @PostMapping
    public Result<?> create(HttpServletRequest request, @RequestBody CreateRepairTicketRequest body) {
        return success(service.create(sessions.resolve(request), body));
    }
    @GetMapping
    public Result<?> list(HttpServletRequest request,
                          @RequestParam(defaultValue = "1") Integer pageNum,
                          @RequestParam(defaultValue = "10") Integer pageSize,
                          @RequestParam(defaultValue = "") String keyword,
                          @RequestParam(defaultValue = "") String status,
                          @RequestParam(defaultValue = "false") Boolean mine) {
        return success(service.list(sessions.resolve(request), pageNum, pageSize, keyword, status, mine));
    }
    @GetMapping("/{id}")
    public Result<?> detail(HttpServletRequest request, @PathVariable Long id) {
        return success(service.detail(sessions.resolve(request), id));
    }
    @GetMapping("/{id}/history")
    public Result<?> history(HttpServletRequest request, @PathVariable Long id) {
        return success(service.history(sessions.resolve(request), id));
    }

    @GetMapping("/staff")
    public Result<?> staff(HttpServletRequest request) {
        return success(service.staffOptions(sessions.resolve(request)));
    }
    @PutMapping("/{id}/assignment")
    public Result<?> assign(HttpServletRequest request, @PathVariable Long id,
                            @RequestBody AssignRepairTicketRequest body) {
        return success(service.assign(sessions.resolve(request), id, body));
    }
    @PutMapping("/{id}/status")
    public Result<?> changeStatus(HttpServletRequest request, @PathVariable Long id,
                                  @RequestBody ChangeRepairStatusRequest body) {
        return success(service.changeStatus(sessions.resolve(request), id, body));
    }
    @PostMapping("/{id}/progress")
    public Result<?> progress(HttpServletRequest request, @PathVariable Long id,
                              @RequestBody AddRepairProgressRequest body) {
        return success(service.addProgress(sessions.resolve(request), id, body));
    }
    private Result<?> success(Object data) { return new Result<>("0", "Success", data); }
}
