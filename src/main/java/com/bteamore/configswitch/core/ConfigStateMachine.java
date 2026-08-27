package com.bteamore.configswitch.core;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.actions.IOAction;
import com.bteamore.configswitch.core.actions.SwitchAction;
import com.bteamore.configswitch.core.actions.SyncAction;

import java.util.EnumMap;

public class ConfigStateMachine {
    private static ConfigStateMachine instance;
    public static ConfigStateMachine getInstance() {
        if (instance == null) {
            instance = new ConfigStateMachine();
        }
        return instance;
    }
    private ConfigState currentState = ConfigState.LINKED;
    private final EnumMap<ConfigState, EnumMap<ConfigEvent, Transition<ConfigState, ConfigEvent>>> transition = new EnumMap<>(ConfigState.class);

    private ConfigStateMachine(){
        this.init();
    }

    // Linked overlay->Local：当前配置覆盖存储(或不操作)
    // Local sync->Linked：备份全局，当前配置覆盖全局 (Push)
    // Linked switch->Local：存储覆盖当前配置 （Store）（这个貌似也可以不用中间态）
    // Local switch->Linked：当前配置覆盖存储，全局覆盖当前配置 (Fetch)
    private void init(){
        // 覆盖
        register(ConfigState.LINKED,ConfigEvent.OVERLAY,ConfigState.LOCAL,(from,to,e) -> {
            Configswitch.LOGGER.info("StateMachine - Overlay to Local");
            return null;
        });

        // 同步
        register(ConfigState.LOCAL,ConfigEvent.SYNC,ConfigState.PUSH,new SyncAction());
        register(ConfigState.PUSH,ConfigEvent.IO,ConfigState.LINKED,new IOAction());

        // 切换
        register(ConfigState.LINKED,ConfigEvent.SWITCH,ConfigState.STORE,new SwitchAction());
        register(ConfigState.STORE,ConfigEvent.IO,ConfigState.LOCAL,new IOAction());

        // 切换
        register(ConfigState.LOCAL,ConfigEvent.SWITCH,ConfigState.FETCH,new SwitchAction());
        register(ConfigState.FETCH,ConfigEvent.IO,ConfigState.LINKED,new IOAction());
    }

    protected void register(ConfigState fromState, ConfigEvent event, ConfigState toState, Action<ConfigState, ConfigEvent> action) {
        Transition<ConfigState, ConfigEvent> trans = new Transition<ConfigState, ConfigEvent>(fromState, event, toState, action);
        transition.computeIfAbsent(fromState, key -> new EnumMap<>(event.getDeclaringClass())).put(event, trans);
    }

    public void handleEvent(ConfigEvent event) {
        var inner = transition.get(this.currentState);
        if (inner == null){
            Configswitch.LOGGER.warn("StateMachine - No transition defined for state: {}", this.currentState);
            return;
        }
        var trans = inner.get(event);
        if (trans ==null){
            Configswitch.LOGGER.warn("StateMachine - No transition defined for event: {} in state: {}", event, this.currentState);
            return;
        }
        var action = trans.action;
        if (action == null){
            Configswitch.LOGGER.warn("StateMachine - No action defined for transition: {} to {} on event: {}", this.currentState, trans.toState, event);
            return;
        }

        ConfigEvent afterEvent = action.execute(this.currentState, trans.toState, event);
        this.currentState = trans.toState;

        if (afterEvent != null) {
            handleEvent(afterEvent);
        }
    }

    public ConfigState getCurrentState(){
        return this.currentState;
    }
}
