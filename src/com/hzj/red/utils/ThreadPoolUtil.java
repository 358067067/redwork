package com.hzj.red.utils;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolUtil {

    //初始化线程池
    //默认20根线程
    //最大值100根线程
    //超过60S空闲回收线程
    //当队列中阻塞请求超过10个才申请新的线程
    private static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            20,
            100,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(10)
    );

    public static void run(Runnable r) {
        threadPool.execute(r);
    }
}
