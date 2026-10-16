package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.core.*;
import com.bteamore.configswitch.util.Log;

public class ImportAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event, Object context) {
        IConfigFileService service = ConfigFileServices.get();
        if (service == null) {
            Log.error("StateMachine - No file service registered, import skipped");
            return ConfigEvent.DONE;
        }
        if (context instanceof ImportRequest importRequest) {
            Log.info("StateMachine - Import started {}", importRequest.input());

            PackReport report = service.importRepo(importRequest.root(), importRequest.input(), importRequest.backupRoot());
            importRequest.onResult().accept(report);
        }
        return ConfigEvent.DONE;
    }
}
