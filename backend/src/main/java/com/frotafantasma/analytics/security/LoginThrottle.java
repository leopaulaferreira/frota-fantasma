package com.frotafantasma.analytics.security;
import java.time.*;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LoginThrottle {
    private record Attempts(int count,Instant since) {}
    private final Map<String,Attempts> attempts=new HashMap<>();
    private final Clock clock;
    public LoginThrottle(Clock clock) { this.clock=clock; }
    public synchronized long retryAfter(String key) {
        var a=attempts.get(key);
        if(a==null) return 0;
        long remaining=Duration.between(clock.instant(),a.since().plusSeconds(300)).getSeconds();
        if(remaining<=0) { attempts.remove(key); return 0; }
        return a.count()>=10?Math.max(1,remaining):0;
    }
    public synchronized void failed(String key) {
        Instant now=clock.instant();
        attempts.entrySet().removeIf(e->!e.getValue().since().plusSeconds(300).isAfter(now));
        var a=attempts.get(key);
        if(a==null && attempts.size()>=10000) return;
        attempts.put(key,a==null?new Attempts(1,now):new Attempts(a.count()+1,a.since()));
    }
    public synchronized void succeeded(String key) { attempts.remove(key); }
}
