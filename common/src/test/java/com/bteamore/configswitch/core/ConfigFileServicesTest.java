package com.bteamore.configswitch.core;

import com.bteamore.configswitch.discovery.BackupSnapshot;
import com.bteamore.configswitch.repo.ConfigPaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigFileServicesTest {

    // ---------- 入参校验 ----------

    // 传 null 一律 NPE：这条检查排在"是否已注册"之前，所以与注册状态无关，任何执行顺序下都成立
    @Test
    void testRegisterNullRejected() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> ConfigFileServices.register(null));

        // 带说明的显式校验，而不是某处意外解引用
        assertNotNull(thrown.getMessage());
    }

    // ---------- 注册生命周期 ----------

    // 注册表是进程级单态、注册后不可撤销也不允许覆盖，未注册态只在本用例第一次取用前存在，
    // 因此"未注册 → 注册 → 取用 → 拒绝重复注册"只能在一个用例里依次走完。
    // 副作用：本用例结束后注册表被占住，别的用例不应再依赖未注册态。
    @Test
    void testGetFailsBeforeRegisterAndRegisterIsOneTime() {
        IllegalStateException beforeRegister = assertThrows(IllegalStateException.class, () -> ConfigFileServices.get());
        assertTrue(beforeRegister.getMessage().contains("not registered"), beforeRegister.getMessage());

        IConfigFileService service = new StubService();
        ConfigFileServices.register(service);

        // 交出来的就是注册时那一个实例，不做包装也不拷副本
        assertSame(service, ConfigFileServices.get());

        IllegalStateException second = assertThrows(IllegalStateException.class,
                () -> ConfigFileServices.register(new StubService()));
        assertTrue(second.getMessage().contains("already registered"), second.getMessage());
        // 被拒的重复注册不留痕迹：先前注册的实例仍是唯一可用的
        assertSame(service, ConfigFileServices.get());
    }

    // 只有签名会被用到：注册表只存取实例，从不调用服务方法
    private static final class StubService implements IConfigFileService {
        @Override
        public SyncReport pushActiveToGlobal(List<ConfigPaths> paths) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SyncReport fetchGlobalToActive(List<ConfigPaths> paths) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SyncReport restore(BackupSnapshot snapshot, Path targetRoot, Path backupRoot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PackReport exportRepo(Path root, Path output, String version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PackReport importRepo(Path root, Path input, Path backupRoot) {
            throw new UnsupportedOperationException();
        }
    }
}
