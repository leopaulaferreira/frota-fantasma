package com.frotafantasma.analytics.api;

import com.frotafantasma.analytics.api.dto.ClanMemberResponse;
import com.frotafantasma.analytics.api.dto.ClanResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clan")
public class ClanController {
    private final ClanService service;

    public ClanController(ClanService service) {
        this.service = service;
    }

    @GetMapping
    public ClanResponse getClan() {
        return service.getClan();
    }

    @GetMapping("/members")
    public List<ClanMemberResponse> getMembers() {
        return service.getMembers();
    }
}

