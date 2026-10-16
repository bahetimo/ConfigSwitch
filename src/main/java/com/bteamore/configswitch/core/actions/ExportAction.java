package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.core.*;
import com.bteamore.configswitch.util.Log;

public class ExportAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event, Object context) {
        IConfigFileService service = ConfigFileServices.get();
        if (service == null) {
            Log.error("StateMachine - No file service registered, export skipped");
            return ConfigEvent.DONE;
        }
        if (context instanceof ExportRequest exportRequest) {
            Log.info("StateMachine - Export started {}", exportRequest.output());

            PackReport report = service.exportRepo(exportRequest.root(), exportRequest.output(), exportRequest.version());
            exportRequest.onResult().accept(report);
        }
        return ConfigEvent.DONE;
    }
}
