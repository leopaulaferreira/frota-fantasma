package com.frotafantasma.analytics.recommendation;

import static com.frotafantasma.analytics.recommendation.RecommendationDtos.*;
import com.frotafantasma.analytics.analytics.AnalyticsProperties;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationRulesService {
    private final RecommendationRepository repository;
    private final AnalyticsProperties defaults;
    private final ClashRoyaleProperties api;
    private final Clock clock;
    private final Validator validator;
    public RecommendationRulesService(RecommendationRepository repository, AnalyticsProperties defaults, ClashRoyaleProperties api, Clock clock, Validator validator) {
        this.repository=repository; this.defaults=defaults; this.api=api; this.clock=clock; this.validator=validator;
    }
    public RuleVersion current() {
        return repository.active(api.clanTag()).orElseGet(() -> new RuleVersion(null,0,null,"Padrões de ambiente; ainda não persistidos",new Settings(defaults,ScoreSettings.defaults(),28,false)));
    }
    @Transactional
    public RuleVersion change(RuleChange request) {
        if (!validator.validate(request).isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Configuração inválida: confira faixas, campos obrigatórios e soma dos pesos.");
        repository.lock(api.clanTag());
        var current=current();
        if (!java.util.Objects.equals(current.id(),request.expectedVersion())) throw new ResponseStatusException(HttpStatus.CONFLICT,"A versão ativa mudou. Leia as regras e envie expectedVersion atualizado.");
        // A fixed calendar avoids assigning the same time interval to incompatible weekly awards.
        if (!current.settings().analytics().timezone().equals(request.settings().analytics().timezone()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"O fuso analítico é fixo para este histórico; mantenha o fuso da versão atual.");
        var next=new RuleVersion(UUID.randomUUID(),current.revision()+1,clock.instant(),request.reason(),request.settings());
        repository.insertRule(api.clanTag(),next);
        return next;
    }
    /** Caller holds the clan recommendation transaction lock. */
    public RuleVersion ensureStored() {
        var current=current();
        if (current.id()!=null) return current;
        var first=new RuleVersion(UUID.randomUUID(),1,clock.instant(),"Configuração inicial usada na primeira avaliação",current.settings());
        repository.insertRule(api.clanTag(),first);
        return first;
    }
    public Page<RuleVersion> history(int page,int size) { return new Page<>(repository.rules(api.clanTag(),page,size),page,size,repository.countRules(api.clanTag())); }
}
