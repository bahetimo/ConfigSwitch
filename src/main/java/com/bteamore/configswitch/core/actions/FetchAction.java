package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.Action;
import com.bteamore.configswitch.core.ConfigEvent;
import com.bteamore.configswitch.core.ConfigFileServices;
import com.bteamore.configswitch.core.ConfigState;
import com.bteamore.configswitch.core.IConfigFileService;
import com.bteamore.configswitch.repo.ConfigPaths;

import java.util.List;

public class FetchAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event, Object context) {
        Configswitch.LOGGER.info("StateMachine - Fetch");
        IConfigFileService service = ConfigFileServices.get();
        if (service == null) {
            Configswitch.LOGGER.warn("StateMachine - No file service registered, fetch skipped");
            return ConfigEvent.IO;
        }
        // service.fetchGlobalToActive();
        if (context instanceof List) {
            List<ConfigPaths> paths = (List<ConfigPaths>) context;
            service.fetchGlobalToActive(paths);
        }
        return ConfigEvent.IO;
    }
}
