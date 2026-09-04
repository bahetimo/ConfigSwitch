package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.Action;
import com.bteamore.configswitch.core.ConfigEvent;
import com.bteamore.configswitch.core.ConfigFileServices;
import com.bteamore.configswitch.core.ConfigState;
import com.bteamore.configswitch.core.IConfigFileService;
import com.bteamore.configswitch.repo.ConfigPaths;

import java.util.List;

public class PushAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event, Object context) {
        Configswitch.LOGGER.info("StateMachine - Push");
        IConfigFileService service = ConfigFileServices.get();
        if (service == null) {
            Configswitch.LOGGER.warn("StateMachine - No file service registered, push skipped");
            return ConfigEvent.IO;
        }
        // service.pushActiveToGlobal();
        if (context instanceof List) {
            List<ConfigPaths> paths = (List<ConfigPaths>) context;
            service.pushActiveToGlobal(paths);
        }
        return ConfigEvent.IO;
    }
}
