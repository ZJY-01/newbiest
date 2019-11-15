package com.newbiest.gc.trigger;

import com.newbiest.base.utils.SessionContext;
import com.newbiest.base.utils.StringUtils;
import com.newbiest.base.utils.ThreadLocalContext;
import com.newbiest.gc.service.GcService;
import com.newbiest.security.model.NBOrg;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
import java.util.UUID;

/**
 * Created by guoxunbo on 2019-11-15 14:32
 */
@Data
@AllArgsConstructor
public abstract class GcTriggerThread implements Runnable{

    protected GcService gcService;

    @Override
    public void run() {
        generatorSessionContext();
        execute();
    }

    public abstract void execute();

    public void generatorSessionContext() {
        SessionContext sc = new SessionContext();
        sc.setOrgRrn(NBOrg.GLOBAL_ORG_RRN);
        sc.setUsername(StringUtils.SYSTEM_USER);
        sc.setTransRrn(UUID.randomUUID().toString());
        sc.setTransTime(new Date());
        ThreadLocalContext.putSessionContext(sc);
    }
}
