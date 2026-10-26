package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.core.ConfigEvent;
import com.bteamore.configswitch.core.ConfigState;
import com.bteamore.configswitch.core.ConfigStateMachine;

public class StateManager {
    private static StateManager instance;

    private StateManager() {}

    public static StateManager getInstance() {
        if (instance == null) {
            instance = new StateManager();
        }
        return instance;
    }

    public void transition(String event, Object context) {
        if (event == null){
            return;
        }

        ConfigEvent configEvent = this.getConfigEvent(event);
        if (configEvent == null) {
            return;
        }

        ConfigStateMachine.getInstance().handleEvent(configEvent, context);
    }

    private ConfigEvent getConfigEvent(String event) {
        return ConfigEvent.valueOf(event.toUpperCase());
    }

    public ConfigState getCurrentState(){
        return ConfigStateMachine.getInstance().getCurrentState();
    }
}
