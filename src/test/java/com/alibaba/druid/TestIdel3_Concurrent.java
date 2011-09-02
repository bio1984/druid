package com.alibaba.druid;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.ObjectName;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.alibaba.druid.mock.MockDriver;
import com.alibaba.druid.pool.DruidDataSource;

public class TestIdel3_Concurrent extends TestCase {

    public void test_idle2() throws Exception {
        MockDriver driver = new MockDriver();

        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:mock:xxx");
        dataSource.setDriver(driver);
        dataSource.setInitialSize(1);
        dataSource.setMaxActive(2);
        dataSource.setMaxIdle(2);
        dataSource.setMinIdle(1);
        dataSource.setMinEvictableIdleTimeMillis(300 * 1000); // 300 / 10
        dataSource.setTimeBetweenEvictionRunsMillis(180 * 1000); // 180 / 10
        dataSource.setTestWhileIdle(true);
        dataSource.setTestOnBorrow(false);
        dataSource.setValidationQuery("SELECT 1");
        dataSource.setFilters("stat");
        
        ManagementFactory.getPlatformMBeanServer().registerMBean(dataSource, new ObjectName("com.alibaba:type=DataSource"));

        // 第一次创建连接
        {
            Assert.assertEquals(0, dataSource.getCreateCount());
            Assert.assertEquals(0, dataSource.getActiveCount());

            Connection conn = dataSource.getConnection();

            Assert.assertEquals(dataSource.getInitialSize(), dataSource.getCreateCount());
            Assert.assertEquals(dataSource.getInitialSize(), driver.getConnections().size());
            Assert.assertEquals(1, dataSource.getActiveCount());

            conn.close();
            Assert.assertEquals(0, dataSource.getDestroyCount());
            Assert.assertEquals(2, driver.getConnections().size());
            Assert.assertEquals(2, dataSource.getCreateCount());
            Assert.assertEquals(0, dataSource.getActiveCount());
        }

        {
            // 并发创建14个
            concurrent(driver, dataSource, 3);
        }

        // 连续打开关闭单个连接
        for (int i = 0; i < 1000; ++i) {
            Assert.assertEquals(0, dataSource.getActiveCount());
            Connection conn = dataSource.getConnection();

            Assert.assertEquals(1, dataSource.getActiveCount());
            conn.close();
        }
        Assert.assertEquals(2, dataSource.getPoolingCount());

        dataSource.close();
    }

    private void concurrent(final MockDriver driver, final DruidDataSource dataSource, final int count) throws Exception {
        final int LOOP_COUNT = 1000;
        Thread[] threads = new Thread[count];
        final CountDownLatch endLatch = new CountDownLatch(count);
        for (int i = 0; i < count; ++i) {
            threads[i] = new Thread("thread-" + i) {
                public void run() {
                    try {
                        for (int i = 0; i < LOOP_COUNT; ++i) {
                            Connection conn = dataSource.getConnection();
                            conn.isClosed();
                            conn.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        endLatch.countDown();
                    }
                }
            };
        }
        
        for (int i = 0; i < count; ++i) {
            threads[i].start();
        }
        
        endLatch.await();
        System.out.println("concurrent end");
        
        int max = count > dataSource.getMaxActive() ? dataSource.getMaxActive() : count;
        Assert.assertEquals(max, driver.getConnections().size());
        
    }
}