package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.core.*;
import com.bteamore.configswitch.repo.ConfigPaths;
import com.bteamore.configswitch.util.Log;

import java.util.List;

public class FetchAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event, Object context) {
        IConfigFileService service = ConfigFileServices.get();
        if (service == null) {
            Log.error("StateMachine - No file service registered, fetch skipped");
            return ConfigEvent.DONE;
        }
        if (context instanceof SyncRequest request) {
            List<ConfigPaths> paths = request.paths();
            Log.info("StateMachine - Fetch started: {} files", paths.size());

            SyncReport report = service.fetchGlobalToActive(paths);
            request.callback().accept(report);
        }
        return ConfigEvent.DONE;
    }
}
