package io.tehuti.utils;


public class RedundantLogFilterTest {

  /**
   * Junit test shutdown will force kill all the live threads (non-daemon), so that here we use a cmd application
   * to validate whether {@link RedundantLogFilter} would stuck or not when {@link RedundantLogFilter#shutdown()} is
   * not explicitly invoked.
   */
  public static void main(String[] args) throws InterruptedException {
    RedundantLogFilter logFilter = new RedundantLogFilter(10, 1000);
    logFilter.isRedundantLog("test");
  }
}
