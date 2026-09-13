package com.bteamore.configswitch.core;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.actions.FetchAction;
import com.bteamore.configswitch.core.actions.PushAction;

import java.util.EnumMap;

public class ConfigStateMachine {
    private static ConfigStateMachine instance;
    public static ConfigStateMachine getInstance() {
        if (instance == null) {
            instance = new ConfigStateMachine();
        }
        return instance;
    }
    private ConfigState currentState = ConfigState.IDLE;
    private final EnumMap<ConfigState, EnumMap<ConfigEvent, Transition<ConfigState, ConfigEvent>>> transition = new EnumMap<>(ConfigState.class);

    private ConfigStateMachine(){
        this.init();
    }

    // 参考 git 模型:push 推送本地到全局,fetch 从全局拉取(后续会加入 diff 以更原子化的 push/fetch)
    private void init(){
        // push
        register(ConfigState.IDLE,ConfigEvent.PUSH,ConfigState.PUSHING,new PushAction());

        // fetch
        register(ConfigState.IDLE,ConfigEvent.FETCH,ConfigState.FETCHING,new FetchAction());

        // complete
        register(ConfigState.FETCHING,ConfigEvent.DONE,ConfigState.IDLE,(from,to,e,c) -> {
            Configswitch.LOGGER.info("StateMachine - Complete");
            return null;
        });
        register(ConfigState.PUSHING,ConfigEvent.DONE,ConfigState.IDLE,(from,to,e,c) -> {
            Configswitch.LOGGER.info("StateMachine - Complete");
            return null;
        });

    }

    protected void register(ConfigState fromState, ConfigEvent event, ConfigState toState, Action<ConfigState, ConfigEvent> action) {
        Transition<ConfigState, ConfigEvent> trans = new Transition<ConfigState, ConfigEvent>(fromState, event, toState, action);
        transition.computeIfAbsent(fromState, key -> new EnumMap<>(event.getDeclaringClass())).put(event, trans);
    }

    public void handleEvent(ConfigEvent event, Object context) {
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

        ConfigEvent afterEvent = action.execute(this.currentState, trans.toState, event, context);
        this.currentState = trans.toState;

        if (afterEvent != null) {
            handleEvent(afterEvent, context);
        }
    }

    public ConfigState getCurrentState(){
        return this.currentState;
    }
}
