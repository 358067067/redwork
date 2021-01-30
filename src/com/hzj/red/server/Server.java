package com.hzj.red.server;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.log.LogFactory;
import cn.hutool.system.SystemUtil;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class Server {

    public static void main(String[] args) throws Exception {

        String serverClassName = "com.hzj.red.server.Server";

        Class<?> serverClazz = ClassLoader.getSystemClassLoader().loadClass(serverClassName);

        Object serverObject = serverClazz.newInstance();

        Method m = serverClazz.getMethod("start");

        m.invoke(serverObject);

    }

    public void start() {
        TimeInterval timeInterval = DateUtil.timer();
        logJVM();
        init();
        LogFactory.get().info("Server startup in {} ms", timeInterval.intervalMs());
    }

    private void init() {
        TimeInterval timeInterval = DateUtil.timer();
        LogFactory.get().info("Initialization processed in {} ms", timeInterval.intervalMs());
        Connector connectors = new Connector();
        connectors.start();
    }

    private static void logJVM() {
        Map<String, String> infos = new LinkedHashMap<>();
        infos.put("Server version", "How2J DiyTomcat/1.0.1");
        infos.put("Server built", "2020-04-08 10:20:22");
        infos.put("Server number", "1.0.1");
        infos.put("OS Name\t", SystemUtil.get("os.name"));
        infos.put("OS Version", SystemUtil.get("os.version"));
        infos.put("Architecture", SystemUtil.get("os.arch"));
        infos.put("Java Home", SystemUtil.get("java.home"));
        infos.put("JVM Version", SystemUtil.get("java.runtime.version"));
        infos.put("JVM Vendor", SystemUtil.get("java.vm.specification.vendor"));

        infos.forEach((key, value) -> {
            LogFactory.get().info(key + ":\t\t" + value);
        });
    }
}
