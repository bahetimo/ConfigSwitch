package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.core.*;
import com.bteamore.configswitch.util.Log;

public class RestoreAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event, Object context) {
        IConfigFileService service = ConfigFileServices.get();
        if (service == null) {
            Log.error("StateMachine - No file service registered, restore skipped");
            return ConfigEvent.DONE;
        }
        if (context instanceof RestoreRequest restoreRequest) {
            Log.info("StateMachine - Restore started {}", restoreRequest.snapshot().timeStamp());

            SyncReport report = service.restore(restoreRequest.snapshot(), restoreRequest.targetRoot(), restoreRequest.backupRoot());
            restoreRequest.onResult().accept(report);
        }
        return ConfigEvent.DONE;
    }
}
