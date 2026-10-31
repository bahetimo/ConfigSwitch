package com.bteamore.configswitch.core;

public record SourceMismatch(
    String modId,
    String sourceMinecraft,
    String sourceLoader,
    String currentMinecraft,
    String currentLoader) {
}
