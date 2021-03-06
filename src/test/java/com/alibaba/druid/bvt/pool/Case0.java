package com.alibaba.druid.bvt.pool;

import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.alibaba.druid.mock.MockDriver;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.stat.DruidDataSourceStatManager;

public class Case0 extends TestCase {

    protected void setUp() throws Exception {
        Assert.assertEquals(0, DruidDataSourceStatManager.getInstance().getDataSourceList().size());
    }

    protected void tearDown() throws Exception {
        Assert.assertEquals(0, DruidDataSourceStatManager.getInstance().getDataSourceList().size());
    }

    public void test_0() throws Exception {

        final DruidDataSource dataSource = new DruidDataSource();

        dataSource.setDriver(new MockDriver() {

        });
        dataSource.setUrl("jdbc:mock:");

        dataSource.setMinIdle(0);
        dataSource.setMaxActive(2);
        dataSource.setMaxIdle(2);

        Connection conn1 = dataSource.getConnection();
        Connection conn2 = dataSource.getConnection();

        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completeLatch = new CountDownLatch(1);
        final AtomicInteger waitCount = new AtomicInteger();
        Thread t = new Thread() {

            public void run() {
                try {
                    startLatch.countDown();
                    waitCount.incrementAndGet();
                    Connection conn = dataSource.getConnection();
                    waitCount.decrementAndGet();
                    conn.close();

                    completeLatch.countDown();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();

        startLatch.await();
        Assert.assertFalse(completeLatch.await(1, TimeUnit.SECONDS));
        conn1.close();
        Assert.assertTrue(completeLatch.await(1, TimeUnit.SECONDS));
        conn2.close();
        Assert.assertTrue(completeLatch.await(1, TimeUnit.SECONDS));

        dataSource.close();
    }
}
