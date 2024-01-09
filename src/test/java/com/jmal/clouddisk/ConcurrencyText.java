package com.jmal.clouddisk;

import cn.hutool.core.lang.Console;
import cn.hutool.core.thread.ConcurrencyTester;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.http.HttpRequest;
import com.jmal.clouddisk.interceptor.AuthInterceptor;

/**
 * @author jmal
 * @Description 高并发测试
 * @Date 2021/2/8 10:50 上午
 */
public class ConcurrencyText {

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            listFileText(i);
        }
    }

    public static void listFileText(int count) {
        ConcurrencyTester tester = ThreadUtil.concurrencyTest(50, () -> {
            // 测试的逻辑内容
            long time = System.currentTimeMillis();
            HttpRequest.get("http://localhost:8088/list?userId=5e2d6675aab5fa4b7fecb59b&username=jmal&currentDirectory=&sortableProp=name&order=ascending&queryCondition=%7B%22isFolder%22:null%7D&pageIndex=1&pageSize=50")
                    .header(AuthInterceptor.JMAL_TOKEN,"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJXRUIiLCJpc3MiOiJTZXJ2aWNlIiwidXNlcm5hbWUiOiJqbWFsIn0.Rm6wR71pGk7O74Pd6dEi8D31G2Q26jA_7YZWoK1J3wI")
                    .header("Host","localhost:8088")
                    .header("User-Agent","Java")
                    .execute();
            Console.log("{} test finished, time consuming: {}", Thread.currentThread().getName(), System.currentTimeMillis() - time);
        });
        // 获取总的执行时间，单位毫秒
        Console.log(tester.getInterval(), count);
    }
}
