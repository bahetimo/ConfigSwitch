package com.bteamore.configswitch.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import static org.junit.jupiter.api.Assertions.*;

public class ModConfigTest {
    // 生产全局点：STORE 指向真实落点（%APPDATA%\.minecraft\configswitch），测试只读不写
    // 同一个 store、settings() 恒可读
    @Test
    void testStoreIsSingleSharedInstance() {
        assertSame(ModConfig.store(), ModConfig.store());
        assertNotNull(ModConfig.settings());
    }

    // store 是无状态的读写入口：load() 每次都重新读盘，不依赖 store 里的状态
    @Test
    void testStoreLoadsOnDemand() {
        ModSettings loaded = assertDoesNotThrow((ThrowingSupplier<ModSettings>) ModConfig.store()::load);

        assertNotNull(loaded);
    }

    // 启动加载不抛异常，加载后设置仍可读（真实落点缺失时就是默认值）
    @Test
    void testLoadKeepsSettingsReadable() {
        assertDoesNotThrow(ModConfig::load);

        assertNotNull(ModConfig.settings());
    }
}
