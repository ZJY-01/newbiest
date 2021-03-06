package com.newbiest.mms;

import com.newbiest.base.factory.ModelFactory;
import com.newbiest.mms.model.*;
import com.newbiest.mms.state.model.MaterialEvent;
import com.newbiest.mms.state.model.MaterialStatus;
import com.newbiest.mms.state.model.MaterialStatusCategory;
import com.newbiest.mms.state.model.MaterialStatusModel;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

@Slf4j
@Component
public class MmsConfiguration {

    @Bean("mmsLiquibase")
    public SpringLiquibase liquibase(DataSource dataSource) throws Exception{
        if (log.isInfoEnabled()) {
            log.info("Load MMS Liquibase Configuration.");
        }
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-mms.yaml");
        liquibase.setShouldRun(true);
        liquibase.setDropFirst(false);
        return liquibase;
    }

    @PostConstruct
    public void init() {
        // 注册modelClassLoader
        ModelFactory.registerModelClassLoader(MaterialEvent.class.getName(), MaterialEvent.class.getClassLoader());
        ModelFactory.registerModelClassLoader(MaterialStatus.class.getName(), MaterialStatus.class.getClassLoader());
        ModelFactory.registerModelClassLoader(MaterialStatusCategory.class.getName(), MaterialStatusCategory.class.getClassLoader());
        ModelFactory.registerModelClassLoader(MaterialStatusModel.class.getName(), MaterialStatusModel.class.getClassLoader());
        ModelFactory.registerModelClassLoader(RawMaterial.class.getName(), RawMaterial.class.getClassLoader());
        ModelFactory.registerModelClassLoader(MaterialLot.class.getName(), MaterialLot.class.getClassLoader());
        ModelFactory.registerModelClassLoader(MaterialLotHistory.class.getName(), MaterialLotHistory.class.getClassLoader());
        ModelFactory.registerModelClassLoader(Warehouse.class.getName(), Warehouse.class.getClassLoader());
        ModelFactory.registerModelClassLoader(Storage.class.getName(), Storage.class.getClassLoader());
        ModelFactory.registerModelClassLoader(MaterialLotPackageType.class.getName(), MaterialLotPackageType.class.getClassLoader());
        ModelFactory.registerModelClassLoader(MaterialLotMergeRule.class.getName(), MaterialLotMergeRule.class.getClassLoader());
        ModelFactory.registerModelClassLoader(DeliveryOrder.class.getName(), DeliveryOrder.class.getClassLoader());

        // 注册历史
        ModelFactory.registerHistoryModelClassLoader(RawMaterial.class.getName(), MaterialHistory.class.getClassLoader());
        ModelFactory.registerHistoryClassName(RawMaterial.class.getName(), MaterialHistory.class.getName());

        ModelFactory.registerHistoryModelClassLoader(MaterialLot.class.getName(), MaterialLotHistory.class.getClassLoader());
        ModelFactory.registerHistoryClassName(MaterialLot.class.getName(), MaterialLotHistory.class.getName());
    }

}
