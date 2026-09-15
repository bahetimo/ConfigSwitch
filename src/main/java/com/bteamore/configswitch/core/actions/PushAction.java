package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.*;
import com.bteamore.configswitch.repo.ConfigPaths;

import java.util.List;

public class PushAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event, Object context) {
        Configswitch.LOGGER.info("StateMachine - Push");
        IConfigFileService service = ConfigFileServices.get();
        if (service == null) {
            Configswitch.LOGGER.warn("StateMachine - No file service registered, push skipped");
            return ConfigEvent.DONE;
        }
        if (context instanceof SyncRequest request) {
            List<ConfigPaths> paths = request.paths();
            SyncReport report = service.pushActiveToGlobal(paths);
            request.callback().accept(report);
        }
        return ConfigEvent.DONE;
    }
}
