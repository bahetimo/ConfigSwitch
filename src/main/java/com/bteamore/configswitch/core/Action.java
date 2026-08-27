package com.bteamore.configswitch.core;

@FunctionalInterface
public interface Action<S, E> {
    E execute(S fromState, S toState, E event);
}
