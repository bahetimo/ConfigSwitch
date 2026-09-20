package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.core.*;
import com.bteamore.configswitch.repo.ConfigPaths;
import com.bteamore.configswitch.util.Log;

import java.util.List;

public class PushAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event, Object context) {
        IConfigFileService service = ConfigFileServices.get();
        if (service == null) {
            Log.error("StateMachine - No file service registered, push skipped");
            return ConfigEvent.DONE;
        }
        if (context instanceof SyncRequest request) {
            List<ConfigPaths> paths = request.paths();
            Log.info("StateMachine - Push started: {} files", paths.size());

            SyncReport report = service.pushActiveToGlobal(paths);
            request.callback().accept(report);
        }
        return ConfigEvent.DONE;
    }
}
