package com.bteamore.configswitch.core;

public enum ConfigState {
    //弃用
    STORE,
    FETCH,
    LINKED,
    LOCAL,
    PUSH,

    // new
    IDLE,
    FETCHING,
    PUSHING,
}
