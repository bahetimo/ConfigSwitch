package com.bteamore.configswitch.core.actions;

import com.bteamore.configswitch.core.Action;
import com.bteamore.configswitch.core.ConfigState;
import com.bteamore.configswitch.core.ConfigEvent;
import com.bteamore.configswitch.manager.ConfigHandler;

import java.util.Objects;

public class IOAction implements Action<ConfigState, ConfigEvent> {
    @Override
    public ConfigEvent execute(ConfigState from, ConfigState to, ConfigEvent event) {
        if (Objects.requireNonNull(from) == ConfigState.PUSH || from == ConfigState.STORE || from == ConfigState.FETCH) {
            ConfigHandler.overwrite(from);
        }
        return null;
    }
}
