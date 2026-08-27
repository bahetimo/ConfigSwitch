package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.core.Action;
import com.bteamore.configswitch.core.ConfigEvent;
import com.bteamore.configswitch.core.ConfigState;
import com.bteamore.configswitch.manager.ConfigHandler;

import java.util.Objects;

public class SwitchAction implements Action<ConfigState, ConfigEvent> {
    public SwitchAction(){
    }
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event) {
        // 执行逻辑
        if (Objects.requireNonNull(from) == ConfigState.LOCAL) {
            ConfigHandler.store();
        }
        return ConfigEvent.IO;
    }
}
