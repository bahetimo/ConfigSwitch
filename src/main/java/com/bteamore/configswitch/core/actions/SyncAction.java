package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.core.Action;
import com.bteamore.configswitch.core.ConfigState;
import com.bteamore.configswitch.core.ConfigEvent;
import com.bteamore.configswitch.manager.ConfigHandler;

public class SyncAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event) {
        ConfigHandler.backup(to);
        return ConfigEvent.IO;
    }
}
