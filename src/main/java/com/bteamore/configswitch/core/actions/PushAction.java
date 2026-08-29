package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.Action;
import com.bteamore.configswitch.core.ConfigEvent;
import com.bteamore.configswitch.core.ConfigFileServices;
import com.bteamore.configswitch.core.ConfigState;
import com.bteamore.configswitch.core.IConfigFileService;

public class PushAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event) {
        Configswitch.LOGGER.info("StateMachine - Push");
        IConfigFileService service = ConfigFileServices.get();
        if (service == null) {
            Configswitch.LOGGER.warn("StateMachine - No file service registered, push skipped");
            return ConfigEvent.IO;
        }
        service.pushActiveToGlobal();
        return ConfigEvent.IO;
    }
}
