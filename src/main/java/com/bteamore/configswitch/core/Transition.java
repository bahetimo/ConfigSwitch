package com.bteamore.configswitch.core;

public class Transition<S,E> {
    public final S fromState;
    public final E event;
    public final S toState;
    public final Action<S,E> action;

    public Transition(S fromState, E event, S toState, Action<S,E> action) {
        this.fromState = fromState;
        this.event = event;
        this.toState = toState;
        this.action = action;
    }
}
