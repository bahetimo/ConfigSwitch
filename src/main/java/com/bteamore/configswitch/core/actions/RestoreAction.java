package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.*;

public class RestoreAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event, Object context) {
        Configswitch.LOGGER.info("StateMachine - Restore");
        IConfigFileService service = ConfigFileServices.get();
        if (service == null) {
            Configswitch.LOGGER.warn("StateMachine - No file service registered, restore skipped");
            return ConfigEvent.DONE;
        }
        if (context instanceof RestoreRequest restoreRequest) {
            SyncReport report = service.restore(restoreRequest.snapshot(), restoreRequest.targetRoot(), restoreRequest.backupRoot());
            restoreRequest.onResult().accept(report);
        }
        return ConfigEvent.DONE;
    }
}
