package io.tehuti.utils;


public class RedundantLogFilterTest {

  public static void main(String[] args) throws InterruptedException {
    RedundantLogFilter logFilter = new RedundantLogFilter(10, 1000);
    logFilter.isRedundantLog("test");
  }
}
